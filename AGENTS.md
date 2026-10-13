# Plataforma de citas y atención al cliente — instrucciones para agentes

SaaS multi-negocio (peluquería piloto) para agenda, caja y atención por WhatsApp. Backend Java 25 + Spring Boot 4 en `backend/`, frontend Angular 22 en `frontend/`, PostgreSQL 18 con Row Level Security.

## Comandos (bash, desde la raíz; Docker en marcha)

| Tarea | Comando |
|---|---|
| Servicios locales | `docker compose up -d --wait db mailpit` |
| `.env` local | `node scripts/init-env.mjs` |
| Pruebas backend | `(cd backend && ./mvnw -B -ntp verify)` |
| Compilar API | `(cd backend && ./mvnw -B -ntp -DskipTests package)` |
| Humo API | `bash scripts/smoke-api.sh` |
| Build / pruebas frontend | `(cd frontend && npx ng build)` · `(cd frontend && npx ng test --watch=false)` |
| Regenerar cliente TS | `(cd frontend && npx ng-openapi-gen -c ng-openapi-gen.json)` |
| Deriva contrato/cliente | `bash scripts/check-api-drift.sh` |
| E2E | `bash scripts/run-e2e.sh` |

Orden de trabajo: `blueprints/plataforma-citas/tasks.json` y `blueprints/plataforma-citas/epics/`.

## No negociable

1. La aplicación y sus pruebas se conectan a PostgreSQL solo como `app_runtime` (sin `BYPASSRLS`).
2. Toda tabla con `business_id` tiene RLS forzada y política en la misma migración.
3. Nunca editar migraciones confirmadas ni el cliente generado; se regeneran.
4. Nunca construir los Non-Goals del bundle (blueprint §1).
5. Nunca registrar secretos ni versionar `.env`.
6. Nunca marcar una tarea como hecha con un gate en rojo ni editar un comando `verify`.

Arquitectura, límites entre módulos, tokens de diseño y reglas de código: `CLAUDE.md` en este directorio.
