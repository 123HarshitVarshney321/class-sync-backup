package com.classsync.dto.facility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FacilityRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name
) {}
