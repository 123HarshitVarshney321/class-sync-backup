# Prompt 9: Extra-Class Booking & Concurrency Protection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement reliable, concurrent-safe booking of extra classes via `POST /api/bookings` creating a `ScheduleOccurrence` (`event_type = EXTRA`, `status = SCHEDULED`, `timetable_entry_id = NULL`) with MySQL pessimistic locking and transactional conflict protection.

**Architecture:** A lightweight REST controller authenticates the caller as `ROLE_PROFESSOR`. A focused `BookingService` executes within a single `@Transactional(isolation = Isolation.READ_COMMITTED)` boundary. Concurrency protection uses deterministic two-phase row locking on stable existing records (`User` -> `Room`) with `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`), followed by half-open interval conflict checks on `ScheduleOccurrence` for both room and professor before persisting a new `ScheduleOccurrence`.

**Tech Stack:** Java 17, Spring Boot 3.3.4, Spring Data JPA / Hibernate, Spring Security (Session-based), MySQL 8 InnoDB, JUnit 5, MockMvc.

**Spec:** Prompt 9: Extra-Class Booking & Concurrency Protection.

## Global Constraints

- Never create redundant tables: no booking table, reservation table, history table, or availability table.
- Rely on `schedule_occurrences` table directly: `timetable_entry_id = NULL`, `event_type = EXTRA`, `status = SCHEDULED`.
- Strictly enforce `ROLE_PROFESSOR` on `POST /api/bookings`; reject admin attempts with `403 Forbidden` and unauthenticated requests with `401 Unauthorized`.
- Validate rooms: room must exist, room must be active, room's complex must be active; reject with `404 Not Found` if invalid or inactive.
- Concurrency control in MySQL: use row-level pessimistic locking (`PESSIMISTIC_WRITE`) on stable records with strict global order (`User` first, `Room` second) to eliminate race conditions and avoid deadlocks. No distributed locks, Redis, Kafka, or RabbitMQ.
- Half-open interval overlap detection ($S_1 < E_2 \land E_1 > S_2$); cancelled occurrences must never block bookings; adjacent time slots (e.g. 09:00-10:00 and 10:00-11:00) must succeed.

## Review Focus

1. **Concurrent room collision:** Two concurrent booking requests for the exact same room and overlapping interval: exactly one must succeed (`201 Created`), and the other must fail with `409 Conflict`.
2. **Concurrent professor collision:** Two concurrent booking requests by the same professor for different rooms with overlapping intervals: exactly one must succeed (`201 Created`), and the other must fail with `409 Conflict`.
3. **Concurrent non-conflicting booking:** Two concurrent booking requests for different rooms by different professors at the same time: both must succeed (`201 Created`).
4. **Adjacent slot non-interference:** An extra booking requested for 10:00-11:00 when an occurrence already exists for 09:00-10:00: must succeed without conflict.
5. **Cancelled occurrence bypass:** An extra booking requested for a room/time where a cancelled occurrence already exists: must succeed without conflict.

---

### Task 1: Repository Enhancements for Pessimistic Locking & Conflict Checking

**Files:**
- Modify: `src/main/java/com/classsync/repository/UserRepository.java`
- Modify: `src/main/java/com/classsync/repository/RoomRepository.java`
- Modify: `src/main/java/com/classsync/repository/ScheduleOccurrenceRepository.java`

**Interfaces:**
- `UserRepository.findByIdWithLock(Integer id)`: returns `Optional<User>` with `LockModeType.PESSIMISTIC_WRITE`
- `RoomRepository.findByIdWithLock(Integer id)`: returns `Optional<Room>` with `LockModeType.PESSIMISTIC_WRITE`
- `ScheduleOccurrenceRepository.existsRoomConflict(Integer roomId, LocalDate date, LocalTime startTime, LocalTime endTime)`: returns `boolean`

- [ ] **Step 1: Add pessimistic lock query to `UserRepository`**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.id = :id")
Optional<User> findByIdWithLock(@Param("id") Integer id);
```

- [ ] **Step 2: Add pessimistic lock query to `RoomRepository`**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM Room r WHERE r.id = :id")
Optional<Room> findByIdWithLock(@Param("id") Integer id);
```

- [ ] **Step 3: Add `existsRoomConflict` query to `ScheduleOccurrenceRepository`**
```java
@Query("""
    SELECT COUNT(o) > 0
    FROM ScheduleOccurrence o
    WHERE o.room.id = :roomId
      AND o.scheduleDate = :date
      AND o.status = com.classsync.entity.ScheduleStatus.SCHEDULED
      AND o.startTime < :endTime
      AND o.endTime > :startTime
""")
boolean existsRoomConflict(
        @Param("roomId") Integer roomId,
        @Param("date") LocalDate date,
        @Param("startTime") java.time.LocalTime startTime,
        @Param("endTime") java.time.LocalTime endTime
);
```

- [ ] **Step 4: Verify compilation of repository additions**
Run: `mvn test-compile`
Expected: BUILD SUCCESS

---

### Task 2: Booking DTOs

**Files:**
- Create: `src/main/java/com/classsync/dto/booking/CreateBookingRequest.java`
- Create: `src/main/java/com/classsync/dto/booking/BookingProfessorSummary.java`
- Create: `src/main/java/com/classsync/dto/booking/BookingRoomSummary.java`
- Create: `src/main/java/com/classsync/dto/booking/BookingResponse.java`

**Interfaces:**
- `CreateBookingRequest`: contains `roomId`, `date`, `startTime`, `endTime`, `courseCode`, `courseName` with Bean Validation
- `BookingResponse`: contains `id`, `eventType`, `status`, `date`, `startTime`, `endTime`, `courseCode`, `courseName`, `BookingProfessorSummary`, `BookingRoomSummary`

- [ ] **Step 1: Create `CreateBookingRequest`**
Record with validation annotations: `@NotNull`, `@Positive` on `roomId`, `@NotNull` on `date`, `startTime`, `endTime`, `@NotBlank`, `@Size` on `courseCode`, `courseName`.

- [ ] **Step 2: Create `BookingProfessorSummary` and `BookingRoomSummary`**
`BookingProfessorSummary(Integer id, String name, String email)`
`BookingRoomSummary(Integer id, String roomNumber)`

- [ ] **Step 3: Create `BookingResponse`**
Record mapping exact fields matching API specification.

- [ ] **Step 4: Verify compilation of DTOs**
Run: `mvn test-compile`
Expected: BUILD SUCCESS

---

### Task 3: BookingService Implementation

**Files:**
- Create: `src/main/java/com/classsync/service/BookingService.java`

**Interfaces:**
- `BookingService.createExtraClass(CreateBookingRequest request, String professorEmail)`: returns `BookingResponse`

- [ ] **Step 1: Implement `BookingService`**
Annotate class with `@Service`.
Method `createExtraClass(CreateBookingRequest request, String professorEmail)` annotated with `@Transactional(isolation = Isolation.READ_COMMITTED)`.
Logic:
1. Validate `request.startTime().isBefore(request.endTime())`. If not, throw `BadRequestException("Start time must be strictly before end time")`.
2. Look up authenticated professor by email: if not found or `role != PROFESSOR`, throw `AccessDeniedException`. If `status != ACTIVE`, throw `BadRequestException("Professor account is inactive")`.
3. Acquire lock on Professor user row: `userRepository.findByIdWithLock(professor.getId())`.
4. Acquire lock on Room row: `roomRepository.findByIdWithLock(request.roomId())`. If not found, throw `ResourceNotFoundException("Room not found with id: " + request.roomId())`.
5. Validate room active: `if (!room.getActive()) throw new ResourceNotFoundException("Room is inactive with id: " + request.roomId());`.
6. Validate room complex active: `if (!room.getComplex().getActive()) throw new ResourceNotFoundException("Room belongs to an inactive complex");`.
7. Check room conflict: `if (scheduleOccurrenceRepository.existsRoomConflict(room.getId(), request.date(), request.startTime(), request.endTime())) throw new DuplicateResourceException("Room is already scheduled for the requested time.");`.
8. Check professor conflict: `if (scheduleOccurrenceRepository.existsProfessorConflict(lockedProf.getId(), request.date(), request.startTime(), request.endTime())) throw new DuplicateResourceException("Professor already has a scheduled class during the requested time.");`.
9. Create `ScheduleOccurrence` entity:
   - `timetableEntry = null`
   - `professor = lockedProf`
   - `room = room`
   - `scheduleDate = request.date()`
   - `startTime = request.startTime()`
   - `endTime = request.endTime()`
   - `eventType = ScheduleEventType.EXTRA`
   - `status = ScheduleStatus.SCHEDULED`
   - `courseCode = request.courseCode().trim()`
   - `courseName = request.courseName().trim()`
   - `createdBy = lockedProf`
10. Save and map to `BookingResponse`.

- [ ] **Step 2: Verify compilation of `BookingService`**
Run: `mvn test-compile`
Expected: BUILD SUCCESS

---

### Task 4: Security Configuration & Controller

**Files:**
- Modify: `src/main/java/com/classsync/security/SecurityConfig.java`
- Create: `src/main/java/com/classsync/controller/BookingController.java`

**Interfaces:**
- Security rule: `POST /api/bookings` -> `hasRole("PROFESSOR")`
- `BookingController.createBooking(@Valid @RequestBody CreateBookingRequest request, Principal principal)`: returns `ResponseEntity<BookingResponse>` with status `201 Created`

- [ ] **Step 1: Update `SecurityConfig.java`**
Add `.requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("PROFESSOR")` to authorization rules before `anyRequest().authenticated()`.

- [ ] **Step 2: Create `BookingController.java`**
Annotate with `@RestController` and `@RequestMapping("/api/bookings")`.
Inject `BookingService`.
Implement `POST` handler returning `HttpStatus.CREATED`.

- [ ] **Step 3: Verify compilation**
Run: `mvn test-compile`
Expected: BUILD SUCCESS

---

### Task 5: Comprehensive Integration & Concurrency Tests

**Files:**
- Create: `src/test/java/com/classsync/controller/BookingIntegrationTests.java`

**Test Cases (minimum 25 tests):**
- **Basic Booking (Tests 1-6):**
  1. Active professor creates extra booking successfully (`201 Created`).
  2. Created occurrence has `eventType = EXTRA`.
  3. Created occurrence has `timetableEntryId = NULL`.
  4. Created occurrence has `status = SCHEDULED`.
  5. Created occurrence has `createdBy` matching authenticated professor.
  6. Server ignores any client-supplied professor ID / uses authenticated user.
- **Validation (Tests 7-12):**
  7. Non-existent room returns `404 Not Found`.
  8. Inactive room returns `404 Not Found`.
  9. Room in inactive complex returns `404 Not Found`.
  10. Missing date/times returns `400 Bad Request`.
  11. `startTime >= endTime` returns `400 Bad Request`.
  12. Blank/whitespace course code or name returns `400 Bad Request`.
- **Room Conflicts (Tests 13-16):**
  13. Overlapping official scheduled occurrence on the room returns `409 Conflict`.
  14. Cancelled occurrence on the room does not block booking.
  15. Adjacent slot (e.g. 10:00-11:00 after 09:00-10:00) is allowed (`201 Created`).
  16. Overlapping existing EXTRA occurrence on the room returns `409 Conflict`.
- **Professor Conflicts (Tests 17-19):**
  17. Professor's existing scheduled occurrence blocks booking (`409 Conflict`).
  18. Professor's cancelled occurrence does not block booking (`201 Created`).
  19. Professor can book a different room when there is no personal schedule conflict.
- **Authorization (Tests 20-22):**
  20. Professor can book (`201 Created`).
  21. Admin cannot use the booking endpoint (`403 Forbidden`).
  22. Unauthenticated request returns `401 Unauthorized`.
- **Concurrency (Tests 23-25):**
  23. Two concurrent threads booking the same room & overlapping interval -> exactly 1 succeeds, 1 gets `409 Conflict`.
  24. Two concurrent threads with same professor booking overlapping intervals -> exactly 1 succeeds, 1 gets `409 Conflict`.
  25. Concurrent bookings for different rooms by different professors -> both succeed.

- [ ] **Step 1: Write `BookingIntegrationTests.java`**
Use `CountDownLatch` and `CyclicBarrier` to execute real multi-threaded concurrent requests against MySQL.

- [ ] **Step 2: Run all tests**
Run: `mvn clean test`
Expected: All tests pass (91 existing + 25 new = 116 tests).

- [ ] **Step 3: Sync changes from scratch to Desktop repository**
`cp -rf src pom.xml docs README.md /Users/harshit/Desktop/classsync-backend/`

- [ ] **Step 4: Commit and push changes to Git**
Git add, commit, and push to `origin/main`.
