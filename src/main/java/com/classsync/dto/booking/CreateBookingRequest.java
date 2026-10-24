package com.classsync.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateBookingRequest(
        @NotNull(message = "Room ID is required")
        @Positive(message = "Room ID must be positive")
        Integer roomId,

        @NotNull(message = "Date is required")
        LocalDate date,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        @NotBlank(message = "Course code is required")
        @Size(max = 30, message = "Course code must not exceed 30 characters")
        String courseCode,

        @NotBlank(message = "Course name is required")
        @Size(max = 100, message = "Course name must not exceed 100 characters")
        String courseName
) {}
