package org.sainm.schemapilot.parser;

public record ParsedStatement(
        String text,
        int startLine,
        int endLine,
        int startOffset,
        int endOffset) {
}
