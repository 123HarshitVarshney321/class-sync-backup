package com.classsync.controller;

import com.classsync.dto.occurrence.GenerateOccurrencesRequest;
import com.classsync.dto.occurrence.GenerateOccurrencesResponse;
import com.classsync.dto.occurrence.ScheduleOccurrenceResponse;
import com.classsync.entity.ScheduleStatus;
import com.classsync.service.ScheduleOccurrenceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedule-occurrences")
public class ScheduleOccurrenceController {

    private final ScheduleOccurrenceService scheduleOccurrenceService;

    public ScheduleOccurrenceController(ScheduleOccurrenceService scheduleOccurrenceService) {
        this.scheduleOccurrenceService = scheduleOccurrenceService;
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateOccurrencesResponse> generateOccurrences(
            @Valid @RequestBody GenerateOccurrencesRequest request,
            Principal principal) {
        GenerateOccurrencesResponse response = scheduleOccurrenceService.generateOccurrences(request, principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ScheduleOccurrenceResponse>> getScheduleOccurrences(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer professorId,
            @RequestParam(required = false) Integer roomId,
            @RequestParam(required = false) Integer complexId,
            @RequestParam(required = false) ScheduleStatus status) {
        List<ScheduleOccurrenceResponse> response = scheduleOccurrenceService.getScheduleOccurrences(
                startDate, endDate, professorId, roomId, complexId, status
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduleOccurrenceResponse> getScheduleOccurrenceById(@PathVariable Integer id) {
        ScheduleOccurrenceResponse response = scheduleOccurrenceService.getScheduleOccurrenceById(id);
        return ResponseEntity.ok(response);
    }
}
