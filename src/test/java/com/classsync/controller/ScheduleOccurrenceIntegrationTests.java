package com.classsync.controller;

import com.classsync.dto.occurrence.GenerateOccurrencesRequest;
import com.classsync.entity.*;
import com.classsync.repository.*;
import com.classsync.service.ScheduleOccurrenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScheduleOccurrenceIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TimetableVersionRepository timetableVersionRepository;

    @Autowired
    private TimetableEntryRepository timetableEntryRepository;

    @Autowired
    private ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ComplexRepository complexRepository;

    @Autowired
    private ScheduleOccurrenceService scheduleOccurrenceService;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================================
    // Occurrence Generation Tests (1 - 12)
    // =========================================================================

    @Test
    @DisplayName("1. Admin can generate occurrences from an active timetable")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanGenerateOccurrencesFromActiveTimetable() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");
        createTimetableEntry(activeVersion, 2, 1, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "CS101", "Intro to CS"); // Monday
        createTimetableEntry(activeVersion, 3, 2, (byte) 3, LocalTime.of(11, 0), LocalTime.of(12, 30), "MATH201", "Calculus"); // Wednesday

        // Date range: 2026-11-02 (Monday) to 2026-11-08 (Sunday) -> exactly 1 week
        // Expect: 1 Monday occurrence and 1 Wednesday occurrence = 2 occurrences
        GenerateOccurrencesRequest request = new GenerateOccurrencesRequest(
                activeVersion.getId(),
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 8)
        );

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableVersionId").value(activeVersion.getId()))
                .andExpect(jsonPath("$.startDate").value("2026-11-02"))
                .andExpect(jsonPath("$.endDate").value("2026-11-08"))
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.skippedExistingCount").value(0));
    }

    @Test
    @DisplayName("2. Professor cannot trigger generation (403 Forbidden)")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCannotTriggerGeneration() throws Exception {
        GenerateOccurrencesRequest request = new GenerateOccurrencesRequest(
                1,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)
        );

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("3. A draft timetable cannot generate occurrences (400 Bad Request)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void draftTimetableCannotGenerateOccurrences() throws Exception {
        TimetableVersion draft = new TimetableVersion("2026-2027", "1", 1, TimetableVersionStatus.DRAFT, userRepository.findById(1).orElseThrow());
        draft = timetableVersionRepository.save(draft);

        GenerateOccurrencesRequest request = new GenerateOccurrencesRequest(
                draft.getId(),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)
        );

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Occurrences can only be generated from ACTIVE timetable versions")));
    }

    @Test
    @DisplayName("4. An archived timetable cannot generate occurrences (400 Bad Request)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void archivedTimetableCannotGenerateOccurrences() throws Exception {
        TimetableVersion archived = new TimetableVersion("2026-2027", "1", 1, TimetableVersionStatus.ARCHIVED, userRepository.findById(1).orElseThrow());
        archived = timetableVersionRepository.save(archived);

        GenerateOccurrencesRequest request = new GenerateOccurrencesRequest(
                archived.getId(),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)
        );

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Occurrences can only be generated from ACTIVE timetable versions")));
    }

    @Test
    @DisplayName("5. Inclusive start/end dates are handled correctly")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void inclusiveStartEndDatesHandled() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");
        createTimetableEntry(activeVersion, 2, 1, (byte) 5, LocalTime.of(9, 0), LocalTime.of(10, 0), "CS101", "Intro to CS"); // Friday

        // Single day request: 2026-11-06 is Friday
        GenerateOccurrencesRequest singleDay = new GenerateOccurrencesRequest(
                activeVersion.getId(),
                LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 6)
        );

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(singleDay)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.skippedExistingCount").value(0));
    }

    @Test
    @DisplayName("6. Day-of-week mapping is correct (1 = Monday ... 7 = Sunday)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void dayOfWeekMappingIsCorrect() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");
        // Sunday class (day 7)
        createTimetableEntry(activeVersion, 2, 1, (byte) 7, LocalTime.of(10, 0), LocalTime.of(11, 0), "SUN101", "Sunday Seminar");

        // Request Monday Nov 2 through Saturday Nov 7 (no Sunday) -> 0 occurrences
        GenerateOccurrencesRequest noSunday = new GenerateOccurrencesRequest(
                activeVersion.getId(),
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 7)
        );
        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noSunday)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0));

        // Request Sunday Nov 8 -> 1 occurrence
        GenerateOccurrencesRequest sundayOnly = new GenerateOccurrencesRequest(
                activeVersion.getId(),
                LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 8)
        );
        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sundayOnly)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1));
    }

    @Test
    @DisplayName("7. Repeated generation skips existing occurrences")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void repeatedGenerationSkipsExistingOccurrences() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");
        createTimetableEntry(activeVersion, 2, 1, (byte) 2, LocalTime.of(9, 0), LocalTime.of(10, 0), "CS101", "Intro CS"); // Tuesday

        GenerateOccurrencesRequest request = new GenerateOccurrencesRequest(
                activeVersion.getId(),
                LocalDate.of(2026, 11, 3), // Tuesday
                LocalDate.of(2026, 11, 10) // Tuesday (2 Tuesdays total: Nov 3 and Nov 10)
        );

        // First run: 2 created, 0 skipped
        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2))
                .andExpect(jsonPath("$.skippedExistingCount").value(0));

        // Second run: 0 created, 2 skipped
        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.skippedExistingCount").value(2));
    }

    @Test
    @DisplayName("8. Repeated generation preserves a cancelled occurrence")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void repeatedGenerationPreservesCancelledOccurrence() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");
        TimetableEntry entry = createTimetableEntry(activeVersion, 2, 1, (byte) 4, LocalTime.of(9, 0), LocalTime.of(10, 0), "CS101", "Intro CS"); // Thursday

        LocalDate date = LocalDate.of(2026, 11, 5); // Thursday
        GenerateOccurrencesRequest request = new GenerateOccurrencesRequest(activeVersion.getId(), date, date);

        // Generate occurrence
        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1));

        // Manually mark occurrence as CANCELLED (as if cancelled in a future prompt)
        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findWithFilters(date, date, null, null, null, null).get(0);
        occurrence.setStatus(ScheduleStatus.CANCELLED);
        scheduleOccurrenceRepository.save(occurrence);

        // Run generation again
        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.skippedExistingCount").value(1));

        // Verify status remains CANCELLED!
        ScheduleOccurrence preserved = scheduleOccurrenceRepository.findById(occurrence.getId()).orElseThrow();
        assertEquals(ScheduleStatus.CANCELLED, preserved.getStatus());
    }

    @Test
    @DisplayName("9. Invalid or reversed dates are rejected (400 Bad Request)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void reversedDatesRejected() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");

        GenerateOccurrencesRequest request = new GenerateOccurrencesRequest(
                activeVersion.getId(),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 11, 1) // reversed
        );

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Start date must be less than or equal to end date")));
    }

    @Test
    @DisplayName("10. A generation window exceeding 12 months is rejected (400 Bad Request)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void windowExceeding12MonthsRejected() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");

        GenerateOccurrencesRequest request = new GenerateOccurrencesRequest(
                activeVersion.getId(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 5) // > 12 months
        );

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Generation window cannot exceed 12 months")));
    }

    @Test
    @DisplayName("11. created_by comes from authenticated admin")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void createdByComesFromAuthenticatedAdmin() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");
        createTimetableEntry(activeVersion, 2, 1, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "CS101", "Intro CS");

        LocalDate monday = LocalDate.of(2026, 11, 2);
        GenerateOccurrencesRequest request = new GenerateOccurrencesRequest(activeVersion.getId(), monday, monday);

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findWithFilters(monday, monday, null, null, null, null).get(0);
        assertEquals("admin@classsync.edu", occurrence.getCreatedBy().getEmail());
        assertEquals("System Administrator", occurrence.getCreatedBy().getName());
    }

    @Test
    @DisplayName("12. Invalid or missing timetable version returns 404 Not Found")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void missingTimetableVersionReturns404() throws Exception {
        GenerateOccurrencesRequest request = new GenerateOccurrencesRequest(
                99999, // non-existent
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30)
        );

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // Occurrence Retrieval Tests (13 - 19)
    // =========================================================================

    @Test
    @DisplayName("13. Professor can list occurrences")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCanListOccurrences() throws Exception {
        mockMvc.perform(get("/api/schedule-occurrences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("14. Admin can retrieve an occurrence by ID")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanRetrieveOccurrenceById() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");
        createTimetableEntry(activeVersion, 2, 1, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "CS101", "Intro CS");

        LocalDate monday = LocalDate.of(2026, 11, 2);
        GenerateOccurrencesRequest genReq = new GenerateOccurrencesRequest(activeVersion.getId(), monday, monday);

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(genReq)))
                .andExpect(status().isOk());

        Integer occId = scheduleOccurrenceRepository.findWithFilters(monday, monday, null, null, null, null).get(0).getId();

        mockMvc.perform(get("/api/schedule-occurrences/{id}", occId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(occId))
                .andExpect(jsonPath("$.courseCode").value("CS101"))
                .andExpect(jsonPath("$.courseName").value("Intro CS"))
                .andExpect(jsonPath("$.scheduleDate").value("2026-11-02"))
                .andExpect(jsonPath("$.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.endTime").value("10:00:00"))
                .andExpect(jsonPath("$.eventType").value("OFFICIAL"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.professor.name").value("Dr. Alan Turing"))
                .andExpect(jsonPath("$.room.roomNumber").value("SEC-101"))
                .andExpect(jsonPath("$.room.complex.code").value("SEC"));
    }

    @Test
    @DisplayName("15. Date filtering works on retrieval")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void dateFilteringWorks() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");
        createTimetableEntry(activeVersion, 2, 1, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "CS101", "Intro CS"); // Mon

        // Generate for 2 Mondays: Nov 2 and Nov 9
        GenerateOccurrencesRequest genReq = new GenerateOccurrencesRequest(
                activeVersion.getId(), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9)
        );
        adminGenerate(genReq);

        // Filter only Nov 2 to Nov 5 (should return 1)
        mockMvc.perform(get("/api/schedule-occurrences")
                        .param("startDate", "2026-11-02")
                        .param("endDate", "2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].scheduleDate").value("2026-11-02"));

        // Filter reversed dates -> 400 Bad Request
        mockMvc.perform(get("/api/schedule-occurrences")
                        .param("startDate", "2026-11-10")
                        .param("endDate", "2026-11-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("16. Professor, room, complex, and status filters work")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void multifacetedFilteringWorks() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");
        createTimetableEntry(activeVersion, 2, 1, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "CS101", "Intro CS"); // Prof 2, Room 1 (SEC)
        createTimetableEntry(activeVersion, 3, 4, (byte) 1, LocalTime.of(14, 0), LocalTime.of(15, 0), "HSS101", "History"); // Prof 3, Room 4 (HSS)

        LocalDate monday = LocalDate.of(2026, 11, 2);
        GenerateOccurrencesRequest genReq = new GenerateOccurrencesRequest(activeVersion.getId(), monday, monday);
        adminGenerate(genReq);

        // Filter by professor 2
        mockMvc.perform(get("/api/schedule-occurrences").param("professorId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].professor.id").value(2));

        // Filter by room 4
        mockMvc.perform(get("/api/schedule-occurrences").param("roomId", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].room.id").value(4));

        // Filter by complex 1 (SEC)
        mockMvc.perform(get("/api/schedule-occurrences").param("complexId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].room.complex.id").value(1));

        // Filter by status SCHEDULED
        mockMvc.perform(get("/api/schedule-occurrences").param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Filter by status CANCELLED -> 0
        mockMvc.perform(get("/api/schedule-occurrences").param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("17. Missing occurrence returns 404 Not Found")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void missingOccurrenceReturns404() throws Exception {
        mockMvc.perform(get("/api/schedule-occurrences/{id}", 99999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("18. Unauthenticated requests return 401 Unauthorized")
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/schedule-occurrences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/schedule-occurrences/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/schedule-occurrences/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timetableVersionId\":1,\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-30\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("19. DTO responses contain no recursive entity graphs or sensitive user fields")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void dtoResponsesContainNoSensitiveFieldsOrRecursion() throws Exception {
        TimetableVersion activeVersion = createAndActivateVersion("2026-2027", "1");
        createTimetableEntry(activeVersion, 2, 1, (byte) 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "CS101", "Intro CS");

        LocalDate monday = LocalDate.of(2026, 11, 2);
        adminGenerate(new GenerateOccurrencesRequest(activeVersion.getId(), monday, monday));

        Integer occId = scheduleOccurrenceRepository.findWithFilters(monday, monday, null, null, null, null).get(0).getId();

        MvcResult result = mockMvc.perform(get("/api/schedule-occurrences/{id}", occId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.professor.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.createdByUser").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"));
        assertFalse(body.contains("password_hash"));
    }

    // =========================================================================
    // Database Uniqueness Constraint Tests (20)
    // =========================================================================

    @Test
    @DisplayName("20. Database unique constraint on (timetable_entry_id, schedule_date) permits multiple null entries")
    void databaseUniqueConstraintBehavior() {
        User admin = userRepository.findById(1).orElseThrow();
        User prof = userRepository.findById(2).orElseThrow();
        Room room = roomRepository.findById(1).orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 11, 20);

        // Multiple occurrences with timetable_entry = null on same date MUST be permitted (for ad-hoc/extra classes)
        ScheduleOccurrence extra1 = new ScheduleOccurrence(
                null, prof, room, testDate, LocalTime.of(9, 0), LocalTime.of(10, 0),
                ScheduleEventType.EXTRA, ScheduleStatus.SCHEDULED, "EXTRA1", "Extra Class 1", admin
        );
        ScheduleOccurrence extra2 = new ScheduleOccurrence(
                null, prof, room, testDate, LocalTime.of(14, 0), LocalTime.of(15, 0),
                ScheduleEventType.EXTRA, ScheduleStatus.SCHEDULED, "EXTRA2", "Extra Class 2", admin
        );

        scheduleOccurrenceRepository.saveAndFlush(extra1);
        scheduleOccurrenceRepository.saveAndFlush(extra2);
        assertNotNull(extra1.getId());
        assertNotNull(extra2.getId());

        // Duplicate official occurrence for same timetable_entry and date MUST fail
        TimetableVersion version = createAndActivateVersion("2026-2027", "2");
        TimetableEntry entry = createTimetableEntry(version, 2, 1, (byte) 5, LocalTime.of(9, 0), LocalTime.of(10, 0), "CS101", "Intro CS");

        ScheduleOccurrence official1 = new ScheduleOccurrence(
                entry, prof, room, testDate, LocalTime.of(9, 0), LocalTime.of(10, 0),
                ScheduleEventType.OFFICIAL, ScheduleStatus.SCHEDULED, "CS101", "Intro CS", admin
        );
        scheduleOccurrenceRepository.saveAndFlush(official1);

        ScheduleOccurrence officialDuplicate = new ScheduleOccurrence(
                entry, prof, room, testDate, LocalTime.of(9, 0), LocalTime.of(10, 0),
                ScheduleEventType.OFFICIAL, ScheduleStatus.SCHEDULED, "CS101", "Intro CS", admin
        );

        assertThrows(DataIntegrityViolationException.class, () -> {
            scheduleOccurrenceRepository.saveAndFlush(officialDuplicate);
        });
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private TimetableVersion createAndActivateVersion(String academicYear, String semester) {
        User admin = userRepository.findById(1).orElseThrow();
        int maxVer = timetableVersionRepository.findMaxVersionNumber(academicYear, semester);
        TimetableVersion version = new TimetableVersion(academicYear, semester, maxVer + 1, TimetableVersionStatus.ACTIVE, admin);
        return timetableVersionRepository.save(version);
    }

    private TimetableEntry createTimetableEntry(TimetableVersion version, Integer profId, Integer roomId,
                                                Byte dayOfWeek, LocalTime start, LocalTime end,
                                                String code, String name) {
        User prof = userRepository.findById(profId).orElseThrow();
        Room room = roomRepository.findById(roomId).orElseThrow();
        TimetableEntry entry = new TimetableEntry(version, prof, code, name, room, dayOfWeek, start, end);
        return timetableEntryRepository.save(entry);
    }

    private void adminGenerate(GenerateOccurrencesRequest request) {
        User admin = userRepository.findById(1).orElseThrow();
        scheduleOccurrenceService.generateOccurrences(request, admin.getEmail());
    }
}
