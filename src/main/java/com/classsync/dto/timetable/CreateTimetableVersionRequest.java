package com.classsync.dto.timetable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTimetableVersionRequest(
        @NotBlank(message = "Academic year is required")
        @Size(max = 20, message = "Academic year must not exceed 20 characters")
        String academicYear,

        @NotBlank(message = "Semester is required")
        @Size(max = 20, message = "Semester must not exceed 20 characters")
        String semester
) {}
