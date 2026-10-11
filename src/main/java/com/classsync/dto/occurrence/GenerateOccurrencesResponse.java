package com.classsync.dto.occurrence;

import java.time.LocalDate;

public record GenerateOccurrencesResponse(
        Integer timetableVersionId,
        LocalDate startDate,
        LocalDate endDate,
        int createdCount,
        int skippedExistingCount
) {}
