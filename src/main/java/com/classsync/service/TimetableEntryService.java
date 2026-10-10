package com.classsync.service;

import com.classsync.dto.room.RoomSummaryResponse;
import com.classsync.dto.timetable.CreateTimetableEntryRequest;
import com.classsync.dto.timetable.ProfessorSummaryResponse;
import com.classsync.dto.timetable.TimetableEntryResponse;
import com.classsync.dto.timetable.UpdateTimetableEntryRequest;
import com.classsync.entity.*;
import com.classsync.exception.BadRequestException;
import com.classsync.exception.DuplicateResourceException;
import com.classsync.exception.ResourceNotFoundException;
import com.classsync.repository.RoomRepository;
import com.classsync.repository.TimetableEntryRepository;
import com.classsync.repository.TimetableVersionRepository;
import com.classsync.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TimetableEntryService {

    private final TimetableEntryRepository timetableEntryRepository;
    private final TimetableVersionRepository timetableVersionRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    public TimetableEntryService(TimetableEntryRepository timetableEntryRepository,
                                 TimetableVersionRepository timetableVersionRepository,
                                 UserRepository userRepository,
                                 RoomRepository roomRepository) {
        this.timetableEntryRepository = timetableEntryRepository;
        this.timetableVersionRepository = timetableVersionRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<TimetableEntryResponse> listEntriesByVersion(Integer versionId) {
        timetableVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable version not found with id: " + versionId));

        return timetableEntryRepository.findByTimetableVersionIdWithDetails(versionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TimetableEntryResponse getEntryById(Integer id) {
        TimetableEntry entry = timetableEntryRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable entry not found with id: " + id));

        return toResponse(entry);
    }

    @Transactional
    public TimetableEntryResponse createEntry(Integer versionId, CreateTimetableEntryRequest request) {
        TimetableVersion version = timetableVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable version not found with id: " + versionId));

        if (version.getStatus() != TimetableVersionStatus.DRAFT) {
            throw new BadRequestException("Entries can only be added to DRAFT versions. Current status: " + version.getStatus());
        }

        validateTimeOrder(request.startTime(), request.endTime());
        User professor = validateProfessor(request.professorId());
        Room room = validateRoom(request.roomId());

        validateRoomOverlap(versionId, request.roomId(), request.dayOfWeek(), request.startTime(), request.endTime(), null, room.getRoomNumber());
        validateProfessorOverlap(versionId, request.professorId(), request.dayOfWeek(), request.startTime(), request.endTime(), null, professor.getName());

        TimetableEntry entry = new TimetableEntry(
                version,
                professor,
                request.courseCode().trim(),
                request.courseName().trim(),
                room,
                request.dayOfWeek(),
                request.startTime(),
                request.endTime()
        );

        TimetableEntry saved = timetableEntryRepository.save(entry);
        return toResponse(saved);
    }

    @Transactional
    public TimetableEntryResponse updateEntry(Integer id, UpdateTimetableEntryRequest request) {
        TimetableEntry entry = timetableEntryRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable entry not found with id: " + id));

        TimetableVersion version = entry.getTimetableVersion();
        if (version.getStatus() != TimetableVersionStatus.DRAFT) {
            throw new BadRequestException("Entries in ACTIVE or ARCHIVED versions cannot be modified. Current status: " + version.getStatus());
        }

        validateTimeOrder(request.startTime(), request.endTime());
        User professor = validateProfessor(request.professorId());
        Room room = validateRoom(request.roomId());

        validateRoomOverlap(version.getId(), request.roomId(), request.dayOfWeek(), request.startTime(), request.endTime(), id, room.getRoomNumber());
        validateProfessorOverlap(version.getId(), request.professorId(), request.dayOfWeek(), request.startTime(), request.endTime(), id, professor.getName());

        entry.setProfessor(professor);
        entry.setCourseCode(request.courseCode().trim());
        entry.setCourseName(request.courseName().trim());
        entry.setRoom(room);
        entry.setDayOfWeek(request.dayOfWeek());
        entry.setStartTime(request.startTime());
        entry.setEndTime(request.endTime());

        return toResponse(entry);
    }

    @Transactional
    public void deleteEntry(Integer id) {
        TimetableEntry entry = timetableEntryRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable entry not found with id: " + id));

        TimetableVersion version = entry.getTimetableVersion();
        if (version.getStatus() != TimetableVersionStatus.DRAFT) {
            throw new BadRequestException("Entries in ACTIVE or ARCHIVED versions cannot be deleted. Current status: " + version.getStatus());
        }

        timetableEntryRepository.delete(entry);
    }

    private void validateTimeOrder(java.time.LocalTime startTime, java.time.LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new BadRequestException("Start time must be strictly before end time");
        }
    }

    private User validateProfessor(Integer professorId) {
        User user = userRepository.findById(professorId)
                .orElseThrow(() -> new BadRequestException("Professor not found with id: " + professorId));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Assigned professor account is inactive");
        }

        if (user.getRole() != UserRole.PROFESSOR) {
            throw new BadRequestException("Assigned user must have PROFESSOR role");
        }

        return user;
    }

    private Room validateRoom(Integer roomId) {
        Room room = roomRepository.findByIdWithDetails(roomId)
                .orElseThrow(() -> new BadRequestException("Room not found with id: " + roomId));

        if (!room.getActive()) {
            throw new BadRequestException("Assigned room is inactive");
        }

        if (!room.getComplex().getActive()) {
            throw new BadRequestException("Assigned room belongs to an inactive complex");
        }

        return room;
    }

    private void validateRoomOverlap(Integer versionId, Integer roomId, Byte dayOfWeek,
                                     java.time.LocalTime startTime, java.time.LocalTime endTime,
                                     Integer excludedEntryId, String roomNumber) {
        List<TimetableEntry> overlaps = timetableEntryRepository.findRoomOverlaps(
                versionId, roomId, dayOfWeek, startTime, endTime, excludedEntryId
        );

        if (!overlaps.isEmpty()) {
            TimetableEntry conflict = overlaps.get(0);
            throw new DuplicateResourceException(
                    "Room " + roomNumber + " already has an overlapping class on day " + dayOfWeek +
                    " between " + conflict.getStartTime() + " and " + conflict.getEndTime()
            );
        }
    }

    private void validateProfessorOverlap(Integer versionId, Integer professorId, Byte dayOfWeek,
                                          java.time.LocalTime startTime, java.time.LocalTime endTime,
                                          Integer excludedEntryId, String professorName) {
        List<TimetableEntry> overlaps = timetableEntryRepository.findProfessorOverlaps(
                versionId, professorId, dayOfWeek, startTime, endTime, excludedEntryId
        );

        if (!overlaps.isEmpty()) {
            TimetableEntry conflict = overlaps.get(0);
            throw new DuplicateResourceException(
                    "Professor " + professorName + " already has an overlapping class on day " + dayOfWeek +
                    " between " + conflict.getStartTime() + " and " + conflict.getEndTime()
            );
        }
    }

    private TimetableEntryResponse toResponse(TimetableEntry entry) {
        ProfessorSummaryResponse profSummary = new ProfessorSummaryResponse(
                entry.getProfessor().getId(),
                entry.getProfessor().getName(),
                entry.getProfessor().getEmail(),
                entry.getProfessor().getEmployeeCode()
        );

        RoomSummaryResponse roomSummary = new RoomSummaryResponse(
                entry.getRoom().getId(),
                entry.getRoom().getRoomNumber(),
                entry.getRoom().getRoomType(),
                entry.getRoom().getCapacity(),
                entry.getRoom().getActive()
        );

        return new TimetableEntryResponse(
                entry.getId(),
                entry.getTimetableVersion().getId(),
                profSummary,
                entry.getCourseCode(),
                entry.getCourseName(),
                roomSummary,
                entry.getDayOfWeek(),
                entry.getStartTime(),
                entry.getEndTime()
        );
    }
}
