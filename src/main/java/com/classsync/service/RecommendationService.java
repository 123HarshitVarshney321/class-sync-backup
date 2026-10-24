package com.classsync.service;

import com.classsync.dto.recommendation.RecommendationResponse;
import com.classsync.dto.recommendation.RecommendationRoomSummary;
import com.classsync.entity.Facility;
import com.classsync.entity.Room;
import com.classsync.entity.RoomType;
import com.classsync.entity.User;
import com.classsync.entity.UserRole;
import com.classsync.exception.BadRequestException;
import com.classsync.repository.ScheduleOccurrenceRepository;
import com.classsync.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final AvailabilityService availabilityService;
    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final UserRepository userRepository;

    public RecommendationService(AvailabilityService availabilityService,
                                 ScheduleOccurrenceRepository scheduleOccurrenceRepository,
                                 UserRepository userRepository) {
        this.availabilityService = availabilityService;
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> recommendRooms(
            Integer complexId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            Integer capacity,
            RoomType roomType,
            String facilitiesParam,
            String userEmail) {

        // Validate basic parameters
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

        Set<Integer> requestedFacilityIds = availabilityService.parseAndValidateFacilityIds(facilitiesParam);

        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found with email: " + userEmail));

        // Delegate to AvailabilityService to obtain hard-constraint eligible candidate rooms
        List<Room> candidateRooms = availabilityService.getAvailableRoomEntities(
                complexId, date, startTime, endTime, capacity, roomType, facilitiesParam, userEmail
        );

        if (candidateRooms.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Calculate 30-day rolling utilization for candidate rooms (inclusive)
        List<Integer> candidateRoomIds = candidateRooms.stream().map(Room::getId).toList();
        LocalDate startDate = date.minusDays(30);
        LocalDate endDate = date;

        List<Object[]> countRows = scheduleOccurrenceRepository.countScheduledOccurrencesForRooms(
                candidateRoomIds, startDate, endDate
        );

        Map<Integer, Long> utilizationMap = new HashMap<>();
        for (Integer roomId : candidateRoomIds) {
            utilizationMap.put(roomId, 0L);
        }
        for (Object[] row : countRows) {
            Integer roomId = (Integer) row[0];
            Long count = (Long) row[1];
            utilizationMap.put(roomId, count);
        }

        long minUtil = Collections.min(utilizationMap.values());
        long maxUtil = Collections.max(utilizationMap.values());

        // 2. Query continuity for authenticated professor
        Set<Integer> adjacentRoomIds = Collections.emptySet();
        if (currentUser.getRole() == UserRole.PROFESSOR) {
            List<Integer> adjacent = scheduleOccurrenceRepository.findAdjacentRoomIdsForProfessor(
                    currentUser.getId(), date, startTime, endTime
            );
            adjacentRoomIds = new HashSet<>(adjacent);
        }

        // 3. Score and explain each candidate room
        List<RecommendationResponse> recommendations = new ArrayList<>();

        for (Room room : candidateRooms) {
            List<String> reasons = new ArrayList<>();

            // A. CapacityFit (Weight 30)
            double capacityFit;
            if (capacity != null) {
                capacityFit = Math.min(1.0, Math.max(0.0, (double) capacity / room.getCapacity()));
                if (capacityFit >= 0.8) {
                    reasons.add("Capacity closely matches requested capacity");
                } else {
                    reasons.add("Room capacity meets requirement");
                }
            } else {
                capacityFit = 1.0;
                reasons.add("Room capacity is suitable");
            }

            // B. LocationFit (Weight 25)
            double locationFit;
            if (complexId != null) {
                if (room.getComplex().getId().equals(complexId)) {
                    locationFit = 1.0;
                    reasons.add("Room is in the requested complex");
                } else {
                    locationFit = 0.0;
                }
            } else {
                locationFit = 0.5;
            }

            // C. UtilizationBalance (Weight 20)
            long roomUtil = utilizationMap.getOrDefault(room.getId(), 0L);
            double utilizationBalance;
            if (maxUtil == minUtil) {
                utilizationBalance = 1.0;
            } else {
                utilizationBalance = (double) (maxUtil - roomUtil) / (maxUtil - minUtil);
            }
            if (utilizationBalance >= 0.5) {
                reasons.add("Room has relatively low scheduled utilization");
            } else {
                reasons.add("Room has balanced utilization");
            }

            // D. PreferenceFit (Weight 15)
            double preferenceFit;
            boolean hasRoomTypePref = (roomType != null);
            boolean hasFacilityPref = !requestedFacilityIds.isEmpty();

            if (!hasRoomTypePref && !hasFacilityPref) {
                preferenceFit = 1.0;
            } else {
                double roomTypeScore = 1.0;
                if (hasRoomTypePref) {
                    roomTypeScore = (room.getRoomType() == roomType) ? 1.0 : 0.0;
                    if (roomTypeScore == 1.0) {
                        reasons.add("Room matches requested room type");
                    }
                }

                double facilityScore = 1.0;
                if (hasFacilityPref) {
                    Set<Integer> roomActiveFacilityIds = room.getFacilities().stream()
                            .filter(Facility::getActive)
                            .map(Facility::getId)
                            .collect(Collectors.toSet());
                    long matchedCount = requestedFacilityIds.stream()
                            .filter(roomActiveFacilityIds::contains)
                            .count();
                    facilityScore = (double) matchedCount / requestedFacilityIds.size();
                    if (facilityScore >= 1.0) {
                        reasons.add("Room matches requested facilities");
                    } else if (facilityScore > 0.0) {
                        reasons.add("Room partially matches requested facilities");
                    }
                }

                if (hasRoomTypePref && hasFacilityPref) {
                    preferenceFit = (roomTypeScore + facilityScore) / 2.0;
                } else if (hasRoomTypePref) {
                    preferenceFit = roomTypeScore;
                } else {
                    preferenceFit = facilityScore;
                }
            }

            // E. Continuity (Weight 10)
            double continuity = 0.0;
            if (adjacentRoomIds.contains(room.getId())) {
                continuity = 1.0;
                reasons.add("Professor has a preceding class in this room");
            }

            // Calculate total weighted score clamped to 0.0 - 100.0
            double rawScore = 30.0 * capacityFit
                    + 25.0 * locationFit
                    + 20.0 * utilizationBalance
                    + 15.0 * preferenceFit
                    + 10.0 * continuity;

            rawScore = Math.min(100.0, Math.max(0.0, rawScore));
            double finalScore = BigDecimal.valueOf(rawScore)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            RecommendationRoomSummary summary = new RecommendationRoomSummary(
                    room.getId(),
                    room.getRoomNumber(),
                    room.getRoomType(),
                    room.getCapacity(),
                    room.getComplex().getId(),
                    room.getComplex().getName()
            );

            recommendations.add(new RecommendationResponse(summary, finalScore, reasons));
        }

        // 4. Deterministic sorting:
        // 1. score descending
        // 2. capacity ascending
        // 3. room number ascending
        // 4. room ID ascending
        recommendations.sort(
                Comparator.comparing(RecommendationResponse::score, Comparator.reverseOrder())
                        .thenComparing(r -> r.room().capacity())
                        .thenComparing(r -> r.room().roomNumber())
                        .thenComparing(r -> r.room().id())
        );

        return recommendations;
    }
}
