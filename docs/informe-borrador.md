# Academic Events API — Informe Técnico (borrador)

## Portada

- **Proyecto**: Academic Events API — API REST para la gestión de eventos académicos
- **Asignatura**: Programación y Plataformas Web — Proyecto Integrador
- **Integrantes**:
  - [Nombre A]
  - [Nombre B]
- **Fecha**: [COMPLETAR]
- **Repositorio**: [COMPLETAR CON URL DEL REPOSITORIO]

---

## 1. Objetivo

Diseñar e implementar una API REST segura para la gestión integral de eventos académicos,
que permita administrar categorías, eventos, sesiones e inscripciones de participantes, con
control de acceso basado en roles (`ADMIN`, `ORGANIZER`, `PARTICIPANT`), autenticación mediante
JWT, límite de solicitudes (rate limiting) respaldado por Redis, auditoría de operaciones
críticas, y generación de reportes (PDF/XLSX) y estadísticas para organizadores y
administradores.

[COMPLETAR: objetivos específicos adicionales si el enunciado del proyecto integrador los exige explícitamente.]

---

## 2. Arquitectura

Monolito modular por dominio, bajo el paquete base `ec.edu.ups.academicevents`. Cada módulo de
negocio sigue la misma forma interna: `controller/`, `dto/`, `entity/` (si aplica), `mapper/`,
`repository/`, `service/` (interfaz + `*Impl`), y opcionalmente `validation/`. Todos los módulos
listados a continuación están completos y funcionando.

```
src/main/java/ec/edu/ups/academicevents/
├── AcademicEventsApiApplication.java
├── auth/                      # registro, login, refresh, logout, /me
│   ├── controller/AuthController.java
│   ├── dto/                   # LoginRequest, RegisterRequest, RefreshRequest,
│   │                           # TokenResponse, AuthUserResponse
│   ├── entity/RefreshToken.java
│   ├── repository/RefreshTokenRepository.java
│   └── service/{AuthService, AuthServiceImpl}.java
├── users/                     # administración de usuarios (solo ADMIN)
│   ├── controller/UserController.java
│   ├── dto/                   # UserResponse, UserRolesRequest, UserStatusRequest
│   ├── entity/{User, UserRole, UserRoleId}.java
│   ├── mapper/UserMapper.java
│   ├── repository/{UserRepository, UserRoleRepository}.java
│   └── service/{UserService, UserServiceImpl}.java
├── roles/                     # catálogo de roles (solo lectura, ADMIN)
│   ├── controller/RoleController.java
│   ├── dto/RoleResponse.java
│   ├── entity/Role.java
│   ├── repository/RoleRepository.java
│   └── service/{RoleService, RoleServiceImpl}.java
├── categories/                # catálogo de categorías de eventos
│   ├── controller/CategoryController.java
│   ├── dto/{CategoryRequest, CategoryResponse}.java
│   ├── entity/Category.java
│   ├── mapper/CategoryMapper.java
│   ├── repository/CategoryRepository.java
│   └── service/{CategoryService, CategoryServiceImpl}.java
├── events/                    # eventos académicos
│   ├── controller/EventController.java
│   ├── dto/{EventRequest, EventResponse, EventStatusRequest}.java
│   ├── entity/Event.java
│   ├── mapper/EventMapper.java
│   ├── repository/EventRepository.java
│   ├── service/{EventService, EventServiceImpl}.java
│   └── validation/            # ValidEventDates, ValidEventModality + validadores
├── sessions/                  # sesiones/charlas de un evento
│   ├── controller/SessionController.java
│   ├── dto/{SessionRequest, SessionResponse}.java
│   ├── entity/Session.java
│   ├── mapper/SessionMapper.java
│   ├── repository/SessionRepository.java
│   ├── service/{SessionService, SessionServiceImpl}.java
│   └── validation/            # ValidSessionDates + validador
├── registrations/              # inscripciones de participantes a eventos
│   ├── controller/RegistrationController.java
│   ├── dto/                   # RegistrationRequest, RegistrationResponse,
│   │                           # RegistrationStatusRequest
│   ├── entity/Registration.java
│   ├── mapper/RegistrationMapper.java
│   ├── repository/RegistrationRepository.java
│   └── service/{RegistrationService, RegistrationServiceImpl}.java
├── reports/                   # reportes PDF/XLSX y estadísticas
│   ├── controller/ReportController.java
│   ├── dto/                   # CertificateData, EventReportHeader, RegistrantRow,
│   │                           # ReportFile, StatsSummaryResponse, StatusCount
│   ├── exception/{InvalidReportRangeException, ReportExceptionHandler}.java
│   ├── generator/{PdfGenerator, ExcelGenerator}.java
│   ├── repository/ReportQueryRepository.java   # consultas JPQL de solo lectura
│   └── service/{ReportService, ReportServiceImpl}.java
└── shared/                    # transversal a todos los módulos
    ├── audit/                 # AuditLog, AuditLogRepository, AuditPayloads, AuditService
    ├── config/                 # CorsConfig, OpenApiConfig, RedisConfig,
    │                           # SecurityConfig, SwaggerSecurityConfig
    ├── exception/              # ErrorCode, ApiErrorResponse, GlobalExceptionHandler
    │                           # + excepciones de negocio
    ├── ratelimit/              # LoginAttemptService, RateLimitFilter, RateLimitPolicy,
    │                           # RateLimitResult, RedisRateLimiter
    └── security/                # JwtService, JwtAuthenticationFilter, SecurityUtils,
                                  # CustomUserDetails(Service), AuthenticatedPrincipal, ...
```

**Stack**: Java 25 + Spring Boot 4.1.0, PostgreSQL 16, Redis 7, JWT (`jjwt` 0.12.6), Flyway,
Springdoc OpenAPI, Gradle (Kotlin DSL) con JaCoCo.

---

## 3. Modelo de datos

El esquema completo (9 tablas: `roles`, `users`, `user_roles`, `categories`, `events`,
`sessions`, `registrations`, `refresh_tokens`, `audit_logs`) se crea desde la única migración
Flyway `src/main/resources/db/migration/V1__initial_schema_and_data.sql`, que también incluye
los datos semilla.

El diagrama entidad-relación completo, con columnas, tipos, llaves primarias/foráneas y
cardinalidad de cada relación, está versionado en **[`docs/erd.dbml`](./erd.dbml)** (formato
DBML, visualizable en [dbdiagram.io](https://dbdiagram.io)). No se duplica aquí para evitar que
el informe y el diagrama queden desincronizados; cualquier cambio de esquema debe reflejarse
primero en la migración y luego en `erd.dbml`.

---

## 4. Decisiones de diseño encontradas en el código

Esta sección documenta decisiones reales, verificables en el código y la configuración del
proyecto — no supuestos de diseño "ideal".

### 4.1 Flyway (`ddl-auto: validate`) conviviendo con Hibernate
`application.yml` fija `spring.jpa.hibernate.ddl-auto: validate` y `spring.flyway.baseline-on-migrate: true`.
Esto significa que **Flyway es la única fuente de verdad del esquema** (crea y versiona todas
las tablas mediante `V1__initial_schema_and_data.sql`); Hibernate nunca genera ni modifica DDL,
solo valida en el arranque que el mapeo de las entidades JPA coincida exactamente con las
tablas ya creadas por la migración. Esto evita el riesgo típico de que `ddl-auto: update` derive
en un esquema distinto entre entornos.

### 4.2 Concurrencia optimista (`@Version`) en `Event` y `Registration`
Tanto `Event` como `Registration` tienen una columna `version BIGINT NOT NULL DEFAULT 0`
mapeada con `@Version` (JPA). Se eligió **bloqueo optimista** en vez de bloqueo pesimista
(`SELECT ... FOR UPDATE`) para dos operaciones especialmente sensibles a condiciones de carrera:
confirmar una inscripción y descontar el cupo disponible de un evento. Ante una escritura
concurrente, Hibernate lanza `OptimisticLockingFailureException`, que
`GlobalExceptionHandler.handleOptimisticLocking` traduce a `409 Conflict` con el código
`CONCURRENT_UPDATE`, pidiendo al cliente reintentar con los datos actualizados. Esta elección
prioriza el rendimiento bajo baja-a-media contención frente a la exclusión estricta que exigiría
un lock pesimista.

### 4.3 `REDIS_URL` consolidado vs. variables separadas
El proyecto soporta **dos formas válidas** de configurar Redis, aplicadas en perfiles distintos:
- `application-dev.yml` usa `spring.data.redis.host` + `spring.data.redis.port`
  (variables `REDIS_HOST` / `REDIS_PORT` por separado).
- `application-prod.yml` usa `spring.data.redis.url` (variable **`REDIS_URL` consolidada**),
  que es también la que expone `.env.example` para desarrollo local con Docker Compose.

`render.yaml` declara ambas familias de variables (`REDIS_URL`, `REDIS_HOST`, `REDIS_PORT`,
`REDIS_PASSWORD`) como placeholders `sync: false`, dejando a quien despliega la libertad de usar
el formato que le entregue su proveedor de Redis administrado.

### 4.4 Catálogo de roles paginado
El catálogo de roles (`roles`) tiene solo 3 filas fijas (`ADMIN`, `ORGANIZER`, `PARTICIPANT`,
insertadas por la migración) y no crece dinámicamente — no existe ningún endpoint para crear,
editar o eliminar roles. Pese a eso, `RoleService.findAll(Pageable)` / `GET /api/roles` sigue el
mismo contrato paginado (`Page<RoleResponse>`) que el resto de los listados del proyecto
(`categories`, `events`, `users`, etc.), priorizando la **consistencia de la API** sobre el
ahorro marginal de no paginar un catálogo tan pequeño.

[COMPLETAR: agregar aquí cualquier otra decisión de diseño relevante que el equipo quiera
documentar — por ejemplo, borrado lógico vs. físico por módulo (`events`/`categories` usan
`deleted`/`active`; `sessions` borra físicamente), o el uso de `Specification<T>` inline en vez
de una capa de specifications separada.]

---

## 5. Seguridad (resumen)

*(Ver el código para el detalle completo: `shared/security/`, `shared/ratelimit/`,
`shared/config/SecurityConfig.java`. Aquí solo se resume el flujo, sin reproducir el código.)*

- **Autenticación JWT**: `POST /api/auth/login` valida credenciales (BCrypt) y emite un access
  token de corta duración (15 min por defecto) y un refresh token de larga duración (7 días por
  defecto), ambos firmados HS256 con `JWT_SECRET`. El refresh token se persiste (hasheado) en la
  tabla `refresh_tokens` para poder revocarlo en `logout` o rotarlo en `refresh`.
- **Autorización**: `SecurityConfig` define las rutas públicas (`permitAll`) y exige
  autenticación para el resto; la autorización fina por rol usa `@PreAuthorize` en los
  controladores, y la propiedad del recurso (p. ej. "solo el organizador dueño del evento") se
  valida dentro de los `*ServiceImpl` con `SecurityUtils`.
- **Redis para rate limiting**: un script Lua atómico (`rate_limit.lua`, `INCR` + `EXPIRE`)
  implementa una ventana fija por clave (`RateLimitPolicy`), con límites distintos para login,
  registro, tráfico público, tráfico autenticado y descarga de reportes. Las solicitudes que
  exceden el límite reciben `429` con el header `Retry-After`.
- **Bloqueo por fuerza bruta**: `LoginAttemptService` bloquea temporalmente (15 min) una cuenta
  tras 5 intentos de login fallidos para el mismo correo, independientemente del rate limiting
  por IP.
- **Auditoría**: `AuditService` registra en la tabla `audit_logs` las operaciones críticas
  (cambios de estado, creación/edición de categorías y eventos, cambios de rol/estado de
  usuarios, etc.), con actor, acción, valores antes/después y metadatos de la petición —
  explícitamente sin almacenar contraseñas ni tokens.

---

## 6. Pruebas

- Suite de pruebas unitarias con JUnit 5 + Mockito + AssertJ sobre los `*ServiceImpl` de todos
  los módulos de negocio.
- Ejecución: `./gradlew test` (requiere Postgres y Redis levantados con `docker compose up -d`,
  ya que `AcademicEventsApiApplicationTests` levanta el `ApplicationContext` completo contra
  servicios reales en `localhost`).
- Reporte de cobertura: `./gradlew jacocoTestReport` → `build/reports/jacoco/test/html/index.html`.

**Resultado de la suite de tests**: [COMPLETAR CON CAPTURA DEL RESULTADO DE `./gradlew test`
— número de tests ejecutados, aprobados y fallidos]

**Cobertura JaCoCo**: [COMPLETAR CON CAPTURA DEL REPORTE JACOCO — porcentaje de cobertura de
líneas/ramas por paquete; no se reproduce ninguna cifra aquí porque no se ejecutó la suite como
parte de la generación de este informe]

---

## 7. Despliegue

- **Contenerización**: `Dockerfile` multi-stage — build con `gradle:jdk25` (`bootJar -x test`),
  imagen final sobre `eclipse-temurin:25-jre-alpine` corriendo con un usuario no root, puerto
  `8080` expuesto y `JAVA_TOOL_OPTIONS` ajustado para limitar memoria/GC en el contenedor.
- **Render**: `render.yaml` define un único servicio web (`runtime: docker`, plan `free`),
  `healthCheckPath: /actuator/health`, perfil `SPRING_PROFILES_ACTIVE=prod`. Todas las variables
  sensibles (`DB_*`, `REDIS_*`, `JWT_*`, `ALLOWED_ORIGINS`, `SWAGGER_*`) se declaran con
  `sync: false`, es decir, se completan manualmente en el panel de Render y no se versionan.
- **Postgres y Redis**: no se definen como `services:` adicionales en `render.yaml`; se
  crearon como **servicios administrados separados directamente en el panel de Render**, y sus
  credenciales se pegaron a mano en las variables de entorno del servicio web.
- **Swagger en producción**: protegido con Basic Auth (`SwaggerSecurityConfig`, perfil `prod`),
  usando `SWAGGER_USER` / `SWAGGER_PASSWORD`.

---

## 8. Conclusiones

[COMPLETAR: conclusiones del equipo sobre el resultado del proyecto integrador — qué se cumplió
del alcance planteado, qué tan cerca quedó el diseño final del diseño inicial, aprendizajes
técnicos relevantes.]

## 9. Recomendaciones / trabajo futuro

[COMPLETAR: recomendaciones para una siguiente iteración — por ejemplo, extraer las
`Specification<T>` inline de `EventServiceImpl`/`CategoryServiceImpl`/`UserServiceImpl` a clases
dedicadas, agregar un endpoint de listado de inscritos accesible al organizador sin pasar por
`reports`, evaluar si `GET /api/events` debería exponer un flag explícito para el listado
público en vez de depender de que el cliente conozca la restricción de visibilidad, etc.]
