package org.sainm.schemapilot.mcp;

public record ToolDefinition(
        String name,
        String description,
        boolean writeOperation,
        boolean enabled,
        long timeoutMillis
) {
}
