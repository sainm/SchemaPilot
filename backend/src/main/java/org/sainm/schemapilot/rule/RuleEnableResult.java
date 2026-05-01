package org.sainm.schemapilot.rule;

import java.util.List;

public record RuleEnableResult(
        RuleCandidate candidate,
        List<RuleTestResult> testResults
) {
}
