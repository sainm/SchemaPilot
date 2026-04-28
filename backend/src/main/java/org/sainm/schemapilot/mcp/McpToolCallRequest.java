package org.sainm.schemapilot.mcp;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record McpToolCallRequest(
        @NotBlank
        String toolName,
        Map<String, Object> arguments,
        Boolean dryRun
) {
}
