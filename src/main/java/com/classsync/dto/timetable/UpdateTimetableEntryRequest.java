package com.classsync.dto.timetable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record UpdateTimetableEntryRequest(
        @NotNull(message = "Professor ID is required")
        Integer professorId,

        @NotBlank(message = "Course code is required")
        @Size(max = 30, message = "Course code must not exceed 30 characters")
        String courseCode,

        @NotBlank(message = "Course name is required")
        @Size(max = 100, message = "Course name must not exceed 100 characters")
        String courseName,

        @NotNull(message = "Room ID is required")
        Integer roomId,

        @NotNull(message = "Day of week is required")
        @Min(value = 1, message = "Day of week must be between 1 and 7")
        @Max(value = 7, message = "Day of week must be between 1 and 7")
        Byte dayOfWeek,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime
) {}
