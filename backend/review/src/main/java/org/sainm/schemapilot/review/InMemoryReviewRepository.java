package org.sainm.schemapilot.review;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryReviewRepository implements ReviewRepository {

    private final ConcurrentMap<UUID, ReviewRecord> records = new ConcurrentHashMap<>();

    @Override
    public void save(ReviewRecord record) {
        records.put(record.id(), record);
    }

    @Override
    public List<ReviewRecord> findReviews(UUID projectId) {
        return records.values().stream()
                .filter(record -> record.projectId().equals(projectId))
                .sorted(Comparator.comparing(ReviewRecord::createdAt).reversed())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
