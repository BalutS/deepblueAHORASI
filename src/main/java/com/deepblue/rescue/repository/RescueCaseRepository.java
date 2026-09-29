package com.deepblue.rescue.repository;

import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RescueCaseRepository extends JpaRepository<RescueCase, Long> {

    // Consulta A - Paso 32
    Optional<RescueCase> findByCaseCode(String caseCode);

    // Consulta B - Paso 32
    List<RescueCase> findByStatusOrderByRescueDateAsc(RescueStatus status);

    // Consulta C - Pasos 32 y 33 (navegando RescueCase -> rescueCenter -> code)
    List<RescueCase> findByRescueCenterCode(String code);

    // Paso 37 - Query Method con fechas
    List<RescueCase> findByRescueDateAfterOrderByRescueDateDesc(LocalDate date);
}
