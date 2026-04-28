package org.sainm.schemapilot.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SqlRewriteRequest(
        @NotBlank
        String objectType,
        @NotBlank
        @Size(max = 12000)
        String originalSql,
        @NotBlank
        @Size(max = 12000)
        String postgresSql,
        List<String> riskTypes
) {
    public List<String> riskTypes() {
        return riskTypes == null ? List.of() : List.copyOf(riskTypes);
    }
}
