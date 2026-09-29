package com.deepblue.rescue.repository;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.RescueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnimalRepository extends JpaRepository<Animal, Long> {

    // Consulta A - Paso 34
    Optional<Animal> findByAnimalCode(String animalCode);

    // Consulta B - Paso 34
    List<Animal> findByCommonNameContainingIgnoreCase(String commonName);

    // Paso 35 - Animal -> rescueCase -> status
    List<Animal> findByRescueCaseStatus(RescueStatus status);

    // Paso 36 - Animal -> rescueCase -> rescueCenter -> code
    List<Animal> findByRescueCaseRescueCenterCode(String centerCode);

    // Reto Sin Guía (Pasos 75-77): Animales en determinado status con al menos
    // un tratamiento realizado por un especialista con determinada experiencia.
    @Query("""
            select distinct a
            from Animal a
            join a.rescueCase c
            join a.treatments t
            join t.specialist s
            join s.expertiseAreas e
            where c.status = :status
              and lower(e.name) = lower(:expertiseName)
            """)
    List<Animal> findByRescueStatusAndSpecialistExpertise(
            @Param("status") RescueStatus status,
            @Param("expertiseName") String expertiseName);
}
