# ClassSync Backend

ClassSync is a smart classroom and lecture-hall scheduling and allocation backend built with Java 21, Spring Boot 3, MySQL 8+, and Flyway migrations.

## Prerequisites

- **Java**: 21 or newer (`java -version`)
- **Maven**: 3.9+ (`mvn -version`)
- **MySQL**: 8.0+ (`mysql --version`)

## Architecture & Design Principles

The backend follows a layered monolithic architecture:

```
Controller  -->  Service  -->  Repository  -->  MySQL
```

- **Clean and Minimal**: Adheres to SOLID, KISS, YAGNI, and DRY principles without premature abstractions.
- **Database Migrations**: Managed declaratively through Flyway. All primary keys use `INT AUTO_INCREMENT` (no UUIDs).
- **Referential Integrity**: Deletion safety rules prevent accidental cascades on historical schedule and timetable data.
- **No JPA Entities Yet**: Database schema and migrations are established independently; JPA entities will be mapped in Prompt 3.

## Database Schema (10 Core Tables)

1. `users`: System users (Professors and Admins). Single table for all user types.
2. `complexes`: Academic complexes/zones housing rooms (no separate buildings table).
3. `rooms`: Classrooms, labs, lecture halls, and seminar halls with capacity check (`capacity > 0`) and `UNIQUE(complex_id, room_number)`.
4. `facilities`: Equipment/amenities (e.g., Projector, Smart Board, AC, Computers, Audio System).
5. `room_facilities`: Many-to-many relationship mapping rooms to facilities.
6. `timetable_versions`: Version-controlled official timetable definitions (`academic_year`, `semester`, `version_number`).
7. `timetable_entries`: Recurring weekly class slots with day of week check (`1-7`) and time ordering check (`start_time < end_time`).
8. `schedule_occurrences`: Date-specific calendar occurrences (official and extra classes) with `timetable_entry_id` (NULLable for extra classes) and time ordering check.
9. `notifications`: User notifications (`BOOKING`, `CANCELLATION`, `RESCHEDULE`, `SYSTEM`) with unread tracking index.
10. `audit_logs`: Audit trail with JSON snapshots (`old_value`, `new_value`) for flexible change tracking.

### Flyway Migrations

- `V1__create_initial_schema.sql`: Creates all 10 tables, check constraints, foreign keys, and indexes.
- `V2__seed_development_data.sql`: Seeds minimal realistic local development data:
  - 1 Admin (`admin@classsync.edu`)
  - 2 Professors (`aturing@classsync.edu`, `alovelace@classsync.edu`)
  - 2 Complexes (`SEC`, `HSS`)
  - 5 Rooms across complexes
  - 5 Facilities with room-facility mappings
  - *Note: Seed passwords use development BCrypt hash for `password123` (`$2a$10$7EqJtq98hPqEX7fNZaFWoOhiVjA7RMRp5e8VbZp0m5eLgLgQ16M3O`).*

## Configuration

Configuration is managed in `src/main/resources/application.yml`:

| Environment Variable | Description | Default Value |
|----------------------|-------------|---------------|
| `PORT`               | HTTP server port | `8080` |
| `DB_URL`             | MySQL JDBC URL | `jdbc:mysql://localhost:3306/classsync?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `DB_USERNAME`        | MySQL user | `root` |
| `DB_PASSWORD`        | MySQL password | (empty) |

## How to Run the Backend

```bash
# Start MySQL service if not running:
brew services start mysql

# Run the application (Flyway migrations run automatically on startup):
mvn spring-boot:run

# Or package and run the executable JAR:
mvn clean package
java -jar target/classsync-backend-0.0.1-SNAPSHOT.jar
```

Verify health check:
```bash
curl http://localhost:8080/api/health
```

## Authentication & Authorization (Prompt 4)

Authentication is implemented with Spring Security using a REST session-based approach with BCrypt password hashing.

### Endpoints

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/health` | Public | Application health status |
| `POST` | `/api/auth/login` | Public | Authenticate user, start session |
| `GET` | `/api/test/authenticated` | Authenticated | Test endpoint for any authenticated user |
| `GET` | `/api/test/professor` | `ROLE_PROFESSOR`, `ROLE_ADMIN` | Test endpoint for professors / admins |
| `GET` | `/api/test/admin` | `ROLE_ADMIN` | Test endpoint restricted to admins |

### Login Request / Response

**Request:**
```bash
POST /api/auth/login
Content-Type: application/json

{
  "email": "admin@classsync.edu",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "name": "System Administrator",
  "email": "admin@classsync.edu",
  "role": "ADMIN"
}
```
*Note: A session cookie (`JSESSIONID`) is issued with `HttpOnly` enabled. Password hashes or internal credentials are never exposed in responses.*

### Development Credentials

> **Notice:** These credentials are for local development testing only and must never be used in production.

- **Admin:** `admin@classsync.edu` / `password123`
- **Professor:** `aturing@classsync.edu` / `password123`
- **Professor:** `alovelace@classsync.edu` / `password123`

## Domain APIs (Prompt 5: Complex, Room, and Facility)

Authenticated users (Professors and Admins) can read active resources. Only Admins can create, update, or activate/deactivate resources. Physical deletion is not permitted; status deactivation (`active = false`) is used instead.

### Complex APIs

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/complexes` | Authenticated | List all active complexes |
| `GET` | `/api/complexes/{id}` | Authenticated | Get active complex by ID |
| `POST` | `/api/complexes` | `ROLE_ADMIN` | Create a new complex (unique code) |
| `PUT` | `/api/complexes/{id}` | `ROLE_ADMIN` | Update complex name/code |
| `PATCH` | `/api/complexes/{id}/status` | `ROLE_ADMIN` | Activate or deactivate a complex |

### Room APIs

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/complexes/{complexId}/rooms` | Authenticated | List active rooms for an active complex |
| `POST` | `/api/complexes/{complexId}/rooms` | `ROLE_ADMIN` | Create room inside a complex (unique within complex) |
| `GET` | `/api/rooms/{id}` | Authenticated | Get active room by ID with complex & facilities |
| `PUT` | `/api/rooms/{id}` | `ROLE_ADMIN` | Update room number, type, capacity, and facilities |
| `PATCH` | `/api/rooms/{id}/status` | `ROLE_ADMIN` | Activate or deactivate a room |

### Facility APIs

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/facilities` | Authenticated | List all active facilities |
| `POST` | `/api/facilities` | `ROLE_ADMIN` | Create a new facility (unique name) |
| `PUT` | `/api/facilities/{id}` | `ROLE_ADMIN` | Update facility name |
| `PATCH` | `/api/facilities/{id}/status` | `ROLE_ADMIN` | Activate or deactivate a facility |

## Testing

Run all unit, repository, security, and domain integration tests (33 tests):
```bash
mvn test
```


