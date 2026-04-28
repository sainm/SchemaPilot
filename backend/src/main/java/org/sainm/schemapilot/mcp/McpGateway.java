package org.sainm.schemapilot.mcp;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.sainm.schemapilot.knowledge.KnowledgeSearchRequest;
import org.sainm.schemapilot.knowledge.KnowledgeService;
import org.sainm.schemapilot.skill.SkillExecutor;
import org.sainm.schemapilot.sql.ManualSqlAnalysisService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class McpGateway {
    private final ToolRegistry toolRegistry;
    private final ManualSqlAnalysisService analysisService;
    private final KnowledgeService knowledgeService;
    private final SkillExecutor skillExecutor;
    private final boolean externalClientEnabled;
    private final java.util.concurrent.ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<McpToolCallRecord> toolCalls = new CopyOnWriteArrayList<>();
    private final List<McpResource> resources;
    private final List<McpPrompt> prompts;

    public McpGateway(
            ToolRegistry toolRegistry,
            ManualSqlAnalysisService analysisService,
            KnowledgeService knowledgeService,
            SkillExecutor skillExecutor,
            @Value("${schemapilot.mcp.external-client-enabled:false}") boolean externalClientEnabled
    ) {
        this.toolRegistry = toolRegistry;
        this.analysisService = analysisService;
        this.knowledgeService = knowledgeService;
        this.skillExecutor = skillExecutor;
        this.externalClientEnabled = externalClientEnabled;
        this.resources = List.of(
                new McpResource("migration-risk-rules", "Read-only Oracle to PostgreSQL risk rules", "application/json", "/api/knowledge/chunks"),
                new McpResource("skill-catalog", "Read-only built-in migration skills", "application/json", "/api/skills"),
                new McpResource("mcp-safety-policy", "Tool allowlist, dry-run and audit policy", "text/plain", "External MCP client is disabled by default. Write-like tools must run dry-run unless explicitly enabled.")
        );
        this.prompts = List.of(
                new McpPrompt("explain-risk", "Explain migration risks using cited knowledge chunks."),
                new McpPrompt("draft-conversion", "Draft PostgreSQL SQL and mark human-review gates."),
                new McpPrompt("diagnose-error", "Diagnose migration failures without applying changes.")
        );
    }

    public Map<String, Object> status() {
        return Map.of(
                "externalClientEnabled", externalClientEnabled,
                "allowedTools", toolRegistry.definitions().stream().map(ToolDefinition::name).toList(),
                "writeToolsDefaultDryRun", true,
                "auditRecordCount", toolCalls.size()
        );
    }

    public List<McpResource> resources() {
        return resources;
    }

    public McpResource resource(String resourceId) {
        return resources.stream()
                .filter(resource -> resource.id().equals(resourceId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("MCP resource not found: " + resourceId));
    }

    public List<McpPrompt> prompts() {
        return prompts;
    }

    public McpToolCallRecord callTool(McpToolCallRequest request) {
        var startedAt = Instant.now();
        ToolDefinition tool;
        try {
            tool = toolRegistry.get(request.toolName());
        } catch (RuntimeException ex) {
            return audit(request, false, false, null, ex.getMessage(), startedAt);
        }
        if (!tool.enabled()) {
            return audit(request, true, true, null, "Tool is disabled.", startedAt);
        }
        var dryRun = request.dryRun() == null ? tool.writeOperation() : request.dryRun();
        if (tool.writeOperation() && !dryRun) {
            return audit(request, false, false, null, "Write-like MCP tools require dryRun=true in the MVP.", startedAt);
        }
        try {
            var result = CompletableFuture
                    .supplyAsync(() -> invoke(tool.name(), request.arguments(), dryRun), toolExecutor)
                    .get(tool.timeoutMillis(), TimeUnit.MILLISECONDS);
            return audit(request, true, true, result, "Tool call completed.", startedAt);
        } catch (java.util.concurrent.TimeoutException ex) {
            return audit(request, true, false, null, "MCP tool timed out after " + tool.timeoutMillis() + " ms.", startedAt);
        } catch (java.util.concurrent.ExecutionException ex) {
            var cause = ex.getCause() == null ? ex : ex.getCause();
            return audit(request, true, false, null, cause.getMessage(), startedAt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return audit(request, true, false, null, "MCP tool call was interrupted.", startedAt);
        } catch (RuntimeException ex) {
            return audit(request, true, false, null, ex.getMessage(), startedAt);
        }
    }

    public List<McpToolCallRecord> toolCalls() {
        return new ArrayList<>(toolCalls);
    }

    private Object invoke(String toolName, Map<String, Object> arguments, boolean dryRun) {
        var args = arguments == null ? Map.<String, Object>of() : arguments;
        return switch (toolName) {
            case "sql.analyze" -> analysisService.analyze(requiredString(args, "sql"));
            case "knowledge.search" -> knowledgeService.search(new KnowledgeSearchRequest(
                    requiredString(args, "query"),
                    stringMap(args.get("metadata")),
                    integerValue(args.get("limit"))
            ));
            case "skill.run" -> {
                if (dryRun) {
                    yield Map.of(
                            "dryRun", true,
                            "skillId", requiredString(args, "skillId"),
                            "message", "Skill invocation validated but not executed."
                    );
                }
                yield skillExecutor.execute(requiredString(args, "skillId"), requiredString(args, "sql"));
            }
            default -> throw new BadRequestException("Unsupported MCP tool: " + toolName);
        };
    }

    private McpToolCallRecord audit(
            McpToolCallRequest request,
            boolean allowed,
            boolean success,
            Object result,
            String message,
            Instant startedAt
    ) {
        var record = new McpToolCallRecord(
                UUID.randomUUID(),
                request.toolName(),
                request.dryRun() == null,
                request.dryRun() == null ? null : request.dryRun(),
                allowed,
                success,
                message,
                result,
                startedAt,
                Instant.now()
        );
        toolCalls.add(record);
        return record;
    }

    private String requiredString(Map<String, Object> args, String key) {
        var value = args.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new BadRequestException("Missing MCP tool argument: " + key);
        }
        return value.toString();
    }

    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var converted = new LinkedHashMap<String, String>();
        raw.forEach((key, mapValue) -> {
            if (key != null && mapValue != null) {
                converted.put(key.toString(), mapValue.toString());
            }
        });
        return converted;
    }
}
