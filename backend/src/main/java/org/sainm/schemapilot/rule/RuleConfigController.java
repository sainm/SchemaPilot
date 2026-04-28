package org.sainm.schemapilot.rule;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
public class RuleConfigController {
    private final RuleConfigService service;

    public RuleConfigController(RuleConfigService service) {
        this.service = service;
    }

    @PostMapping("/configs")
    public ApiResponse<RuleConfigUpdateResponse> update(@Valid @RequestBody RuleConfigUpdateRequest request) {
        return ApiResponse.ok(service.update(request));
    }
}
