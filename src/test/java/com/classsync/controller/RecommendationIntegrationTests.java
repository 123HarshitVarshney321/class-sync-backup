package com.classsync.controller;

import com.classsync.entity.*;
import com.classsync.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ComplexRepository complexRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final LocalDate testDate = LocalDate.of(2026, 12, 10);

    private ScheduleOccurrence createOccurrence(Room room, User professor, LocalDate date, LocalTime start, LocalTime end, ScheduleStatus status) {
        return new ScheduleOccurrence(
                null,
                professor,
                room,
                date,
                start,
                end,
                ScheduleEventType.EXTRA,
                status,
                "CS101",
                "Intro CS",
                professor
        );
    }

    // ==========================================
    // Basic (Tests 1 - 5)
    // ==========================================

    @Test
    @DisplayName("1. Recommendation endpoint requires authentication")
    void recommendationEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("2. Professor can request recommendations")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCanRequestRecommendations() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[0].room.id").isNumber())
                .andExpect(jsonPath("$[0].score").isNumber())
                .andExpect(jsonPath("$[0].reasons", is(not(empty()))));
    }

    @Test
    @DisplayName("3. Admin can request recommendations")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanRequestRecommendations() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))));
    }

    @Test
    @DisplayName("4. Returns only available rooms")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void returnsOnlyAvailableRooms() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User professor = userRepository.findById(2).orElseThrow();

        // Occupy room 1 with a SCHEDULED occurrence
        ScheduleOccurrence occ = createOccurrence(room1, professor, testDate, LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);
        scheduleOccurrenceRepository.save(occ);

        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].room.id", not(hasItem(1))))
                .andExpect(jsonPath("$", is(not(empty()))));
    }

    @Test
    @DisplayName("5. Unavailable room never appears in recommendations")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void unavailableRoomNeverAppears() throws Exception {
        Room room2 = roomRepository.findById(2).orElseThrow();
        User professor = userRepository.findById(2).orElseThrow();

        ScheduleOccurrence occ = createOccurrence(room2, professor, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.SCHEDULED);
        scheduleOccurrenceRepository.save(occ);

        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "14:00")
                        .param("endTime", "15:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].room.id", not(hasItem(2))));
    }

    // ==========================================
    // Hard constraints (Tests 6 - 13)
    // ==========================================

    @Test
    @DisplayName("6. Capacity filtering as hard constraint")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void capacityFiltering() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("capacity", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[*].room.capacity", everyItem(greaterThanOrEqualTo(50))));
    }

    @Test
    @DisplayName("7. Room type filtering as hard constraint")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void roomTypeFiltering() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("roomType", "LAB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[*].room.roomType", everyItem(is("LAB"))));
    }

    @Test
    @DisplayName("8. Required facilities use AND semantics")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void requiredFacilitiesUseAndSemantics() throws Exception {
        // SEC-301 has facilities 1, 2, 3, 5
        // Request 1, 2, 5
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("facilities", "1,2,5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[*].room.roomNumber", hasItem("SEC-301")))
                .andExpect(jsonPath("$[*].room.roomNumber", not(hasItem("SEC-101")))); // SEC-101 lacks 2 and 5
    }

    @Test
    @DisplayName("9. Inactive room excluded")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void inactiveRoomExcluded() throws Exception {
        Complex complex = complexRepository.findById(1).orElseThrow();
        Room inactiveRoom = new Room(complex, "INACT-999", RoomType.CLASSROOM, 40, false);
        inactiveRoom = roomRepository.save(inactiveRoom);

        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].room.roomNumber", not(hasItem("INACT-999"))));
    }

    @Test
    @DisplayName("10. Inactive complex excluded")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void inactiveComplexExcluded() throws Exception {
        Complex inactiveComplex = new Complex("Inactive Complex", "INACT", false);
        inactiveComplex = complexRepository.save(inactiveComplex);
        Room roomInInactive = new Room(inactiveComplex, "IN-101", RoomType.CLASSROOM, 40, true);
        roomRepository.save(roomInInactive);

        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].room.roomNumber", not(hasItem("IN-101"))));
    }

    @Test
    @DisplayName("11. Scheduled room conflict excluded")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void scheduledRoomConflictExcluded() throws Exception {
        Room room3 = roomRepository.findById(3).orElseThrow();
        User professor = userRepository.findById(2).orElseThrow();

        ScheduleOccurrence occ = createOccurrence(room3, professor, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.SCHEDULED);
        scheduleOccurrenceRepository.save(occ);

        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "14:00")
                        .param("endTime", "15:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].room.id", not(hasItem(3))));
    }

    @Test
    @DisplayName("12. Cancelled room occurrence does not block recommendation")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void cancelledRoomOccurrenceDoesNotBlockRecommendation() throws Exception {
        Room room3 = roomRepository.findById(3).orElseThrow();
        User professor = userRepository.findById(2).orElseThrow();

        ScheduleOccurrence occ = createOccurrence(room3, professor, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.CANCELLED);
        scheduleOccurrenceRepository.save(occ);

        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "14:00")
                        .param("endTime", "15:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].room.id", hasItem(3)));
    }

    @Test
    @DisplayName("13. Professor conflict follows existing availability behavior")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorConflictFollowsExistingAvailabilityBehavior() throws Exception {
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        Room room1 = roomRepository.findById(1).orElseThrow();

        ScheduleOccurrence occ = createOccurrence(room1, turing, testDate, LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);
        scheduleOccurrenceRepository.save(occ);

        // Turing has personal conflict at 10:00-11:00 -> returns empty list []
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ==========================================
    // Ranking (Tests 14 - 20)
    // ==========================================

    @Test
    @DisplayName("14. Exact-capacity room ranks above unnecessarily large room")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void exactCapacityRoomRanksAboveUnnecessarilyLargeRoom() throws Exception {
        Complex complex = complexRepository.findById(1).orElseThrow();

        Room roomExact = new Room(complex, "TEST-CAP-50", RoomType.CLASSROOM, 50, true);
        Room roomLarge = new Room(complex, "TEST-CAP-200", RoomType.CLASSROOM, 200, true);
        roomRepository.saveAll(List.of(roomExact, roomLarge));

        MvcResult result = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "08:00")
                        .param("endTime", "09:00")
                        .param("capacity", "50")
                        .param("complexId", complex.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.size() >= 2);

        int rankExact = -1;
        int rankLarge = -1;
        for (int i = 0; i < root.size(); i++) {
            String roomNum = root.get(i).get("room").get("roomNumber").asText();
            if ("TEST-CAP-50".equals(roomNum)) rankExact = i;
            if ("TEST-CAP-200".equals(roomNum)) rankLarge = i;
        }

        assertTrue(rankExact != -1 && rankLarge != -1);
        assertTrue(rankExact < rankLarge, "Exact capacity room must rank before unnecessarily large room");
    }

    @Test
    @DisplayName("15. Requested complex receives appropriate location score")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void requestedComplexReceivesAppropriateLocationScore() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "08:00")
                        .param("endTime", "09:00")
                        .param("complexId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[*].room.complexId", everyItem(is(1))))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode firstReasons = root.get(0).get("reasons");
        boolean hasComplexReason = false;
        for (JsonNode r : firstReasons) {
            if (r.asText().contains("requested complex")) {
                hasComplexReason = true;
                break;
            }
        }
        assertTrue(hasComplexReason, "Reasons should mention requested complex");
    }

    @Test
    @DisplayName("16. Lower-utilization room receives better utilization score")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void lowerUtilizationRoomReceivesBetterUtilizationScore() throws Exception {
        Complex complex = complexRepository.findById(1).orElseThrow();
        User professor = userRepository.findById(2).orElseThrow();

        Room roomBusy = new Room(complex, "UTIL-BUSY", RoomType.CLASSROOM, 50, true);
        Room roomFree = new Room(complex, "UTIL-FREE", RoomType.CLASSROOM, 50, true);
        roomRepository.saveAll(List.of(roomBusy, roomFree));

        // Insert 5 scheduled occurrences in last 30 days for roomBusy
        for (int i = 1; i <= 5; i++) {
            ScheduleOccurrence occ = createOccurrence(roomBusy, professor, testDate.minusDays(i), LocalTime.of(9, 0), LocalTime.of(10, 0), ScheduleStatus.SCHEDULED);
            scheduleOccurrenceRepository.save(occ);
        }

        MvcResult result = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "07:00")
                        .param("endTime", "08:00")
                        .param("capacity", "50")
                        .param("complexId", complex.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        double scoreFree = -1;
        double scoreBusy = -1;
        for (JsonNode item : root) {
            String roomNum = item.get("room").get("roomNumber").asText();
            if ("UTIL-FREE".equals(roomNum)) scoreFree = item.get("score").asDouble();
            if ("UTIL-BUSY".equals(roomNum)) scoreBusy = item.get("score").asDouble();
        }

        assertTrue(scoreFree > scoreBusy, "Room with 0 utilization must have higher score than room with 5 occurrences");
    }

    @Test
    @DisplayName("17. Facility preference contributes to score")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void facilityPreferenceContributesToScore() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("facilities", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean hasFacilityReason = false;
        for (JsonNode reason : root.get(0).get("reasons")) {
            if (reason.asText().contains("requested facilities")) {
                hasFacilityReason = true;
                break;
            }
        }
        assertTrue(hasFacilityReason);
    }

    @Test
    @DisplayName("18. Continuity preference contributes to score")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void continuityPreferenceContributesToScore() throws Exception {
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        Room room3 = roomRepository.findById(3).orElseThrow();

        // Alan Turing has class in Room 3 ending at 14:00 (13:00 - 14:00)
        ScheduleOccurrence priorClass = createOccurrence(room3, turing, testDate, LocalTime.of(13, 0), LocalTime.of(14, 0), ScheduleStatus.SCHEDULED);
        scheduleOccurrenceRepository.save(priorClass);

        // Turing asks for 14:00 - 15:00
        MvcResult result = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "14:00")
                        .param("endTime", "15:00"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean foundContinuityReason = false;
        for (JsonNode item : root) {
            if (item.get("room").get("id").asInt() == 3) {
                for (JsonNode reason : item.get("reasons")) {
                    if (reason.asText().contains("preceding class")) {
                        foundContinuityReason = true;
                        break;
                    }
                }
            }
        }
        assertTrue(foundContinuityReason, "Room 3 must receive continuity reason for Turing's preceding class");
    }

    @Test
    @DisplayName("19. No optional preference still produces valid scores")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void noOptionalPreferenceStillProducesValidScores() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[*].score", everyItem(greaterThanOrEqualTo(0.0))))
                .andExpect(jsonPath("$[*].score", everyItem(lessThanOrEqualTo(100.0))))
                .andExpect(jsonPath("$[*].reasons", everyItem(not(empty()))));
    }

    @Test
    @DisplayName("20. Final score is between 0 and 100")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void finalScoreIsBetween0And100() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("capacity", "30"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode item : root) {
            double score = item.get("score").asDouble();
            assertTrue(score >= 0.0 && score <= 100.0, "Score " + score + " must be between 0 and 100");
        }
    }

    // ==========================================
    // Explainability (Tests 21 - 23)
    // ==========================================

    @Test
    @DisplayName("21. Every recommendation contains reasons")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void everyRecommendationContainsReasons() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode item : root) {
            JsonNode reasons = item.get("reasons");
            assertNotNull(reasons);
            assertTrue(reasons.isArray());
            assertTrue(reasons.size() > 0, "Reasons array must not be empty");
        }
    }

    @Test
    @DisplayName("22. Reasons correspond to actual score components")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void reasonsCorrespondToActualScoreComponents() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("capacity", "60")
                        .param("complexId", "1")
                        .param("roomType", "LECTURE_HALL"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.size() > 0);
        JsonNode first = root.get(0);
        String reasonsStr = first.get("reasons").toString();

        assertTrue(reasonsStr.contains("Capacity closely matches requested capacity") || reasonsStr.contains("Room capacity"));
        assertTrue(reasonsStr.contains("Room is in the requested complex"));
        assertTrue(reasonsStr.contains("Room matches requested room type"));
    }

    @Test
    @DisplayName("23. No random or non-deterministic reasons")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void noRandomNonDeterministicReasons() throws Exception {
        MvcResult res1 = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult res2 = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(res1.getResponse().getContentAsString(), res2.getResponse().getContentAsString(),
                "Two identical requests must return 100% identical JSON");
    }

    // ==========================================
    // Ordering (Tests 24 - 26)
    // ==========================================

    @Test
    @DisplayName("24. Equal scores use deterministic capacity ordering")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void equalScoresUseDeterministicCapacityOrdering() throws Exception {
        Complex complex = complexRepository.findById(1).orElseThrow();

        Room r1 = new Room(complex, "ORDER-C1", RoomType.CLASSROOM, 40, true);
        Room r2 = new Room(complex, "ORDER-C2", RoomType.CLASSROOM, 60, true);
        roomRepository.saveAll(List.of(r1, r2));

        // When capacity is not requested, both have capacityFit = 1.0, locationFit = 1.0 (if complexId passed), util = 1.0
        MvcResult res = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "06:00")
                        .param("endTime", "07:00")
                        .param("complexId", complex.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        int rankR1 = -1, rankR2 = -1;
        for (int i = 0; i < root.size(); i++) {
            String num = root.get(i).get("room").get("roomNumber").asText();
            if ("ORDER-C1".equals(num)) rankR1 = i;
            if ("ORDER-C2".equals(num)) rankR2 = i;
        }

        assertTrue(rankR1 != -1 && rankR2 != -1);
        assertTrue(rankR1 < rankR2, "Smaller capacity (40) must rank before larger capacity (60)");
    }

    @Test
    @DisplayName("25. Room number breaks remaining ties")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void roomNumberBreaksRemainingTies() throws Exception {
        Complex complex = complexRepository.findById(1).orElseThrow();

        Room rA = new Room(complex, "AAA-100", RoomType.CLASSROOM, 50, true);
        Room rZ = new Room(complex, "ZZZ-100", RoomType.CLASSROOM, 50, true);
        roomRepository.saveAll(List.of(rA, rZ));

        MvcResult res = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "05:00")
                        .param("endTime", "06:00")
                        .param("complexId", complex.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        int rankA = -1, rankZ = -1;
        for (int i = 0; i < root.size(); i++) {
            String num = root.get(i).get("room").get("roomNumber").asText();
            if ("AAA-100".equals(num)) rankA = i;
            if ("ZZZ-100".equals(num)) rankZ = i;
        }

        assertTrue(rankA != -1 && rankZ != -1);
        assertTrue(rankA < rankZ, "AAA-100 must rank before ZZZ-100");
    }

    @Test
    @DisplayName("26. Room ID breaks final ties")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void roomIdBreaksFinalTies() throws Exception {
        Complex complex1 = complexRepository.findById(1).orElseThrow();
        Complex complex2 = complexRepository.findById(2).orElseThrow();

        // Same room number, same capacity, but in different complexes
        Room r1 = new Room(complex1, "COMMON-101", RoomType.CLASSROOM, 50, true);
        Room r2 = new Room(complex2, "COMMON-101", RoomType.CLASSROOM, 50, true);
        roomRepository.saveAll(List.of(r1, r2));

        MvcResult res = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "04:00")
                        .param("endTime", "05:00"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        int rank1 = -1, rank2 = -1;
        for (int i = 0; i < root.size(); i++) {
            int id = root.get(i).get("room").get("id").asInt();
            if (id == r1.getId()) rank1 = i;
            if (id == r2.getId()) rank2 = i;
        }

        assertTrue(rank1 != -1 && rank2 != -1);
        if (r1.getId() < r2.getId()) {
            assertTrue(rank1 < rank2);
        } else {
            assertTrue(rank2 < rank1);
        }
    }

    // ==========================================
    // Edge cases (Tests 27 - 32)
    // ==========================================

    @Test
    @DisplayName("27. No eligible rooms returns empty list")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void noEligibleRoomsReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("capacity", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("28. Invalid time range returns 400")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void invalidTimeRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "15:00")
                        .param("endTime", "14:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("29. Invalid capacity returns 400")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void invalidCapacityReturns400() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("capacity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("30. Invalid facility ID returns 400")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void invalidFacilityIdReturns400() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("facilities", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("31. Duplicate facility IDs handled deterministically")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void duplicateFacilityIdsHandledDeterministically() throws Exception {
        mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("facilities", "1,1,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))));
    }

    @Test
    @DisplayName("32. Cancelled occurrences are ignored for utilization")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void cancelledOccurrencesAreIgnoredForUtilization() throws Exception {
        Complex complex = complexRepository.findById(1).orElseThrow();
        User professor = userRepository.findById(2).orElseThrow();

        Room roomWithCancelled = new Room(complex, "UTIL-CANC", RoomType.CLASSROOM, 50, true);
        roomRepository.save(roomWithCancelled);

        // Add 10 CANCELLED occurrences in last 30 days
        for (int i = 1; i <= 10; i++) {
            ScheduleOccurrence occ = createOccurrence(roomWithCancelled, professor, testDate.minusDays(i), LocalTime.of(9, 0), LocalTime.of(10, 0), ScheduleStatus.CANCELLED);
            scheduleOccurrenceRepository.save(occ);
        }

        // It should still have 0 scheduled utilization and get high utilization score
        MvcResult res = mockMvc.perform(get("/api/rooms/recommendations")
                        .param("date", testDate.toString())
                        .param("startTime", "03:00")
                        .param("endTime", "04:00")
                        .param("complexId", complex.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        for (JsonNode item : root) {
            if ("UTIL-CANC".equals(item.get("room").get("roomNumber").asText())) {
                boolean hasLowUtil = false;
                for (JsonNode reason : item.get("reasons")) {
                    if (reason.asText().contains("low scheduled utilization")) {
                        hasLowUtil = true;
                        break;
                    }
                }
                assertTrue(hasLowUtil, "Cancelled occurrences must not count towards utilization");
            }
        }
    }
}
