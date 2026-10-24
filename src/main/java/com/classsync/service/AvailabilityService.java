package com.classsync.service;

import com.classsync.dto.complex.ComplexSummaryResponse;
import com.classsync.dto.facility.FacilityResponse;
import com.classsync.dto.room.AvailableRoomResponse;
import com.classsync.entity.Facility;
import com.classsync.entity.Room;
import com.classsync.entity.RoomType;
import com.classsync.entity.User;
import com.classsync.entity.UserRole;
import com.classsync.exception.BadRequestException;
import com.classsync.repository.FacilityRepository;
import com.classsync.repository.RoomRepository;
import com.classsync.repository.ScheduleOccurrenceRepository;
import com.classsync.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    private final RoomRepository roomRepository;
    private final FacilityRepository facilityRepository;
    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final UserRepository userRepository;

    public AvailabilityService(RoomRepository roomRepository,
                               FacilityRepository facilityRepository,
                               ScheduleOccurrenceRepository scheduleOccurrenceRepository,
                               UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.facilityRepository = facilityRepository;
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AvailableRoomResponse> getAvailableRooms(
            Integer complexId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            Integer capacity,
            RoomType roomType,
            String facilitiesParam,
            String userEmail) {
        return getAvailableRoomEntities(complexId, date, startTime, endTime, capacity, roomType, facilitiesParam, userEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Room> getAvailableRoomEntities(
            Integer complexId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            Integer capacity,
            RoomType roomType,
            String facilitiesParam,
            String userEmail) {

        if (date == null) {
            throw new BadRequestException("Date is required");
        }
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Start time and end time are required");
        }
        if (!startTime.isBefore(endTime)) {
            throw new BadRequestException("Start time must be strictly before end time");
        }
        if (capacity != null && capacity < 1) {
            throw new BadRequestException("Capacity must be at least 1");
        }

        Set<Integer> requiredFacilityIds = parseAndValidateFacilityIds(facilitiesParam);

        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found with email: " + userEmail));

        // If authenticated user is a PROFESSOR, check personal schedule conflict
        if (currentUser.getRole() == UserRole.PROFESSOR) {
            boolean hasConflict = scheduleOccurrenceRepository.existsProfessorConflict(
                    currentUser.getId(), date, startTime, endTime
            );
            if (hasConflict) {
                return Collections.emptyList();
            }
        }

        // Query busy room IDs during the requested date and time interval
        List<Integer> busyRoomIds = scheduleOccurrenceRepository.findBusyRoomIds(date, startTime, endTime);
        Collection<Integer> excludedIds = busyRoomIds.isEmpty() ? List.of(-1) : busyRoomIds;

        List<Room> candidateRooms = roomRepository.findAvailableRooms(complexId, capacity, roomType, excludedIds);

        // Filter rooms that possess ALL requested active facilities
        if (!requiredFacilityIds.isEmpty()) {
            candidateRooms = candidateRooms.stream()
                    .filter(room -> {
                        Set<Integer> roomActiveFacilityIds = room.getFacilities().stream()
                                .filter(Facility::getActive)
                                .map(Facility::getId)
                                .collect(Collectors.toSet());
                        return roomActiveFacilityIds.containsAll(requiredFacilityIds);
                    })
                    .toList();
        }

        return candidateRooms;
    }

    public Set<Integer> parseAndValidateFacilityIds(String facilitiesParam) {
        if (facilitiesParam == null || facilitiesParam.isBlank()) {
            return Collections.emptySet();
        }

        String[] parts = facilitiesParam.split(",");
        Set<Integer> facilityIds = new LinkedHashSet<>();

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new BadRequestException("Facility ID cannot be empty");
            }
            try {
                int id = Integer.parseInt(trimmed);
                if (id <= 0) {
                    throw new BadRequestException("Facility ID must be a positive integer: " + id);
                }
                facilityIds.add(id);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Malformed facility ID: '" + trimmed + "'");
            }
        }

        if (facilityIds.isEmpty()) {
            return Collections.emptySet();
        }

        List<Facility> activeFacilities = facilityRepository.findAllByIdInAndActiveTrue(facilityIds);
        if (activeFacilities.size() != facilityIds.size()) {
            Set<Integer> foundActiveIds = activeFacilities.stream().map(Facility::getId).collect(Collectors.toSet());
            List<Integer> missingOrInactive = facilityIds.stream()
                    .filter(id -> !foundActiveIds.contains(id))
                    .toList();
            throw new BadRequestException("Requested facility ID(s) not found or inactive: " + missingOrInactive);
        }

        return facilityIds;
    }

    private AvailableRoomResponse toResponse(Room room) {
        ComplexSummaryResponse complexSummary = new ComplexSummaryResponse(
                room.getComplex().getId(),
                room.getComplex().getName(),
                room.getComplex().getCode()
        );

        List<FacilityResponse> facilityResponses = room.getFacilities().stream()
                .sorted(Comparator.comparing(Facility::getName))
                .map(f -> new FacilityResponse(f.getId(), f.getName(), f.getActive()))
                .toList();

        return new AvailableRoomResponse(
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
