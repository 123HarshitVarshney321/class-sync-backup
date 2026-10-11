package com.classsync.controller;

import com.classsync.entity.*;
import com.classsync.repository.*;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AvailabilityIntegrationTests {

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

    private final LocalDate testDate = LocalDate.of(2026, 11, 2);

    @Test
    @DisplayName("1. Professor can search available rooms")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCanSearchAvailableRooms() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].roomNumber").exists())
                .andExpect(jsonPath("$[0].complex.name").exists());
    }

    @Test
    @DisplayName("2. Admin can search available rooms")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanSearchAvailableRooms() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))));
    }

    @Test
    @DisplayName("3. Unauthenticated request returns 401")
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("4. Inactive rooms are excluded")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void inactiveRoomsAreExcluded() throws Exception {
        Complex complex = complexRepository.findById(1).orElseThrow();
        Room inactiveRoom = new Room(complex, "INACT-999", RoomType.CLASSROOM, 40, false);
        inactiveRoom = roomRepository.save(inactiveRoom);

        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(inactiveRoom.getId()))));
    }

    @Test
    @DisplayName("5. Rooms in inactive complexes are excluded")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void roomsInInactiveComplexesAreExcluded() throws Exception {
        Complex inactiveComplex = new Complex("Inactive Complex", "INCX", false);
        inactiveComplex = complexRepository.save(inactiveComplex);

        Room roomInInactiveComplex = new Room(inactiveComplex, "INCX-101", RoomType.CLASSROOM, 40, true);
        roomInInactiveComplex = roomRepository.save(roomInInactiveComplex);

        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(roomInInactiveComplex.getId()))));
    }

    @Test
    @DisplayName("6. Complex filter works")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void complexFilterWorks() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("complexId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[*].complex.id", everyItem(is(1))));
    }

    @Test
    @DisplayName("7. Minimum capacity filter works")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void minimumCapacityFilterWorks() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("capacity", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[*].capacity", everyItem(greaterThanOrEqualTo(50))));
    }

    @Test
    @DisplayName("8. Room type filter works")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void roomTypeFilterWorks() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("roomType", "LAB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[*].roomType", everyItem(is("LAB"))));
    }

    @Test
    @DisplayName("9. All requested facilities must match")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void allRequestedFacilitiesMustMatch() throws Exception {
        // SEC-301 has facilities 1 (Projector), 2 (Smart Board), 3 (AC), 5 (Audio)
        // Request facilities 1, 2, 5
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("facilities", "1,2,5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[*].roomNumber", hasItem("SEC-301")));

        // SEC-101 only has 1 and 3 (does NOT have 2 and 5), so it should NOT be in the result
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("facilities", "1,2,5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].roomNumber", not(hasItem("SEC-101"))));
    }

    @Test
    @DisplayName("10. Inactive facility IDs are rejected (400 Bad Request)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void inactiveFacilityIdRejected() throws Exception {
        Facility inactiveFac = new Facility("Obsolete Overhead", false);
        inactiveFac = facilityRepository.save(inactiveFac);

        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("facilities", "1," + inactiveFac.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("inactive")));
    }

    @Test
    @DisplayName("11. Malformed facility IDs are rejected (400 Bad Request)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void malformedFacilityIdRejected() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("facilities", "1,abc,2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Malformed facility ID")));

        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("facilities", "1,-3,2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("positive integer")));
    }

    @Test
    @DisplayName("12. Overlapping scheduled occurrence excludes the room")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void overlappingScheduledOccurrenceExcludesRoom() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User prof = userRepository.findById(2).orElseThrow();
        User admin = userRepository.findById(1).orElseThrow();

        // Create occurrence in Room 1 from 09:00 to 11:00 on testDate
        ScheduleOccurrence occ = new ScheduleOccurrence(
                null, prof, room1, testDate, LocalTime.of(9, 0), LocalTime.of(11, 0),
                ScheduleEventType.OFFICIAL, ScheduleStatus.SCHEDULED, "CS101", "Intro CS", admin
        );
        scheduleOccurrenceRepository.save(occ);

        // Search 10:00 to 12:00 -> Room 1 overlaps (10:00-11:00) and must be excluded
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(1))));
    }

    @Test
    @DisplayName("13. Cancelled occurrence does not block the room")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void cancelledOccurrenceDoesNotBlockRoom() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User prof = userRepository.findById(2).orElseThrow();
        User admin = userRepository.findById(1).orElseThrow();

        // Create CANCELLED occurrence in Room 1 from 09:00 to 11:00 on testDate
        ScheduleOccurrence occ = new ScheduleOccurrence(
                null, prof, room1, testDate, LocalTime.of(9, 0), LocalTime.of(11, 0),
                ScheduleEventType.OFFICIAL, ScheduleStatus.CANCELLED, "CS101", "Intro CS", admin
        );
        scheduleOccurrenceRepository.save(occ);

        // Search 10:00 to 12:00 -> Room 1 should be included because occurrence is CANCELLED
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(1)));
    }

    @Test
    @DisplayName("14. Adjacent non-overlapping occurrence is allowed")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adjacentNonOverlappingOccurrenceAllowed() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User prof = userRepository.findById(2).orElseThrow();
        User admin = userRepository.findById(1).orElseThrow();

        // Create occurrence in Room 1 from 09:00 to 10:00
        ScheduleOccurrence occ = new ScheduleOccurrence(
                null, prof, room1, testDate, LocalTime.of(9, 0), LocalTime.of(10, 0),
                ScheduleEventType.OFFICIAL, ScheduleStatus.SCHEDULED, "CS101", "Intro CS", admin
        );
        scheduleOccurrenceRepository.save(occ);

        // Search 10:00 to 11:00 -> immediately adjacent, half-open interval rule allows it
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(1)));
    }

    @Test
    @DisplayName("15. Professor's own overlapping occurrence excludes rooms for that professor")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorOwnOverlappingOccurrenceExcludesRooms() throws Exception {
        User profTuring = userRepository.findById(2).orElseThrow(); // aturing@classsync.edu
        Room room2 = roomRepository.findById(2).orElseThrow();
        User admin = userRepository.findById(1).orElseThrow();

        // Prof Turing has a scheduled class in Room 2 from 09:00 to 11:00
        ScheduleOccurrence occ = new ScheduleOccurrence(
                null, profTuring, room2, testDate, LocalTime.of(9, 0), LocalTime.of(11, 0),
                ScheduleEventType.OFFICIAL, ScheduleStatus.SCHEDULED, "CS101", "Intro CS", admin
        );
        scheduleOccurrenceRepository.save(occ);

        // When Prof. Turing searches for 10:00 to 12:00, he has a personal conflict -> empty list returned
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("16. An admin search does not apply a professor-specific conflict check")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminSearchDoesNotApplyProfessorConflictCheck() throws Exception {
        User profTuring = userRepository.findById(2).orElseThrow();
        Room room2 = roomRepository.findById(2).orElseThrow();
        User admin = userRepository.findById(1).orElseThrow();

        // Prof Turing has a class in Room 2 from 09:00 to 11:00
        ScheduleOccurrence occ = new ScheduleOccurrence(
                null, profTuring, room2, testDate, LocalTime.of(9, 0), LocalTime.of(11, 0),
                ScheduleEventType.OFFICIAL, ScheduleStatus.SCHEDULED, "CS101", "Intro CS", admin
        );
        scheduleOccurrenceRepository.save(occ);

        // When Admin searches for 10:00 to 12:00, Room 2 is busy, but other rooms (e.g. Room 1, 3, 4, 5) are returned!
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "12:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))))
                .andExpect(jsonPath("$[*].id", not(hasItem(2))))
                .andExpect(jsonPath("$[*].id", hasItem(1)));
    }

    @Test
    @DisplayName("17. Invalid date or time range is rejected (400 Bad Request)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void invalidDateOrTimeRangeRejected() throws Exception {
        // startTime equal to endTime
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Start time must be strictly before end time")));

        // startTime after endTime
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "11:00")
                        .param("endTime", "10:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Start time must be strictly before end time")));

        // Invalid capacity (< 1)
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("capacity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Capacity must be at least 1")));
    }

    @Test
    @DisplayName("18. Empty result returns 200 with an empty list")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void emptyResultReturns200WithEmptyList() throws Exception {
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00")
                        .param("capacity", "99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("19. Response DTOs do not expose sensitive or recursive entity data")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void responseDtoContainsNoSensitiveOrRecursiveData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "10:00")
                        .param("endTime", "11:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].rooms").doesNotExist()) // no recursive complex.rooms
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"));
        assertFalse(body.contains("password_hash"));
    }
}
