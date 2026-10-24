package com.classsync.controller;

import com.classsync.dto.booking.BookingResponse;
import com.classsync.dto.booking.CreateBookingRequest;
import com.classsync.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            Principal principal) {
        BookingResponse response = bookingService.createExtraClass(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
