package org.sainm.schemapilot.rule;

import org.junit.jupiter.api.Test;
import org.sainm.schemapilot.common.api.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleCandidateServiceTest {
    @Test
    void extractsManualEditRuleRequiresReviewAndReappliesAfterFixturesPass() {
        var service = new RuleCandidateService();
        var candidate = service.create(new RuleCandidateCreateRequest(
                RuleCandidateSource.MANUAL_EDIT,
                "project-a",
                "VIEW",
                "NVL",
                "select NVL(name, 'n/a') from users",
                "select COALESCE(name, 'n/a') from users"
        ));

        assertThat(candidate.status()).isEqualTo(RuleCandidateStatus.DRAFT);
        assertThat(candidate.sourceProject()).isEqualTo("project-a");
        assertThat(candidate.pattern()).contains("NVL");
        assertThatThrownBy(() -> service.enable(candidate.id()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("manually approved");

        service.addFixture(candidate.id(), new RuleFixtureRequest(
                "similar-view",
                "select NVL(email, 'none') from accounts",
                "select COALESCE(email, 'none') from accounts",
                true
        ));
        service.addFixture(candidate.id(), new RuleFixtureRequest(
                "negative",
                "select COALESCE(email, 'none') from accounts",
                "select COALESCE(email, 'none') from accounts",
                false
        ));
        var approved = service.review(candidate.id(), new RuleCandidateReviewRequest("reviewer-a", true));
        var enabled = service.enable(approved.id());

        assertThat(enabled.candidate().status()).isEqualTo(RuleCandidateStatus.ENABLED);
        assertThat(enabled.candidate().reviewer()).isEqualTo("reviewer-a");
        assertThat(enabled.testResults()).allMatch(RuleTestResult::passed);

        var reapplied = service.reapply(new RuleReapplyRequest("VIEW", "NVL", "select NVL(code, 'x') from customers"));
        assertThat(reapplied.revisedSql()).isEqualTo("select COALESCE(code, 'x') from customers");
        assertThat(reapplied.appliedRuleIds()).contains(enabled.candidate().id().toString());
    }

    @Test
    void rejectedCandidateDoesNotEnable() {
        var service = new RuleCandidateService();
        var candidate = service.create(new RuleCandidateCreateRequest(
                RuleCandidateSource.AI_SUGGESTION,
                "project-b",
                "TABLE",
                "CURRENT_TIME",
                "created_at DATE DEFAULT SYSDATE",
                "created_at timestamp DEFAULT CURRENT_TIMESTAMP"
        ));

        var rejected = service.review(candidate.id(), new RuleCandidateReviewRequest("reviewer-b", false));

        assertThat(rejected.status()).isEqualTo(RuleCandidateStatus.REJECTED);
        assertThatThrownBy(() -> service.enable(candidate.id()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void fixtureFailurePreventsEnablement() {
        var service = new RuleCandidateService();
        var candidate = service.create(new RuleCandidateCreateRequest(
                RuleCandidateSource.MANUAL_EDIT,
                "project-c",
                "VIEW",
                "NVL",
                "select NVL(name, 'n/a') from users",
                "select COALESCE(name, 'n/a') from users"
        ));
        service.addFixture(candidate.id(), new RuleFixtureRequest(
                "bad-expected",
                "select NVL(email, 'none') from accounts",
                "select email from accounts",
                true
        ));
        var approved = service.review(candidate.id(), new RuleCandidateReviewRequest("reviewer-c", true));

        var result = service.enable(approved.id());

        assertThat(result.candidate().status()).isEqualTo(RuleCandidateStatus.TEST_FAILED);
        assertThat(result.testResults()).anyMatch(test -> !test.passed());
        var reapplied = service.reapply(new RuleReapplyRequest("VIEW", "NVL", "select NVL(code, 'x') from customers"));
        assertThat(reapplied.appliedRuleIds()).isEmpty();
    }
}
