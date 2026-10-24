package com.classsync.dto.recommendation;

import com.classsync.entity.RoomType;

public record RecommendationRoomSummary(
        Integer id,
        String roomNumber,
        RoomType roomType,
        Integer capacity,
        Integer complexId,
        String complexName
) {}
