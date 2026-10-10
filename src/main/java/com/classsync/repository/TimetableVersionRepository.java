package com.classsync.repository;

import com.classsync.entity.TimetableVersion;
import com.classsync.entity.TimetableVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimetableVersionRepository extends JpaRepository<TimetableVersion, Integer> {

    List<TimetableVersion> findByAcademicYearAndSemesterOrderByVersionNumberDesc(String academicYear, String semester);

    List<TimetableVersion> findAllByOrderByAcademicYearDescSemesterAscVersionNumberDesc();

    Optional<TimetableVersion> findByAcademicYearAndSemesterAndStatus(String academicYear, String semester, TimetableVersionStatus status);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM TimetableVersion v WHERE v.academicYear = :academicYear AND v.semester = :semester")
    int findMaxVersionNumber(@Param("academicYear") String academicYear, @Param("semester") String semester);

    @Query("SELECT v FROM TimetableVersion v JOIN FETCH v.createdBy WHERE v.id = :id")
    Optional<TimetableVersion> findByIdWithCreator(@Param("id") Integer id);
}
