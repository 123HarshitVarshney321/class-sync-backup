package com.classsync.dto.room;

import com.classsync.dto.complex.ComplexSummaryResponse;
import com.classsync.dto.facility.FacilityResponse;
import com.classsync.entity.RoomType;

import java.util.List;

public record AvailableRoomResponse(
        Integer id,
        String roomNumber,
        RoomType roomType,
        Integer capacity,
        Boolean active,
        ComplexSummaryResponse complex,
        List<FacilityResponse> facilities
) {}
