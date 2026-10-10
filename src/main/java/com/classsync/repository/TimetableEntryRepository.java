package com.classsync.repository;

import com.classsync.entity.TimetableEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Integer> {

    @Query("SELECT e FROM TimetableEntry e JOIN FETCH e.professor JOIN FETCH e.room r JOIN FETCH r.complex WHERE e.timetableVersion.id = :versionId ORDER BY e.dayOfWeek ASC, e.startTime ASC")
    List<TimetableEntry> findByTimetableVersionIdWithDetails(@Param("versionId") Integer versionId);

    @Query("SELECT e FROM TimetableEntry e JOIN FETCH e.timetableVersion JOIN FETCH e.professor JOIN FETCH e.room r JOIN FETCH r.complex WHERE e.id = :id")
    Optional<TimetableEntry> findByIdWithDetails(@Param("id") Integer id);

    @Query("""
        SELECT e FROM TimetableEntry e
        WHERE e.timetableVersion.id = :versionId
          AND e.room.id = :roomId
          AND e.dayOfWeek = :dayOfWeek
          AND e.startTime < :endTime
          AND e.endTime > :startTime
          AND (:excludedEntryId IS NULL OR e.id != :excludedEntryId)
    """)
    List<TimetableEntry> findRoomOverlaps(
            @Param("versionId") Integer versionId,
            @Param("roomId") Integer roomId,
            @Param("dayOfWeek") Byte dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludedEntryId") Integer excludedEntryId
    );

    @Query("""
        SELECT e FROM TimetableEntry e
        WHERE e.timetableVersion.id = :versionId
          AND e.professor.id = :professorId
          AND e.dayOfWeek = :dayOfWeek
          AND e.startTime < :endTime
          AND e.endTime > :startTime
          AND (:excludedEntryId IS NULL OR e.id != :excludedEntryId)
    """)
    List<TimetableEntry> findProfessorOverlaps(
            @Param("versionId") Integer versionId,
            @Param("professorId") Integer professorId,
            @Param("dayOfWeek") Byte dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludedEntryId") Integer excludedEntryId
    );
}
