package com.classsync.repository;

import com.classsync.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {
    Optional<Room> findByComplexIdAndRoomNumber(Integer complexId, String roomNumber);

    List<Room> findByComplexIdAndActiveTrue(Integer complexId);

    Optional<Room> findByIdAndActiveTrue(Integer id);

    boolean existsByComplexIdAndRoomNumberIgnoreCase(Integer complexId, String roomNumber);

    boolean existsByComplexIdAndRoomNumberIgnoreCaseAndIdNot(Integer complexId, String roomNumber, Integer id);

    @Query("SELECT r FROM Room r JOIN FETCH r.complex LEFT JOIN FETCH r.facilities WHERE r.id = :id AND r.active = true")
    Optional<Room> findByIdWithDetailsAndActiveTrue(@Param("id") Integer id);

    @Query("SELECT r FROM Room r JOIN FETCH r.complex LEFT JOIN FETCH r.facilities WHERE r.id = :id")
    Optional<Room> findByIdWithDetails(@Param("id") Integer id);

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
    List<Room> findAvailableRooms(
            @Param("complexId") Integer complexId,
            @Param("capacity") Integer capacity,
            @Param("roomType") com.classsync.entity.RoomType roomType,
            @Param("busyRoomIds") java.util.Collection<Integer> busyRoomIds
    );

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdWithLock(@Param("id") Integer id);
}
