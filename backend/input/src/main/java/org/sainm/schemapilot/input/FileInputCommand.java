package org.sainm.schemapilot.input;

import java.util.List;
import java.util.UUID;

public record FileInputCommand(
        UUID projectId,
        UUID sourceProjectId,
        InputSourceType type,
        List<InputFile> files) {

    public FileInputCommand {
        files = List.copyOf(files);
    }
}
