package org.sainm.schemapilot.convert;

import java.util.List;

public record TypeMappingResult(
        String sourceType,
        String targetType,
        boolean reviewRequired,
        List<String> notes) {

    public TypeMappingResult {
        notes = List.copyOf(notes);
    }
}
