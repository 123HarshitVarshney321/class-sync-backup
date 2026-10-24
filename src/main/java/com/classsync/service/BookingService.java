package com.classsync.service;

import com.classsync.dto.booking.BookingProfessorSummary;
import com.classsync.dto.booking.BookingResponse;
import com.classsync.dto.booking.BookingRoomSummary;
import com.classsync.dto.booking.CreateBookingRequest;
import com.classsync.entity.*;
import com.classsync.exception.BadRequestException;
import com.classsync.exception.DuplicateResourceException;
import com.classsync.exception.ResourceNotFoundException;
import com.classsync.repository.RoomRepository;
import com.classsync.repository.ScheduleOccurrenceRepository;
import com.classsync.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    public BookingService(UserRepository userRepository,
                          RoomRepository roomRepository,
                          ScheduleOccurrenceRepository scheduleOccurrenceRepository) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse createExtraClass(CreateBookingRequest request, String professorEmail) {
        if (request.startTime() == null || request.endTime() == null || !request.startTime().isBefore(request.endTime())) {
            throw new BadRequestException("Start time must be strictly before end time");
        }

        User authenticatedUser = userRepository.findByEmail(professorEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found with email: " + professorEmail));

        if (authenticatedUser.getRole() != UserRole.PROFESSOR) {
            throw new AccessDeniedException("Only professors can create extra bookings");
        }

        if (authenticatedUser.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Professor account is inactive");
        }

        // Strict lock hierarchy (User -> Room) to prevent deadlocks and race conditions:
        // 1. Pessimistic write lock on the professor row: serializes simultaneous bookings by the same professor.
        User lockedProf = userRepository.findByIdWithLock(authenticatedUser.getId())
                .orElseThrow(() -> new IllegalStateException("Professor record not found for locking: " + authenticatedUser.getId()));

        // 2. Pessimistic write lock on the requested room row: serializes simultaneous bookings for the same room.
        Room room = roomRepository.findByIdWithLock(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + request.roomId()));

        // Room active & complex active validation
        if (!room.getActive()) {
            throw new ResourceNotFoundException("Room is inactive with id: " + request.roomId());
        }
        if (!room.getComplex().getActive()) {
            throw new ResourceNotFoundException("Room belongs to an inactive complex");
        }

        // Check room conflict
        boolean hasRoomConflict = scheduleOccurrenceRepository.existsRoomConflict(
                room.getId(),
                request.date(),
                request.startTime(),
                request.endTime()
        );
        if (hasRoomConflict) {
            throw new DuplicateResourceException("Room is already scheduled for the requested time.");
        }

        // Check professor conflict
        boolean hasProfessorConflict = scheduleOccurrenceRepository.existsProfessorConflict(
                lockedProf.getId(),
                request.date(),
                request.startTime(),
                request.endTime()
        );
        if (hasProfessorConflict) {
            throw new DuplicateResourceException("Professor already has a scheduled class during the requested time.");
        }

        // Create new EXTRA occurrence
        ScheduleOccurrence occurrence = new ScheduleOccurrence(
                null,
                lockedProf,
                room,
                request.date(),
                request.startTime(),
                request.endTime(),
                ScheduleEventType.EXTRA,
                ScheduleStatus.SCHEDULED,
                request.courseCode().trim(),
                request.courseName().trim(),
                lockedProf
        );

        ScheduleOccurrence saved = scheduleOccurrenceRepository.save(occurrence);
        return toResponse(saved);
    }

    private BookingResponse toResponse(ScheduleOccurrence occurrence) {
        BookingProfessorSummary profSummary = new BookingProfessorSummary(
                occurrence.getProfessor().getId(),
                occurrence.getProfessor().getName(),
                occurrence.getProfessor().getEmail()
        );

        BookingRoomSummary roomSummary = new BookingRoomSummary(
                occurrence.getRoom().getId(),
                occurrence.getRoom().getRoomNumber()
        );

        return new BookingResponse(
                occurrence.getId(),
                occurrence.getEventType(),
                occurrence.getStatus(),
                occurrence.getScheduleDate(),
                occurrence.getStartTime(),
                occurrence.getEndTime(),
                occurrence.getCourseCode(),
                occurrence.getCourseName(),
                profSummary,
                roomSummary
        );
    }
}
