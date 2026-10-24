package com.classsync.controller;

import com.classsync.dto.booking.BookingResponse;
import com.classsync.dto.occurrence.RescheduleRequest;
import com.classsync.service.ClassScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/classes")
public class ClassScheduleController {

    private final ClassScheduleService classScheduleService;

    public ClassScheduleController(ClassScheduleService classScheduleService) {
        this.classScheduleService = classScheduleService;
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelOccurrence(
            @PathVariable("id") Integer id,
            Principal principal) {
        BookingResponse response = classScheduleService.cancelOccurrence(id, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reschedule")
    public ResponseEntity<BookingResponse> rescheduleOccurrence(
            @PathVariable("id") Integer id,
            @Valid @RequestBody RescheduleRequest request,
            Principal principal) {
        BookingResponse response = classScheduleService.rescheduleOccurrence(id, request, principal.getName());
        return ResponseEntity.ok(response);
    }
}
