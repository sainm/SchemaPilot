package org.sainm.schemapilot.datamove;

public record NumericBounds(Long minValue, Long maxValue) {
    public static NumericBounds unknown() {
        return new NumericBounds(null, null);
    }

    public boolean known() {
        return minValue != null && maxValue != null;
    }
}
