package com.classsync.controller;

import com.classsync.dto.timetable.CreateTimetableVersionRequest;
import com.classsync.dto.timetable.TimetableVersionResponse;
import com.classsync.service.TimetableVersionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/timetables/versions")
public class TimetableVersionController {

    private final TimetableVersionService timetableVersionService;

    public TimetableVersionController(TimetableVersionService timetableVersionService) {
        this.timetableVersionService = timetableVersionService;
    }

    @GetMapping
    public ResponseEntity<List<TimetableVersionResponse>> listVersions(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) String semester) {
        return ResponseEntity.ok(timetableVersionService.listVersions(academicYear, semester));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TimetableVersionResponse> getVersionById(@PathVariable Integer id) {
        return ResponseEntity.ok(timetableVersionService.getVersionById(id));
    }

    @PostMapping
    public ResponseEntity<TimetableVersionResponse> createVersion(
            @Valid @RequestBody CreateTimetableVersionRequest request,
            Principal principal) {
        TimetableVersionResponse response = timetableVersionService.createVersion(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<TimetableVersionResponse> activateVersion(@PathVariable Integer id) {
        return ResponseEntity.ok(timetableVersionService.activateVersion(id));
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<TimetableVersionResponse> archiveVersion(@PathVariable Integer id) {
        return ResponseEntity.ok(timetableVersionService.archiveVersion(id));
    }
}
