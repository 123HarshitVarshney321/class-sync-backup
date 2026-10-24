package com.classsync.dto.recommendation;

import java.util.List;

public record RecommendationResponse(
        RecommendationRoomSummary room,
        Double score,
        List<String> reasons
) {}
