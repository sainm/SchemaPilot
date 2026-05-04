package org.sainm.schemapilot.input;

import java.util.List;

public record InputBatchImportResult(
        InputBatch batch,
        List<InputSource> sources) {

    public InputBatchImportResult {
        sources = List.copyOf(sources);
    }
}
