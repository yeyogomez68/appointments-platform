#!/bin/bash
# Crea los roles de base de datos la primera vez que arranca el volumen de PostgreSQL.
#   app_owner   — dueño de la base y de las tablas; lo usa Flyway.
#   app_runtime — lo usa la API; NOSUPERUSER, NOBYPASSRLS, sin ser dueño: RLS siempre le aplica.
# Las contraseñas llegan por variables de entorno (docker-compose.yml / deploy/compose.prod.yml).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v dbname="$POSTGRES_DB" \
  -v owner_pw="$APP_OWNER_PASSWORD" \
  -v runtime_pw="$APP_RUNTIME_PASSWORD" <<'EOSQL'
CREATE ROLE app_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'owner_pw';
CREATE ROLE app_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'runtime_pw';
ALTER DATABASE :"dbname" OWNER TO app_owner;
GRANT CONNECT, TEMPORARY ON DATABASE :"dbname" TO app_runtime;
GRANT USAGE ON SCHEMA public TO app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE app_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE app_owner IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO app_runtime;
EOSQL
