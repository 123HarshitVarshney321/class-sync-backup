package com.classsync.controller;

import com.classsync.entity.Complex;
import com.classsync.entity.Facility;
import com.classsync.entity.Room;
import com.classsync.entity.RoomType;
import com.classsync.repository.ComplexRepository;
import com.classsync.repository.FacilityRepository;
import com.classsync.repository.RoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DomainIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ComplexRepository complexRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================================
    // Complex Tests
    // =========================================================================

    @Test
    @DisplayName("1. Professor can list active complexes")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCanListActiveComplexes() throws Exception {
        mockMvc.perform(get("/api/complexes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].code").exists());
    }

    @Test
    @DisplayName("2. Professor cannot create a complex (403 Forbidden)")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCannotCreateComplex() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "name", "New Science Block",
                "code", "NSB"
        ));

        mockMvc.perform(post("/api/complexes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("3. Admin can create a complex (201 Created)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanCreateComplex() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "name", "Main Academic Complex",
                "code", "MAC"
        ));

        mockMvc.perform(post("/api/complexes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Main Academic Complex"))
                .andExpect(jsonPath("$.code").value("MAC"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("4. Duplicate complex code returns 409 Conflict")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void duplicateComplexCodeReturns409() throws Exception {
        // 'SEC' already exists in seed data
        String json = objectMapper.writeValueAsString(Map.of(
                "name", "Duplicate SEC Complex",
                "code", "SEC"
        ));

        mockMvc.perform(post("/api/complexes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message", containsString("SEC")));
    }

    @Test
    @DisplayName("5. Admin can deactivate a complex")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanDeactivateComplex() throws Exception {
        Complex complex = complexRepository.save(new Complex("Deactivation Test", "DEACT", true));

        String json = objectMapper.writeValueAsString(Map.of("active", false));

        mockMvc.perform(patch("/api/complexes/" + complex.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(complex.getId()))
                .andExpect(jsonPath("$.active").value(false));

        // GET /api/complexes/{id} should now return 404 because it is inactive
        mockMvc.perform(get("/api/complexes/" + complex.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // Room Tests
    // =========================================================================

    @Test
    @DisplayName("6. Professor can list rooms for an active complex")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCanListRoomsForActiveComplex() throws Exception {
        // Complex 1 (SEC) exists in seed data
        mockMvc.perform(get("/api/complexes/1/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[0].roomNumber").exists())
                .andExpect(jsonPath("$[0].roomType").exists())
                .andExpect(jsonPath("$[0].capacity").isNumber())
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    @DisplayName("7. Professor cannot create a room (403 Forbidden)")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCannotCreateRoom() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "roomNumber", "SEC-999",
                "roomType", "CLASSROOM",
                "capacity", 50
        ));

        mockMvc.perform(post("/api/complexes/1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("8. Admin can create a room")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanCreateRoom() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "roomNumber", "SEC-401",
                "roomType", "CLASSROOM",
                "capacity", 60,
                "facilityIds", Set.of(1, 2)
        ));

        mockMvc.perform(post("/api/complexes/1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.roomNumber").value("SEC-401"))
                .andExpect(jsonPath("$.roomType").value("CLASSROOM"))
                .andExpect(jsonPath("$.capacity").value(60))
                .andExpect(jsonPath("$.complex.code").value("SEC"))
                .andExpect(jsonPath("$.facilities", hasSize(2)));
    }

    @Test
    @DisplayName("9. Duplicate room number within same complex returns 409 Conflict")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void duplicateRoomNumberWithinSameComplexReturns409() throws Exception {
        // SEC-101 already exists in complex 1
        String json = objectMapper.writeValueAsString(Map.of(
                "roomNumber", "SEC-101",
                "roomType", "CLASSROOM",
                "capacity", 40
        ));

        mockMvc.perform(post("/api/complexes/1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("10. Invalid capacity is rejected (400 Bad Request)")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void invalidCapacityIsRejected() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "roomNumber", "SEC-501",
                "roomType", "CLASSROOM",
                "capacity", -5
        ));

        mockMvc.perform(post("/api/complexes/1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("11. Room with facilities can be retrieved correctly")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void roomWithFacilitiesCanBeRetrievedCorrectly() throws Exception {
        // Room 1 (SEC-101) exists in seed data and has facilities (Projector, AC)
        mockMvc.perform(get("/api/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roomNumber").value("SEC-101"))
                .andExpect(jsonPath("$.complex.id").value(1))
                .andExpect(jsonPath("$.complex.code").value("SEC"))
                .andExpect(jsonPath("$.facilities", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.facilities[0].name").exists());
    }

    @Test
    @DisplayName("12. Admin can deactivate a room")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanDeactivateRoom() throws Exception {
        Complex complex = complexRepository.findById(1).orElseThrow();
        Room room = roomRepository.save(new Room(complex, "SEC-TEMP", RoomType.LAB, 25, true));

        String json = objectMapper.writeValueAsString(Map.of("active", false));

        mockMvc.perform(patch("/api/rooms/" + room.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(room.getId()))
                .andExpect(jsonPath("$.active").value(false));

        // GET /api/rooms/{id} should now return 404 because it is inactive
        mockMvc.perform(get("/api/rooms/" + room.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // Facility Tests
    // =========================================================================

    @Test
    @DisplayName("13. Professor can view facilities")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCanViewFacilities() throws Exception {
        mockMvc.perform(get("/api/facilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    @DisplayName("14. Professor cannot create a facility (403 Forbidden)")
    @WithMockUser(username = "aturing@classsync.edu", roles = {"PROFESSOR"})
    void professorCannotCreateFacility() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("name", "Document Camera"));

        mockMvc.perform(post("/api/facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("15. Admin can create a facility")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void adminCanCreateFacility() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("name", "Document Camera"));

        mockMvc.perform(post("/api/facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Document Camera"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("16. Duplicate facility name returns 409 Conflict")
    @WithMockUser(username = "admin@classsync.edu", roles = {"ADMIN"})
    void duplicateFacilityNameReturns409() throws Exception {
        // 'Projector' already exists in seed data
        String json = objectMapper.writeValueAsString(Map.of("name", "Projector"));

        mockMvc.perform(post("/api/facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    // =========================================================================
    // Security Test
    // =========================================================================

    @Test
    @DisplayName("17. Unauthenticated requests are rejected (401 Unauthorized)")
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/complexes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/rooms/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/facilities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
