-- Ejecutar manualmente con un usuario que tenga permiso CREATEDB.
-- No colocar este archivo dentro de db/migration de Flyway.

CREATE DATABASE academic_events_db
    WITH
    ENCODING = 'UTF8'
    TEMPLATE = template0;
