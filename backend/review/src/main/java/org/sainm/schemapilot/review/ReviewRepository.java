package org.sainm.schemapilot.review;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository {

    void save(ReviewRecord record);

    List<ReviewRecord> findReviews(UUID projectId);
}
