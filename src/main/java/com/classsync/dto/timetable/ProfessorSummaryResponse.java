package com.classsync.dto.timetable;

public record ProfessorSummaryResponse(
        Integer id,
        String name,
        String email,
        String employeeCode
) {}
