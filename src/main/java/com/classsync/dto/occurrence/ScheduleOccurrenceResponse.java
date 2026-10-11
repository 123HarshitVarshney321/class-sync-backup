package com.classsync.dto.occurrence;

import com.classsync.dto.timetable.ProfessorSummaryResponse;
import com.classsync.entity.ScheduleEventType;
import com.classsync.entity.ScheduleStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ScheduleOccurrenceResponse(
        Integer id,
        Integer timetableEntryId,
        ProfessorSummaryResponse professor,
        RoomOccurrenceSummaryResponse room,
        LocalDate scheduleDate,
        LocalTime startTime,
        LocalTime endTime,
        ScheduleEventType eventType,
        ScheduleStatus status,
        String courseCode,
        String courseName,
        Integer createdById,
        String createdByName,
        LocalDateTime createdAt
) {}
