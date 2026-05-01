package org.sainm.schemapilot.knowledge;

public record KnowledgeMetrics(
        long searchCount,
        long hitCount,
        long acceptedCount,
        long rejectedCount,
        double hitRate,
        double adoptionRate
) {
}
