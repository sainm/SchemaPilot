package org.sainm.schemapilot.convert;

import java.util.List;
import java.util.UUID;

public interface ConversionRepository {

    void replaceInputSourceConversions(UUID inputSourceId, List<ConversionResult> results);

    List<ConversionResult> findConversions(UUID projectId);
}
