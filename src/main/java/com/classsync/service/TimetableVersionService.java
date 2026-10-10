package com.classsync.service;

import com.classsync.dto.timetable.CreateTimetableVersionRequest;
import com.classsync.dto.timetable.TimetableVersionResponse;
import com.classsync.entity.TimetableVersion;
import com.classsync.entity.TimetableVersionStatus;
import com.classsync.entity.User;
import com.classsync.exception.BadRequestException;
import com.classsync.exception.ResourceNotFoundException;
import com.classsync.repository.TimetableVersionRepository;
import com.classsync.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TimetableVersionService {

    private final TimetableVersionRepository timetableVersionRepository;
    private final UserRepository userRepository;

    public TimetableVersionService(TimetableVersionRepository timetableVersionRepository,
                                   UserRepository userRepository) {
        this.timetableVersionRepository = timetableVersionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TimetableVersionResponse> listVersions(String academicYear, String semester) {
        List<TimetableVersion> versions;
        if (academicYear != null && semester != null) {
            versions = timetableVersionRepository.findByAcademicYearAndSemesterOrderByVersionNumberDesc(
                    academicYear.trim(), semester.trim()
            );
        } else {
            versions = timetableVersionRepository.findAllByOrderByAcademicYearDescSemesterAscVersionNumberDesc();
        }

        return versions.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TimetableVersionResponse getVersionById(Integer id) {
        TimetableVersion version = timetableVersionRepository.findByIdWithCreator(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable version not found with id: " + id));
        return toResponse(version);
    }

    @Transactional
    public TimetableVersionResponse createVersion(CreateTimetableVersionRequest request, String creatorEmail) {
        String academicYear = request.academicYear().trim();
        String semester = request.semester().trim();

        User creator = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new IllegalStateException("Creator user not found with email: " + creatorEmail));

        int maxVersion = timetableVersionRepository.findMaxVersionNumber(academicYear, semester);
        int nextVersion = maxVersion + 1;

        TimetableVersion version = new TimetableVersion(
                academicYear,
                semester,
                nextVersion,
                TimetableVersionStatus.DRAFT,
                creator
        );

        TimetableVersion saved = timetableVersionRepository.save(version);
        return toResponse(saved);
    }

    @Transactional
    public TimetableVersionResponse activateVersion(Integer id) {
        TimetableVersion version = timetableVersionRepository.findByIdWithCreator(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable version not found with id: " + id));

        if (version.getStatus() != TimetableVersionStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT versions can be activated. Current status: " + version.getStatus());
        }

        Optional<TimetableVersion> currentActive = timetableVersionRepository.findByAcademicYearAndSemesterAndStatus(
                version.getAcademicYear(), version.getSemester(), TimetableVersionStatus.ACTIVE
        );
        currentActive.ifPresent(activeVer -> activeVer.setStatus(TimetableVersionStatus.ARCHIVED));

        version.setStatus(TimetableVersionStatus.ACTIVE);
        return toResponse(version);
    }

    @Transactional
    public TimetableVersionResponse archiveVersion(Integer id) {
        TimetableVersion version = timetableVersionRepository.findByIdWithCreator(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable version not found with id: " + id));

        if (version.getStatus() == TimetableVersionStatus.ARCHIVED) {
            throw new BadRequestException("Timetable version is already ARCHIVED");
        }

        version.setStatus(TimetableVersionStatus.ARCHIVED);
        return toResponse(version);
    }

    public TimetableVersionResponse toResponse(TimetableVersion version) {
        return new TimetableVersionResponse(
                version.getId(),
                version.getAcademicYear(),
                version.getSemester(),
                version.getVersionNumber(),
                version.getStatus(),
                version.getCreatedBy().getId(),
                version.getCreatedBy().getName(),
                version.getCreatedAt()
        );
    }
}
