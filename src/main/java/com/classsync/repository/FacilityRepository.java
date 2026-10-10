package com.classsync.repository;

import com.classsync.entity.Facility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacilityRepository extends JpaRepository<Facility, Integer> {
    Optional<Facility> findByName(String name);

    List<Facility> findByActiveTrue();

    Optional<Facility> findByIdAndActiveTrue(Integer id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    List<Facility> findAllByIdInAndActiveTrue(Collection<Integer> ids);
}
