package com.classsync.service;

import com.classsync.dto.booking.BookingProfessorSummary;
import com.classsync.dto.booking.BookingResponse;
import com.classsync.dto.booking.BookingRoomSummary;
import com.classsync.dto.occurrence.RescheduleRequest;
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
public class ClassScheduleService {

    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    public ClassScheduleService(ScheduleOccurrenceRepository scheduleOccurrenceRepository,
                                UserRepository userRepository,
                                RoomRepository roomRepository) {
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse cancelOccurrence(Integer occurrenceId, String authenticatedUserEmail) {
        // 1. Lock the occurrence being cancelled to serialize concurrent cancellations/modifications
        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findByIdWithLock(occurrenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule occurrence not found with id: " + occurrenceId));

        // 2. Cancellation eligibility: only SCHEDULED occurrences can be cancelled
        if (occurrence.getStatus() == ScheduleStatus.CANCELLED) {
            throw new DuplicateResourceException("Occurrence is already cancelled");
        }

        // 3. Authorization check
        User caller = userRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found with email: " + authenticatedUserEmail));

        if (caller.getRole() == UserRole.PROFESSOR && !occurrence.getProfessor().getId().equals(caller.getId())) {
            throw new AccessDeniedException("Professors can only cancel their own classes");
        }

        // 4. Update status to CANCELLED without modifying other fields
        occurrence.setStatus(ScheduleStatus.CANCELLED);
        ScheduleOccurrence saved = scheduleOccurrenceRepository.save(occurrence);

        return toResponse(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse rescheduleOccurrence(Integer occurrenceId, RescheduleRequest request, String authenticatedUserEmail) {
        // 1. Time range validation
        if (request.startTime() == null || request.endTime() == null || !request.startTime().isBefore(request.endTime())) {
            throw new BadRequestException("Start time must be strictly before end time");
        }

        User caller = userRepository.findByEmail(authenticatedUserEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found with email: " + authenticatedUserEmail));

        // 2. Strict global lock hierarchy (Occurrence -> User -> Room) to prevent deadlocks:
        // Lock 1: Occurrence being modified (serializes concurrent reschedule attempts on the same occurrence)
        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findByIdWithLock(occurrenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule occurrence not found with id: " + occurrenceId));

        // Check eligibility: cancelled occurrences cannot be rescheduled
        if (occurrence.getStatus() == ScheduleStatus.CANCELLED) {
            throw new DuplicateResourceException("Cancelled occurrences cannot be rescheduled");
        }

        // Check caller authorization
        if (caller.getRole() == UserRole.PROFESSOR && !occurrence.getProfessor().getId().equals(caller.getId())) {
            throw new AccessDeniedException("Professors can only reschedule their own classes");
        }

        // Lock 2: Assigned Professor row (serializes concurrent allocations for the same professor)
        User assignedProfessor = userRepository.findByIdWithLock(occurrence.getProfessor().getId())
                .orElseThrow(() -> new IllegalStateException("Assigned professor not found for locking: " + occurrence.getProfessor().getId()));

        // Lock 3: Target Room row (serializes concurrent claims on the target room)
        Room targetRoom = roomRepository.findByIdWithLock(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + request.roomId()));

        // Target room and complex active verification
        if (!targetRoom.getActive()) {
            throw new ResourceNotFoundException("Room is inactive with id: " + request.roomId());
        }
        if (!targetRoom.getComplex().getActive()) {
            throw new ResourceNotFoundException("Room belongs to an inactive complex");
        }

        // 3. Conflict detection excluding the occurrence itself
        boolean hasRoomConflict = scheduleOccurrenceRepository.existsRoomConflictExcludingId(
                targetRoom.getId(),
                request.date(),
                request.startTime(),
                request.endTime(),
                occurrenceId
        );
        if (hasRoomConflict) {
            throw new DuplicateResourceException("Room is already scheduled for the requested time.");
        }

        boolean hasProfessorConflict = scheduleOccurrenceRepository.existsProfessorConflictExcludingId(
                assignedProfessor.getId(),
                request.date(),
                request.startTime(),
                request.endTime(),
                occurrenceId
        );
        if (hasProfessorConflict) {
            throw new DuplicateResourceException("Professor already has a scheduled class during the requested time.");
        }

        // 4. Update the occurrence's date, time, and room while preserving all other attributes
        occurrence.setScheduleDate(request.date());
        occurrence.setStartTime(request.startTime());
        occurrence.setEndTime(request.endTime());
        occurrence.setRoom(targetRoom);

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
