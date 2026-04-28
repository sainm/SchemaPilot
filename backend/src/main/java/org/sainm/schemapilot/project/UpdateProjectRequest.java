package org.sainm.schemapilot.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @NotBlank
        @Size(max = 128)
        String name,
        @Size(max = 2000)
        String description
) {
}
