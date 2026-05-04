package org.sainm.schemapilot.export;

import java.util.List;
import java.util.UUID;

public record SqlPackagePreview(
        UUID projectId,
        String status,
        int baselineCount,
        int approvedReviewCount,
        int blockerCount,
        List<String> fileNames,
        List<String> orderedObjectIds) {

    public SqlPackagePreview {
        fileNames = List.copyOf(fileNames);
        orderedObjectIds = List.copyOf(orderedObjectIds);
    }
}
