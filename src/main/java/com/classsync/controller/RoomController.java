package com.classsync.controller;

import com.classsync.dto.common.StatusUpdateRequest;
import com.classsync.dto.room.CreateRoomRequest;
import com.classsync.dto.room.RoomDetailResponse;
import com.classsync.dto.room.RoomSummaryResponse;
import com.classsync.dto.room.UpdateRoomRequest;
import com.classsync.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/complexes/{complexId}/rooms")
    public ResponseEntity<List<RoomSummaryResponse>> getActiveRoomsForComplex(@PathVariable Integer complexId) {
        return ResponseEntity.ok(roomService.getActiveRoomsForComplex(complexId));
    }

    @PostMapping("/complexes/{complexId}/rooms")
    public ResponseEntity<RoomDetailResponse> createRoom(@PathVariable Integer complexId,
                                                         @Valid @RequestBody CreateRoomRequest request) {
        RoomDetailResponse response = roomService.createRoom(complexId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/rooms/{id}")
    public ResponseEntity<RoomDetailResponse> getActiveRoomById(@PathVariable Integer id) {
        return ResponseEntity.ok(roomService.getActiveRoomById(id));
    }

    @PutMapping("/rooms/{id}")
    public ResponseEntity<RoomDetailResponse> updateRoom(@PathVariable Integer id,
                                                         @Valid @RequestBody UpdateRoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(id, request));
    }

    @PatchMapping("/rooms/{id}/status")
    public ResponseEntity<RoomSummaryResponse> updateRoomStatus(@PathVariable Integer id,
                                                                @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(roomService.updateRoomStatus(id, request));
    }
}
