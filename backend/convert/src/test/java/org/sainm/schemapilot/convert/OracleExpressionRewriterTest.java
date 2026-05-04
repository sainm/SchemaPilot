package org.sainm.schemapilot.convert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OracleExpressionRewriterTest {

    private final OracleExpressionRewriter rewriter = new OracleExpressionRewriter();

    @Test
    void doesNotBlindlyRewriteSysdateToCurrentTimestamp() {
        ExpressionRewrite rewrite = rewriter.rewrite("SYSDATE");

        assertThat(rewrite.targetExpression()).isEqualTo("clock_timestamp()");
        assertThat(rewrite.reviewRequired()).isTrue();
        assertThat(rewrite.notes()).anySatisfy(note -> assertThat(note).contains("CURRENT_TIMESTAMP"));
    }
}
