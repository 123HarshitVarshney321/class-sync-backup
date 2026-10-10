package com.classsync.service;

import com.classsync.dto.common.StatusUpdateRequest;
import com.classsync.dto.facility.FacilityRequest;
import com.classsync.dto.facility.FacilityResponse;
import com.classsync.entity.Facility;
import com.classsync.exception.DuplicateResourceException;
import com.classsync.exception.ResourceNotFoundException;
import com.classsync.repository.FacilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FacilityService {

    private final FacilityRepository facilityRepository;

    public FacilityService(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    @Transactional(readOnly = true)
    public List<FacilityResponse> getActiveFacilities() {
        return facilityRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FacilityResponse createFacility(FacilityRequest request) {
        String name = request.name().trim();

        if (facilityRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("Facility with name '" + name + "' already exists");
        }

        Facility facility = new Facility(name, true);
        Facility saved = facilityRepository.save(facility);
        return toResponse(saved);
    }

    @Transactional
    public FacilityResponse updateFacility(Integer id, FacilityRequest request) {
        Facility facility = facilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + id));

        String name = request.name().trim();

        if (facilityRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateResourceException("Facility with name '" + name + "' already exists");
        }

        facility.setName(name);
        return toResponse(facility);
    }

    @Transactional
    public FacilityResponse updateFacilityStatus(Integer id, StatusUpdateRequest request) {
        Facility facility = facilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + id));

        facility.setActive(request.active());
        return toResponse(facility);
    }

    public FacilityResponse toResponse(Facility facility) {
        return new FacilityResponse(
                facility.getId(),
                facility.getName(),
                facility.getActive()
        );
    }
}
