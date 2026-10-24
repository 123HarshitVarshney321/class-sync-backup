package com.classsync.controller;

import com.classsync.dto.occurrence.RescheduleRequest;
import com.classsync.entity.*;
import com.classsync.exception.DuplicateResourceException;
import com.classsync.repository.*;
import com.classsync.service.ClassScheduleService;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ClassScheduleIntegrationTests {

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
    private TimetableVersionRepository timetableVersionRepository;

    @Autowired
    private TimetableEntryRepository timetableEntryRepository;

    @Autowired
    private ClassScheduleService classScheduleService;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        scheduleOccurrenceRepository.deleteAll();
    }

    private ScheduleOccurrence createOccurrence(Room room, User prof, LocalDate date, LocalTime start, LocalTime end, ScheduleStatus status) {
        ScheduleOccurrence occ = new ScheduleOccurrence(
                null,
                prof,
                room,
                date,
                start,
                end,
                ScheduleEventType.EXTRA,
                status,
                "CS301",
                "Database Systems",
                prof
        );
        return scheduleOccurrenceRepository.save(occ);
    }

    private ScheduleOccurrence createOfficialOccurrence(TimetableEntry entry, Room room, User prof, LocalDate date, LocalTime start, LocalTime end, ScheduleStatus status) {
        ScheduleOccurrence occ = new ScheduleOccurrence(
                entry,
                prof,
                room,
                date,
                start,
                end,
                ScheduleEventType.OFFICIAL,
                status,
                entry.getCourseCode(),
                entry.getCourseName(),
                prof
        );
        return scheduleOccurrenceRepository.save(occ);
    }

    // =========================================================================
    // Cancellation Tests (1–9)
    // =========================================================================

    @Test
    @DisplayName("1. Professor cancels own scheduled occurrence -> 200")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCancelsOwnScheduledOccurrence() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room, turing, LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(occ.getId())))
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    @DisplayName("2. Cancellation changes status to CANCELLED")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void cancellationChangesStatusToCancelled() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room, turing, LocalDate.of(2026, 12, 1), LocalTime.of(11, 0), LocalTime.of(12, 0), ScheduleStatus.SCHEDULED);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/cancel"))
                .andExpect(status().isOk());

        ScheduleOccurrence updated = scheduleOccurrenceRepository.findById(occ.getId()).orElseThrow();
        assertEquals(ScheduleStatus.CANCELLED, updated.getStatus());
    }

    @Test
    @DisplayName("3. Occurrence remains in database (not deleted)")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void occurrenceRemainsInDatabaseAfterCancellation() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room, turing, LocalDate.of(2026, 12, 1), LocalTime.of(12, 0), LocalTime.of(13, 0), ScheduleStatus.SCHEDULED);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/cancel"))
                .andExpect(status().isOk());

        assertTrue(scheduleOccurrenceRepository.findById(occ.getId()).isPresent());
    }

    @Test
    @DisplayName("4. Professor cannot cancel another professor's occurrence -> 403")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCannotCancelAnotherProfessorsOccurrence() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User lovelace = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room, lovelace, LocalDate.of(2026, 12, 1), LocalTime.of(13, 0), LocalTime.of(14, 0), ScheduleStatus.SCHEDULED);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/cancel"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("5. Admin can cancel another professor's occurrence -> 200")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanCancelAnotherProfessorsOccurrence() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User lovelace = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room, lovelace, LocalDate.of(2026, 12, 1), LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.SCHEDULED);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    @DisplayName("6. Already cancelled occurrence returns 409 Conflict")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void alreadyCancelledOccurrenceReturns409() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room, turing, LocalDate.of(2026, 12, 1), LocalTime.of(15, 0), LocalTime.of(16, 0), ScheduleStatus.CANCELLED);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("already cancelled")));
    }

    @Test
    @DisplayName("7. Unauthenticated cancellation returns 401")
    void unauthenticatedCancellationReturns401() throws Exception {
        mockMvc.perform(post("/api/classes/1/cancel"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("8. Cancelled occurrence no longer blocks room availability")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void cancelledOccurrenceNoLongerBlocksRoomAvailability() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 12, 1);
        ScheduleOccurrence occ = createOccurrence(room, turing, testDate, LocalTime.of(17, 0), LocalTime.of(18, 0), ScheduleStatus.SCHEDULED);

        // Cancel the occurrence
        mockMvc.perform(post("/api/classes/" + occ.getId() + "/cancel"))
                .andExpect(status().isOk());

        // Check availability: Room 1 should now be available from 17:00 to 18:00
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "17:00")
                        .param("endTime", "18:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(1)));
    }

    @Test
    @DisplayName("9. Cancelled occurrence no longer blocks professor availability")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void cancelledOccurrenceNoLongerBlocksProfessorAvailability() throws Exception {
        Room room = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 12, 1);
        ScheduleOccurrence occ = createOccurrence(room, turing, testDate, LocalTime.of(18, 0), LocalTime.of(19, 0), ScheduleStatus.SCHEDULED);

        // Cancel Turing's class
        mockMvc.perform(post("/api/classes/" + occ.getId() + "/cancel"))
                .andExpect(status().isOk());

        // Now Turing searches for rooms at 18:00-19:00 -> not blocked by personal conflict
        mockMvc.perform(get("/api/rooms/available")
                        .param("date", testDate.toString())
                        .param("startTime", "18:00")
                        .param("endTime", "19:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(not(empty()))));
    }

    // =========================================================================
    // Rescheduling Tests (10–29)
    // =========================================================================

    @Test
    @DisplayName("10. Professor reschedules own occurrence -> 200")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorReschedulesOwnOccurrence() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 5), LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        RescheduleRequest request = new RescheduleRequest(
                LocalDate.of(2026, 12, 5),
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                2
        );

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(occ.getId())))
                .andExpect(jsonPath("$.startTime", is("14:00:00")))
                .andExpect(jsonPath("$.endTime", is("15:00:00")))
                .andExpect(jsonPath("$.room.id", is(2)));
    }

    @Test
    @DisplayName("11. Admin reschedules another professor's occurrence -> 200")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminReschedulesAnotherProfessorsOccurrence() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User lovelace = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, lovelace, LocalDate.of(2026, 12, 5), LocalTime.of(11, 0), LocalTime.of(12, 0), ScheduleStatus.SCHEDULED);

        RescheduleRequest request = new RescheduleRequest(
                LocalDate.of(2026, 12, 5),
                LocalTime.of(15, 0),
                LocalTime.of(16, 0),
                2
        );

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(occ.getId())))
                .andExpect(jsonPath("$.room.id", is(2)));
    }

    @Test
    @DisplayName("12. Professor cannot reschedule another professor's occurrence -> 403")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCannotRescheduleAnotherProfessorsOccurrence() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User lovelace = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, lovelace, LocalDate.of(2026, 12, 5), LocalTime.of(12, 0), LocalTime.of(13, 0), ScheduleStatus.SCHEDULED);

        RescheduleRequest request = new RescheduleRequest(
                LocalDate.of(2026, 12, 5),
                LocalTime.of(16, 0),
                LocalTime.of(17, 0),
                2
        );

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("13. Cancelled occurrence cannot be rescheduled -> 409")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void cancelledOccurrenceCannotBeRescheduled() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 5), LocalTime.of(13, 0), LocalTime.of(14, 0), ScheduleStatus.CANCELLED);

        RescheduleRequest request = new RescheduleRequest(
                LocalDate.of(2026, 12, 5),
                LocalTime.of(17, 0),
                LocalTime.of(18, 0),
                2
        );

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Cancelled occurrences cannot be rescheduled")));
    }

    @Test
    @DisplayName("14. Missing room returns 404")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void missingRoomReturns404() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 5), LocalTime.of(9, 0), LocalTime.of(10, 0), ScheduleStatus.SCHEDULED);

        RescheduleRequest request = new RescheduleRequest(
                LocalDate.of(2026, 12, 5),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                99999
        );

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Room not found with id: 99999")));
    }

    @Test
    @DisplayName("15. Inactive room returns 404")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void inactiveRoomReturns404() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Complex complex = complexRepository.findById(1).orElseThrow();
        Room inactiveRoom = roomRepository.save(new Room(complex, "INACT-" + suffix, RoomType.CLASSROOM, 30, false));

        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 5), LocalTime.of(8, 0), LocalTime.of(9, 0), ScheduleStatus.SCHEDULED);

        try {
            RescheduleRequest request = new RescheduleRequest(
                    LocalDate.of(2026, 12, 5),
                    LocalTime.of(10, 0),
                    LocalTime.of(11, 0),
                    inactiveRoom.getId()
            );

            mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.message", containsString("Room is inactive")));
        } finally {
            roomRepository.delete(inactiveRoom);
        }
    }

    @Test
    @DisplayName("16. Inactive complex returns 404")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void inactiveComplexReturns404() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Complex inactiveComplex = complexRepository.save(new Complex("Decommissioned " + suffix, "D" + suffix, false));
        Room roomInInactiveComplex = roomRepository.save(new Room(inactiveComplex, "DEC-" + suffix, RoomType.CLASSROOM, 40, true));

        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 5), LocalTime.of(7, 0), LocalTime.of(8, 0), ScheduleStatus.SCHEDULED);

        try {
            RescheduleRequest request = new RescheduleRequest(
                    LocalDate.of(2026, 12, 5),
                    LocalTime.of(10, 0),
                    LocalTime.of(11, 0),
                    roomInInactiveComplex.getId()
            );

            mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.message", containsString("Room belongs to an inactive complex")));
        } finally {
            roomRepository.delete(roomInInactiveComplex);
            complexRepository.delete(inactiveComplex);
        }
    }

    @Test
    @DisplayName("17. Invalid time range (startTime >= endTime) returns 400")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void invalidTimeRangeReturns400() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 6), LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        RescheduleRequest equalTimes = new RescheduleRequest(
                LocalDate.of(2026, 12, 6),
                LocalTime.of(14, 0),
                LocalTime.of(14, 0),
                1
        );

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(equalTimes)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Start time must be strictly before end time")));

        RescheduleRequest invertedTimes = new RescheduleRequest(
                LocalDate.of(2026, 12, 6),
                LocalTime.of(15, 0),
                LocalTime.of(14, 0),
                1
        );

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invertedTimes)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Start time must be strictly before end time")));
    }

    @Test
    @DisplayName("18. Overlapping room occurrence returns 409")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void overlappingRoomOccurrenceReturns409() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        Room room2 = roomRepository.findById(2).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        User lovelace = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();

        LocalDate testDate = LocalDate.of(2026, 12, 7);
        // Lovelace already has Room 2 booked 14:00-15:00
        createOccurrence(room2, lovelace, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.SCHEDULED);

        // Turing has an occurrence in Room 1 10:00-11:00
        ScheduleOccurrence occ = createOccurrence(room1, turing, testDate, LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        // Turing attempts to reschedule into Room 2 overlapping 14:30-15:30
        RescheduleRequest request = new RescheduleRequest(testDate, LocalTime.of(14, 30), LocalTime.of(15, 30), 2);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Room is already scheduled for the requested time.")));
    }

    @Test
    @DisplayName("19. Overlapping professor occurrence returns 409")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void overlappingProfessorOccurrenceReturns409() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        Room room2 = roomRepository.findById(2).orElseThrow();
        Room room3 = roomRepository.findById(3).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();

        LocalDate testDate = LocalDate.of(2026, 12, 7);
        // Turing has a class in Room 3 14:00-15:00
        createOccurrence(room3, turing, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.SCHEDULED);

        // Turing has another occurrence in Room 1 10:00-11:00
        ScheduleOccurrence occ = createOccurrence(room1, turing, testDate, LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        // Turing attempts to reschedule Occurrence into Room 2 at 14:30-15:30 (conflicts with his Room 3 class)
        RescheduleRequest request = new RescheduleRequest(testDate, LocalTime.of(14, 30), LocalTime.of(15, 30), 2);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Professor already has a scheduled class during the requested time.")));
    }

    @Test
    @DisplayName("20. Cancelled room occurrence does not block rescheduling")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void cancelledRoomOccurrenceDoesNotBlockRescheduling() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        Room room2 = roomRepository.findById(2).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        User lovelace = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();

        LocalDate testDate = LocalDate.of(2026, 12, 8);
        // Room 2 has a CANCELLED class 14:00-15:00
        createOccurrence(room2, lovelace, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.CANCELLED);

        // Turing has occurrence in Room 1 10:00-11:00
        ScheduleOccurrence occ = createOccurrence(room1, turing, testDate, LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        // Reschedule into Room 2 at 14:00-15:00
        RescheduleRequest request = new RescheduleRequest(testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), 2);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.id", is(2)));
    }

    @Test
    @DisplayName("21. Cancelled professor occurrence does not block rescheduling")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void cancelledProfessorOccurrenceDoesNotBlockRescheduling() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        Room room2 = roomRepository.findById(2).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();

        LocalDate testDate = LocalDate.of(2026, 12, 8);
        // Turing has a CANCELLED class in Room 3 16:00-17:00
        Room room3 = roomRepository.findById(3).orElseThrow();
        createOccurrence(room3, turing, testDate, LocalTime.of(16, 0), LocalTime.of(17, 0), ScheduleStatus.CANCELLED);

        // Turing has occurrence in Room 1 10:00-11:00
        ScheduleOccurrence occ = createOccurrence(room1, turing, testDate, LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        // Reschedule into Room 2 at 16:00-17:00
        RescheduleRequest request = new RescheduleRequest(testDate, LocalTime.of(16, 0), LocalTime.of(17, 0), 2);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.id", is(2)));
    }

    @Test
    @DisplayName("22. Adjacent room slot is allowed")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void adjacentRoomSlotIsAllowed() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        User lovelace = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();

        LocalDate testDate = LocalDate.of(2026, 12, 9);
        // Lovelace has Room 1 14:00-15:00
        createOccurrence(room1, lovelace, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.SCHEDULED);

        // Turing has occurrence in Room 2 10:00-11:00
        ScheduleOccurrence occ = createOccurrence(roomRepository.findById(2).orElseThrow(), turing, testDate, LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        // Turing reschedules into Room 1 adjacent 15:00-16:00
        RescheduleRequest request = new RescheduleRequest(testDate, LocalTime.of(15, 0), LocalTime.of(16, 0), 1);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime", is("15:00:00")))
                .andExpect(jsonPath("$.endTime", is("16:00:00")));
    }

    @Test
    @DisplayName("23. Same occurrence does not conflict with itself")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void sameOccurrenceDoesNotConflictWithItself() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 12, 10);

        // Occurrence in Room 1 14:00-15:00
        ScheduleOccurrence occ = createOccurrence(room1, turing, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), ScheduleStatus.SCHEDULED);

        // Reschedule in the SAME Room 1 to an overlapping time 14:30-15:30
        RescheduleRequest request = new RescheduleRequest(testDate, LocalTime.of(14, 30), LocalTime.of(15, 30), 1);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime", is("14:30:00")))
                .andExpect(jsonPath("$.endTime", is("15:30:00")));
    }

    @Test
    @DisplayName("24. Rescheduling preserves occurrence ID")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void reschedulingPreservesOccurrenceId() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 11), LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        RescheduleRequest request = new RescheduleRequest(LocalDate.of(2026, 12, 11), LocalTime.of(12, 0), LocalTime.of(13, 0), 2);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(occ.getId())));
    }

    @Test
    @DisplayName("25. Rescheduling preserves professor")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void reschedulingPreservesProfessor() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 11), LocalTime.of(11, 0), LocalTime.of(12, 0), ScheduleStatus.SCHEDULED);

        // Admin reschedules Turing's occurrence
        RescheduleRequest request = new RescheduleRequest(LocalDate.of(2026, 12, 11), LocalTime.of(14, 0), LocalTime.of(15, 0), 2);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professor.id", is(turing.getId())))
                .andExpect(jsonPath("$.professor.email", is(turing.getEmail())));
    }

    @Test
    @DisplayName("26. Rescheduling preserves event type")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void reschedulingPreservesEventType() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 11), LocalTime.of(13, 0), LocalTime.of(14, 0), ScheduleStatus.SCHEDULED);

        RescheduleRequest request = new RescheduleRequest(LocalDate.of(2026, 12, 11), LocalTime.of(16, 0), LocalTime.of(17, 0), 2);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType", is("EXTRA")));
    }

    @Test
    @DisplayName("27. Rescheduling preserves timetableEntry relationship")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void reschedulingPreservesTimetableEntryRelationship() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();

        String uniqueYear = "9999-" + UUID.randomUUID().toString().substring(0, 4);
        TimetableVersion version = timetableVersionRepository.save(new TimetableVersion(uniqueYear, "1", 1, TimetableVersionStatus.ACTIVE, turing));
        TimetableEntry entry = timetableEntryRepository.save(new TimetableEntry(
                version, turing, "CS500", "Advanced Research", room1, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0)
        ));

        ScheduleOccurrence officialOcc = null;
        try {
            officialOcc = createOfficialOccurrence(entry, room1, turing, LocalDate.of(2026, 12, 12), LocalTime.of(9, 0), LocalTime.of(10, 0), ScheduleStatus.SCHEDULED);

            RescheduleRequest request = new RescheduleRequest(LocalDate.of(2026, 12, 12), LocalTime.of(14, 0), LocalTime.of(15, 0), 2);

            mockMvc.perform(post("/api/classes/" + officialOcc.getId() + "/reschedule")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            ScheduleOccurrence updated = scheduleOccurrenceRepository.findById(officialOcc.getId()).orElseThrow();
            assertNotNull(updated.getTimetableEntry());
            assertEquals(entry.getId(), updated.getTimetableEntry().getId());
        } finally {
            if (officialOcc != null) {
                scheduleOccurrenceRepository.delete(officialOcc);
            }
            timetableEntryRepository.delete(entry);
            timetableVersionRepository.delete(version);
        }
    }

    @Test
    @DisplayName("28. Rescheduling preserves course information")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void reschedulingPreservesCourseInformation() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 13), LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        RescheduleRequest request = new RescheduleRequest(LocalDate.of(2026, 12, 13), LocalTime.of(14, 0), LocalTime.of(15, 0), 2);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseCode", is("CS301")))
                .andExpect(jsonPath("$.courseName", is("Database Systems")));
    }

    @Test
    @DisplayName("29. Rescheduling updates date/time/room correctly")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void reschedulingUpdatesDateTimeRoomCorrectly() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        ScheduleOccurrence occ = createOccurrence(room1, turing, LocalDate.of(2026, 12, 14), LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleStatus.SCHEDULED);

        LocalDate newDate = LocalDate.of(2026, 12, 15);
        LocalTime newStart = LocalTime.of(15, 0);
        LocalTime newEnd = LocalTime.of(16, 0);
        RescheduleRequest request = new RescheduleRequest(newDate, newStart, newEnd, 2);

        mockMvc.perform(post("/api/classes/" + occ.getId() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ScheduleOccurrence updated = scheduleOccurrenceRepository.findById(occ.getId()).orElseThrow();
        assertEquals(newDate, updated.getScheduleDate());
        assertEquals(newStart, updated.getStartTime());
        assertEquals(newEnd, updated.getEndTime());
        assertEquals(2, updated.getRoom().getId());
    }

    // =========================================================================
    // Concurrency Tests (30–32)
    // =========================================================================

    @Test
    @DisplayName("30. Concurrent rescheduling of the same occurrence does not cause lost updates")
    void concurrentReschedulingSameOccurrenceDoesNotCauseLostUpdates() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 12, 20);

        ScheduleOccurrence occ = createOccurrence(room1, turing, testDate, LocalTime.of(9, 0), LocalTime.of(10, 0), ScheduleStatus.SCHEDULED);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);

        RescheduleRequest reqA = new RescheduleRequest(testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), 1);
        RescheduleRequest reqB = new RescheduleRequest(testDate, LocalTime.of(16, 0), LocalTime.of(17, 0), 2);

        executor.submit(() -> {
            try {
                barrier.await();
                classScheduleService.rescheduleOccurrence(occ.getId(), reqA, "aturing@classsync.edu");
                successes.incrementAndGet();
            } catch (Exception e) {
                // Could be duplicate/conflict depending on ordering
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                barrier.await();
                classScheduleService.rescheduleOccurrence(occ.getId(), reqB, "aturing@classsync.edu");
                successes.incrementAndGet();
            } catch (Exception e) {
                // Could be duplicate/conflict depending on ordering
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency test timed out");
        // Both requests are non-conflicting time slots (14:00-15:00 vs 16:00-17:00),
        // so serialized execution through the occurrence lock allows both to succeed sequentially
        assertEquals(2, successes.get());

        // The final state must be the last committed update (valid, non-corrupted state)
        ScheduleOccurrence finalOcc = scheduleOccurrenceRepository.findById(occ.getId()).orElseThrow();
        assertTrue(finalOcc.getStartTime().equals(LocalTime.of(14, 0)) || finalOcc.getStartTime().equals(LocalTime.of(16, 0)));
    }

    @Test
    @DisplayName("31. Concurrent rescheduling into the same target room/time results in deterministic conflict handling")
    void concurrentReschedulingSameTargetRoomSlotDeterministicallyConflicts() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        Room room2 = roomRepository.findById(2).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        User lovelace = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();

        LocalDate testDate = LocalDate.of(2026, 12, 21);

        ScheduleOccurrence occTuring = createOccurrence(room1, turing, testDate, LocalTime.of(9, 0), LocalTime.of(10, 0), ScheduleStatus.SCHEDULED);
        ScheduleOccurrence occLovelace = createOccurrence(room2, lovelace, testDate, LocalTime.of(11, 0), LocalTime.of(12, 0), ScheduleStatus.SCHEDULED);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        // Both attempt to reschedule into Room 3 at the EXACT same time 14:00-15:00
        RescheduleRequest targetSlot = new RescheduleRequest(testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), 3);

        executor.submit(() -> {
            try {
                barrier.await();
                classScheduleService.rescheduleOccurrence(occTuring.getId(), targetSlot, "aturing@classsync.edu");
                successes.incrementAndGet();
            } catch (DuplicateResourceException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected error: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                barrier.await();
                classScheduleService.rescheduleOccurrence(occLovelace.getId(), targetSlot, "alovelace@classsync.edu");
                successes.incrementAndGet();
            } catch (DuplicateResourceException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected error: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency test timed out");
        assertEquals(1, successes.get(), "Expected exactly one reschedule to succeed");
        assertEquals(1, conflicts.get(), "Expected exactly one reschedule to receive 409 Conflict");
    }

    @Test
    @DisplayName("32. Concurrent valid reschedules involving different rooms/professors proceed independently without blocking")
    void concurrentDisjointReschedulesProceedIndependently() throws Exception {
        Room room1 = roomRepository.findById(1).orElseThrow();
        Room room2 = roomRepository.findById(2).orElseThrow();
        User turing = userRepository.findByEmail("aturing@classsync.edu").orElseThrow();
        User lovelace = userRepository.findByEmail("alovelace@classsync.edu").orElseThrow();

        LocalDate testDate = LocalDate.of(2026, 12, 22);

        ScheduleOccurrence occTuring = createOccurrence(room1, turing, testDate, LocalTime.of(9, 0), LocalTime.of(10, 0), ScheduleStatus.SCHEDULED);
        ScheduleOccurrence occLovelace = createOccurrence(room2, lovelace, testDate, LocalTime.of(11, 0), LocalTime.of(12, 0), ScheduleStatus.SCHEDULED);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger(0);

        // Turing moves to Room 4 at 14:00-15:00
        RescheduleRequest reqTuring = new RescheduleRequest(testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), 4);
        // Lovelace moves to Room 5 at 14:00-15:00
        RescheduleRequest reqLovelace = new RescheduleRequest(testDate, LocalTime.of(14, 0), LocalTime.of(15, 0), 5);

        executor.submit(() -> {
            try {
                barrier.await();
                classScheduleService.rescheduleOccurrence(occTuring.getId(), reqTuring, "aturing@classsync.edu");
                successes.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected error: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                barrier.await();
                classScheduleService.rescheduleOccurrence(occLovelace.getId(), reqLovelace, "alovelace@classsync.edu");
                successes.incrementAndGet();
            } catch (Exception e) {
                fail("Unexpected error: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrency test timed out");
        assertEquals(2, successes.get(), "Expected both disjoint reschedules to succeed concurrently");
    }
}
