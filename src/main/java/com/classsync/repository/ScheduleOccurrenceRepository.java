package com.classsync.repository;

import com.classsync.entity.ScheduleOccurrence;
import com.classsync.entity.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleOccurrenceRepository extends JpaRepository<ScheduleOccurrence, Integer> {

    @Query("""
        SELECT o.timetableEntry.id, o.scheduleDate
        FROM ScheduleOccurrence o
        WHERE o.timetableEntry.id IN :entryIds
          AND o.scheduleDate BETWEEN :startDate AND :endDate
    """)
    List<Object[]> findExistingEntryDates(
            @Param("entryIds") Collection<Integer> entryIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT o FROM ScheduleOccurrence o
        JOIN FETCH o.professor p
        JOIN FETCH o.room r
        JOIN FETCH r.complex c
        JOIN FETCH o.createdBy u
        LEFT JOIN FETCH o.timetableEntry te
        WHERE o.id = :id
    """)
    Optional<ScheduleOccurrence> findByIdWithDetails(@Param("id") Integer id);

    @Query("""
        SELECT o FROM ScheduleOccurrence o
        JOIN FETCH o.professor p
        JOIN FETCH o.room r
        JOIN FETCH r.complex c
        JOIN FETCH o.createdBy u
        LEFT JOIN FETCH o.timetableEntry te
        WHERE (:startDate IS NULL OR o.scheduleDate >= :startDate)
          AND (:endDate IS NULL OR o.scheduleDate <= :endDate)
          AND (:professorId IS NULL OR p.id = :professorId)
          AND (:roomId IS NULL OR r.id = :roomId)
          AND (:complexId IS NULL OR c.id = :complexId)
          AND (:status IS NULL OR o.status = :status)
        ORDER BY o.scheduleDate ASC, o.startTime ASC, o.id ASC
    """)
    List<ScheduleOccurrence> findWithFilters(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("professorId") Integer professorId,
            @Param("roomId") Integer roomId,
            @Param("complexId") Integer complexId,
            @Param("status") ScheduleStatus status
    );

    @Query("""
        SELECT DISTINCT o.room.id
        FROM ScheduleOccurrence o
        WHERE o.scheduleDate = :date
          AND o.status = com.classsync.entity.ScheduleStatus.SCHEDULED
          AND o.startTime < :endTime
          AND o.endTime > :startTime
    """)
    List<Integer> findBusyRoomIds(
            @Param("date") LocalDate date,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime
    );

    @Query("""
        SELECT COUNT(o) > 0
        FROM ScheduleOccurrence o
        WHERE o.professor.id = :professorId
          AND o.scheduleDate = :date
          AND o.status = com.classsync.entity.ScheduleStatus.SCHEDULED
          AND o.startTime < :endTime
          AND o.endTime > :startTime
    """)
    boolean existsProfessorConflict(
            @Param("professorId") Integer professorId,
            @Param("date") LocalDate date,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime
    );

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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ScheduleOccurrence o WHERE o.id = :id")
    Optional<ScheduleOccurrence> findByIdWithLock(@Param("id") Integer id);

    @Query("""
        SELECT COUNT(o) > 0
        FROM ScheduleOccurrence o
        WHERE o.room.id = :roomId
          AND o.scheduleDate = :date
          AND o.status = com.classsync.entity.ScheduleStatus.SCHEDULED
          AND o.startTime < :endTime
          AND o.endTime > :startTime
          AND (:excludedOccurrenceId IS NULL OR o.id != :excludedOccurrenceId)
    """)
    boolean existsRoomConflictExcludingId(
            @Param("roomId") Integer roomId,
            @Param("date") LocalDate date,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime,
            @Param("excludedOccurrenceId") Integer excludedOccurrenceId
    );

    @Query("""
        SELECT COUNT(o) > 0
        FROM ScheduleOccurrence o
        WHERE o.professor.id = :professorId
          AND o.scheduleDate = :date
          AND o.status = com.classsync.entity.ScheduleStatus.SCHEDULED
          AND o.startTime < :endTime
          AND o.endTime > :startTime
          AND (:excludedOccurrenceId IS NULL OR o.id != :excludedOccurrenceId)
    """)
    boolean existsProfessorConflictExcludingId(
            @Param("professorId") Integer professorId,
            @Param("date") LocalDate date,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime,
            @Param("excludedOccurrenceId") Integer excludedOccurrenceId
    );

    @Query("""
        SELECT o.room.id, COUNT(o)
        FROM ScheduleOccurrence o
        WHERE o.room.id IN :roomIds
          AND o.scheduleDate BETWEEN :startDate AND :endDate
          AND o.status = com.classsync.entity.ScheduleStatus.SCHEDULED
        GROUP BY o.room.id
    """)
    List<Object[]> countScheduledOccurrencesForRooms(
            @Param("roomIds") Collection<Integer> roomIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT DISTINCT o.room.id
        FROM ScheduleOccurrence o
        WHERE o.professor.id = :professorId
          AND o.scheduleDate = :date
          AND o.status = com.classsync.entity.ScheduleStatus.SCHEDULED
          AND (o.endTime = :startTime OR o.startTime = :endTime)
    """)
    List<Integer> findAdjacentRoomIdsForProfessor(
            @Param("professorId") Integer professorId,
            @Param("date") LocalDate date,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime
    );
}
