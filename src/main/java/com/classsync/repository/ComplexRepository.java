package com.classsync.repository;

import com.classsync.entity.Complex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplexRepository extends JpaRepository<Complex, Integer> {
    Optional<Complex> findByCode(String code);

    List<Complex> findByActiveTrue();

    Optional<Complex> findByIdAndActiveTrue(Integer id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Integer id);
}
