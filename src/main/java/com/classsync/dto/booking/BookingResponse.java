package com.classsync.dto.booking;

import com.classsync.entity.ScheduleEventType;
import com.classsync.entity.ScheduleStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookingResponse(
        Integer id,
        ScheduleEventType eventType,
        ScheduleStatus status,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String courseCode,
        String courseName,
        BookingProfessorSummary professor,
        BookingRoomSummary room
) {}
