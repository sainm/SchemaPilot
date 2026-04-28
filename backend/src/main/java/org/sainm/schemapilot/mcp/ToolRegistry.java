package org.sainm.schemapilot.mcp;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {
    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        register(new ToolDefinition("sql.analyze", "Analyze Oracle SQL and return preserved objects, drafts and risks.", false, true, 3000));
        register(new ToolDefinition("knowledge.search", "Search redacted migration knowledge chunks.", false, true, 2000));
        register(new ToolDefinition("skill.run", "Run a versioned migration skill; dry-run by default through MCP.", true, true, 3000));
    }

    public List<ToolDefinition> definitions() {
        return List.copyOf(tools.values());
    }

    public ToolDefinition get(String name) {
        var definition = tools.get(name);
        if (definition == null) {
            throw new BadRequestException("MCP tool is not allowlisted: " + name);
        }
        return definition;
    }

    private void register(ToolDefinition definition) {
        tools.put(definition.name(), definition);
    }
}
