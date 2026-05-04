package org.sainm.schemapilot.convert;

import java.util.List;

public record ExpressionRewrite(
        String sourceExpression,
        String targetExpression,
        boolean reviewRequired,
        List<String> notes) {

    public ExpressionRewrite {
        notes = List.copyOf(notes);
    }
}
