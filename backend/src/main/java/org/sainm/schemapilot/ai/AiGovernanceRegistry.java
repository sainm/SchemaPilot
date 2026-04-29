package org.sainm.schemapilot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class AiGovernanceRegistry {
    private final List<AiProviderConfig> providerConfigs;
    private final List<PromptTemplate> promptTemplates;

    public AiGovernanceRegistry(
            @Value("${schemapilot.ai.cloud.endpoint:}") String cloudEndpoint,
            @Value("${schemapilot.ai.cloud.model:gpt-5.4-mini}") String cloudModel,
            @Value("${schemapilot.ai.cloud.secret-configured:false}") boolean cloudSecretConfigured,
            @Value("${schemapilot.ai.local.endpoint:http://localhost:11434}") String localEndpoint,
            @Value("${schemapilot.ai.local.model:qwen2.5-coder:latest}") String localModel
    ) {
        this.providerConfigs = List.of(
                new AiProviderConfig("mock", AiProviderType.MOCK, "Local mock provider", "in-process", "schemapilot-rule-backed", true, false),
                new AiProviderConfig("cloud-openai-compatible", AiProviderType.CLOUD, "Cloud OpenAI-compatible provider", cloudEndpoint, cloudModel, false, cloudSecretConfigured),
                new AiProviderConfig("local-openai-compatible", AiProviderType.LOCAL, "Local OpenAI-compatible provider", localEndpoint, localModel, false, false)
        );
        var now = Instant.now();
        this.promptTemplates = List.of(
                new PromptTemplate("risk-explanation", "risk-explanation-v1", "Explain one migration risk with cited knowledge.", "riskType + objectType + redacted original SQL + knowledge chunks", true, now),
                new PromptTemplate("sql-rewrite", "sql-rewrite-v1", "Suggest a PostgreSQL rewrite without applying it.", "objectType + original SQL + generated SQL + risk list + knowledge chunks", true, now),
                new PromptTemplate("plsql-explanation", "plsql-explanation-v1", "Explain PL/SQL rewrite boundaries.", "objectType + PL/SQL body + risk list + knowledge chunks", true, now),
                new PromptTemplate("precheck-summary", "precheck-summary-v1", "Summarize a precheck report for review.", "statement counts + risk distribution + top risk types + cited chunks", true, now),
                new PromptTemplate("execution-error-diagnosis", "execution-error-diagnosis-v1", "Diagnose DDL/COPY/runtime execution errors.", "phase + object + SQL + redacted error + knowledge chunks", true, now),
                new PromptTemplate("validation-diff-diagnosis", "validation-diff-diagnosis-v1", "Diagnose validation differences.", "check type + source/target values + issue codes + knowledge chunks", true, now),
                new PromptTemplate("rule-candidate", "rule-candidate-v1", "Propose a human-reviewed conversion rule candidate.", "source + original SQL + revised SQL + risk types", true, now),
                new PromptTemplate("project-question", "project-question-v1", "Answer project-level migration questions using summarized project context.", "question + project summary + top risks + object names", true, now),
                new PromptTemplate("long-plsql-summary", "long-plsql-summary-v1", "Summarize long PL/SQL in chunks.", "object + chunk summaries + sensitive-data redaction", true, now),
                new PromptTemplate("package-modernization", "package-modernization-v1", "Generate an Oracle package modernization plan.", "package source + routines + state + built-in package usage", true, now),
                new PromptTemplate("historical-rule-template", "historical-rule-template-v1", "Recommend rule templates from historical migration cases.", "current risks + object type + historical cases + enabled rule hints", true, now)
        );
    }

    public List<AiProviderConfig> providerConfigs() {
        return providerConfigs;
    }

    public List<PromptTemplate> promptTemplates() {
        return promptTemplates;
    }

    public String activePromptVersion(String key) {
        return promptTemplates.stream()
                .filter(template -> template.key().equals(key))
                .filter(PromptTemplate::active)
                .map(PromptTemplate::version)
                .findFirst()
                .orElse(key + "-v1");
    }
}
