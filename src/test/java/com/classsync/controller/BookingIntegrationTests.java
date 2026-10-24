package com.classsync.controller;

import com.classsync.dto.booking.CreateBookingRequest;
import com.classsync.entity.*;
import com.classsync.exception.DuplicateResourceException;
import com.classsync.repository.*;
import com.classsync.service.BookingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BookingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ComplexRepository complexRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        scheduleOccurrenceRepository.deleteAll();
    }

    private ScheduleOccurrence createTestOccurrence(Room room, User prof, LocalDate date, LocalTime start, LocalTime end, ScheduleStatus status) {
        ScheduleOccurrence occ = new ScheduleOccurrence(
                null,
                prof,
                room,
                date,
                start,
                end,
                ScheduleEventType.OFFICIAL,
                status,
                "CS-OCC",
                "Official Occurrence",
                prof
        );
        return scheduleOccurrenceRepository.save(occ);
    }

    // =========================================================================
    // Basic Booking Tests (1-6)
    // =========================================================================

    @Test
    @DisplayName("1. Active professor can create an extra booking")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void activeProfessorCanCreateExtraBooking() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.eventType", is("EXTRA")))
                .andExpect(jsonPath("$.status", is("SCHEDULED")))
                .andExpect(jsonPath("$.date", is("2026-11-10")))
                .andExpect(jsonPath("$.startTime", is("10:00:00")))
                .andExpect(jsonPath("$.endTime", is("11:00:00")))
                .andExpect(jsonPath("$.courseCode", is("CS301")))
                .andExpect(jsonPath("$.courseName", is("Database Systems")))
                .andExpect(jsonPath("$.professor.name", is("Dr. Alan Turing")))
                .andExpect(jsonPath("$.professor.email", is("aturing@classsync.edu")))
                .andExpect(jsonPath("$.room.id", is(1)))
                .andExpect(jsonPath("$.room.roomNumber", is("SEC-101")));
    }

    @Test
    @DisplayName("2. Booking creates event_type = EXTRA")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void bookingCreatesEventTypeExtra() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(11, 0),
                LocalTime.of(12, 0),
                "CS302",
                "Advanced OS"
        );

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        Integer occurrenceId = jsonNode.get("id").asInt();

        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findById(occurrenceId).orElseThrow();
        assertEquals(ScheduleEventType.EXTRA, occurrence.getEventType());
    }

    @Test
    @DisplayName("3. Booking has timetable_entry_id = NULL")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void bookingHasTimetableEntryIdNull() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                "CS303",
                "Networks"
        );

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        Integer occurrenceId = jsonNode.get("id").asInt();

        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findById(occurrenceId).orElseThrow();
        assertNull(occurrence.getTimetableEntry());
    }

    @Test
    @DisplayName("4. Booking status is SCHEDULED")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void bookingStatusIsScheduled() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                "CS304",
                "Compilers"
        );

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        Integer occurrenceId = jsonNode.get("id").asInt();

        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findById(occurrenceId).orElseThrow();
        assertEquals(ScheduleStatus.SCHEDULED, occurrence.getStatus());
    }

    @Test
    @DisplayName("5. created_by comes from authenticated user")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void createdByComesFromAuthenticatedUser() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                "CS305",
                "AI Fundamentals"
        );

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        Integer occurrenceId = jsonNode.get("id").asInt();

        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findByIdWithDetails(occurrenceId).orElseThrow();
        assertEquals("aturing@classsync.edu", occurrence.getCreatedBy().getEmail());
        assertEquals("Dr. Alan Turing", occurrence.getCreatedBy().getName());
    }

    @Test
    @DisplayName("6. Professor cannot provide another professor ID to impersonate someone else")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCannotImpersonateAnotherProfessor() throws Exception {
        // Attempt sending extraneous professorId or createdBy fields in the payload
        String rawJson = """
                {
                  "roomId": 1,
                  "date": "2026-11-10",
                  "startTime": "15:00",
                  "endTime": "16:00",
                  "courseCode": "CS306",
                  "courseName": "Security",
                  "professorId": 3,
                  "createdBy": 3
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.professor.id", is(2)))
                .andExpect(jsonPath("$.professor.email", is("aturing@classsync.edu")))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        Integer occurrenceId = jsonNode.get("id").asInt();

        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findByIdWithDetails(occurrenceId).orElseThrow();
        assertEquals(2, occurrence.getProfessor().getId());
        assertEquals("Dr. Alan Turing", occurrence.getProfessor().getName());
        assertEquals(2, occurrence.getCreatedBy().getId());
    }

    // =========================================================================
    // Validation Tests (7-12)
    // =========================================================================

    @Test
    @DisplayName("7. Invalid room returns 404")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void invalidRoomReturns404() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                99999,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Room not found with id: 99999")));
    }

    @Test
    @DisplayName("8. Inactive room returns 404")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void inactiveRoomReturns404() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 6);
        Complex complex = complexRepository.findById(1).orElseThrow();
        Room inactiveRoom = new Room(complex, "INACT-" + suffix, RoomType.CLASSROOM, 30, false);
        Room savedRoom = roomRepository.save(inactiveRoom);

        try {
            CreateBookingRequest request = new CreateBookingRequest(
                    savedRoom.getId(),
                    LocalDate.of(2026, 11, 10),
                    LocalTime.of(10, 0),
                    LocalTime.of(11, 0),
                    "CS301",
                    "Database Systems"
            );

            mockMvc.perform(post("/api/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.message", containsString("Room is inactive")));
        } finally {
            roomRepository.delete(savedRoom);
        }
    }

    @Test
    @DisplayName("9. Inactive complex rejects booking")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void inactiveComplexRejectsBooking() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 6);
        Complex inactiveComplex = new Complex("Decommissioned " + suffix, "D" + suffix, false);
        Complex savedComplex = complexRepository.save(inactiveComplex);
        Room roomInInactiveComplex = new Room(savedComplex, "DEC-" + suffix, RoomType.CLASSROOM, 40, true);
        Room savedRoom = roomRepository.save(roomInInactiveComplex);

        try {
            CreateBookingRequest request = new CreateBookingRequest(
                    savedRoom.getId(),
                    LocalDate.of(2026, 11, 10),
                    LocalTime.of(10, 0),
                    LocalTime.of(11, 0),
                    "CS301",
                    "Database Systems"
            );

            mockMvc.perform(post("/api/bookings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.message", containsString("Room belongs to an inactive complex")));
        } finally {
            roomRepository.delete(savedRoom);
            complexRepository.delete(savedComplex);
        }
    }

    @Test
    @DisplayName("10. Invalid date/time is rejected")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void invalidDateTimeIsRejected() throws Exception {
        String invalidJson = """
                {
                  "roomId": 1,
                  "courseCode": "CS301",
                  "courseName": "Database Systems"
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("11. startTime >= endTime is rejected")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void startTimeGteEndTimeIsRejected() throws Exception {
        // Equal times
        CreateBookingRequest equalTimes = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(10, 0),
                LocalTime.of(10, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(equalTimes)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Start time must be strictly before end time")));

        // Start time after end time
        CreateBookingRequest invertedTimes = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(11, 0),
                LocalTime.of(10, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invertedTimes)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Start time must be strictly before end time")));
    }

    @Test
    @DisplayName("12. Blank course fields are rejected")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void blankCourseFieldsAreRejected() throws Exception {
        CreateBookingRequest blankCode = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "   ",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        CreateBookingRequest blankName = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 10),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "CS301",
                "   "
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    // =========================================================================
    // Room Conflicts Tests (13-16)
    // =========================================================================

    @Test
    @DisplayName("13. Overlapping scheduled room occurrence returns 409")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void overlappingScheduledRoomOccurrenceReturns409() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User ada = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 11, 15);

        // Pre-existing official class in Room 1 from 14:00 to 15:00
        createTestOccurrence(room, ada, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.SCHEDULED);

        // Alan Turing attempts booking Room 1 overlapping 14:30 to 15:30
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                testDate,
                LocalTime.of(14, 30),
                LocalTime.of(15, 30),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", is("Room is already scheduled for the requested time.")));
    }

    @Test
    @DisplayName("14. Cancelled room occurrence does not block booking")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void cancelledRoomOccurrenceDoesNotBlockBooking() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User ada = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 11, 15);

        // Cancelled occurrence in Room 1 from 16:00 to 17:00
        createTestOccurrence(room, ada, testDate, LocalTime.of(16, 0), LocalTime.of(17, 0), ScheduleStatus.CANCELLED);

        // Alan Turing books the same slot
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                testDate,
                LocalTime.of(16, 0),
                LocalTime.of(17, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("SCHEDULED")));
    }

    @Test
    @DisplayName("15. Adjacent room occurrence is allowed")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void adjacentRoomOccurrenceIsAllowed() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User ada = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 11, 15);

        // Class from 09:00 to 10:00
        createTestOccurrence(room, ada, testDate, LocalTime.of(9, 0), LocalTime.of(10, 0), ScheduleStatus.SCHEDULED);

        // Class right after from 10:00 to 11:00 (adjacent)
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                testDate,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime", is("10:00:00")))
                .andExpect(jsonPath("$.endTime", is("11:00:00")));
    }

    @Test
    @DisplayName("16. Existing EXTRA occurrence overlapping the room returns 409")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void existingExtraOccurrenceOverlappingRoomReturns409() throws Exception {
        LocalDate testDate = LocalDate.of(2026, 11, 16);

        // Book first extra class: 11:00 to 12:00
        CreateBookingRequest firstBooking = new CreateBookingRequest(
                1,
                testDate,
                LocalTime.of(11, 0),
                LocalTime.of(12, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstBooking)))
                .andExpect(status().isCreated());

        // Book second extra class overlapping: 11:30 to 12:30
        CreateBookingRequest secondBooking = new CreateBookingRequest(
                1,
                testDate,
                LocalTime.of(11, 30),
                LocalTime.of(12, 30),
                "CS302",
                "Operating Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondBooking)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Room is already scheduled for the requested time.")));
    }

    // =========================================================================
    // Professor Conflicts Tests (17-19)
    // =========================================================================

    @Test
    @DisplayName("17. Professor's existing scheduled occurrence blocks booking")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorExistingScheduledOccurrenceBlocksBooking() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 11, 17);

        // Turing is already teaching in Room 1 from 14:00 to 15:00
        createTestOccurrence(room1, turing, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.SCHEDULED);

        // Turing tries to book Room 2 from 14:30 to 15:30 (personal schedule collision)
        CreateBookingRequest request = new CreateBookingRequest(
                2,
                testDate,
                LocalTime.of(14, 30),
                LocalTime.of(15, 30),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", is("Professor already has a scheduled class during the requested time.")));
    }

    @Test
    @DisplayName("18. Professor's cancelled occurrence does not block booking")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCancelledOccurrenceDoesNotBlockBooking() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 11, 17);

        // Turing has a CANCELLED class in Room 1 from 16:00 to 17:00
        createTestOccurrence(room1, turing, testDate, LocalTime.of(16, 0), LocalTime.of(17, 0), ScheduleStatus.CANCELLED);

        // Turing books Room 2 from 16:00 to 17:00
        CreateBookingRequest request = new CreateBookingRequest(
                2,
                testDate,
                LocalTime.of(16, 0),
                LocalTime.of(17, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("SCHEDULED")));
    }

    @Test
    @DisplayName("19. Professor can book a different room when there is no personal conflict")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCanBookDifferentRoomWhenNoPersonalConflict() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 11, 17);

        // Turing has class in Room 1 from 10:00 to 11:00
        createTestOccurrence(room1, turing, testDate, LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        // Turing books Room 2 from 12:00 to 13:00 (no conflict)
        CreateBookingRequest request = new CreateBookingRequest(
                2,
                testDate,
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.room.id", is(2)));
    }

    // =========================================================================
    // Authorization Tests (20-22)
    // =========================================================================

    @Test
    @DisplayName("20. Professor can book")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCanBook() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 18),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("21. Admin cannot use the professor booking endpoint")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCannotUseProfessorBookingEndpoint() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 18),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")));
    }

    @Test
    @DisplayName("22. Unauthenticated request returns 401")
    void unauthenticatedRequestReturns401() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                1,
                LocalDate.of(2026, 11, 18),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "CS301",
                "Database Systems"
        );

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    // =========================================================================
    // Concurrency Tests (23-25)
    // =========================================================================

    @Test
    @DisplayName("23. Two concurrent bookings for the same room and overlapping interval result in exactly one success")
    void concurrentBookingsSameRoomOverlapOnlyOneSucceeds() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);
        LocalDate testDate = LocalDate.of(2026, 12, 1);

        CreateBookingRequest request1 = new CreateBookingRequest(
                1,
                testDate,
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                "CS301-A",
                "Operating Systems"
        );
        CreateBookingRequest request2 = new CreateBookingRequest(
                1,
                testDate,
                LocalTime.of(14, 30),
                LocalTime.of(15, 30),
                "CS301-B",
                "Algorithms"
        );

        executor.submit(() -> {
            try {
                barrier.await();
                bookingService.createExtraClass(request1, "aturing@classsync.edu");
                successes.incrementAndGet();
            } catch (DuplicateResourceException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                barrier.await();
                bookingService.createExtraClass(request2, "alovelace@classsync.edu");
                successes.incrementAndGet();
            } catch (DuplicateResourceException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency test timed out");
        assertEquals(1, successes.get(), "Expected exactly one booking to succeed");
        assertEquals(1, conflicts.get(), "Expected exactly one booking to receive 409 Conflict");
    }

    @Test
    @DisplayName("24. Two concurrent overlapping bookings by the same professor result in exactly one success")
    void concurrentBookingsSameProfessorOverlapOnlyOneSucceeds() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);
        LocalDate testDate = LocalDate.of(2026, 12, 2);

        // Same professor (Turing) trying to book Room 1 and Room 2 at the same time
        CreateBookingRequest requestRoom1 = new CreateBookingRequest(
                1,
                testDate,
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                "CS301-Room1",
                "Operating Systems"
        );
        CreateBookingRequest requestRoom2 = new CreateBookingRequest(
                2,
                testDate,
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                "CS301-Room2",
                "Algorithms"
        );

        executor.submit(() -> {
            try {
                barrier.await();
                bookingService.createExtraClass(requestRoom1, "aturing@classsync.edu");
                successes.incrementAndGet();
            } catch (DuplicateResourceException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                barrier.await();
                bookingService.createExtraClass(requestRoom2, "aturing@classsync.edu");
                successes.incrementAndGet();
            } catch (DuplicateResourceException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency test timed out");
        assertEquals(1, successes.get(), "Expected exactly one booking to succeed for the same professor");
        assertEquals(1, conflicts.get(), "Expected exactly one booking to receive 409 Conflict due to professor overlap");
    }

    @Test
    @DisplayName("25. Concurrent bookings for different rooms by different professors can both succeed")
    void concurrentBookingsDifferentRoomsDifferentProfessorsBothSucceed() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);
        LocalDate testDate = LocalDate.of(2026, 12, 3);

        // Turing books Room 1, Lovelace books Room 2 at the same time
        CreateBookingRequest turingRequest = new CreateBookingRequest(
                1,
                testDate,
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                "CS301-Turing",
                "Theory of Computation"
        );
        CreateBookingRequest lovelaceRequest = new CreateBookingRequest(
                2,
                testDate,
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                "CS302-Lovelace",
                "Analytical Engine Architecture"
        );

        executor.submit(() -> {
            try {
                barrier.await();
                bookingService.createExtraClass(turingRequest, "aturing@classsync.edu");
                successes.incrementAndGet();
            } catch (DuplicateResourceException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                barrier.await();
                bookingService.createExtraClass(lovelaceRequest, "alovelace@classsync.edu");
                successes.incrementAndGet();
            } catch (DuplicateResourceException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected exception: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency test timed out");
        assertEquals(2, successes.get(), "Expected both concurrent bookings to succeed");
        assertEquals(0, conflicts.get(), "Expected zero conflicts");
    }
}
