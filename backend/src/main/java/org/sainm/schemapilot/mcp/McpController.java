package org.sainm.schemapilot.mcp;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpController {
    private final McpGateway gateway;

    public McpController(McpGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(gateway.status());
    }

    @GetMapping("/resources")
    public ApiResponse<List<McpResource>> resources() {
        return ApiResponse.ok(gateway.resources());
    }

    @GetMapping("/resources/{resourceId}")
    public ApiResponse<McpResource> resource(@PathVariable String resourceId) {
        return ApiResponse.ok(gateway.resource(resourceId));
    }

    @GetMapping("/prompts")
    public ApiResponse<List<McpPrompt>> prompts() {
        return ApiResponse.ok(gateway.prompts());
    }

    @PostMapping("/tools/call")
    public ApiResponse<McpToolCallRecord> callTool(@Valid @RequestBody McpToolCallRequest request) {
        return ApiResponse.ok(gateway.callTool(request));
    }

    @GetMapping("/tool-calls")
    public ApiResponse<List<McpToolCallRecord>> toolCalls() {
        return ApiResponse.ok(gateway.toolCalls());
    }
}
