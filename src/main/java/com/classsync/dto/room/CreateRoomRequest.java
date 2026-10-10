package com.classsync.dto.room;

import com.classsync.entity.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateRoomRequest(
        @NotBlank(message = "Room number is required")
        @Size(max = 30, message = "Room number must not exceed 30 characters")
        String roomNumber,

        @NotNull(message = "Room type is required")
        RoomType roomType,

        @NotNull(message = "Capacity is required")
        @Positive(message = "Capacity must be greater than 0")
        Integer capacity,

        Set<Integer> facilityIds
) {}
