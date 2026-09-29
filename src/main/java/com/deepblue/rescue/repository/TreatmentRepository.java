package com.deepblue.rescue.repository;

import com.deepblue.rescue.domain.Treatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    // Paso 41 - Query Method: tratamientos de un animal, orden cronológico.
    List<Treatment> findByAnimalIdOrderByPerformedAtAsc(Long animalId);

    // Paso 42 - JPQL: tratamientos realizados entre dos fechas.
    @Query("""
            select t
            from Treatment t
            where t.performedAt between :start and :end
            order by t.performedAt asc
            """)
    List<Treatment> findBetweenDates(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    // Paso 43 - JPQL navegando Treatment -> Animal -> RescueCase -> RescueCenter.
    @Query("""
            select t
            from Treatment t
            where t.animal.rescueCase.rescueCenter.code = :centerCode
            order by t.performedAt asc
            """)
    List<Treatment> findByAnimalRescueCenterCode(@Param("centerCode") String centerCode);

    // Paso 44 - JPQL con N:M: tratamientos realizados por especialistas
    // con determinada experiencia.
    @Query("""
            select distinct t
            from Treatment t
            join t.specialist s
            join s.expertiseAreas e
            where lower(e.name) = lower(:expertiseName)
            order by t.performedAt asc
            """)
    List<Treatment> findBySpecialistExpertise(@Param("expertiseName") String expertiseName);
}
