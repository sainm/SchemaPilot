package org.sainm.schemapilot.convert;

import java.util.List;
import java.util.UUID;

import org.sainm.schemapilot.rule.RuleHit;

public record ConversionResult(
        UUID id,
        UUID projectId,
        UUID sourceProjectId,
        UUID inputSourceId,
        UUID objectId,
        String sourceSql,
        String targetSql,
        ConversionLevel level,
        List<RuleHit> ruleHits,
        List<String> notes) {

    public ConversionResult {
        ruleHits = List.copyOf(ruleHits);
        notes = List.copyOf(notes);
    }
}
