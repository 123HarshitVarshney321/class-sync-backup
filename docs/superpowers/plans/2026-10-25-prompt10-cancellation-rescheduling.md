# Prompt 10: Cancellation & Rescheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement reliable cancellation (`POST /api/classes/{id}/cancel`) and rescheduling (`POST /api/classes/{id}/reschedule`) of scheduled occurrences while preserving historical records, enforcing strict professor/admin authorization, and guaranteeing concurrency safety with MySQL InnoDB pessimistic row locking.

**Architecture:** A thin REST controller `ClassScheduleController` at `/api/classes` dispatches requests to `ClassScheduleService`. Concurrency protection enforces a strict global lock hierarchy ($O < U < R$: `ScheduleOccurrence` -> `User` -> `Room`) with `PESSIMISTIC_WRITE` and `@Transactional(isolation = Isolation.READ_COMMITTED)`. Conflict checks for rescheduling exclude the current occurrence ID ($S_1 < E_2 \land E_1 > S_2 \land \text{id} \neq \text{currentId}$). Both operations return the updated occurrence as `BookingResponse`.

**Tech Stack:** Java 21, Spring Boot 3.4.3, Spring Data JPA / Hibernate, Spring Security (Session-based), MySQL 8 InnoDB, JUnit 5, MockMvc, `java.util.concurrent`.

**Spec:** Prompt 10 — Cancellation & Rescheduling.

## Global Constraints

- Never physically delete schedule occurrences; cancelled records remain with `status = CANCELLED`.
- Do not create booking-history, reservation, or availability tables.
- Do not introduce Kafka, RabbitMQ, or Redis.
- Enforce role-based access: both PROFESSOR and ADMIN can access endpoints. Professors can only cancel/reschedule their own occurrences; Admins can cancel/reschedule any eligible occurrence. Unauthorized attempts must return `403 Forbidden`.
- Concurrency control in MySQL: use row-level pessimistic locking (`PESSIMISTIC_WRITE`) with a strict global DAG ordering ($O < U < R$) to avoid deadlocks.
- Rescheduling preserves the original occurrence ID, professor, event type, timetable entry, course code, course name, createdBy, and createdAt.

## Review Focus

1. **Self-Conflict Exclusion in Reschedule:** Rescheduling within the same room or at an adjacent time must not conflict with the occurrence itself.
2. **Concurrent Same-Occurrence Reschedule:** Two concurrent threads attempting to reschedule the same occurrence must be serialized by the occurrence lock without lost updates.
3. **Concurrent Same-Target-Slot Collision:** Two concurrent reschedules into the exact same room and overlapping interval: exactly one must succeed (`200 OK`), and the other must fail with `409 Conflict`.
4. **Cancelled Occurrence Invariance:** Already cancelled occurrences cannot be cancelled again (`409 Conflict`) and cannot be rescheduled (`409 Conflict`).
5. **Immediate Availability Release:** A cancelled occurrence must immediately free up room and professor availability without blocking subsequent searches or bookings.

---

### Task 1: Repository Enhancements for Occurrence Locking and Self-Excluding Conflict Queries

**Files:**
- Modify: `src/main/java/com/classsync/repository/ScheduleOccurrenceRepository.java`

**Interfaces:**
- `findByIdWithLock(Integer id)`: returns `Optional<ScheduleOccurrence>` with `LockModeType.PESSIMISTIC_WRITE`
- `existsRoomConflictExcludingId(Integer roomId, LocalDate date, LocalTime startTime, LocalTime endTime, Integer excludedOccurrenceId)`: returns `boolean`
- `existsProfessorConflictExcludingId(Integer professorId, LocalDate date, LocalTime startTime, LocalTime endTime, Integer excludedOccurrenceId)`: returns `boolean`

- [ ] **Step 1: Add `findByIdWithLock` to `ScheduleOccurrenceRepository`**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT o FROM ScheduleOccurrence o WHERE o.id = :id")
Optional<ScheduleOccurrence> findByIdWithLock(@Param("id") Integer id);
```

- [ ] **Step 2: Add `existsRoomConflictExcludingId` to `ScheduleOccurrenceRepository`**
```java
@Query("""
    SELECT COUNT(o) > 0
    FROM ScheduleOccurrence o
    WHERE o.room.id = :roomId
      AND o.scheduleDate = :date
      AND o.status = com.classsync.entity.ScheduleStatus.SCHEDULED
      AND o.startTime < :endTime
      AND o.endTime > :startTime
      AND (:excludedOccurrenceId IS NULL OR o.id != :excludedOccurrenceId)
""")
boolean existsRoomConflictExcludingId(
        @Param("roomId") Integer roomId,
        @Param("date") LocalDate date,
        @Param("startTime") LocalTime startTime,
        @Param("endTime") LocalTime endTime,
        @Param("excludedOccurrenceId") Integer excludedOccurrenceId
);
```

- [ ] **Step 3: Add `existsProfessorConflictExcludingId` to `ScheduleOccurrenceRepository`**
```java
@Query("""
    SELECT COUNT(o) > 0
    FROM ScheduleOccurrence o
    WHERE o.professor.id = :professorId
      AND o.scheduleDate = :date
      AND o.status = com.classsync.entity.ScheduleStatus.SCHEDULED
      AND o.startTime < :endTime
      AND o.endTime > :startTime
      AND (:excludedOccurrenceId IS NULL OR o.id != :excludedOccurrenceId)
""")
boolean existsProfessorConflictExcludingId(
        @Param("professorId") Integer professorId,
        @Param("date") LocalDate date,
        @Param("startTime") LocalTime startTime,
        @Param("endTime") LocalTime endTime,
        @Param("excludedOccurrenceId") Integer excludedOccurrenceId
);
```

- [ ] **Step 4: Verify repository compilation**
Run: `mvn test-compile`
Expected: BUILD SUCCESS

---

### Task 2: DTOs & Exception Handling

**Files:**
- Create: `src/main/java/com/classsync/dto/occurrence/RescheduleRequest.java`
- Modify: `src/main/java/com/classsync/exception/GlobalExceptionHandler.java`

**Interfaces:**
- `RescheduleRequest`: contains `date`, `startTime`, `endTime`, `roomId` with Bean Validation
- `GlobalExceptionHandler`: add handler for `AccessDeniedException` to guarantee standard JSON `403 Forbidden` response for service-layer authorization checks

- [ ] **Step 1: Create `RescheduleRequest`**
Record with Bean Validation: `@NotNull` on date, startTime, endTime, `@NotNull` and `@Positive` on roomId.

- [ ] **Step 2: Add `@ExceptionHandler(AccessDeniedException.class)` in `GlobalExceptionHandler`**
```java
@ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
public ResponseEntity<Map<String, Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
            "status", 403,
            "error", "Forbidden",
            "message", ex.getMessage() != null ? ex.getMessage() : "Access is denied"
    ));
}
```

- [ ] **Step 3: Verify compilation**
Run: `mvn test-compile`
Expected: BUILD SUCCESS

---

### Task 3: ClassScheduleService Implementation

**Files:**
- Create: `src/main/java/com/classsync/service/ClassScheduleService.java`

**Interfaces:**
- `cancelOccurrence(Integer occurrenceId, String authenticatedEmail)`: returns `BookingResponse`
- `rescheduleOccurrence(Integer occurrenceId, RescheduleRequest request, String authenticatedEmail)`: returns `BookingResponse`

- [ ] **Step 1: Implement `cancelOccurrence`**
1. `@Transactional(isolation = Isolation.READ_COMMITTED)`
2. Lock occurrence: `scheduleOccurrenceRepository.findByIdWithLock(occurrenceId)`. If not found, throw `ResourceNotFoundException`.
3. Check status: if `occurrence.getStatus() == ScheduleStatus.CANCELLED`, throw `DuplicateResourceException("Occurrence is already cancelled")` (`409 Conflict`).
4. Validate caller: lookup caller by email. If caller has `role == UserRole.PROFESSOR` and `!occurrence.getProfessor().getId().equals(caller.getId())`, throw `AccessDeniedException("Professors can only cancel their own classes")` (`403 Forbidden`).
5. Update `occurrence.setStatus(ScheduleStatus.CANCELLED)`.
6. Save and return `toResponse(occurrence)`.

- [ ] **Step 2: Implement `rescheduleOccurrence`**
1. `@Transactional(isolation = Isolation.READ_COMMITTED)`
2. Validate `request.startTime().isBefore(request.endTime())`. If not, throw `BadRequestException("Start time must be strictly before end time")`.
3. Strict lock ordering: $O < U < R$:
   - Lock Occurrence: `scheduleOccurrenceRepository.findByIdWithLock(occurrenceId)`. If not found, throw `ResourceNotFoundException`.
   - Check status: if `occurrence.getStatus() == ScheduleStatus.CANCELLED`, throw `DuplicateResourceException("Cancelled occurrences cannot be rescheduled")` (`409 Conflict`).
   - Validate caller: lookup caller by email. If caller is `PROFESSOR` and `!occurrence.getProfessor().getId().equals(caller.getId())`, throw `AccessDeniedException("Professors can only reschedule their own classes")` (`403 Forbidden`).
   - Lock assigned professor row: `userRepository.findByIdWithLock(occurrence.getProfessor().getId())`.
   - Lock target room row: `roomRepository.findByIdWithLock(request.roomId())`. If not found, throw `ResourceNotFoundException`.
4. Validate target room and complex:
   - If `!targetRoom.getActive()`, throw `ResourceNotFoundException("Room is inactive with id: " + request.roomId())`.
   - If `!targetRoom.getComplex().getActive()`, throw `ResourceNotFoundException("Room belongs to an inactive complex")`.
5. Check room conflict: `scheduleOccurrenceRepository.existsRoomConflictExcludingId(targetRoom.getId(), request.date(), request.startTime(), request.endTime(), occurrenceId)`. If conflict, throw `DuplicateResourceException("Room is already scheduled for the requested time.")` (`409 Conflict`).
6. Check professor conflict: `scheduleOccurrenceRepository.existsProfessorConflictExcludingId(occurrence.getProfessor().getId(), request.date(), request.startTime(), request.endTime(), occurrenceId)`. If conflict, throw `DuplicateResourceException("Professor already has a scheduled class during the requested time.")` (`409 Conflict`).
7. Update occurrence:
   - `occurrence.setScheduleDate(request.date())`
   - `occurrence.setStartTime(request.startTime())`
   - `occurrence.setEndTime(request.endTime())`
   - `occurrence.setRoom(targetRoom)`
8. Save and return `toResponse(occurrence)`.

- [ ] **Step 3: Verify compilation**
Run: `mvn test-compile`
Expected: BUILD SUCCESS

---

### Task 4: Security Configuration & Controller

**Files:**
- Modify: `src/main/java/com/classsync/security/SecurityConfig.java`
- Create: `src/main/java/com/classsync/controller/ClassScheduleController.java`

**Interfaces:**
- Security rule: `POST /api/classes/**` -> `hasAnyRole("PROFESSOR", "ADMIN")`
- `POST /api/classes/{id}/cancel` -> calls `cancelOccurrence` -> returns `200 OK`
- `POST /api/classes/{id}/reschedule` -> calls `rescheduleOccurrence` -> returns `200 OK`

- [ ] **Step 1: Update `SecurityConfig.java`**
Add `.requestMatchers(HttpMethod.POST, "/api/classes/**").hasAnyRole("PROFESSOR", "ADMIN")` before `.anyRequest().authenticated()`.

- [ ] **Step 2: Create `ClassScheduleController.java`**
Thin REST controller mapping cancel and reschedule endpoints with `@PathVariable Integer id` and `Principal principal`.

- [ ] **Step 3: Verify compilation**
Run: `mvn test-compile`
Expected: BUILD SUCCESS

---

### Task 5: Comprehensive Integration & Concurrency Tests

**Files:**
- Create: `src/test/java/com/classsync/controller/ClassScheduleIntegrationTests.java`

**Test Cases (minimum 32 tests):**
- **Cancellation (Tests 1–9):**
  1. Professor cancels own scheduled occurrence -> 200
  2. Cancellation changes status to CANCELLED
  3. Occurrence remains in database (not deleted)
  4. Professor cannot cancel another professor's occurrence -> 403
  5. Admin can cancel another professor's occurrence -> 200
  6. Already cancelled occurrence -> 409
  7. Unauthenticated cancellation -> 401
  8. Cancelled occurrence no longer blocks room availability
  9. Cancelled occurrence no longer blocks professor availability
- **Rescheduling (Tests 10–29):**
  10. Professor reschedules own occurrence -> 200
  11. Admin reschedules another professor's occurrence -> 200
  12. Professor cannot reschedule another professor's occurrence -> 403
  13. Cancelled occurrence cannot be rescheduled -> 409
  14. Missing room -> 404
  15. Inactive room -> 404
  16. Inactive complex -> 404
  17. Invalid time range (`startTime >= endTime`) -> 400
  18. Overlapping room occurrence -> 409
  19. Overlapping professor occurrence -> 409
  20. Cancelled room occurrence does not block rescheduling -> 200
  21. Cancelled professor occurrence does not block rescheduling -> 200
  22. Adjacent room slot is allowed -> 200
  23. Same occurrence does not conflict with itself -> 200
  24. Rescheduling preserves occurrence ID
  25. Rescheduling preserves professor
  26. Rescheduling preserves event type
  27. Rescheduling preserves timetableEntry relationship
  28. Rescheduling preserves course information
  29. Rescheduling updates date/time/room correctly
- **Concurrency (Tests 30–32):**
  30. Concurrent rescheduling of the same occurrence does not cause lost updates
  31. Concurrent rescheduling into the same target room/time results in deterministic conflict handling (1 success, 1 conflict)
  32. Concurrent valid reschedules involving different rooms/professors proceed independently without blocking

- [ ] **Step 1: Write `ClassScheduleIntegrationTests.java`**
- [ ] **Step 2: Run all tests**
Run: `mvn clean test`
Expected: All 116 existing + 32 new = 148 tests pass.
- [ ] **Step 3: Sync changes from scratch to Desktop repository**
- [ ] **Step 4: Commit and push changes to Git**
