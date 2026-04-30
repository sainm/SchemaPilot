package org.sainm.schemapilot.ai;

public record AiProviderConfig(
        String id,
        AiProviderType type,
        String displayName,
        String endpoint,
        String defaultModel,
        boolean enabled,
        boolean secretConfigured,
        String knowledgeMode,
        boolean preferredForPrivateDeployment,
        boolean externalNetworkRequired
) {
}
