package com.classsync.dto.room;

import com.classsync.entity.RoomType;

public record RoomSummaryResponse(
        Integer id,
        String roomNumber,
        RoomType roomType,
        Integer capacity,
        Boolean active
) {}
