package com.classsync.service;

import com.classsync.dto.complex.ComplexSummaryResponse;
import com.classsync.dto.occurrence.GenerateOccurrencesRequest;
import com.classsync.dto.occurrence.GenerateOccurrencesResponse;
import com.classsync.dto.occurrence.RoomOccurrenceSummaryResponse;
import com.classsync.dto.occurrence.ScheduleOccurrenceResponse;
import com.classsync.dto.timetable.ProfessorSummaryResponse;
import com.classsync.entity.*;
import com.classsync.exception.BadRequestException;
import com.classsync.exception.ResourceNotFoundException;
import com.classsync.repository.ScheduleOccurrenceRepository;
import com.classsync.repository.TimetableEntryRepository;
import com.classsync.repository.TimetableVersionRepository;
import com.classsync.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleOccurrenceService {

    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final TimetableVersionRepository timetableVersionRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final UserRepository userRepository;

    public ScheduleOccurrenceService(ScheduleOccurrenceRepository scheduleOccurrenceRepository,
                                     TimetableVersionRepository timetableVersionRepository,
                                     TimetableEntryRepository timetableEntryRepository,
                                     UserRepository userRepository) {
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
        this.timetableVersionRepository = timetableVersionRepository;
        this.timetableEntryRepository = timetableEntryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public GenerateOccurrencesResponse generateOccurrences(GenerateOccurrencesRequest request, String adminEmail) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BadRequestException("Start date must be less than or equal to end date");
        }

        if (request.startDate().plusYears(1).isBefore(request.endDate())) {
            throw new BadRequestException("Generation window cannot exceed 12 months");
        }

        TimetableVersion version = timetableVersionRepository.findById(request.timetableVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("Timetable version not found with id: " + request.timetableVersionId()));

        if (version.getStatus() != TimetableVersionStatus.ACTIVE) {
            throw new BadRequestException("Occurrences can only be generated from ACTIVE timetable versions. Current status: " + version.getStatus());
        }

        User creator = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalStateException("Admin user not found with email: " + adminEmail));

        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableVersionIdWithDetails(version.getId());
        if (entries.isEmpty()) {
            return new GenerateOccurrencesResponse(version.getId(), request.startDate(), request.endDate(), 0, 0);
        }

        List<Integer> entryIds = entries.stream().map(TimetableEntry::getId).toList();
        Set<String> existingKeys = scheduleOccurrenceRepository
                .findExistingEntryDates(entryIds, request.startDate(), request.endDate())
                .stream()
                .map(row -> row[0] + "_" + row[1])
                .collect(Collectors.toSet());

        Map<Byte, List<TimetableEntry>> entriesByDay = entries.stream()
                .collect(Collectors.groupingBy(TimetableEntry::getDayOfWeek));

        List<ScheduleOccurrence> toSave = new ArrayList<>();
        int createdCount = 0;
        int skippedCount = 0;

        for (LocalDate date = request.startDate(); !date.isAfter(request.endDate()); date = date.plusDays(1)) {
            byte dayOfWeek = (byte) date.getDayOfWeek().getValue();
            List<TimetableEntry> dayEntries = entriesByDay.getOrDefault(dayOfWeek, Collections.emptyList());

            for (TimetableEntry entry : dayEntries) {
                String key = entry.getId() + "_" + date;
                if (existingKeys.contains(key)) {
                    skippedCount++;
                } else {
                    ScheduleOccurrence occurrence = new ScheduleOccurrence(
                            entry,
                            entry.getProfessor(),
                            entry.getRoom(),
                            date,
                            entry.getStartTime(),
                            entry.getEndTime(),
                            ScheduleEventType.OFFICIAL,
                            ScheduleStatus.SCHEDULED,
                            entry.getCourseCode(),
                            entry.getCourseName(),
                            creator
                    );
                    toSave.add(occurrence);
                    createdCount++;
                }
            }
        }

        if (!toSave.isEmpty()) {
            scheduleOccurrenceRepository.saveAll(toSave);
        }

        return new GenerateOccurrencesResponse(version.getId(), request.startDate(), request.endDate(), createdCount, skippedCount);
    }

    @Transactional(readOnly = true)
    public List<ScheduleOccurrenceResponse> getScheduleOccurrences(LocalDate startDate,
                                                                   LocalDate endDate,
                                                                   Integer professorId,
                                                                   Integer roomId,
                                                                   Integer complexId,
                                                                   ScheduleStatus status) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("Start date must be less than or equal to end date");
        }

        return scheduleOccurrenceRepository.findWithFilters(startDate, endDate, professorId, roomId, complexId, status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleOccurrenceResponse getScheduleOccurrenceById(Integer id) {
        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule occurrence not found with id: " + id));

        return toResponse(occurrence);
    }

    private ScheduleOccurrenceResponse toResponse(ScheduleOccurrence o) {
        ProfessorSummaryResponse profSummary = new ProfessorSummaryResponse(
                o.getProfessor().getId(),
                o.getProfessor().getName(),
                o.getProfessor().getEmail(),
                o.getProfessor().getEmployeeCode()
        );

        ComplexSummaryResponse complexSummary = new ComplexSummaryResponse(
                o.getRoom().getComplex().getId(),
                o.getRoom().getComplex().getName(),
                o.getRoom().getComplex().getCode()
        );

        RoomOccurrenceSummaryResponse roomSummary = new RoomOccurrenceSummaryResponse(
                o.getRoom().getId(),
                o.getRoom().getRoomNumber(),
                o.getRoom().getRoomType(),
                o.getRoom().getCapacity(),
                o.getRoom().getActive(),
                complexSummary
        );

        Integer timetableEntryId = o.getTimetableEntry() != null ? o.getTimetableEntry().getId() : null;

        return new ScheduleOccurrenceResponse(
                o.getId(),
                timetableEntryId,
                profSummary,
                roomSummary,
                o.getScheduleDate(),
                o.getStartTime(),
                o.getEndTime(),
                o.getEventType(),
                o.getStatus(),
                o.getCourseCode(),
                o.getCourseName(),
                o.getCreatedBy().getId(),
                o.getCreatedBy().getName(),
                o.getCreatedAt()
        );
    }
}
