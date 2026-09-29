package com.deepblue.rescue;

import com.deepblue.rescue.domain.*;
import com.deepblue.rescue.repository.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Paso 45 - Container real de PostgreSQL levantado por Testcontainers.
@Testcontainers
@SpringBootTest
@Transactional
class PersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18-alpine")
                    .withDatabaseName("deepblue_test")
                    .withUsername("deepblue")
                    .withPassword("deepblue");

    // Paso 46 - Repositories e infraestructura inyectados.
    @Autowired
    private RescueCenterRepository rescueCenterRepository;

    @Autowired
    private RescueCaseRepository rescueCaseRepository;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private SpecialistRepository specialistRepository;

    @Autowired
    private ExpertiseRepository expertiseRepository;

    @Autowired
    private TreatmentRepository treatmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Paso 47 - Test de Flyway: el esquema fue creado por Flyway, no por Hibernate.
    @Test
    void flywayShouldHaveExecutedV1AndV2AndV3() {
        List<String> appliedVersions = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success = true",
                String.class
        );

        assertThat(appliedVersions).contains("1", "2", "3");
    }

    // Paso 48 - Test de métodos heredados
    @Test
    void testInheritedMethods() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean Center", "Santa Marta");
        RescueCenter saved = rescueCenterRepository.save(center);

        assertThat(saved.getId()).isNotNull();
        assertThat(rescueCenterRepository.findById(saved.getId())).isPresent();
        assertThat(rescueCenterRepository.existsById(saved.getId())).isTrue();
        assertThat(rescueCenterRepository.count()).isGreaterThanOrEqualTo(1);
    }

    // Paso 49 - Test relación 1:N
    @Test
    void testOneToManyRelationship() {
        RescueCenter center = new RescueCenter("DB-CAR-1N", "Caribbean 1N Center", "Santa Marta");
        RescueCase case1 = new RescueCase("RES-1N-001", LocalDate.now(), "Location 1", RescueStatus.ADMITTED);
        RescueCase case2 = new RescueCase("RES-1N-002", LocalDate.now(), "Location 2", RescueStatus.IN_REHABILITATION);

        center.addCase(case1);
        center.addCase(case2);

        rescueCenterRepository.save(center);
        rescueCaseRepository.save(case1);
        rescueCaseRepository.save(case2);

        List<RescueCase> cases = rescueCaseRepository.findByRescueCenterCode("DB-CAR-1N");
        assertThat(cases).hasSize(2);
        assertThat(cases).extracting(RescueCase::getRescueCenter)
                .allMatch(c -> c.getCode().equals("DB-CAR-1N"));
    }

    // Paso 50 - Test RescueCase 1:1 Animal
    @Test
    void testRescueCaseOneToOneAnimal() {
        RescueCenter center = rescueCenterRepository.save(new RescueCenter("DB-CAR-50", "Center 50", "City"));
        RescueCase rescueCase = new RescueCase("RES-2026-001", LocalDate.now(), "Beach 1", RescueStatus.ADMITTED);
        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-001", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        rescueCase.assignAnimal(animal);

        rescueCaseRepository.save(rescueCase);

        Optional<RescueCase> foundCase = rescueCaseRepository.findByCaseCode("RES-2026-001");
        assertThat(foundCase).isPresent();
        assertThat(foundCase.get().getAnimal()).isNotNull();
        assertThat(foundCase.get().getAnimal().getAnimalCode()).isEqualTo("AN-2026-001");
        assertThat(foundCase.get().getAnimal().getRescueCase().getCaseCode()).isEqualTo("RES-2026-001");
    }

    // Paso 51 - Test Animal 1:1 MedicalRecord (Cascade)
    @Test
    void testAnimalOneToOneMedicalRecordCascade() {
        RescueCenter center = rescueCenterRepository.save(new RescueCenter("DB-CAR-51", "Center 51", "City"));
        RescueCase rescueCase = new RescueCase("RES-2026-051", LocalDate.now(), "Beach 2", RescueStatus.ADMITTED);
        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-002", "Hawksbill Turtle", "Eretmochelys imbricata", AnimalSex.MALE);
        rescueCase.assignAnimal(animal);

        MedicalRecord record = new MedicalRecord(
                new BigDecimal("28.40"),
                "STABLE",
                "Left front flipper injury",
                "Observing recovery"
        );
        animal.assignMedicalRecord(record);

        rescueCaseRepository.save(rescueCase);

        Optional<Animal> foundAnimal = animalRepository.findByAnimalCode("AN-2026-002");
        assertThat(foundAnimal).isPresent();
        assertThat(foundAnimal.get().getMedicalRecord()).isNotNull();
        assertThat(foundAnimal.get().getMedicalRecord().getId()).isNotNull();
        assertThat(foundAnimal.get().getMedicalRecord().getInitialWeight()).isEqualTo(new BigDecimal("28.40"));
    }

    // Paso 52 - Test N:M Specialist <-> Expertise
    @Test
    void testManyToManySpecialistExpertise() {
        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        Expertise rehab = expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow();

        Specialist elena = new Specialist("SPEC-052", "Elena", "Vargas", "elena52@deepblue.org", true);
        elena.addExpertise(trauma);
        elena.addExpertise(rehab);

        specialistRepository.save(elena);

        Specialist found = specialistRepository.findById(elena.getId()).orElseThrow();
        assertThat(found.getExpertiseAreas()).hasSize(2);
        assertThat(found.getExpertiseAreas()).extracting(Expertise::getName)
                .contains("Trauma", "Rehabilitation");
    }

    // Paso 53 - Test Query Method simple
    @Test
    void testSimpleQueryMethodByStatus() {
        RescueCenter center = rescueCenterRepository.save(new RescueCenter("DB-CAR-53", "Center 53", "City"));

        RescueCase c1 = new RescueCase("RES-53-001", LocalDate.now(), "Loc 1", RescueStatus.IN_REHABILITATION);
        RescueCase c2 = new RescueCase("RES-53-002", LocalDate.now(), "Loc 2", RescueStatus.READY_FOR_RELEASE);
        RescueCase c3 = new RescueCase("RES-53-003", LocalDate.now(), "Loc 3", RescueStatus.IN_REHABILITATION);

        center.addCase(c1);
        center.addCase(c2);
        center.addCase(c3);

        rescueCaseRepository.saveAll(List.of(c1, c2, c3));

        List<RescueCase> rehabCases = rescueCaseRepository.findByStatusOrderByRescueDateAsc(RescueStatus.IN_REHABILITATION);
        assertThat(rehabCases).hasSize(2);
    }

    // Paso 54 - Test Query Method navegando relaciones
    @Test
    void testQueryMethodNavigatingRelations() {
        RescueCenter centerCar = rescueCenterRepository.save(new RescueCenter("DB-CAR-54", "Caribbean", "City A"));
        RescueCenter centerPac = rescueCenterRepository.save(new RescueCenter("DB-PAC-54", "Pacific", "City B"));

        RescueCase caseCar = new RescueCase("RES-CAR-54", LocalDate.now(), "Loc A", RescueStatus.ADMITTED);
        centerCar.addCase(caseCar);
        Animal animalCar = new Animal("AN-CAR-54", "Turtle A", "Species A", AnimalSex.FEMALE);
        caseCar.assignAnimal(animalCar);
        rescueCaseRepository.save(caseCar);

        RescueCase casePac = new RescueCase("RES-PAC-54", LocalDate.now(), "Loc B", RescueStatus.ADMITTED);
        centerPac.addCase(casePac);
        Animal animalPac = new Animal("AN-PAC-54", "Dolphin B", "Species B", AnimalSex.MALE);
        casePac.assignAnimal(animalPac);
        rescueCaseRepository.save(casePac);

        List<Animal> carAnimals = animalRepository.findByRescueCaseRescueCenterCode("DB-CAR-54");
        assertThat(carAnimals).hasSize(1);
        assertThat(carAnimals.get(0).getAnimalCode()).isEqualTo("AN-CAR-54");
    }

    // Paso 55 - Test JPQL de especialistas
    @Test
    void testJpqlActiveSpecialistsByExpertise() {
        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        Expertise rehab = expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow();
        Expertise mammals = expertiseRepository.findByNameIgnoreCase("Marine Mammals").orElseThrow();

        Specialist elena = new Specialist("SPEC-55-1", "Elena", "Vargas", "elena55@deepblue.org", true);
        elena.addExpertise(trauma);
        elena.addExpertise(rehab);

        Specialist mateo = new Specialist("SPEC-55-2", "Mateo", "Gomez", "mateo55@deepblue.org", true);
        mateo.addExpertise(mammals);
        mateo.addExpertise(rehab);

        Specialist sofia = new Specialist("SPEC-55-3", "Sofia", "Lopez", "sofia55@deepblue.org", true);
        sofia.addExpertise(trauma);

        specialistRepository.saveAll(List.of(elena, mateo, sofia));

        List<Specialist> traumaSpecialists = specialistRepository.findActiveByExpertise("Trauma");
        assertThat(traumaSpecialists).extracting(Specialist::getFirstName)
                .containsExactlyInAnyOrder("Elena", "Sofia");
    }

    // Pasos 56, 57, 58 - Test Treatment queries
    @Test
    void testTreatmentQueries() {
        RescueCenter center = rescueCenterRepository.save(new RescueCenter("DB-56", "Center 56", "City"));
        RescueCase c1 = new RescueCase("RES-56", LocalDate.now(), "Loc", RescueStatus.IN_REHABILITATION);
        center.addCase(c1);
        Animal animal = new Animal("AN-56", "Seal", "Species", AnimalSex.MALE);
        c1.assignAnimal(animal);
        rescueCaseRepository.save(c1);

        Specialist spec = new Specialist("SPEC-56", "John", "Doe", "john56@deepblue.org", true);
        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        spec.addExpertise(trauma);
        specialistRepository.save(spec);

        LocalDateTime t1Time = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime t2Time = LocalDateTime.of(2026, 8, 10, 10, 0);
        LocalDateTime t3Time = LocalDateTime.of(2026, 8, 20, 10, 0);

        Treatment tr1 = new Treatment(animal, spec, t1Time, TreatmentType.WOUND_CARE, "Care 1");
        Treatment tr2 = new Treatment(animal, spec, t2Time, TreatmentType.HYDRATION, "Care 2");
        Treatment tr3 = new Treatment(animal, spec, t3Time, TreatmentType.OBSERVATION, "Care 3");

        treatmentRepository.saveAll(List.of(tr1, tr2, tr3));

        // Paso 57: Chronological order
        List<Treatment> animalTreatments = treatmentRepository.findByAnimalIdOrderByPerformedAtAsc(animal.getId());
        assertThat(animalTreatments).hasSize(3);
        assertThat(animalTreatments.get(0).getPerformedAt()).isEqualTo(t1Time);
        assertThat(animalTreatments.get(2).getPerformedAt()).isEqualTo(t3Time);

        // Paso 58: Interval query
        List<Treatment> rangeTreatments = treatmentRepository.findBetweenDates(
                LocalDateTime.of(2026, 8, 5, 0, 0),
                LocalDateTime.of(2026, 8, 15, 23, 59)
        );
        assertThat(rangeTreatments).hasSize(1);
        assertThat(rangeTreatments.get(0).getPerformedAt()).isEqualTo(t2Time);
    }

    // Paso 59 - Test UNIQUE constraint
    @Test
    void testUniqueConstraintViolation() {
        RescueCenter center = rescueCenterRepository.save(new RescueCenter("DB-59", "Center 59", "City"));
        RescueCase c1 = new RescueCase("RES-59-1", LocalDate.now(), "Loc 1", RescueStatus.ADMITTED);
        RescueCase c2 = new RescueCase("RES-59-2", LocalDate.now(), "Loc 2", RescueStatus.ADMITTED);
        center.addCase(c1);
        center.addCase(c2);

        Animal a1 = new Animal("AN-100", "Turtle 1", "Species 1", AnimalSex.FEMALE);
        Animal a2 = new Animal("AN-100", "Turtle 2", "Species 2", AnimalSex.MALE);

        c1.assignAnimal(a1);
        c2.assignAnimal(a2);

        rescueCaseRepository.save(c1);

        assertThatThrownBy(() -> rescueCaseRepository.saveAndFlush(c2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Pasos 62-64 - Test V3 Tracking Device Code
    @Test
    void testTrackingDeviceCode() {
        RescueCenter center = rescueCenterRepository.save(new RescueCenter("DB-V3", "Center V3", "City"));
        RescueCase c = new RescueCase("RES-V3", LocalDate.now(), "Loc", RescueStatus.READY_FOR_RELEASE);
        center.addCase(c);
        Animal animal = new Animal("AN-V3", "Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        animal.setTrackingDeviceCode("GPS-TRK-999");
        c.assignAnimal(animal);

        rescueCaseRepository.save(c);

        Optional<Animal> found = animalRepository.findByAnimalCode("AN-V3");
        assertThat(found).isPresent();
        assertThat(found.get().getTrackingDeviceCode()).isEqualTo("GPS-TRK-999");
    }

    // Pasos 65, 66 - Reto Integrador
    @Test
    void testIntegratorChallenge() {
        // Setup scenarios: DeepBlue Caribbean receives a sea turtle
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
        RescueCase rescueCase = new RescueCase("RES-2026-100", LocalDate.of(2026, 8, 18), "Bahía Concha", RescueStatus.IN_REHABILITATION);
        center.addCase(rescueCase);

        Animal animal = new Animal("AN-2026-100", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE);
        rescueCase.assignAnimal(animal);

        MedicalRecord record = new MedicalRecord(
                new BigDecimal("27.80"),
                "STABLE",
                "Injury caused by fishing net",
                "Possible plastic ingestion"
        );
        animal.assignMedicalRecord(record);

        rescueCenterRepository.save(center);
        rescueCaseRepository.save(rescueCase);

        Expertise reptiles = expertiseRepository.findByNameIgnoreCase("Marine Reptiles").orElseThrow();
        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        Expertise rehab = expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow();

        Specialist elena = new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", true);
        elena.addExpertise(reptiles);
        elena.addExpertise(trauma);
        elena.addExpertise(rehab);

        specialistRepository.save(elena);

        Treatment t1 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 18, 10, 0), TreatmentType.WOUND_CARE, "Cleaning of left front flipper");
        Treatment t2 = new Treatment(animal, elena, LocalDateTime.of(2026, 8, 19, 11, 0), TreatmentType.HYDRATION, "Subcutaneous fluid therapy");

        treatmentRepository.saveAll(List.of(t1, t2));

        // Consulta 1: ¿Existe el caso RES-2026-100?
        Optional<RescueCase> q1Case = rescueCaseRepository.findByCaseCode("RES-2026-100");
        assertThat(q1Case).isPresent();

        // Consulta 2: Obtener todos los casos IN_REHABILITATION
        List<RescueCase> q2Cases = rescueCaseRepository.findByStatusOrderByRescueDateAsc(RescueStatus.IN_REHABILITATION);
        assertThat(q2Cases).extracting(RescueCase::getCaseCode).contains("RES-2026-100");

        // Consulta 3: Obtener animales pertenecientes a DB-CAR
        List<Animal> q3Animals = animalRepository.findByRescueCaseRescueCenterCode("DB-CAR");
        assertThat(q3Animals).extracting(Animal::getAnimalCode).contains("AN-2026-100");

        // Consulta 4: Buscar animales cuyo nombre común contenga "turtle" ignorando mayúsculas
        List<Animal> q4Animals = animalRepository.findByCommonNameContainingIgnoreCase("turtle");
        assertThat(q4Animals).extracting(Animal::getAnimalCode).contains("AN-2026-100");

        // Consulta 5: Obtener especialistas con experiencia "Trauma"
        List<Specialist> q5Specs = specialistRepository.findActiveByExpertise("Trauma");
        assertThat(q5Specs).extracting(Specialist::getProfessionalCode).contains("SPEC-001");

        // Consulta 6: Obtener todos los tratamientos de AN-2026-100 ordenados cronológicamente
        List<Treatment> q6Treatments = treatmentRepository.findByAnimalIdOrderByPerformedAtAsc(animal.getId());
        assertThat(q6Treatments).hasSize(2);
        assertThat(q6Treatments.get(0).getType()).isEqualTo(TreatmentType.WOUND_CARE);

        // Consulta 7: Obtener tratamientos realizados por especialistas con experiencia "Rehabilitation"
        List<Treatment> q7Treatments = treatmentRepository.findBySpecialistExpertise("Rehabilitation");
        assertThat(q7Treatments).hasSize(2);

        // Consulta 8: Obtener tratamientos realizados entre dos fechas
        List<Treatment> q8Treatments = treatmentRepository.findBetweenDates(
                LocalDateTime.of(2026, 8, 18, 0, 0),
                LocalDateTime.of(2026, 8, 18, 23, 59)
        );
        assertThat(q8Treatments).hasSize(1);
        assertThat(q8Treatments.get(0).getType()).isEqualTo(TreatmentType.WOUND_CARE);
    }

    // Pasos 75-77 - Reto Sin Guía Test
    @Test
    void testRetoSinGuiaQuery() {
        RescueCenter center = new RescueCenter("DB-RETO", "Center Reto", "City");
        RescueCase c1 = new RescueCase("RES-RETO-1", LocalDate.now(), "Loc 1", RescueStatus.IN_REHABILITATION);
        center.addCase(c1);
        Animal a1 = new Animal("AN-RETO-1", "Dolphin", "Species D", AnimalSex.MALE);
        c1.assignAnimal(a1);

        RescueCase c2 = new RescueCase("RES-RETO-2", LocalDate.now(), "Loc 2", RescueStatus.RELEASED);
        center.addCase(c2);
        Animal a2 = new Animal("AN-RETO-2", "Turtle", "Species T", AnimalSex.FEMALE);
        c2.assignAnimal(a2);

        rescueCenterRepository.save(center);
        rescueCaseRepository.saveAll(List.of(c1, c2));

        Specialist traumaSpec = new Specialist("SPEC-RETO-1", "Dr", "Trauma", "trauma@deepblue.org", true);
        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        traumaSpec.addExpertise(trauma);

        Specialist rehabSpec = new Specialist("SPEC-RETO-2", "Dr", "Rehab", "rehab@deepblue.org", true);
        Expertise rehab = expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow();
        rehabSpec.addExpertise(rehab);

        specialistRepository.saveAll(List.of(traumaSpec, rehabSpec));

        // Animal 1 received treatment from traumaSpec (has Trauma expertise) and is IN_REHABILITATION
        Treatment t1 = new Treatment(a1, traumaSpec, LocalDateTime.now(), TreatmentType.MEDICATION, "Meds");
        // Animal 2 received treatment from traumaSpec, but status is RELEASED
        Treatment t2 = new Treatment(a2, traumaSpec, LocalDateTime.now(), TreatmentType.OBSERVATION, "Obs");

        treatmentRepository.saveAll(List.of(t1, t2));

        List<Animal> result = animalRepository.findByRescueStatusAndSpecialistExpertise(
                RescueStatus.IN_REHABILITATION, "Trauma"
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAnimalCode()).isEqualTo("AN-RETO-1");
    }
}
