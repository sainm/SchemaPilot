package org.sainm.schemapilot.mcp;

public record McpResource(
        String id,
        String description,
        String mimeType,
        String content
) {
}
