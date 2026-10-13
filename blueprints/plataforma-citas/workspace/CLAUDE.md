# Plataforma de citas y atención al cliente

SaaS multi-negocio (peluquería piloto) para agenda, caja y atención por WhatsApp; este repo lo construye por bundles, empezando por la fundación multi-negocio.

## Comandos

Todos desde la raíz del repo, en bash (Git Bash en Windows). Docker Desktop debe estar corriendo.

| Tarea | Comando |
|---|---|
| Servicios locales (PostgreSQL 18 + Mailpit) | `docker compose up -d --wait db mailpit` · parar: `docker compose down` · borrar datos: `docker compose down -v` |
| Crear `.env` local (una vez) | `node scripts/init-env.mjs` |
| Compilar la API | `(cd backend && ./mvnw -B -ntp -DskipTests package)` → `backend/target/plataforma-citas-api.jar` |
| API en local (perfil `dev`) | `(cd backend && ./mvnw spring-boot:run)` — http://localhost:8080 |
| Pruebas backend + Modulith + ArchUnit | `(cd backend && ./mvnw -B -ntp verify)` |
| Una clase de prueba | `(cd backend && ./mvnw -B -ntp test -Dtest=NombreIT)` |
| Humo de la API | `bash scripts/smoke-api.sh [--expect-demo] [--expect-mail]` |
| Instalar frontend | `(cd frontend && npm ci)` |
| Frontend en local | `(cd frontend && npx ng serve)` — http://localhost:4200 (proxy `/api` → :8080) |
| Build frontend | `(cd frontend && npx ng build)` |
| Pruebas frontend (Vitest) | `(cd frontend && npx ng test --watch=false)` |
| Contraste de colores | `node scripts/check-contrast.mjs` |
| Exportar contrato OpenAPI | `(cd backend && ./mvnw -B -ntp test -Dtest=OpenApiExportIT -Dopenapi.export=true)` |
| Regenerar cliente TS | `(cd frontend && npx ng-openapi-gen -c ng-openapi-gen.json)` |
| Deriva contrato/cliente | `bash scripts/check-api-drift.sh` (0 al día · 1 deriva · 2 error) |
| Humo del proxy de `ng serve` | `bash scripts/smoke-web-proxy.sh` |
| E2E (Playwright + Mailpit) | `bash scripts/run-e2e.sh` |
| Pila de staging local | `bash scripts/smoke-prod-stack.sh` |
| Buzón de correo local | http://localhost:8025 |

**Gate antes de marcar una tarea como hecha:** `(cd backend && ./mvnw -B -ntp verify)`, `(cd frontend && npx ng build)`, `(cd frontend && npx ng test --watch=false)` y `bash scripts/check-api-drift.sh`.

Volver al estado del paso anterior (`git reset --hard step-NN-<slug>`) es una acción humana: no está en el allowlist y el agente no la ejecuta por su cuenta.

Orden de construcción y estado: `blueprints/plataforma-citas/tasks.json`; detalle de cada tarea: `blueprints/plataforma-citas/epics/`. Versiones: `backend/pom.xml` y `frontend/package-lock.json` — léelas, no las adivines.

## Stack

Java 25 · Spring Boot 4.1 (Security 7, Hibernate 7, Jackson 3 = `tools.jackson`) · Spring Modulith · Flyway · PostgreSQL 18 con RLS · Angular 22 + Material M3 · ng-openapi-gen · Playwright · Docker Compose + Caddy · GHCR + GitHub Actions.

## Arquitectura

**Recorrido de una petición.** Navegador → Caddy (`deploy/Caddyfile`; en local el proxy de `ng serve`) → `/api/v1/**` → `RequestIdFilter` (MDC `requestId`) → cadena de Spring Security (`shared/security/SecurityConfig`) → `JwtConfig.TenantBindingFilter` fija `TenantContext` (negocio `bid` y cuenta `sub` del JWT) → controlador del módulo → servicio `@Transactional` → `TenantAwareJpaTransactionManager` ejecuta `set_config('app.business_id', …, true)` y `set_config('app.user_id', …, true)` al iniciar la transacción → JPA/`JdbcClient` como `app_runtime` → PostgreSQL aplica RLS. Errores: `ApiExceptionHandler` → `application/problem+json` con `code` estable.

**Módulos** (`com.plataformacitas.*`, Spring Modulith): la raíz del paquete de un módulo es su API pública; los subpaquetes son internos.

| Módulo | Contenido | Puede usar | Nunca |
|---|---|---|---|
| `shared` (abierto) | dinero, errores, tenancy, seguridad, correo, límites, captcha, OpenAPI | librerías | módulos de negocio |
| `platform` | negocio, sede, ajustes, suscripción, aceptación legal; API `Businesses` | `shared` | `identity`, `vertical` |
| `identity` | cuentas, membresías, tokens, login, registro, recuperación | `shared`, `platform` (API) | internos de `platform` |
| `vertical/peluqueria` | `PeluqueriaVerticalSeed` (etiquetas y ajustes por defecto) | `platform` (API) | ser referenciado por otros |

Hexagonal completa solo en `agenda` y `cash` (bundles 2 y 3): `domain/` sin Spring ni JPA, `application/` sin `adapter/`. `identity` y `platform` son ligeros (`internal/`). Guía: `docs/modules/README.md`.

**Dónde vive cada cosa.**

| Tema | Única fuente |
|---|---|
| Esquema | `backend/src/main/resources/db/migration/V*.sql` (Flyway como `app_owner`) |
| Negocio actual | `shared/tenancy/TenantContext` — `TenantContext.run(negocio, cuenta, trabajo)` envuelve la transacción |
| Roles de base de datos | `infra/db/init/01-roles.sh` (local/servidor) y `backend/src/test/resources/db/test-roles.sql` (pruebas) |
| Configuración | `backend/src/main/resources/application.yml` + variables de `.env` |
| Contrato HTTP | `frontend/openapi/openapi.json` (generado) → `frontend/src/app/api/**` (generado) |
| Tokens de diseño | `frontend/src/styles/_tokens.scss` |
| Mensajes de error y etiquetas | `frontend/src/app/core/ui-text.ts` |
| Sesión en el navegador | `frontend/src/app/core/auth.ts` (`AuthStore`) |

## Reglas de código

1. Identificadores, rutas y JSON en inglés; textos para personas en español (es-CO).
2. Toda entidad JPA con `businessId` extiende `BusinessScopedEntity`; toda tabla con `business_id` trae `ENABLE` + `FORCE ROW LEVEL SECURITY` y su política en la misma migración (lo verifican `ArchitectureRulesTest` y `RowLevelSecurityIT`).
3. Nada de acceso a datos fuera de una transacción; `spring.jpa.open-in-view=false`.
4. Dinero con `Money` (entero en unidad menor); fechas `Instant`/`timestamptz` UTC; hora actual solo desde el `Clock` inyectado.
5. Errores: lanzar `DomainException(Code.X, detalle)`; nunca devolver 200 con un error dentro. Un código nuevo exige su mensaje en `ERROR_MESSAGES` del frontend.
6. Correos: publicar `EmailSender.Message` como evento (sale después del commit); nunca llamar `send` directamente.
7. Frontend: componentes standalone con signals y `OnPush`; llamadas solo con el cliente generado (`inject(Api).invoke(...)`); rutas perezosas `path: '<ruta>'`; sin NgRx.
8. Pantallas sin términos del vertical escritos a mano («Empleado», «Cliente»…): usar `LabelsService`.
9. Colores, espacios y radios solo con `var(--…)` de `_tokens.scss`; foco visible siempre.
10. Pruebas de integración extienden `support/IntegrationTest` (conectan como `app_runtime`); datos con sufijo `UUID` (la base del contenedor se comparte en la corrida).
11. Ninguna dependencia nueva sin verificar su versión estable y su compatibilidad con Jackson 3 / Angular 22; el motivo va en el commit.

## Sistema de diseño

| Rol | Claro | Oscuro | Uso |
|---|---|---|---|
| `--c-primary` | `#5B4FE9` | `#8B83FF` | botones principales, enlaces, foco |
| `--c-on-primary` | `#FFFFFF` | `#0E1018` | texto sobre primario |
| `--c-bg` | `#F7F8FC` | `#0E1018` | fondo de página |
| `--c-surface` / `--c-surface-2` | `#FFFFFF` / `#F1F3F9` | `#161A25` / `#1E2331` | tarjetas, paneles |
| `--c-border` / `--c-border-strong` | `#E3E6EF` / `#767D90` | `#2A3042` / `#6B748C` | divisores / bordes de controles |
| `--c-text` / `--c-text-muted` | `#141826` / `#5B6275` | `#EEF0F6` / `#9AA2B5` | texto |
| `--c-danger` / `--c-danger-text` | `#E5484D` / `#C42F35` | `#FF6B6F` | errores (texto usa `-text`) |
| `--c-success` / `--c-success-text` | `#12A150` / `#0B7A3B` | `#3DD68C` | confirmaciones |
| `--c-warning`, `--c-info`, `--c-accent` | `#D97706`, `#2F80ED`, `#0FA3A3` | `#F5A524`, `#5EA2FF`, `#2CC7C7` | avisos, información, acento |

- **Tipo:** Inter Variable; escala 12/14/16/20/24/32 px; `tabular-nums` en dinero y horas.
- **Espacio:** base 4 px (4, 8, 12, 16, 24, 32, 48). **Radio:** 10 px controles, 14 px tarjetas.
- **Movimiento:** 150–200 ms `cubic-bezier(0.2, 0, 0, 1)`; nada se anima con `prefers-reduced-motion: reduce`.
- **Diseño:** móvil primero; funciona a 360 px sin scroll horizontal; tablas densas (−2) en escritorio; contraste WCAG 2.2 AA (`node scripts/check-contrast.mjs`).

## Entorno

| Variable | Requerida | Usada por |
|---|---|---|
| `DB_URL`, `DB_OWNER_PASSWORD`, `DB_RUNTIME_PASSWORD`, `DB_SUPERUSER_PASSWORD` | sí | API, Flyway, docker compose |
| `JWT_SECRET` (64 hex) | sí | `JwtConfig` |
| `DEV_SEED_PASSWORD` | sí en perfil `dev` | `DemoDataSeeder` |
| `APP_BASE_URL`, `CORS_ALLOWED_ORIGINS` | sí | enlaces de correo, CORS |
| `SMTP_*`, `EMAIL_FROM`, `EMAIL_PROVIDER` | sí | `EmailConfig` |
| `CAPTCHA_PROVIDER`, `TURNSTILE_SITE_KEY`, `TURNSTILE_SECRET` | sí en staging/producción | `CaptchaVerifier` |
| `SENTRY_DSN` | no | Sentry |

`.env.example` se versiona y se mantiene al día; `.env` nunca. Lista completa: `blueprints/plataforma-citas/blueprint.md` §10.

## Reglas por área

| Archivo | Aplica a |
|---|---|
| `.claude/rules/backend-datos.md` | migraciones, tenancy, entidades |
| `.claude/rules/backend-api.md` | controladores, seguridad, errores |
| `.claude/rules/pruebas-backend.md` | `backend/src/test/**` |
| `.claude/rules/frontend.md` | `frontend/src/**`, `frontend/e2e/**` |
| `.claude/rules/despliegue.md` | `deploy/**`, `.github/**`, `docker-compose.yml`, `infra/**` |

## No negociable

1. La aplicación se conecta a PostgreSQL solo como `app_runtime` (sin `BYPASSRLS`, no dueña de las tablas); las pruebas también.
2. Ninguna tabla con `business_id` sin RLS forzada y política; ninguna consulta confía solo en un `WHERE business_id`.
3. Nunca editar una migración ya confirmada ni el cliente generado (`frontend/src/app/api`, `frontend/openapi/openapi.json`): se regeneran.
4. Nunca construir nada de los Non-Goals del bundle (blueprint §1): catálogo, agenda, caja, WhatsApp, cobros, invitaciones.
5. Nunca registrar secretos, tokens ni contraseñas en logs, en Sentry ni en el repo; `.env` nunca se versiona.
6. Nunca marcar una tarea como hecha con un gate en rojo ni editar un comando `verify` para que pase.
