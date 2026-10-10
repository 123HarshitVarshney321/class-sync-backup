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
}
