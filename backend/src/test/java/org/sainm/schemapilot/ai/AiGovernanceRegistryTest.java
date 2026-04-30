package org.sainm.schemapilot.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiGovernanceRegistryTest {
    @Test
    void treatsLocalLlmKnowledgeBaseAsPrivateDeploymentDefault() {
        var registry = new AiGovernanceRegistry(
                "",
                "gpt-5.4-mini",
                false,
                "http://localhost:11434",
                "qwen2.5-coder:latest"
        );

        var providers = registry.providerConfigs();

        assertThat(providers.getFirst().id()).isEqualTo("local-openai-compatible");
        assertThat(providers.getFirst().knowledgeMode()).isEqualTo("local-rag");
        assertThat(providers.getFirst().preferredForPrivateDeployment()).isTrue();
        assertThat(providers.getFirst().externalNetworkRequired()).isFalse();
        assertThat(providers)
                .filteredOn(provider -> provider.type() == AiProviderType.CLOUD)
                .allSatisfy(provider -> {
                    assertThat(provider.preferredForPrivateDeployment()).isFalse();
                    assertThat(provider.externalNetworkRequired()).isTrue();
                });
    }
}
