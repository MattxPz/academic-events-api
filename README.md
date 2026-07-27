# Academic Events API

API REST para la gestión de eventos académicos — Proyecto Integrador, Programación y Plataformas Web.

## Stack
- Java 25 + Spring Boot 4.1.0
- PostgreSQL 16 + Redis 7
- JWT, Spring Security, Flyway, Springdoc OpenAPI

## Requisitos previos
- JDK 25
- Docker Desktop

## Cómo levantar el entorno local

```bash
docker compose up -d
```

Luego ejecutar los scripts SQL del docente (`00_create_database.sql` y `01_schema_and_data.sql`) contra el contenedor `postgres-dev`:

```bash
docker exec -i postgres-dev psql -U ups -d postgres < 00_create_database.sql
docker exec -i postgres-dev psql -U ups -d academic_events_db < 01_schema_and_data.sql
```

Copiar `.env.example` como `.env` y completar los valores. Correr la aplicación desde VS Code o con:

```bash
./gradlew bootRun
```

> El proyecto usa [spring-dotenv](https://github.com/paulschwarz/spring-dotenv) para cargar `.env` automáticamente, así que basta con tener el archivo en la raíz del proyecto: no hace falta exportar las variables de entorno manualmente en ningún sistema operativo, ni depender de que el IDE las inyecte.

## Estructura del proyecto
Monolito modular por dominio: `auth`, `users`, `categories`, `events`, `sessions`, `registrations`, `reports`, y `shared/` para configuración transversal.

## Integrantes
- [Nombre A] — Seguridad, JWT, Redis, rate limiting, despliegue
- [Nombre B] — Dominio, transacciones, reportes, estadísticas