package org.sainm.schemapilot.workbench;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SaveFileImportJobsRequest(
        @NotEmpty
        @Size(max = 100)
        List<UUID> jobIds
) {
}
