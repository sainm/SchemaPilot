package org.sainm.schemapilot.skill;

import jakarta.validation.Valid;
import org.sainm.schemapilot.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final SkillRegistry registry;
    private final SkillExecutor executor;

    public SkillController(SkillRegistry registry, SkillExecutor executor) {
        this.registry = registry;
        this.executor = executor;
    }

    @GetMapping
    public ApiResponse<List<SkillDefinition>> definitions() {
        return ApiResponse.ok(registry.definitions());
    }

    @PostMapping("/{skillId}/run")
    public ApiResponse<SkillRun> run(@PathVariable String skillId, @Valid @RequestBody SkillRunRequest request) {
        return ApiResponse.ok(executor.execute(skillId, request.sql()));
    }
}
