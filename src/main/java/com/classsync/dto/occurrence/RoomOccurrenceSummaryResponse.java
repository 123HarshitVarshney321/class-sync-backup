package com.classsync.dto.occurrence;

import com.classsync.dto.complex.ComplexSummaryResponse;
import com.classsync.entity.RoomType;

public record RoomOccurrenceSummaryResponse(
        Integer id,
        String roomNumber,
        RoomType roomType,
        Integer capacity,
        Boolean active,
        ComplexSummaryResponse complex
) {}
