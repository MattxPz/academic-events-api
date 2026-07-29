# Academic Events API

API REST para la gestión de eventos académicos: categorías, eventos, sesiones, inscripciones,
reportes y administración de usuarios/roles, con autenticación JWT, rate limiting sobre Redis
y auditoría de operaciones críticas. Proyecto Integrador — Programación y Plataformas Web.

**Enlaces en vivo**
- API desplegada: [URL_RENDER]
- Documentación Swagger / OpenAPI: [URL_SWAGGER]

---

## 1. Stack tecnológico

| Componente | Versión / detalle |
|---|---|
| Lenguaje | Java 25 (toolchain de Gradle) |
| Framework | Spring Boot 4.1.0 |
| Base de datos | PostgreSQL 16 (imagen `postgres:16-alpine` en desarrollo) |
| Caché / rate limiting | Redis 7 (imagen `redis:7-alpine` en desarrollo) |
| Autenticación | JWT (access + refresh token) vía `io.jsonwebtoken:jjwt` 0.12.6 |
| Migraciones | Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`) |
| Documentación de API | Springdoc OpenAPI (`springdoc-openapi-starter-webmvc-ui` 3.0.3) |
| Seguridad | Spring Security (JWT stateless + Basic Auth solo para Swagger en prod) |
| Reportes | OpenPDF 2.0.3 (PDF) y Apache POI 5.4.0 (XLSX) |
| Build | Gradle (Kotlin DSL), plugin JaCoCo para cobertura |
| Tests | JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`) |

---

## 2. Arquitectura

Monolito modular por dominio bajo el paquete base `ec.edu.ups.academicevents`. Cada módulo
sigue la misma forma interna: `controller/`, `dto/`, `entity/` (si aplica), `mapper/`,
`repository/`, `service/` (interfaz + `*Impl`), y opcionalmente `validation/`.

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
├── events/                    # eventos académicos (completo)
│   ├── controller/EventController.java
│   ├── dto/{EventRequest, EventResponse, EventStatusRequest}.java
│   ├── entity/Event.java
│   ├── mapper/EventMapper.java
│   ├── repository/EventRepository.java
│   ├── service/{EventService, EventServiceImpl}.java
│   └── validation/            # ValidEventDates, ValidEventModality + validadores
├── sessions/                  # sesiones/charlas de un evento (completo)
│   ├── controller/SessionController.java
│   ├── dto/{SessionRequest, SessionResponse}.java
│   ├── entity/Session.java
│   ├── mapper/SessionMapper.java
│   ├── repository/SessionRepository.java
│   ├── service/{SessionService, SessionServiceImpl}.java
│   └── validation/            # ValidSessionDates + validador
├── registrations/             # inscripciones de participantes a eventos
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
    │                           # + excepciones de negocio (BusinessRuleException,
    │                           # DuplicateResourceException, ResourceNotFoundException, ...)
    ├── ratelimit/              # LoginAttemptService, RateLimitFilter, RateLimitPolicy,
    │                           # RateLimitResult, RedisRateLimiter
    └── security/                # JwtService, JwtAuthenticationFilter, SecurityUtils,
                                  # CustomUserDetails(Service), AuthenticatedPrincipal, ...
```

Recursos no versionados en Java:
- `src/main/resources/db/migration/V1__initial_schema_and_data.sql` — única migración Flyway (esquema + datos semilla).
- `src/main/resources/scripts/rate_limit.lua` — script Lua atómico (INCR + EXPIRE) usado por `RedisRateLimiter`.
- `src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`.
- `scripts/00_create_database.sql` — creación manual de la base (fuera de Flyway).

---

## 3. Modelo de datos

El esquema se crea íntegramente desde la migración Flyway `V1__initial_schema_and_data.sql`
(no hay migraciones adicionales). Contiene **9 tablas**:

| # | Tabla | Propósito |
|---|---|---|
| 1 | `roles` | Catálogo fijo de roles: `ADMIN`, `ORGANIZER`, `PARTICIPANT`. |
| 2 | `users` | Cuentas de usuario (`status`: `ACTIVE` / `BLOCKED`). |
| 3 | `user_roles` | Relación N:M entre `users` y `roles`. |
| 4 | `categories` | Categorías de eventos, con borrado lógico (`active`). |
| 5 | `events` | Eventos académicos (estado, capacidad, organizador, categoría). |
| 6 | `sessions` | Sesiones/charlas que pertenecen a un evento (`event_id`). |
| 7 | `registrations` | Inscripciones de un participante a un evento. |
| 8 | `refresh_tokens` | Refresh tokens JWT emitidos, para poder revocarlos. |
| 9 | `audit_logs` | Auditoría de operaciones críticas (actor, acción, valores antes/después). |

El diagrama entidad-relación versionado del proyecto vive en **`docs/erd.dbml`**
(formato [dbdiagram.io](https://dbdiagram.io)). *Nota: al momento de generar este README ese
archivo no existe todavía en el repositorio — crearlo/actualizarlo junto con cualquier cambio
de esquema.*

---

## 4. Instalación local

**Requisitos previos**: JDK 25, Docker Desktop.

1. Levantar Postgres y Redis con Docker Compose:
   ```bash
   docker compose up -d
   ```
   Esto crea los contenedores `postgres-dev-events` (Postgres 16, puerto 5432) y
   `redis-dev-events` (Redis 7, puerto 6379) definidos en `docker-compose.yml`.

2. Crear la base de datos ejecutando el script manual (no es parte de Flyway):
   ```bash
   docker exec -i postgres-dev-events psql -U ups -d postgres < scripts/00_create_database.sql
   ```

3. Copiar `.env.example` a `.env` y completar los valores:
   ```bash
   cp .env.example .env
   ```
   El proyecto usa [spring-dotenv](https://github.com/paulschwarz/spring-dotenv)
   (`developmentOnly("me.paulschwarz:springboot4-dotenv:5.1.0")`) para cargar `.env`
   automáticamente al arrancar; no hace falta exportar las variables a mano.

4. Ejecutar la aplicación:
   ```bash
   ./gradlew bootRun
   ```
   Con `ddl-auto: validate` en `application.yml`, Flyway se encarga de crear el resto del
   esquema (`V1__initial_schema_and_data.sql`, incluidos los datos semilla) automáticamente
   al arrancar contra la base ya creada en el paso 2. Hibernate solo valida que el esquema
   resultante coincida con las entidades — nunca lo genera ni lo modifica.

---

## 5. Variables de entorno

Tabla completa según `.env.example` y `render.yaml`:

| Variable | Usada en | Descripción | Valor de ejemplo / default |
|---|---|---|---|
| `DB_URL` | local + Render | URL JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/academic_events_db` |
| `DB_USERNAME` | local + Render | Usuario de la base de datos | `ups` |
| `DB_PASSWORD` | local + Render | Contraseña de la base de datos | `ups123` |
| `REDIS_URL` | local + Render (perfil `prod`) | URL de conexión consolidada a Redis (`redis://host:puerto`) | `redis://localhost:6379` |
| `REDIS_HOST` | Render (placeholder) / perfil `dev` | Host de Redis, alternativa a `REDIS_URL` | `localhost` (default en `application-dev.yml`) |
| `REDIS_PORT` | Render (placeholder) / perfil `dev` | Puerto de Redis, alternativa a `REDIS_URL` | `6379` (default en `application-dev.yml`) |
| `REDIS_PASSWORD` | Render (placeholder) | Contraseña de Redis si el proveedor la exige | — |
| `JWT_SECRET` | local + Render | Secreto Base64 (≥32 bytes) para firmar los JWT con HS256 | *(placeholder, generar uno propio)* |
| `JWT_ACCESS_EXPIRATION` | local + Render | Expiración del access token, en milisegundos | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION` | local + Render | Expiración del refresh token, en milisegundos | `604800000` (7 días) |
| `ALLOWED_ORIGINS` | local + Render | Orígenes permitidos por CORS, separados por coma | `http://localhost:5173` |
| `SWAGGER_USER` | local + Render (perfil `prod`) | Usuario Basic Auth para proteger Swagger en producción | `admin` |
| `SWAGGER_PASSWORD` | local + Render (perfil `prod`) | Contraseña Basic Auth para Swagger en producción | *(placeholder, cambiar)* |
| `PORT` | local + Render | Puerto en el que escucha la aplicación | `8080` |

> **Nota sobre Redis**: el proyecto soporta **dos formas válidas** de configurar la conexión,
> según el perfil activo:
> - `application-dev.yml` usa `REDIS_HOST` + `REDIS_PORT` por separado
>   (`spring.data.redis.host` / `spring.data.redis.port`).
> - `application-prod.yml` usa la variable **consolidada `REDIS_URL`**
>   (`spring.data.redis.url`), que es también la que expone `.env.example` para desarrollo
>   local con Docker Compose.
>
> `render.yaml` declara las cuatro variables (`REDIS_URL`, `REDIS_HOST`, `REDIS_PORT`,
> `REDIS_PASSWORD`) como placeholders `sync: false`, para que quien despliegue elija el
> formato que le entregue su proveedor de Redis administrado.

---

## 6. Endpoints por módulo

Todas las rutas de negocio cuelgan de `/api/**`. Documentación interactiva disponible en
`/swagger-ui.html` (`/v3/api-docs` para el JSON OpenAPI).

### Auth (`/api/auth`) — `AuthController`
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | Público | Registra un usuario con rol `PARTICIPANT` por defecto. |
| POST | `/api/auth/login` | Público | Valida credenciales y emite access + refresh token. |
| POST | `/api/auth/refresh` | Público | Cambia un refresh token válido por un nuevo par de tokens. |
| POST | `/api/auth/logout` | Autenticado (cualquier rol) | Revoca el refresh token indicado; idempotente. |
| GET | `/api/auth/me` | Autenticado (cualquier rol) | Datos del usuario del token actual. |

---

### Categorías (`/api/categories`) — `CategoryController`
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/categories` | Público | Lista paginada, filtro por texto e `includeInactive`. |
| GET | `/api/categories/{id}` | Público | Detalle de una categoría. |
| POST | `/api/categories` | ADMIN | Crear categoría. |
| PUT | `/api/categories/{id}` | ADMIN | Actualizar nombre/descripción. |
| DELETE | `/api/categories/{id}` | ADMIN | Borrado lógico (`active=false`), idempotente. |

---

### Eventos (`/api/events`) — `EventController`
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/events` | Público (visibilidad restringida) | Búsqueda paginada por texto, categoría, modalidad, estado y rango de fechas; ver §8. |
| GET | `/api/events/me` | ORGANIZER | Eventos propios del organizador autenticado, incluidos los DRAFT. |
| GET | `/api/events/{id}` | Público (visibilidad restringida) | Detalle de un evento. |
| POST | `/api/events` | ADMIN, ORGANIZER | Crea un evento en estado `DRAFT`. |
| PUT | `/api/events/{id}` | ADMIN, dueño ORGANIZER | Actualiza datos del evento (no toca estado ni cupos). |
| PATCH | `/api/events/{id}/status` | ADMIN, dueño ORGANIZER | Transición de estado (`DRAFT→PUBLISHED/CANCELLED`, `PUBLISHED→FINISHED/CANCELLED`). |
| DELETE | `/api/events/{id}` | ADMIN, dueño ORGANIZER | Borrado lógico; rechaza (409) si el evento está `PUBLISHED` con inscripciones `CONFIRMED`. |

---

### Sesiones — `SessionController` (rutas explícitas, sin prefijo de clase)
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/events/{eventId}/sessions` | Público | Sesiones del evento, ordenadas por fecha de inicio. |
| POST | `/api/events/{eventId}/sessions` | ADMIN, dueño ORGANIZER del evento | Crea una sesión dentro del rango del evento. |
| GET | `/api/sessions/{id}` | Público | Detalle de una sesión. |
| PUT | `/api/sessions/{id}` | ADMIN, dueño ORGANIZER del evento | Actualiza una sesión (dentro del rango del evento). |
| DELETE | `/api/sessions/{id}` | ADMIN, dueño ORGANIZER del evento | Borrado físico de la sesión. |

#### Decisiones de diseño

El listado de sesiones por evento devuelve una lista completa, no paginada — el volumen esperado por evento (2-5 sesiones en los datos semilla) no justifica paginación; se prioriza simplicidad.

---

### Inscripciones (`/api/registrations`) — `RegistrationController`
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/registrations` | PARTICIPANT | Solicita inscripción (queda en `PENDING`). |
| GET | `/api/registrations/me` | Autenticado | Inscripciones propias, paginadas, filtro por estado. |
| GET | `/api/registrations/{id}` | Autenticado (dueño, organizador del evento o ADMIN) | Detalle de una inscripción. |
| PATCH | `/api/registrations/{id}/cancel` | Autenticado (dueño o ADMIN) | Cancela la inscripción; libera cupo si estaba `CONFIRMED`. |
| PATCH | `/api/registrations/{id}/status` | ADMIN, ORGANIZER | Confirma o rechaza; confirmar descuenta cupo. |
| GET | `/api/registrations/event/{eventId}` | Autenticado (dueño ORGANIZER del evento o ADMIN) | Inscripciones de un evento, paginadas, filtro por estado. |
| GET | `/api/registrations` | ADMIN | Listado administrativo, filtros por `eventId` y `status`. |

---

### Reportes — `ReportController` (rutas explícitas, sin prefijo de clase)
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/reports/events/{eventId}/registrations.pdf` | Autenticado (dueño ORGANIZER del evento o ADMIN) | Listado de inscritos en PDF. |
| GET | `/api/reports/events/{eventId}/registrations.xlsx` | Autenticado (dueño ORGANIZER del evento o ADMIN) | Listado de inscritos en Excel. |
| GET | `/api/registrations/{id}/certificate.pdf` | Autenticado (dueño PARTICIPANT o ADMIN) | Certificado PDF; solo si la inscripción está `CONFIRMED`. |
| GET | `/api/reports/stats/summary` | ORGANIZER, ADMIN | Resumen estadístico (últimos 30 días si no se pasan fechas); ADMIN ve todo, ORGANIZER solo lo propio. |

---

### Usuarios (`/api/users`) — `UserController` (clase completa bajo `@PreAuthorize("hasRole('ADMIN')")`)
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/users` | ADMIN | Lista paginada, filtro por texto y `status`. |
| GET | `/api/users/{id}` | ADMIN | Detalle de un usuario. |
| PATCH | `/api/users/{id}/status` | ADMIN | Cambia el estado (`ACTIVE`/`BLOCKED`). |
| PUT | `/api/users/{id}/roles` | ADMIN | Reemplaza todos los roles del usuario. |

---

### Roles (`/api/roles`) — `RoleController`
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/roles` | ADMIN | Lista paginada de roles disponibles. |

---

## 7. Matriz de permisos por rol y recurso

| Recurso | Anónimo | PARTICIPANT | ORGANIZER | ADMIN |
|---|---|---|---|---|
| **Categorías** — lectura | ✅ | ✅ | ✅ | ✅ |
| **Categorías** — crear/editar/eliminar | ❌ | ❌ | ❌ | ✅ |
| **Eventos** — lectura | ✅ (solo `PUBLISHED`) | ✅ (`PUBLISHED` + propios si aplica) | ✅ (`PUBLISHED` + los propios en cualquier estado) | ✅ (todos los estados) |
| **Eventos** — `GET /api/events/me` | ❌ | ❌ | ✅ (los propios) | ❌ *(la ruta exige el rol `ORGANIZER`)* |
| **Eventos** — crear | ❌ | ❌ | ✅ | ✅ |
| **Eventos** — editar / cambiar estado / eliminar | ❌ | ❌ | ✅ (solo si es el dueño) | ✅ (cualquiera) |
| **Sesiones** — lectura | ✅ | ✅ | ✅ | ✅ |
| **Sesiones** — crear/editar/eliminar | ❌ | ❌ | ✅ (solo si es dueño del evento padre) | ✅ (cualquiera) |
| **Inscripciones** — crear | ❌ | ✅ | ❌ *(la ruta exige `PARTICIPANT`)* | ❌ *(la ruta exige `PARTICIPANT`)* |
| **Inscripciones** — ver/cancelar propias | ❌ | ✅ (las propias) | — | ✅ (cualquiera) |
| **Inscripciones** — confirmar/rechazar | ❌ | ❌ | ✅ (solo del evento propio) | ✅ (cualquiera) |
| **Inscripciones** — listado por evento | ❌ | ❌ | ✅ (solo su evento) | ✅ (cualquiera) |
| **Inscripciones** — listado global | ❌ | ❌ | ❌ | ✅ |
| **Reportes** — PDF/XLSX de inscritos | ❌ | ❌ | ✅ (solo su evento) | ✅ (cualquiera) |
| **Reportes** — certificado | ❌ | ✅ (el propio, si `CONFIRMED`) | — | ✅ (cualquiera) |
| **Reportes** — estadísticas | ❌ | ❌ | ✅ (alcance propio) | ✅ (alcance global) |
| **Usuarios** — cualquier operación | ❌ | ❌ | ❌ | ✅ |
| **Roles** — listar | ❌ | ❌ | ❌ | ✅ |

`✅` = permitido, `❌` = rechazado (401 si no hay token, 403 si el rol o la propiedad no
corresponden), `—` = no aplica al modelo de datos (p. ej. un organizador no es dueño de una
inscripción, solo del evento).

---

## 8. Seguridad

### Flujo JWT
1. `POST /api/auth/login` valida credenciales contra `users.password_hash` (BCrypt) vía
   `CustomUserDetailsService` + `DaoAuthenticationProvider`.
2. `JwtService` emite un **access token** (claims `email`, `roles`, `exp` según
   `JWT_ACCESS_EXPIRATION`) y un **refresh token** (claim `type=refresh`, `exp` según
   `JWT_REFRESH_EXPIRATION`), ambos firmados HS256 con `JWT_SECRET` y con `issuer` fijo
   (`academic-events-api`).
3. El refresh token emitido se persiste en la tabla `refresh_tokens` para poder revocarlo
   explícitamente (`logout`) o al rotarlo (`refresh`).
4. Cada request pasa por `JwtAuthenticationFilter`, que valida el access token (firma, emisor,
   expiración) y puebla el `SecurityContext` con un `AuthenticatedPrincipal` (`CustomUserDetails`).
5. `SecurityConfig` define qué rutas son públicas (`permitAll`) y cuáles requieren
   autenticación (`anyRequest().authenticated()`); la autorización fina por rol y por
   propiedad del recurso se aplica con `@PreAuthorize` en los controladores y con chequeos de
   propiedad (`SecurityUtils.currentUserId()` / `isAdmin()`) dentro de los `*ServiceImpl`.
6. `GET /api/events` fuerza `status=PUBLISHED` para cualquier llamador que no sea ADMIN ni el
   organizador dueño de cada evento, sin importar el `status` recibido por parámetro
   (`EventServiceImpl.visibilitySpecification()`).

### Claves de Redis y TTL

| Prefijo de clave | Definido en | TTL | Propósito |
|---|---|---|---|
| `rl:login:ip:` | `RateLimitPolicy.LOGIN_IP` | 60 s (5 intentos) | Límite de intentos de login por IP. |
| `rl:login:email:` | `RateLimitPolicy.LOGIN_EMAIL` | 60 s (5 intentos) | Límite de intentos de login por correo. |
| `rl:register:ip:` | `RateLimitPolicy.REGISTER_IP` | 3600 s (3 intentos) | Límite de registros por IP. |
| `rl:public:ip:` | `RateLimitPolicy.PUBLIC_IP` | 60 s (60 solicitudes) | Límite general para tráfico anónimo. |
| `rl:auth:user:` | `RateLimitPolicy.AUTHENTICATED_USER` | 60 s (120 solicitudes) | Límite general para usuarios autenticados. |
| `rl:reports:user:` | `RateLimitPolicy.REPORTS_USER` | 60 s (5 solicitudes) | Límite específico para descargas de reportes. |
| `login-attempts:` | `LoginAttemptService` | 900 s | Contador de intentos fallidos de login por correo. |
| `blocked-user:` | `LoginAttemptService` | 900 s | Marca de bloqueo temporal tras 5 fallos de login. |

Todas las claves `rl:*` usan el script atómico `rate_limit.lua` (`INCR` + `EXPIRE` si es la
primera solicitud de la ventana) ejecutado vía `RedisRateLimiter` + `DefaultRedisScript`.

### Políticas de rate limiting (`RateLimitFilter`)
- `POST /api/auth/login`: se valida primero el límite por IP y luego, si el body trae
  `email`, el límite por correo (ambos con la misma ventana de 60 s / 5 intentos).
- `POST /api/auth/register`: 3 intentos por hora por IP.
- Rutas de reportes (`/api/reports/**` y `/api/registrations/{id}/certificate.pdf`): 5
  solicitudes por minuto, por usuario autenticado si hay un Bearer token válido, o por IP si
  la petición es anónima.
- Cualquier otra ruta: 120 solicitudes por minuto por usuario autenticado, o 60 por minuto por
  IP si es anónima.
- Toda solicitud rechazada responde `429 Too Many Requests` con el error `RATE_LIMIT_EXCEEDED`
  y el header `Retry-After` con los segundos restantes de la ventana.
- Bloqueo adicional por fuerza bruta: tras 5 fallos de login para el mismo correo en 15
  minutos (`LoginAttemptService`), la cuenta queda bloqueada 15 minutos aunque las
  credenciales sean correctas.

---

## 9. Pruebas

```bash
./gradlew test
```

> ⚠️ **Advertencia**: los tests requieren Postgres y Redis disponibles. Antes de ejecutar
> `./gradlew test`, levanta los contenedores con:
> ```bash
> docker compose up -d
> ```
> (el arranque del contexto de Spring, incluida `AcademicEventsApiApplicationTests`, necesita
> una conexión real a la base de datos y a Redis; sin los contenedores arriba, la suite falla
> al intentar levantar el `ApplicationContext`).

Generar el reporte de cobertura JaCoCo:

```bash
./gradlew jacocoTestReport
```

El reporte HTML queda en `build/reports/jacoco/test/html/index.html` — ábrelo directamente en
el navegador para ver la cobertura por paquete/clase/línea.

---

## 10. Despliegue

- **`Dockerfile`**: build multi-stage — `gradle:jdk25` compila el `bootJar` (`-x test`) y la
  imagen final corre sobre `eclipse-temurin:25-jre-alpine` con un usuario no root (`spring`),
  expone el puerto `8080` y fija `JAVA_TOOL_OPTIONS` para limitar memoria/GC en el contenedor.
- **`render.yaml`**: define un único servicio web (`academic-events-api`, `runtime: docker`,
  plan `free`), con `healthCheckPath: /actuator/health` y `SPRING_PROFILES_ACTIVE=prod`. Todas
  las variables sensibles (`DB_*`, `REDIS_*`, `JWT_*`, `ALLOWED_ORIGINS`, `SWAGGER_*`) están
  declaradas con `sync: false`, es decir, **se configuran manualmente en el panel de Render**,
  no se versionan.
- **Postgres y Redis en Render**: `render.yaml` solo describe el servicio web; la base de
  datos PostgreSQL y la instancia de Redis se crearon como **servicios separados directamente
  en el panel de Render** (no como `services:` adicionales en este archivo), y sus credenciales
  se pegaron a mano en las variables de entorno del servicio web (`DB_URL`/`DB_USERNAME`/
  `DB_PASSWORD` y `REDIS_URL` o el trío `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`, según el
  addon usado).
- En producción, Swagger UI queda protegido con Basic Auth (`SwaggerSecurityConfig`, perfil
  `prod`), usando `SWAGGER_USER` / `SWAGGER_PASSWORD`.

---

## 11. Credenciales de evaluación

Datos semilla insertados por la migración Flyway `V1__initial_schema_and_data.sql`.

**Contraseña común para todos los usuarios de prueba: `Password123*`**

| Correo | Nombre | Rol(es) | Estado |
|---|---|---|---|
| `admin@academic.test` | Administrador Académico | ADMIN | ACTIVE |
| `maria.cordero@academic.test` | María Fernanda Cordero Vega | ORGANIZER, PARTICIPANT | ACTIVE |
| `jose.mora@academic.test` | José Andrés Mora Sánchez | ORGANIZER, PARTICIPANT | ACTIVE |
| `ana.torres@academic.test` | Ana Lucía Torres Paredes | ORGANIZER, PARTICIPANT | ACTIVE |
| `carlos.velez@academic.test` … `gabriela.villacis@academic.test` (12 cuentas) | — | PARTICIPANT | ACTIVE |
| `andres.sarmiento@academic.test` | Andrés Felipe Sarmiento Paz | PARTICIPANT | **BLOCKED** (para probar el flujo de cuenta bloqueada) |

Para probar en Swagger:
1. Ir a [URL_SWAGGER].
2. `POST /api/auth/login` con uno de los correos anteriores y contraseña `Password123*`.
3. Copiar el `accessToken` de la respuesta y usar el botón **Authorize** (esquema
   `bearerAuth`, `Bearer <token>`) para autenticar el resto de las llamadas.

---

## 12. Autores

- Mateo Paez — Seguridad, JWT, Redis, rate limiting, despliegue
- John Tigre — Dominio, transacciones, reportes, estadísticas
