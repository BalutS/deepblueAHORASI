# DeepBlue Rescue — Capas de Persistencia y Servicio

## 1. Nombre del proyecto
DeepBlue Rescue

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

cd deepblue-rescue
mvn clean package -DskipTests


Para levantar la aplicación localmente apuntando a un PostgreSQL existente (asegurarse de tener las variables de entorno configuradas o usar los defaults):
mvn spring-boot:run


## 6. Instrucciones para ejecutar tests
Para ejecutar la suite completa de pruebas de integración contra PostgreSQL real vía Testcontainers:
cd deepblue-rescue
mvn clean test


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



## 11. Capa de servicio

Sobre la capa de persistencia se agregó una capa `Service` con la siguiente estructura:


Service (interface)  ->  ServiceImpl  ->  Repository  ->  Hibernate  ->  PostgreSQL
                              |
                              +-> Mapper (MapStruct)  ->  DTO (record)


| Paquete | Contenido |
|---|---|
| `dto.request` | `ChangeRescueStatusRequest`, `CreateTreatmentRequest` (records) |
| `dto.response` | `RescueCaseResponse`, `TreatmentResponse`, `AnimalResponse` (records) |
| `mapper` | `RescueCaseMapper`, `TreatmentMapper`, `AnimalMapper` (MapStruct, `componentModel = "spring"`) |
| `exception` | `ResourceNotFoundException` (el recurso no existe), `BusinessRuleException` (existe, pero la operación no está permitida) |
| `service` / `service.impl` | `RescueCaseService`, `TreatmentService`, `AnimalService` y sus implementaciones |

Decisiones de diseño: inyección por constructor, `@Transactional(readOnly = true)` a nivel de clase y `@Transactional` solo en las operaciones de escritura, `Optional` + `orElseThrow` con mensajes significativos y las entidades nunca salen de la capa de servicio (se retornan DTOs).

## 12. Reglas de negocio

**Cambio de estado de un caso (`RescueCaseService.changeStatus`)** — flujo único permitido:


ADMITTED -> UNDER_EVALUATION -> IN_REHABILITATION -> READY_FOR_RELEASE -> RELEASED


Cualquier otra transición (saltos, retrocesos, mismo estado, o salir de `RELEASED`/`CLOSED`) lanza `BusinessRuleException` y **nunca** llega a `save()`.

**Registro de tratamientos (`TreatmentService.register`)**, en este orden:

1. El animal debe existir (`ResourceNotFoundException`).
2. El especialista debe existir (`ResourceNotFoundException`).
3. El especialista debe estar activo (`BusinessRuleException`).
4. El animal debe tener caso de rescate y este no puede estar `RELEASED` ni `CLOSED` (`BusinessRuleException`).
5. La fecha del tratamiento no puede ser anterior a `rescueDate` (`BusinessRuleException`). El mismo día del rescate es válido.

**`AnimalService.canReceiveTreatment(animalCode)`** retorna `true` solo si el caso está en `UNDER_EVALUATION` o `IN_REHABILITATION`.

Repositories agregados para esta capa: `SpecialistRepository.findByProfessionalCode` y `TreatmentRepository.findByAnimalAnimalCodeOrderByPerformedAtAsc`.

## 13. Unit tests de la capa de servicio

Los tests (`RescueCaseServiceImplTest`, `TreatmentServiceImplTest`, `AnimalServiceImplTest`) usan JUnit 5 + Mockito + AssertJ, sin `@SpringBootTest`, sin PostgreSQL y sin Testcontainers: los repositories y mappers son mocks.

Para ejecutar solo los unit tests de servicio:


mvn clean test -Dtest='*ServiceImplTest'


`mvn clean test` (sin filtro) también ejecuta `PersistenceIntegrationTest`, que sí requiere Docker por Testcontainers.
