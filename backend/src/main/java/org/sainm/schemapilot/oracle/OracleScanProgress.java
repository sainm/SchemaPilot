package org.sainm.schemapilot.oracle;

public record OracleScanProgress(
        int percent,
        String stage,
        String message
) {
}
