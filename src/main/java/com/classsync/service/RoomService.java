package com.classsync.service;

import com.classsync.dto.common.StatusUpdateRequest;
import com.classsync.dto.complex.ComplexSummaryResponse;
import com.classsync.dto.facility.FacilityResponse;
import com.classsync.dto.room.CreateRoomRequest;
import com.classsync.dto.room.RoomDetailResponse;
import com.classsync.dto.room.RoomSummaryResponse;
import com.classsync.dto.room.UpdateRoomRequest;
import com.classsync.entity.Complex;
import com.classsync.entity.Facility;
import com.classsync.entity.Room;
import com.classsync.exception.BadRequestException;
import com.classsync.exception.DuplicateResourceException;
import com.classsync.exception.ResourceNotFoundException;
import com.classsync.repository.ComplexRepository;
import com.classsync.repository.FacilityRepository;
import com.classsync.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final ComplexRepository complexRepository;
    private final FacilityRepository facilityRepository;

    public RoomService(RoomRepository roomRepository,
                       ComplexRepository complexRepository,
                       FacilityRepository facilityRepository) {
        this.roomRepository = roomRepository;
        this.complexRepository = complexRepository;
        this.facilityRepository = facilityRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomSummaryResponse> getActiveRoomsForComplex(Integer complexId) {
        complexRepository.findByIdAndActiveTrue(complexId)
                .orElseThrow(() -> new ResourceNotFoundException("Complex not found or inactive with id: " + complexId));

        return roomRepository.findByComplexIdAndActiveTrue(complexId).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomDetailResponse getActiveRoomById(Integer id) {
        Room room = roomRepository.findByIdWithDetailsAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found or inactive with id: " + id));

        return toDetailResponse(room);
    }

    @Transactional
    public RoomDetailResponse createRoom(Integer complexId, CreateRoomRequest request) {
        Complex complex = complexRepository.findByIdAndActiveTrue(complexId)
                .orElseThrow(() -> new ResourceNotFoundException("Complex not found or inactive with id: " + complexId));

        String roomNumber = request.roomNumber().trim();

        if (roomRepository.existsByComplexIdAndRoomNumberIgnoreCase(complexId, roomNumber)) {
            throw new DuplicateResourceException("Room with number '" + roomNumber + "' already exists in complex with id: " + complexId);
        }

        Set<Facility> facilities = resolveFacilities(request.facilityIds());

        Room room = new Room(complex, roomNumber, request.roomType(), request.capacity(), true);
        room.setFacilities(facilities);

        Room saved = roomRepository.save(room);
        return toDetailResponse(saved);
    }

    @Transactional
    public RoomDetailResponse updateRoom(Integer id, UpdateRoomRequest request) {
        Room room = roomRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));

        String roomNumber = request.roomNumber().trim();

        if (roomRepository.existsByComplexIdAndRoomNumberIgnoreCaseAndIdNot(room.getComplex().getId(), roomNumber, id)) {
            throw new DuplicateResourceException("Room with number '" + roomNumber + "' already exists in complex with id: " + room.getComplex().getId());
        }

        Set<Facility> facilities = resolveFacilities(request.facilityIds());

        room.setRoomNumber(roomNumber);
        room.setRoomType(request.roomType());
        room.setCapacity(request.capacity());
        room.setFacilities(facilities);

        return toDetailResponse(room);
    }

    @Transactional
    public RoomSummaryResponse updateRoomStatus(Integer id, StatusUpdateRequest request) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));

        room.setActive(request.active());
        return toSummaryResponse(room);
    }

    private Set<Facility> resolveFacilities(Set<Integer> facilityIds) {
        if (facilityIds == null || facilityIds.isEmpty()) {
            return new HashSet<>();
        }

        List<Facility> found = facilityRepository.findAllByIdInAndActiveTrue(facilityIds);
        if (found.size() != facilityIds.size()) {
            throw new BadRequestException("One or more specified facilities do not exist or are inactive");
        }

        return new HashSet<>(found);
    }

    private RoomSummaryResponse toSummaryResponse(Room room) {
        return new RoomSummaryResponse(
                room.getId(),
                room.getRoomNumber(),
                room.getRoomType(),
                room.getCapacity(),
                room.getActive()
        );
    }

    private RoomDetailResponse toDetailResponse(Room room) {
        ComplexSummaryResponse complexSummary = new ComplexSummaryResponse(
                room.getComplex().getId(),
                room.getComplex().getName(),
                room.getComplex().getCode()
        );

        List<FacilityResponse> facilityResponses = room.getFacilities().stream()
                .map(f -> new FacilityResponse(f.getId(), f.getName(), f.getActive()))
                .sorted(Comparator.comparing(FacilityResponse::id))
                .toList();

        return new RoomDetailResponse(
                room.getId(),
                room.getRoomNumber(),
                room.getRoomType(),
                room.getCapacity(),
                room.getActive(),
                complexSummary,
                facilityResponses
        );
    }
}
