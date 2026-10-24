package com.classsync.controller;

import com.classsync.dto.recommendation.RecommendationResponse;
import com.classsync.entity.RoomType;
import com.classsync.service.RecommendationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/rooms/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESSOR', 'ADMIN')")
    public ResponseEntity<List<RecommendationResponse>> getRecommendations(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(name = "endTime") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(name = "capacity", required = false) Integer capacity,
            @RequestParam(name = "complexId", required = false) Integer complexId,
            @RequestParam(name = "roomType", required = false) RoomType roomType,
            @RequestParam(name = "facilities", required = false) String facilities,
            Principal principal) {

        List<RecommendationResponse> recommendations = recommendationService.recommendRooms(
                complexId, date, startTime, endTime, capacity, roomType, facilities, principal.getName()
        );

        return ResponseEntity.ok(recommendations);
    }
}
