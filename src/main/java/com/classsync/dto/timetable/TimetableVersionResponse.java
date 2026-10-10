package com.classsync.dto.timetable;

import com.classsync.entity.TimetableVersionStatus;

import java.time.LocalDateTime;

public record TimetableVersionResponse(
        Integer id,
        String academicYear,
        String semester,
        Integer versionNumber,
        TimetableVersionStatus status,
        Integer createdById,
        String createdByName,
        LocalDateTime createdAt
) {}
