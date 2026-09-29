# Análisis — Capa de servicio (DeepBlue Rescue)

Respuestas a los Checkpoints y a la Parte XIII del laboratorio.

## Checkpoint 1 — Entity ≠ DTO

- **Entity**: objeto que representa información **persistente**. Está mapeado a una tabla, lo gestiona Hibernate (estado managed/detached, proxies lazy, dirty checking) y su forma la dicta el modelo relacional.
- **DTO**: objeto que **transfiere** información entre capas. Es un contrato explícito, inmutable (`record`) y con la forma que necesita quien lo consume.

Por eso `Entity != DTO`: tienen razones distintas para cambiar. Si se usara la entidad como contrato, cualquier cambio de esquema (renombrar una columna, agregar una relación) rompería a los consumidores, y cualquier detalle interno quedaría expuesto.

## Checkpoint 2 — ¿Por qué esas reglas viven en el Service?

`¿specialist está activo?`, `¿el caso está RELEASED?` y `¿la fecha es válida?` son **reglas de negocio**: dependen de políticas de DeepBlue, combinan datos de varias entidades (animal + caso + especialista + request) y su incumplimiento se traduce en una `BusinessRuleException` con significado de dominio. El Repository solo sabe *cómo acceder a los datos*; no debe decidir si una operación está permitida ni lanzar excepciones de negocio.

## 58. Clasificar responsabilidades

| Necesidad | Capa |
|---|---|
| `SELECT` de RescueCase por código | **Repository** |
| Validar transición de status | **Service** |
| Convertir RescueCase a DTO | **Mapper** |
| Guardar Treatment | **Repository** (el Service decide *cuándo*; el Repository ejecuta el `save`) |
| Verificar especialista activo | **Service** |
| Crear tabla treatments | **Flyway** |
| Controlar transacción | **Service** |
| Representar información persistente | **Entity** |

## 59. ¿Por qué `if (!specialist.isActive())` no va en `SpecialistRepository`?

- El Repository es acceso a datos; una regla de negocio ahí mezcla responsabilidades.
- Un especialista inactivo es un dato válido: hay casos donde se consulta igual (reportes, historial, reactivarlo). La regla solo aplica *al registrar un tratamiento*, es decir, depende del caso de uso.
- La regla forma parte de un flujo con otras validaciones (animal, estado del caso, fecha) que solo el Service puede orquestar.
- Los repositories de Spring Data son interfaces sin cuerpo; no hay dónde poner esa lógica, y si existiera sería difícil de testear de forma aislada.
- La excepción de negocio (`BusinessRuleException`) no debe originarse en la capa de persistencia.

## 60. Transacciones

| Operación | Transacción |
|---|---|
| `findByCode()` | `@Transactional(readOnly = true)` |
| `findByStatus()` | `@Transactional(readOnly = true)` |
| `registerTreatment()` (`register`) | `@Transactional` (escritura) |
| `changeStatus()` | `@Transactional` (escritura) |

`readOnly = true` comunica la intención de solo lectura y permite optimizaciones (Hibernate omite dirty checking/flush). Las operaciones que modifican datos necesitan la transacción de escritura para que el cambio se confirme (o se revierta completo ante una excepción).

## 61. `orElseThrow(...)` vs `.get()`

`.get()` sobre un `Optional` vacío lanza `NoSuchElementException`, sin mensaje útil y sin significado de dominio: termina como un error genérico en lugar de un "no encontrado" controlado. Además oculta el caso vacío en el código (nada obliga a tratarlo) y los analizadores estáticos lo marcan como riesgo. `orElseThrow(() -> new ResourceNotFoundException("Rescue case not found: " + code))` deja explícito el caso ausente y produce un error con contexto que una futura capa REST podrá traducir a `404`.

## 62. ¿Por qué no retornar `RescueCase` desde el Service?

- **Acoplamiento**: las capas superiores dependerían del modelo persistente y de JPA.
- **Lazy Loading**: `rescueCenter` y `animal` son `LAZY`; con `open-in-view: false` acceder a ellos fuera de la transacción lanza `LazyInitializationException`. El DTO se llena *dentro* de la transacción del Service, con datos ya resueltos.
- **Contrato entre capas**: el DTO define exactamente qué se entrega (`centerCode`, `animalCode`), no el grafo completo.
- **Información expuesta**: una entidad arrastra campos y relaciones que el consumidor no debe ver, y sus relaciones bidireccionales (`RescueCase ↔ Animal`, `Animal ↔ Treatment`) provocan ciclos al serializar.
- **Evolución del modelo**: se puede cambiar el esquema o las entidades sin romper a los consumidores mientras el DTO siga igual; el `Mapper` absorbe la diferencia.
