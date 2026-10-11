# ClassSync: Automated University Timetable & Classroom Allocation Backend
## Comprehensive Project Progress Report & Future Development Roadmap

**Project Name:** ClassSync Backend  
**Author:** Harshit Varshney  
**Institution:** Academic Project Submission  
**Technology Stack:** Java 21, Spring Boot 3.4.3, Spring Security 6, Spring Data JPA, Hibernate 6, MySQL 8.0, Flyway Migration, JUnit 5, MockMvc  
**Repository:** [GitHub - classsync-backend](https://github.com/123HarshitVarshney321/classsync-backend.git)  
**Status:** Completed Modules (Milestones 1–8), 91 Automated Tests Passing (100% Success Rate)

---

## Table of Contents
1. [Executive Summary & Problem Statement](#1-executive-summary--problem-statement)
2. [System Architecture & Technology Stack](#2-system-architecture--technology-stack)
3. [Database Architecture & Evolutionary Schema](#3-database-architecture--evolutionary-schema)
4. [Detailed Breakdown of Completed Work (Milestones 1–8)](#4-detailed-breakdown-of-completed-work-milestones-18)
   - [Phase 1: Database Foundation & Seed Data (Prompts 1 & 2)](#phase-1-database-foundation--seed-data-prompts-1--2)
   - [Phase 2: Authentication & Role-Based Security (Prompts 3 & 4)](#phase-2-authentication--role-based-security-prompts-3--4)
   - [Phase 3: Campus Infrastructure Management (Prompt 5)](#phase-3-campus-infrastructure-management-prompt-5)
   - [Phase 4: Timetable Versioning & Conflict Detection (Prompt 6)](#phase-4-timetable-versioning--conflict-detection-prompt-6)
   - [Phase 5: Calendar Occurrence Generation Engine (Prompt 7)](#phase-5-calendar-occurrence-generation-engine-prompt-7)
   - [Phase 6: Advisory Room Availability Search Engine (Prompt 8)](#phase-6-advisory-room-availability-search-engine-prompt-8)
5. [Key Mathematical & Algorithmic Innovations](#5-key-mathematical--algorithmic-innovations)
   - [Half-Open Interval Collision Formula](#51-half-open-interval-collision-formula)
   - [High-Performance Idempotent Calendar Projection](#52-high-performance-idempotent-calendar-projection)
   - [Database-Level Concurrency via SQL NULL Semantics](#53-database-level-concurrency-via-sql-null-semantics)
   - [Zero N+1 Query Optimization with JPA Fetch Joins](#54-zero-n1-query-optimization-with-jpa-fetch-joins)
6. [API Specification & Security Matrix](#6-api-specification--security-matrix)
7. [Testing Strategy & Quality Assurance](#7-testing-strategy--quality-assurance)
8. [Remaining Work & Future Roadmap (Phases 9–14)](#8-remaining-work--future-roadmap-phases-914)
9. [Conclusion](#9-conclusion)

---

## 1. Executive Summary & Problem Statement

### 1.1 Problem Statement
Higher education institutions face significant operational bottlenecks when managing physical classroom space and academic schedules:
1. **Physical Resource Scarcity**: Classrooms, lecture halls, and laboratories have fixed capacities, equipment constraints (projectors, smart boards, computers), and geographic distributions across campus complexes.
2. **Scheduling Conflicts**: Manual timetable creation leads to catastrophic collisions—such as booking two classes simultaneously in the same room or assigning a professor to teach in two disparate buildings at the same time.
3. **Drafting vs. Publishing Friction**: Academic deans iterate on curriculum schedules weeks before a semester commences. Changes made during the planning phase must not prematurely disrupt active sessions, yet once published, the master schedule must be protected against accidental deletion.
4. **The "Blueprint vs. Occurrence" Gap**: Standard timetable applications conflate weekly recurring templates with actual calendar dates. If a professor cancels a class on Tuesday, November 10th, traditional systems risk breaking all future Tuesdays across the semester.

### 1.2 The ClassSync Solution
**ClassSync** resolves these issues through a strictly decoupled, 4-tier enterprise backend:
- **Decoupled Architecture**: Separates recurring weekly schedule blueprints (`TimetableEntry`) from dated calendar events (`ScheduleOccurrence`).
- **Mathematical Conflict Prevention**: Enforces half-open interval overlap algorithms $[S, E)$ directly at the database and application levels.
- **Idempotent Calendar Projection**: Allows administrators to project recurring weekly blueprints onto calendar windows without overwriting historical cancellations or rescheduled classes.
- **Advisory Availability Search**: Provides millisecond-level room search evaluating room types, capacities, multi-facility requirements, and professor schedule conflicts in real time.

---

## 2. System Architecture & Technology Stack

The project adheres to **Domain-Driven Design (DDD)**, **KISS**, **SOLID**, and **YAGNI** principles. No unneeded abstraction layers or heavy third-party authentication servers are introduced.

```
                              Client Layer (Web / Mobile)
                                          │
                                          ▼  HTTP / JSON + Cookie
┌──────────────────────────────────────────────────────────────────────────────┐
│  Tier 1: Security & Filter Chain (Spring Security 6)                         │
│  - Session-based authentication via HTTP-only JSESSIONID                     │
│  - Role-Based Access Control (RBAC): ADMIN vs. PROFESSOR                     │
│  - Custom AuthenticationEntryPoint (401) & AccessDeniedHandler (403)         │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Tier 2: Web / Controller Layer (Spring MVC)                                 │
│  - REST Controllers exposing clean JSON endpoints                            │
│  - Jakarta Bean Validation (@Valid, @NotNull, @Size, @Min, @Max)             │
│  - GlobalExceptionHandler mapping domain exceptions to RFC-7807 responses    │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Tier 3: Service / Business Logic Layer (Spring Service & Transactions)      │
│  - Declarative @Transactional boundaries (Read-Write & Read-Only)            │
│  - Collision detection & half-open interval conflict engine                  │
│  - State machine governance (DRAFT -> ACTIVE -> ARCHIVED)                    │
│  - Entity-to-DTO projection (Zero entity graph leakage)                      │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Tier 4: Persistence Layer (Hibernate 6 / Spring Data JPA / MySQL 8)         │
│  - Custom JPQL queries with Fetch Joins (Zero N+1 overhead)                  │
│  - Flyway Migrations (V1 to V4) for automated, versioned schema management   │
│  - MySQL InnoDB Engine with foreign keys, check constraints, & unique indexes│
└──────────────────────────────────────────────────────────────────────────────┘
```

### Core Technologies
- **Language**: Java 21 LTS (utilizing Java Records for immutable DTOs, Pattern Matching, and modern Stream APIs).
- **Framework**: Spring Boot 3.4.3.
- **Security**: Spring Security 6 (Session-based RBAC, BCrypt password hashing).
- **Persistence**: Spring Data JPA / Hibernate ORM 6.
- **Database**: MySQL 8.0 with InnoDB engine.
- **Migration**: Flyway Database Migration.
- **Testing**: JUnit 5, Spring Boot Test, MockMvc, AssertJ.

---

## 3. Database Architecture & Evolutionary Schema

The database model comprises **10 core tables** managed under strict version control via Flyway.

```
                  ┌──────────────┐
                  │  complexes   │
                  └──────┬───────┘
                         │ 1:N
                         ▼
┌──────────────┐  M:N  ┌──────────────┐
│  facilities  ├───────┤    rooms     │
└──────────────┘       └──────┬───────┘
                              │
               ┌──────────────┴──────────────┐
               │ 1:N                         │ 1:N
               ▼                             ▼
       ┌───────────────┐             ┌───────────────────────┐
       │timetable_     │             │  schedule_            │
       │entries        │             │  occurrences          │
       └───────┬───────┘             └───────────┬───────────┘
               │                                 │
               │ N:1 (source blueprint)          │ N:1
               └─────────────────────────────────┤
                                                 │
                                                 │ N:1 (instructor)
  ┌──────────────┐  1:N  ┌──────────────┐        │
  │timetable_    ├───────┤    users     ├────────┘
  │versions      │       └──────┬───────┘
  └──────────────┘              │
                                ├──────────────► notifications
                                └──────────────► audit_logs
```

### Flyway Migration History
- **`V1__create_initial_schema.sql`**: Initialized all 10 tables with foreign keys, checks (`chk_timetable_entries_time_order`, `chk_timetable_entries_day_of_week`), and performance indexes.
- **`V2__seed_development_data.sql`**: Seeded realistic development records (1 Admin, 2 Professors, 2 Complexes, 5 Rooms, 5 Facilities, and Room-Facility mappings).
- **`V3__add_active_to_facilities.sql`**: Added `active BOOLEAN DEFAULT TRUE NOT NULL` to `facilities` enabling soft deactivation.
- **`V4__add_unique_entry_date_to_schedule_occurrences.sql`**: Enforced `UNIQUE KEY uk_schedule_occurrences_entry_date (timetable_entry_id, schedule_date)` for concurrency protection.

---

## 4. Detailed Breakdown of Completed Work (Milestones 1–8)

### Phase 1: Database Foundation & Seed Data (Prompts 1 & 2)
- **Objective**: Establish relational baseline without premature application code.
- **Implementation**:
  - Defined explicit data types (`TINYINT` for days of week 1–7, `TIME` for schedule hours, `VARCHAR` with length limits).
  - Configured cascading constraints (`ON DELETE RESTRICT` on core business entities to prevent accidental data loss; `ON DELETE CASCADE` on auxiliary join tables).
  - Seeded BCrypt hashes for test credentials (`admin@classsync.edu`, `aturing@classsync.edu`, `alovelace@classsync.edu`).

---

### Phase 2: Authentication & Role-Based Security (Prompts 3 & 4)
- **Objective**: Implement secure, stateless-ready, session-based authentication without third-party OAuth/JWT complexity.
- **Implementation**:
  - `AuthController`: Handles `POST /api/auth/login`. Authenticates credentials via Spring's `AuthenticationManager`, creates an authenticated session, and writes context into `SecurityContextRepository`.
  - `CustomUserDetailsService`: Bridges JPA `User` entity to Spring Security's `UserDetails`, mapping roles to `ROLE_ADMIN` and `ROLE_PROFESSOR`.
  - `SecurityConfig`: Configures CSRF, session storage, and path-based authorization.
  - Custom Entry Points: Unauthenticated requests intercept with `401 Unauthorized` (`RestAuthenticationEntryPoint`); unauthorized role requests yield `403 Forbidden` (`RestAccessDeniedHandler`).

---

### Phase 3: Campus Infrastructure Management (Prompt 5)
- **Objective**: Model physical campus entities (Complexes, Rooms, Facilities).
- **Implementation**:
  - CRUD endpoints for `/api/complexes`, `/api/rooms`, and `/api/facilities`.
  - Invariant Enforcement: A room is only schedulable if its parent `Complex` is active. Deactivating a complex cascades logical unavailability to all constituent rooms.
  - M:N Facility Mapping: Rooms maintain a clean join table `room_facilities`.
  - DTO Encapsulation: Built `ComplexResponse`, `RoomDetailResponse`, `RoomSummaryResponse`, and `FacilityResponse` preventing cyclic entity exposure.

---

### Phase 4: Timetable Versioning & Conflict Detection (Prompt 6)
- **Objective**: Implement recurring timetable versioning and master schedule CRUD with zero double-booking.
- **The State Machine**:
  - `DRAFT`: Editable master schedule. Entries can be added, updated, or deleted.
  - `ACTIVE`: Published semester schedule. Strictly immutable. Entries cannot be modified.
  - `ARCHIVED`: Read-only historical version. Cannot be reactivated.
- **Atomic Version Activation**:
  - When draft Version $N$ is activated for a semester, the service queries any existing `ACTIVE` version, marks it `ARCHIVED`, and marks Version $N$ `ACTIVE` in a single atomic transaction.
- **Collision Engine**:
  - Validates that start time is strictly before end time (`start < end`).
  - Room Collision Check: Verifies room is free during the half-open interval.
  - Professor Collision Check: Verifies instructor is not scheduled elsewhere.
  - Self-Exclusion: Updates exclude the current entry ID (`:excludedEntryId IS NULL OR e.id != :excludedEntryId`) to prevent false-positive self-conflicts.

---

### Phase 5: Calendar Occurrence Generation Engine (Prompt 7)
- **Objective**: Transform recurring weekly templates into dated calendar classes (`ScheduleOccurrence`).
- **Generation Logic**:
  - Admin calls `POST /api/schedule-occurrences/generate` specifying `timetableVersionId`, `startDate`, and `endDate`.
  - Validates that the timetable version is in `ACTIVE` state.
  - Validates that the generation window does not exceed 12 months.
  - Projects `day_of_week` (`1 = Monday` through `7 = Sunday`) across every calendar day in the range.
- **Idempotency & History Preservation**:
  - Before inserting occurrences, the engine checks existing records in batch.
  - Existing occurrences are skipped (`skippedExistingCount`).
  - Critical Rule: If a professor had previously cancelled a class (`status = CANCELLED`), running generation again **never** resets it to `SCHEDULED`.
- **Concurrency & Flyway Migration V4**:
  - Enforces `UNIQUE(timetable_entry_id, schedule_date)` at the database level.

---

### Phase 6: Advisory Room Availability Search Engine (Prompt 8)
- **Objective**: Real-time search API (`GET /api/rooms/available`) for finding unbooked rooms for extra classes, workshops, and reschedules.
- **Hard Constraints Evaluated**:
  1. Room is active (`active = true`).
  2. Complex is active (`complex.active = true`).
  3. Matches requested `complexId` (if provided).
  4. Room capacity $\ge$ requested minimum capacity.
  5. Matches requested `roomType` (if provided).
  6. Room possesses **all** requested active facilities (AND semantics).
  7. Room has no `SCHEDULED` occurrence overlapping the half-open interval (cancelled classes do not block).
  8. Requesting professor has no overlapping personal class scheduled at that time.
- **Professor Conflict Isolation**:
  - If a professor searches for rooms while already teaching another class, the system returns `[]` immediately.
  - If an admin searches, the professor conflict check is bypassed.

---

## 5. Key Mathematical & Algorithmic Innovations

### 5.1 Half-Open Interval Collision Formula
Classes operate on half-open time intervals $[S, E)$. A class from `09:00 to 10:00` vacates the room at 10:00:00, allowing the subsequent class to commence immediately at `10:00:00`.

**Mathematical Collision Condition:**
Two intervals $[S_1, E_1)$ and $[S_2, E_2)$ on the same date collide if and only if:
$$\mathbf{S_1 < E_2 \quad \text{AND} \quad E_1 > S_2}$$

| Scenario | Existing Class | Requested Class | $S_1 < E_2$? | $E_1 > S_2$? | Conflict Result |
|---|---|---|---|---|---|
| **Interior Overlap** | `09:00 - 11:00` | `09:30 - 10:30` | `09:00 < 10:30` (True) | `11:00 > 09:30` (True) | **CONFLICT (409)** |
| **Partial Overlap** | `09:00 - 10:30` | `10:00 - 11:30` | `09:00 < 11:30` (True) | `10:30 > 10:00` (True) | **CONFLICT (409)** |
| **Back-to-Back (Adjacent)** | `09:00 - 10:00` | `10:00 - 11:00` | `09:00 < 11:00` (True) | `10:00 > 10:00` (**False**) | **NO CONFLICT (Allowed)** |
| **Completely Before** | `08:00 - 09:00` | `10:00 - 11:00` | `08:00 < 11:00` (True) | `09:00 > 10:00` (**False**) | **NO CONFLICT (Allowed)** |

Implemented via parameterized JPQL:
```sql
WHERE o.scheduleDate = :date
  AND o.status = 'SCHEDULED'
  AND o.startTime < :endTime
  AND o.endTime > :startTime
```

---

### 5.2 High-Performance Idempotent Calendar Projection
Generating occurrences across a 6-month semester for 50 courses could produce over $1,500$ queries under naive architectures.

**Our $O(1)$ Database Roundtrip Pipeline**:
1. Eagerly load all recurring entries for the active version in **1 query** via `JOIN FETCH`.
2. Group entries in memory by `dayOfWeek` into a `Map<Byte, List<TimetableEntry>>`.
3. Query all existing occurrences in the date window in **1 batch query**:
   `SELECT o.timetableEntry.id, o.scheduleDate FROM ScheduleOccurrence o WHERE ...`
4. Store existing keys in a memory `HashSet<String>` (`entryId + "_" + date`).
5. Project calendar dates day-by-day in memory, performing $O(1)$ set lookups.
6. Persist all newly created occurrences in a single batch call via `saveAll()`.

Result: **2 database roundtrips total**, regardless of whether generating 10 or 1,000 occurrences.

---

### 5.3 Database-Level Concurrency via SQL NULL Semantics
Flyway migration `V4` adds:
```sql
ALTER TABLE schedule_occurrences
ADD CONSTRAINT uk_schedule_occurrences_entry_date UNIQUE (timetable_entry_id, schedule_date);
```
- **Official Recurring Classes**: Possess a non-null `timetable_entry_id`. If two administrators trigger occurrence generation simultaneously, the unique index blocks duplicate insertions, guaranteeing data integrity.
- **Future Ad-Hoc / Extra Classes**: Possess a `NULL` `timetable_entry_id`. Under standard SQL and MySQL InnoDB, **`NULL` values are treated as distinct in unique indexes**. Multiple ad-hoc classes can coexist on the same date without violating the constraint.

---

### 5.4 Zero N+1 Query Optimization with JPA Fetch Joins
To prevent Hibernate from executing secondary `SELECT` statements for every related complex, room, and facility:
```java
@Query("""
    SELECT DISTINCT r FROM Room r
    JOIN FETCH r.complex c
    LEFT JOIN FETCH r.facilities f
    WHERE r.active = true
      AND c.active = true
      AND (:complexId IS NULL OR c.id = :complexId)
      AND (:capacity IS NULL OR r.capacity >= :capacity)
      AND (:roomType IS NULL OR r.roomType = :roomType)
      AND r.id NOT IN :busyRoomIds
    ORDER BY c.name ASC, r.roomNumber ASC, r.id ASC
""")
List<Room> findAvailableRooms(...);
```
A single query retrieves the complete room graph with its parent complex and facility mappings.

---

## 6. API Specification & Security Matrix

| Method | Endpoint | Allowed Roles | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Authenticate user and initialize session |
| `GET` | `/api/health` | Public | System health probe |
| `GET` | `/api/complexes` | `PROFESSOR`, `ADMIN` | List active campus complexes |
| `POST` | `/api/complexes` | `ADMIN` only | Create complex building |
| `GET` | `/api/rooms/{id}` | `PROFESSOR`, `ADMIN` | Get room details with facilities |
| `POST` | `/api/complexes/{id}/rooms`| `ADMIN` only | Create classroom in complex |
| `GET` | `/api/facilities` | `PROFESSOR`, `ADMIN` | List active room amenities |
| `GET` | `/api/timetables/versions` | `PROFESSOR`, `ADMIN` | List timetable versions |
| `POST` | `/api/timetables/versions` | `ADMIN` only | Create draft timetable version |
| `PATCH`| `/api/timetables/versions/{id}/activate` | `ADMIN` only | Atomically publish timetable version |
| `PATCH`| `/api/timetables/versions/{id}/archive` | `ADMIN` only | Archive timetable version |
| `POST` | `/api/timetables/versions/{id}/entries` | `ADMIN` only | Add class to draft timetable |
| `PUT` | `/api/timetables/entries/{id}` | `ADMIN` only | Update recurring class |
| `DELETE`| `/api/timetables/entries/{id}`| `ADMIN` only | Delete recurring class (204 No Content) |
| `POST` | `/api/schedule-occurrences/generate` | `ADMIN` only | Project timetable to calendar dates |
| `GET` | `/api/schedule-occurrences` | `PROFESSOR`, `ADMIN` | List calendar classes with filters |
| `GET` | `/api/schedule-occurrences/{id}` | `PROFESSOR`, `ADMIN` | Get single class occurrence details |
| `GET` | `/api/rooms/available` | `PROFESSOR`, `ADMIN` | Search free rooms matching constraints |

---

## 7. Testing Strategy & Quality Assurance

The codebase incorporates a comprehensive suite of **91 automated integration tests** executed against the MySQL test database.

```text
Results :
- SecurityAuthenticationTests          : 11 / 11 PASS
- HealthControllerTest                 :  1 /  1 PASS
- DomainIntegrationTests               : 17 / 17 PASS
- TimetableIntegrationTests            : 19 / 19 PASS
- ScheduleOccurrenceIntegrationTests   : 20 / 20 PASS
- AvailabilityIntegrationTests         : 19 / 19 PASS
- RepositoryTests                      :  4 /  4 PASS
-------------------------------------------------------
Total Tests Run: 91, Failures: 0, Errors: 0, Skipped: 0
Build Status: SUCCESS (Execution Time: ~9.4s)
```

### Coverage Highlights
- **Role Enforcement**: Tests prove professors receive `403 Forbidden` on admin-only endpoints.
- **Lifecycle Guarantees**: Tests prove drafts cannot generate occurrences, and archived timetables cannot be reactivated.
- **Collision Proofs**: Tests explicitly verify that overlapping intervals fail with `409 Conflict`, while adjacent back-to-back classes succeed.
- **Database Unique Constraints**: Integration tests prove that duplicate official occurrences fail at the MySQL level, while multiple null-entry extra occurrences are permitted.

---

## 8. Remaining Work & Future Roadmap (Phases 9–14)

With the timetable blueprint, occurrence generator, and availability engine fully implemented, the remaining architecture focuses on **dynamic operational features**:

```
                       CURRENT SYSTEM (Completed)
           [ Campus Infrastructure + Master Timetable + Occurrences + Availability ]
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ Phase 9: Transactional Room Booking Engine                                             │
│ - Booking creation endpoint: POST /api/bookings                                        │
│ - Concurrency control: Optimistic / Pessimistic row locking to eliminate race conditions│
│ - Booking approval workflow (Auto-approval vs. Admin review)                           │
└──────────────────────────────────────┬─────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ Phase 10: Class Cancellation & Rescheduling Workflows                                  │
│ - PATCH /api/schedule-occurrences/{id}/cancel (marks CANCELLED, frees up room)         │
│ - POST /api/schedule-occurrences/{id}/reschedule (cancels old, creates new occurrence) │
│ - Audit logging of professor reasons for cancellations/reschedules                     │
└──────────────────────────────────────┬─────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ Phase 11: Ad-Hoc / Extra Class Scheduling                                              │
│ - POST /api/schedule-occurrences/extra (creates occurrence with timetable_entry_id = null)│
│ - Conflict validation against official classes and existing bookings                   │
└──────────────────────────────────────┬─────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ Phase 12: High-Performance Caching Layer (Redis)                                       │
│ - Redis caching for frequently read static data (Complexes, Rooms, Facilities)         │
│ - Cache eviction policies tied to admin mutation events                                │
└──────────────────────────────────────┬─────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ Phase 13: Event-Driven Notification & Audit System                                     │
│ - Internal notification generation for schedule cancellations and room changes         │
│ - Structured audit logging table tracking user mutations                               │
└──────────────────────────────────────┬─────────────────────────────────────────────────┘
                                       │
                                       ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ Phase 14: Analytics, Dashboard & Frontend Integration                                  │
│ - Room utilization rate analytics (Heatmaps, peak hours, empty seat metrics)          │
│ - REST API integration with React / Next.js responsive web frontend                   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Detailed Future Roadmap Milestones
1. **Milestone 9 — Room Booking Engine**:
   - Provide `POST /api/bookings` for reserving rooms discovered via the Availability Engine.
   - Enforce database-level serializable isolation or pessimistic locking (`PESSIMISTIC_WRITE`) to ensure two professors cannot book the same room simultaneously.
2. **Milestone 10 — Cancellation & Rescheduling Operations**:
   - Allow professors to cancel their dated occurrences, automatically freeing the room for availability search.
   - Provide atomic rescheduling that cancels the source class and creates the target replacement in one transaction.
3. **Milestone 11 — Extra Class Management**:
   - Endpoints allowing professors to schedule ad-hoc sessions, exam reviews, or makeup classes.
4. **Milestone 12 — Redis Caching**:
   - Cache active room configurations and complex directories in Redis with sub-millisecond retrieval.
5. **Milestone 13 — Notifications & Audit Trail**:
   - Asynchronous notification dispatcher alerting students and professors when classes are rescheduled.
6. **Milestone 14 — Analytics & UI Integration**:
   - Compute classroom utilization percentages across departments and connect backend to modern frontend client.

---

## 9. Conclusion

The **ClassSync Backend** has reached a stable milestone. The foundation—spanning database schema design, session authentication, physical infrastructure management, versioned timetable generation, collision detection, calendar projection, and advisory availability search—is operational and validated by **91 passing automated tests**.

The system is architected for clean extensibility, ready for the upcoming Booking, Cancellation, and Caching modules.

---
*Report generated on October 11, 2026. Codebase synchronized on branch `main` at commit `24d3095`.*
