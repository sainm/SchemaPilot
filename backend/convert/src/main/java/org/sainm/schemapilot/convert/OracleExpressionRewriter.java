package org.sainm.schemapilot.convert;

import java.util.List;
import java.util.Locale;

public class OracleExpressionRewriter {

    public ExpressionRewrite rewrite(String expression) {
        String normalized = expression.trim().toUpperCase(Locale.ROOT);
        if ("SYSDATE".equals(normalized) || "SYSTIMESTAMP".equals(normalized)) {
            return new ExpressionRewrite(
                    expression,
                    "clock_timestamp()",
                    true,
                    List.of("Review time semantics: PostgreSQL CURRENT_TIMESTAMP is transaction start time, not a direct SYSDATE equivalent."));
        }
        return new ExpressionRewrite(expression, expression, false, List.of());
    }
}
