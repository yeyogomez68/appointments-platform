-- Roles de base de datos para Testcontainers: mismo modelo que infra/db/init/01-roles.sh.
-- Lo ejecuta el superusuario del contenedor sobre la base "citas" antes de que arranque la API.
-- Contraseñas solo de prueba (IntegrationTest las usa como literales).
CREATE ROLE app_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD 'owner_test';
CREATE ROLE app_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD 'runtime_test';
ALTER DATABASE citas OWNER TO app_owner;
GRANT CONNECT, TEMPORARY ON DATABASE citas TO app_runtime;
GRANT USAGE ON SCHEMA public TO app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE app_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE app_owner IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO app_runtime;
