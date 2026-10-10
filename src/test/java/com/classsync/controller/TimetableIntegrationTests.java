package com.classsync.controller;

import com.classsync.dto.timetable.CreateTimetableEntryRequest;
import com.classsync.dto.timetable.CreateTimetableVersionRequest;
import com.classsync.dto.timetable.UpdateTimetableEntryRequest;
import com.classsync.entity.*;
import com.classsync.repository.ComplexRepository;
import com.classsync.repository.RoomRepository;
import com.classsync.repository.TimetableEntryRepository;
import com.classsync.repository.TimetableVersionRepository;
import com.classsync.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TimetableIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TimetableVersionRepository timetableVersionRepository;

    @Autowired
    private TimetableEntryRepository timetableEntryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ComplexRepository complexRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================================
    // Version Management Tests (1 - 8)
    // =========================================================================

    @Test
    @DisplayName("1. Admin creates a draft version")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCreatesDraftVersion() throws Exception {
        CreateTimetableVersionRequest request = new CreateTimetableVersionRequest("2026-2027", "1");

        mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.academicYear").value("2026-2027"))
                .andExpect(jsonPath("$.semester").value("1"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdById").isNumber())
                .andExpect(jsonPath("$.createdByName").value("System Administrator"));
    }

    @Test
    @DisplayName("2. Professor cannot create a version (403 Forbidden)")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCannotCreateVersion() throws Exception {
        CreateTimetableVersionRequest request = new CreateTimetableVersionRequest("2026-2027", "1");

        mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("3. Version numbers increment within an academic year/semester")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void versionNumbersIncrement() throws Exception {
        CreateTimetableVersionRequest request = new CreateTimetableVersionRequest("2026-2027", "1");

        mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1));

        mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2));

        mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(3));
    }

    @Test
    @DisplayName("4. Draft can be activated")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void draftCanBeActivated() throws Exception {
        CreateTimetableVersionRequest request = new CreateTimetableVersionRequest("2026-2027", "1");
        MvcResult result = mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Integer versionId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();

        mockMvc.perform(patch("/api/timetables/versions/{id}/activate", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("5. Activating a replacement archives the previously active version")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void activatingReplacementArchivesPreviousActive() throws Exception {
        CreateTimetableVersionRequest request = new CreateTimetableVersionRequest("2026-2027", "1");

        // Create version 1 and activate it
        MvcResult res1 = mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer v1Id = objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asInt();

        mockMvc.perform(patch("/api/timetables/versions/{id}/activate", v1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Create version 2 and activate it
        MvcResult res2 = mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer v2Id = objectMapper.readTree(res2.getResponse().getContentAsString()).get("id").asInt();

        mockMvc.perform(patch("/api/timetables/versions/{id}/activate", v2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify version 1 is now ARCHIVED
        mockMvc.perform(get("/api/timetables/versions/{id}", v1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("6. Archived versions cannot be activated")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void archivedVersionCannotBeActivated() throws Exception {
        CreateTimetableVersionRequest request = new CreateTimetableVersionRequest("2026-2027", "1");

        MvcResult res1 = mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer v1Id = objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asInt();

        // Archive it
        mockMvc.perform(patch("/api/timetables/versions/{id}/archive", v1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // Attempt to activate it
        mockMvc.perform(patch("/api/timetables/versions/{id}/activate", v1Id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Only DRAFT versions can be activated")));
    }

    @Test
    @DisplayName("7. Invalid lifecycle transitions are rejected")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void invalidLifecycleTransitionsRejected() throws Exception {
        CreateTimetableVersionRequest request = new CreateTimetableVersionRequest("2026-2027", "1");

        MvcResult res = mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer vId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asInt();

        // Archive it
        mockMvc.perform(patch("/api/timetables/versions/{id}/archive", vId))
                .andExpect(status().isOk());

        // Archiving an already archived version should return 400
        mockMvc.perform(patch("/api/timetables/versions/{id}/archive", vId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("already ARCHIVED")));
    }

    @Test
    @DisplayName("8. Unauthenticated requests return 401 Unauthorized")
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/timetables/versions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYear\":\"2026-2027\",\"semester\":\"1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // =========================================================================
    // Entry Management Tests (9 - 19)
    // =========================================================================

    @Test
    @DisplayName("9. Admin can create an entry in a draft version")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanCreateEntryInDraftVersion() throws Exception {
        Integer vId = createDraftVersion("2026-2027", "1");

        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                2, "CS101", "Intro to CS", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );

        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.timetableVersionId").value(vId))
                .andExpect(jsonPath("$.courseCode").value("CS101"))
                .andExpect(jsonPath("$.courseName").value("Intro to CS"))
                .andExpect(jsonPath("$.professor.id").value(2))
                .andExpect(jsonPath("$.professor.name").value("Dr. Alan Turing"))
                .andExpect(jsonPath("$.room.id").value(1))
                .andExpect(jsonPath("$.room.roomNumber").value("SEC-101"))
                .andExpect(jsonPath("$.dayOfWeek").value(1))
                .andExpect(jsonPath("$.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.endTime").value("10:00:00"));
    }

    @Test
    @DisplayName("10. Professor cannot create, update, or delete entries (403 Forbidden)")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCannotMutateEntries() throws Exception {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                2, "CS101", "Intro to CS", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );

        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/timetables/entries/{id}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/timetables/entries/{id}", 1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11. Invalid day or time values are rejected")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void invalidDayOrTimeRejected() throws Exception {
        Integer vId = createDraftVersion("2026-2027", "1");

        // Invalid day (8 > 7)
        CreateTimetableEntryRequest invalidDay = new CreateTimetableEntryRequest(
                2, "CS101", "Intro to CS", 1, (byte) 8,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDay)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Start time equal to end time
        CreateTimetableEntryRequest equalTimes = new CreateTimetableEntryRequest(
                2, "CS101", "Intro to CS", 1, (byte) 1,
                LocalTime.of(10, 0), LocalTime.of(10, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(equalTimes)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Start time must be strictly before end time")));

        // Start time after end time
        CreateTimetableEntryRequest reversedTimes = new CreateTimetableEntryRequest(
                2, "CS101", "Intro to CS", 1, (byte) 1,
                LocalTime.of(11, 0), LocalTime.of(10, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reversedTimes)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Start time must be strictly before end time")));
    }

    @Test
    @DisplayName("12. Inactive or non-professor users cannot be assigned as professors")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void inactiveOrNonProfessorRejected() throws Exception {
        Integer vId = createDraftVersion("2026-2027", "1");

        // Non-professor user (admin has id 1, role ADMIN)
        CreateTimetableEntryRequest adminAsProf = new CreateTimetableEntryRequest(
                1, "CS101", "Intro to CS", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminAsProf)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must have PROFESSOR role")));

        // Create an inactive professor
        User inactiveProf = new User("Inactive Prof", "inactive@classsync.edu", "hash", UserRole.PROFESSOR, "EMP-INACT", UserStatus.INACTIVE);
        inactiveProf = userRepository.save(inactiveProf);

        CreateTimetableEntryRequest inactiveProfReq = new CreateTimetableEntryRequest(
                inactiveProf.getId(), "CS101", "Intro to CS", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactiveProfReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("inactive")));
    }

    @Test
    @DisplayName("13. Inactive rooms or rooms in inactive complexes are rejected")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void inactiveRoomOrComplexRejected() throws Exception {
        Integer vId = createDraftVersion("2026-2027", "1");
        Complex complex = complexRepository.findById(1).orElseThrow();

        // Save inactive room
        Room inactiveRoom = new Room(complex, "INACT-101", RoomType.CLASSROOM, 30, false);
        inactiveRoom = roomRepository.save(inactiveRoom);

        CreateTimetableEntryRequest reqInactiveRoom = new CreateTimetableEntryRequest(
                2, "CS101", "Intro to CS", inactiveRoom.getId(), (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInactiveRoom)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Assigned room is inactive")));

        // Save inactive complex with an active room
        Complex inactiveComplex = new Complex("Inactive Complex", "INC", false);
        inactiveComplex = complexRepository.save(inactiveComplex);
        Room roomInInactiveComplex = new Room(inactiveComplex, "INC-101", RoomType.CLASSROOM, 30, true);
        roomInInactiveComplex = roomRepository.save(roomInInactiveComplex);

        CreateTimetableEntryRequest reqInactiveComplex = new CreateTimetableEntryRequest(
                2, "CS101", "Intro to CS", roomInInactiveComplex.getId(), (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInactiveComplex)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("inactive complex")));
    }

    @Test
    @DisplayName("14. Overlapping room assignments are rejected (409 Conflict)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void overlappingRoomAssignmentsRejected() throws Exception {
        Integer vId = createDraftVersion("2026-2027", "1");

        // Entry 1: room 1, day 1, 09:00 - 11:00, prof 2
        CreateTimetableEntryRequest entry1 = new CreateTimetableEntryRequest(
                2, "CS101", "Intro CS", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(11, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry1)))
                .andExpect(status().isCreated());

        // Entry 2: room 1, day 1, 10:00 - 12:00, prof 3 (different professor, same room, overlaps 10:00-11:00)
        CreateTimetableEntryRequest entry2 = new CreateTimetableEntryRequest(
                3, "MATH201", "Calculus", 1, (byte) 1,
                LocalTime.of(10, 0), LocalTime.of(12, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already has an overlapping class")));
    }

    @Test
    @DisplayName("15. Overlapping professor assignments are rejected (409 Conflict)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void overlappingProfessorAssignmentsRejected() throws Exception {
        Integer vId = createDraftVersion("2026-2027", "1");

        // Entry 1: room 1, day 1, 09:00 - 11:00, prof 2
        CreateTimetableEntryRequest entry1 = new CreateTimetableEntryRequest(
                2, "CS101", "Intro CS", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(11, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry1)))
                .andExpect(status().isCreated());

        // Entry 2: room 2 (different room), day 1, 09:30 - 10:30, prof 2 (same prof)
        CreateTimetableEntryRequest entry2 = new CreateTimetableEntryRequest(
                2, "CS102", "Data Structures", 2, (byte) 1,
                LocalTime.of(9, 30), LocalTime.of(10, 30)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already has an overlapping class")));
    }

    @Test
    @DisplayName("16. Adjacent non-overlapping classes are allowed")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adjacentNonOverlappingClassesAllowed() throws Exception {
        Integer vId = createDraftVersion("2026-2027", "1");

        // Entry 1: 09:00 - 10:00, room 1, prof 2
        CreateTimetableEntryRequest entry1 = new CreateTimetableEntryRequest(
                2, "CS101", "Intro CS", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry1)))
                .andExpect(status().isCreated());

        // Entry 2: 10:00 - 11:00, room 1, prof 2 (immediately adjacent, half-open interval rule allows this)
        CreateTimetableEntryRequest entry2 = new CreateTimetableEntryRequest(
                2, "CS102", "Data Structures", 1, (byte) 1,
                LocalTime.of(10, 0), LocalTime.of(11, 0)
        );
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry2)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("17. Updating an entry excludes itself from overlap checks")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void updateExcludesSelfFromOverlapChecks() throws Exception {
        Integer vId = createDraftVersion("2026-2027", "1");

        CreateTimetableEntryRequest entry1 = new CreateTimetableEntryRequest(
                2, "CS101", "Intro CS", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );
        MvcResult res = mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry1)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer entryId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asInt();

        // Update the entry keeping same time, room, and prof, but changed name
        UpdateTimetableEntryRequest updateReq = new UpdateTimetableEntryRequest(
                2, "CS101", "Intro CS (Updated)", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );

        mockMvc.perform(put("/api/timetables/entries/{id}", entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryId))
                .andExpect(jsonPath("$.courseName").value("Intro CS (Updated)"));
    }

    @Test
    @DisplayName("18. Entries in active or archived versions cannot be modified or deleted")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void entriesInActiveOrArchivedVersionsImmutable() throws Exception {
        Integer vId = createDraftVersion("2026-2027", "1");

        CreateTimetableEntryRequest entryReq = new CreateTimetableEntryRequest(
                2, "CS101", "Intro CS", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );
        MvcResult res = mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entryReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Integer entryId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asInt();

        // Activate version
        mockMvc.perform(patch("/api/timetables/versions/{id}/activate", vId))
                .andExpect(status().isOk());

        UpdateTimetableEntryRequest updateReq = new UpdateTimetableEntryRequest(
                2, "CS101", "Intro CS Modified", 1, (byte) 1,
                LocalTime.of(9, 0), LocalTime.of(10, 0)
        );

        // Cannot update in active version
        mockMvc.perform(put("/api/timetables/entries/{id}", entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be modified")));

        // Cannot delete in active version
        mockMvc.perform(delete("/api/timetables/entries/{id}", entryId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be deleted")));

        // Cannot add entry to active version
        mockMvc.perform(post("/api/timetables/versions/{vId}/entries", vId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entryReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("can only be added to DRAFT versions")));
    }

    @Test
    @DisplayName("19. Reading an entry returns DTOs without recursive entity graphs")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void readingEntryReturnsDtoWithoutRecursion() throws Exception {
        // First create version and entry as admin
        TimetableVersion version = new TimetableVersion(
                "2026-2027", "1", 1, TimetableVersionStatus.DRAFT, userRepository.findById(1).orElseThrow()
        );
        version = timetableVersionRepository.save(version);

        TimetableEntry entry = new TimetableEntry(
                version,
                userRepository.findById(2).orElseThrow(),
                "CS301",
                "Database Systems",
                roomRepository.findById(1).orElseThrow(),
                (byte) 2,
                LocalTime.of(14, 0),
                LocalTime.of(15, 30)
        );
        entry = timetableEntryRepository.save(entry);

        // Professor reads the entry
        mockMvc.perform(get("/api/timetables/entries/{id}", entry.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entry.getId()))
                .andExpect(jsonPath("$.timetableVersionId").value(version.getId()))
                .andExpect(jsonPath("$.professor.id").value(2))
                .andExpect(jsonPath("$.professor.name").value("Dr. Alan Turing"))
                .andExpect(jsonPath("$.professor.email").value("aturing@classsync.edu"))
                .andExpect(jsonPath("$.professor.employeeCode").value("EMP-PRF-001"))
                .andExpect(jsonPath("$.room.id").value(1))
                .andExpect(jsonPath("$.room.roomNumber").value("SEC-101"))
                .andExpect(jsonPath("$.courseCode").value("CS301"))
                .andExpect(jsonPath("$.courseName").value("Database Systems"))
                .andExpect(jsonPath("$.dayOfWeek").value(2))
                .andExpect(jsonPath("$.startTime").value("14:00:00"))
                .andExpect(jsonPath("$.endTime").value("15:30:00"));

        // Professor lists entries in version
        mockMvc.perform(get("/api/timetables/versions/{versionId}/entries", version.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(entry.getId()));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Integer createDraftVersion(String academicYear, String semester) throws Exception {
        CreateTimetableVersionRequest request = new CreateTimetableVersionRequest(academicYear, semester);
        MvcResult res = mockMvc.perform(post("/api/timetables/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asInt();
    }
}
