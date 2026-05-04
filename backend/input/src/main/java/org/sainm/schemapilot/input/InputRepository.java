package org.sainm.schemapilot.input;

import java.util.List;
import java.util.UUID;

public interface InputRepository {

    void saveBatch(InputBatch batch);

    void saveSource(InputSource source);

    List<InputBatch> findBatches(UUID projectId);

    List<InputSource> findSources(UUID batchId);
}
