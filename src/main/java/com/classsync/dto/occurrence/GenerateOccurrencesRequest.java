package com.classsync.dto.occurrence;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record GenerateOccurrencesRequest(
        @NotNull(message = "Timetable version ID is required")
        Integer timetableVersionId,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate
) {}
