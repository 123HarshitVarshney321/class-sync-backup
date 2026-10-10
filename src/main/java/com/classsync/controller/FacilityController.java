package com.classsync.controller;

import com.classsync.dto.common.StatusUpdateRequest;
import com.classsync.dto.facility.FacilityRequest;
import com.classsync.dto.facility.FacilityResponse;
import com.classsync.service.FacilityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @GetMapping
    public ResponseEntity<List<FacilityResponse>> getActiveFacilities() {
        return ResponseEntity.ok(facilityService.getActiveFacilities());
    }

    @PostMapping
    public ResponseEntity<FacilityResponse> createFacility(@Valid @RequestBody FacilityRequest request) {
        FacilityResponse response = facilityService.createFacility(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FacilityResponse> updateFacility(@PathVariable Integer id,
                                                           @Valid @RequestBody FacilityRequest request) {
        return ResponseEntity.ok(facilityService.updateFacility(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<FacilityResponse> updateFacilityStatus(@PathVariable Integer id,
                                                                 @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(facilityService.updateFacilityStatus(id, request));
    }
}
