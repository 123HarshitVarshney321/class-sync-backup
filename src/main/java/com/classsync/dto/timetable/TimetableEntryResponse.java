package com.classsync.dto.timetable;

import com.classsync.dto.room.RoomSummaryResponse;

import java.time.LocalTime;

public record TimetableEntryResponse(
        Integer id,
        Integer timetableVersionId,
        ProfessorSummaryResponse professor,
        String courseCode,
        String courseName,
        RoomSummaryResponse room,
        Byte dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {}
