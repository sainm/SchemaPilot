package org.sainm.schemapilot.rule;

import org.sainm.schemapilot.workbench.WorkbenchService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RuleConfigService {
    private final WorkbenchService workbenchService;

    public RuleConfigService(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    public RuleConfigUpdateResponse update(RuleConfigUpdateRequest request) {
        var expired = workbenchService.expireAllForRuleConfigChange(request.name() + "@" + request.version());
        return new RuleConfigUpdateResponse(
                request.name(),
                request.version(),
                Instant.now(),
                expired.stream().map(snapshot -> snapshot.id()).toList()
        );
    }
}
