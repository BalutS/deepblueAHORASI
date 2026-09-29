# DeepBlue Rescue — Capa de Persistencia

## 1. Nombre del proyecto
**DeepBlue Rescue** (`deepblue-rescue`)

## 2. Descripción breve
DeepBlue Rescue es una plataforma para organizaciones dedicadas al rescate y rehabilitación de fauna marina. Este proyecto implementa de manera robusta y completa la capa de persistencia utilizando Java 21, Spring Boot 4, Spring Data JPA, Hibernate ORM, Flyway y PostgreSQL ejecutado en contenedores mediante Testcontainers.

## 3. Modelo de datos
El modelo relacional está compuesto por 8 tablas diseñadas en PostgreSQL:
- `rescue_centers`: Registra los centros de rescate con código único, nombre y ciudad.
- `rescue_cases`: Registra los casos de rescate asociados a un centro, con ubicación, fecha de rescate y estado de rescate (`RescueStatus`).
- `animals`: Almacena la información del animal rescatado, incluyendo su código único, nombres común y científico, sexo y opcionalmente el código de dispositivo de rastreo GPS (`tracking_device_code`).
- `medical_records`: Expediente médico 1:1 con `animals`, registrando peso inicial, condición inicial, lesiones u observaciones.
- `specialists`: Especialistas médicos con código profesional único, nombre, apellido, email activo e historial de tratamientos.
- `expertise`: Catálogo de áreas de experiencia médica (ej. Trauma, Marine Reptiles, Rehabilitation).
- `specialist_expertise`: Tabla asociativa con clave primaria compuesta `(specialist_id, expertise_id)` que implementa la relación N:M entre especialista y áreas de experiencia.
- `treatments`: Tratamientos realizados a un animal por un especialista en una fecha determinada (`performed_at`), con tipo (`TreatmentType`) y descripción.

## 4. Relaciones
- `RescueCenter 1 ─── N RescueCase`: Un centro gestiona múltiples casos. FK en `rescue_cases.rescue_center_id`.
- `RescueCase 1 ─── 1 Animal`: Cada caso se relaciona de forma unívoca con un animal. FK `animals.rescue_case_id` con restricción `UNIQUE`.
- `Animal 1 ─── 1 MedicalRecord`: Cada animal posee un único expediente médico. FK `medical_records.animal_id` con restricción `UNIQUE`.
- `Animal 1 ─── N Treatment`: Un animal puede recibir múltiples tratamientos. FK en `treatments.animal_id`.
- `Specialist 1 ─── N Treatment`: Un especialista puede realizar múltiples tratamientos. FK en `treatments.specialist_id`.
- `Specialist N ─── M Expertise`: Un especialista puede poseer varias áreas de experiencia y un área de experiencia puede pertenecer a varios especialistas. Implementada mediante la tabla asociativa `specialist_expertise`.

## 5. Instrucciones para ejecutar
Para empaquetar el proyecto:
```bash
cd deepblue-rescue
mvn clean package -DskipTests
```

Para levantar la aplicación localmente apuntando a un PostgreSQL existente (asegurarse de tener las variables de entorno configuradas o usar los defaults):
```bash
mvn spring-boot:run
```

## 6. Instrucciones para ejecutar tests
Para ejecutar la suite completa de pruebas de integración contra PostgreSQL real vía Testcontainers:
```bash
cd deepblue-rescue
mvn clean test
```

## 7. Explicación de Flyway
Flyway es la herramienta de migración de base de datos encargada de versionar y evolucionar el esquema en PostgreSQL.
- Se configura `spring.jpa.hibernate.ddl-auto: validate` para garantizar que Hibernate únicamente valide las entidades contra el esquema generado previamente por Flyway, impidiendo que Hibernate altere las tablas automáticamente.
- Las migraciones ejecutadas son:
  1. `V1__create_schema.sql`: Crea las tablas, PK, FKs, restricciones `UNIQUE`, checks (`ck_rescue_cases_status`) e índices optimizados.
  2. `V2__insert_expertise_catalog.sql`: Puebla el catálogo inicial de áreas de experiencia.
  3. `V3__add_tracking_device_to_animal.sql`: Evoluciona la tabla `animals` agregando la columna `tracking_device_code VARCHAR(50) UNIQUE`.

## 8. Explicación de Testcontainers
Testcontainers permite ejecutar pruebas de integración reales sobre un contenedor efímero de PostgreSQL en lugar de bases de datos en memoria como H2.
- Utiliza la anotación `@Testcontainers` y `@ServiceConnection` junto a `PostgreSQLContainer("postgres:18-alpine")`.
- `@ServiceConnection` configura dinámicamente el `DataSource` de Spring Boot conectándolo al contenedor real, permitiendo probar constraints reales de PostgreSQL (`CHECK`, `FOREIGN KEY`, `UNIQUE`).

## 9. Listado de Query Methods implementados
- `RescueCenterRepository.findByCode(String code)`
- `RescueCaseRepository.findByCaseCode(String caseCode)`
- `RescueCaseRepository.findByStatusOrderByRescueDateAsc(RescueStatus status)`
- `RescueCaseRepository.findByRescueCenterCode(String code)` (Navegación de relación)
- `RescueCaseRepository.findByRescueDateAfterOrderByRescueDateDesc(LocalDate date)`
- `AnimalRepository.findByAnimalCode(String animalCode)`
- `AnimalRepository.findByCommonNameContainingIgnoreCase(String commonName)`
- `AnimalRepository.findByRescueCaseStatus(RescueStatus status)` (Navegación a través de `RescueCase`)
- `AnimalRepository.findByRescueCaseRescueCenterCode(String centerCode)` (Navegación a través de `RescueCase` -> `RescueCenter`)
- `ExpertiseRepository.findByNameIgnoreCase(String name)`
- `TreatmentRepository.findByAnimalIdOrderByPerformedAtAsc(Long animalId)`

## 10. Listado de consultas JPQL implementadas
- `SpecialistRepository.findActiveByExpertise(String expertiseName)`:
  Consulta especialistas activos asociados a un área de experiencia específica utilizando `JOIN` con `s.expertiseAreas`.
- `TreatmentRepository.findBetweenDates(LocalDateTime start, LocalDateTime end)`:
  Obtiene tratamientos en un rango de fechas ordenados de forma ascendente.
- `TreatmentRepository.findByAnimalRescueCenterCode(String centerCode)`:
  Recorre `Treatment -> Animal -> RescueCase -> RescueCenter` para filtrar tratamientos por el código del centro de rescate.
- `TreatmentRepository.findBySpecialistExpertise(String expertiseName)`:
  Filtra tratamientos realizados por especialistas con una determinada experiencia navegando N:M `Treatment -> Specialist -> expertiseAreas`.
- `AnimalRepository.findByRescueStatusAndSpecialistExpertise(RescueStatus status, String expertiseName)` (Reto Sin Guía):
  Recupera animales distintos (`DISTINCT`) en un determinado estado cuyo tratamiento haya sido realizado por un especialista con una experiencia en particular.
