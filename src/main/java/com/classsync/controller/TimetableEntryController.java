package com.classsync.controller;

import com.classsync.dto.timetable.CreateTimetableEntryRequest;
import com.classsync.dto.timetable.TimetableEntryResponse;
import com.classsync.dto.timetable.UpdateTimetableEntryRequest;
import com.classsync.service.TimetableEntryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timetables")
public class TimetableEntryController {

    private final TimetableEntryService timetableEntryService;

    public TimetableEntryController(TimetableEntryService timetableEntryService) {
        this.timetableEntryService = timetableEntryService;
    }

    @GetMapping("/versions/{versionId}/entries")
    public ResponseEntity<List<TimetableEntryResponse>> listEntriesByVersion(@PathVariable Integer versionId) {
        return ResponseEntity.ok(timetableEntryService.listEntriesByVersion(versionId));
    }

    @PostMapping("/versions/{versionId}/entries")
    public ResponseEntity<TimetableEntryResponse> createEntry(
            @PathVariable Integer versionId,
            @Valid @RequestBody CreateTimetableEntryRequest request) {
        TimetableEntryResponse response = timetableEntryService.createEntry(versionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/entries/{entryId}")
    public ResponseEntity<TimetableEntryResponse> getEntryById(@PathVariable Integer entryId) {
        return ResponseEntity.ok(timetableEntryService.getEntryById(entryId));
    }

    @PutMapping("/entries/{entryId}")
    public ResponseEntity<TimetableEntryResponse> updateEntry(
            @PathVariable Integer entryId,
            @Valid @RequestBody UpdateTimetableEntryRequest request) {
        return ResponseEntity.ok(timetableEntryService.updateEntry(entryId, request));
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Integer entryId) {
        timetableEntryService.deleteEntry(entryId);
        return ResponseEntity.noContent().build();
    }
}
