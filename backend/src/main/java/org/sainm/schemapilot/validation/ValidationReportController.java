package org.sainm.schemapilot.validation;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/validation-reports")
public class ValidationReportController {
    private final ValidationReportService service;

    public ValidationReportController(ValidationReportService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<ValidationReport> create(@Valid @RequestBody ValidationRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<ValidationReport> get(@PathVariable UUID reportId) {
        return ApiResponse.ok(service.get(reportId));
    }
}
