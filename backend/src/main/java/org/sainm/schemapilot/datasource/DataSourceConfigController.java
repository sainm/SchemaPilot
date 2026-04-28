package org.sainm.schemapilot.datasource;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/datasources")
public class DataSourceConfigController {
    private final DataSourceConfigService service;

    public DataSourceConfigController(DataSourceConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DataSourceConfigResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<DataSourceConfigResponse> create(@Valid @RequestBody SaveDataSourceConfigRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PostMapping("/{configId}/test")
    public ApiResponse<DataSourceConnectionTestResult> test(@PathVariable UUID configId) {
        return ApiResponse.ok(service.test(configId));
    }
}
