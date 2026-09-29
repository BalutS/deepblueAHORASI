package com.deepblue.rescue.repository;

import com.deepblue.rescue.domain.Specialist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpecialistRepository extends JpaRepository<Specialist, Long> {

    // Paso 39 - JPQL: especialistas activos con determinada experiencia.
    // Nota (paso 40): JPQL usa nombres de ENTIDAD (Specialist, expertiseAreas),
    // no nombres de TABLA (specialists, specialist_expertise), porque JPQL
    // consulta el modelo de objetos mapeado, no las tablas físicas;
    // Hibernate traduce esos nombres a SQL real en tiempo de ejecución.
    @Query("""
            select distinct s
            from Specialist s
            join s.expertiseAreas e
            where s.active = true
              and lower(e.name) = lower(:expertiseName)
            order by s.lastName asc
            """)
    List<Specialist> findActiveByExpertise(@Param("expertiseName") String expertiseName);
}
