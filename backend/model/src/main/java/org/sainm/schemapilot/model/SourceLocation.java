package org.sainm.schemapilot.model;

public record SourceLocation(
        String path,
        int startLine,
        int endLine,
        int startOffset,
        int endOffset) {
}
