package org.sainm.schemapilot.sql;

import org.sainm.schemapilot.model.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class RiskScoringService {
    public RiskLevel highestLevel(List<DetectedRisk> risks) {
        return risks.stream()
                .map(DetectedRisk::level)
                .max(Comparator.comparingInt(this::severity))
                .orElse(RiskLevel.LOW);
    }

    public int compatibilityScore(List<AnalyzedStatement> statements) {
        var penalty = statements.stream()
                .flatMap(statement -> statement.risks().stream())
                .mapToInt(risk -> switch (risk.level()) {
                    case LOW -> 1;
                    case MEDIUM -> 5;
                    case HIGH -> 15;
                    case BLOCKER -> 35;
                })
                .sum();
        return Math.max(0, 100 - penalty);
    }

    private int severity(RiskLevel level) {
        return switch (level) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case BLOCKER -> 4;
        };
    }
}
