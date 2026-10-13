# Plataforma de citas y atención al cliente — Blueprint (bundle 1 de 7: Fundación multi-negocio)

> Generado por The Architect el 2026-10-12
> Forma: saas-webapp + capacidad availability-engine (el motor llega en el bundle 2) · `knowledge/shapes/saas-webapp.md`
> Runtime track: Java 25 / Spring Boot 4.1 + Angular 22 — no existe un archivo de track para Java/Spring en el plugin; **todas** las versiones vienen del informe de `stack-researcher` del 2026-10-12 (§11)
> Modo de emisión: bundle (18 pasos ⇒ bundle)
> Versión del blueprint: 1
> Versiones verificadas por última vez: 2026-10-12 — ver §11 para la procedencia de cada paquete

**Cómo se usa este bundle.** El bundle vive en `blueprints/plataforma-citas/` dentro del repositorio nuevo. El constructor ejecuta el bloque *Bootstrap* de §10 desde la raíz del repositorio (que copia `workspace/` a la raíz sin pisar nada) y después sigue `tasks.json` en orden, abriendo solo la épica de cada tarea. Este documento explica el porqué; `epics/` explica el cómo; `tasks.json` decide qué sigue.

---

## 1. Project Overview & Non-Goals

### Vision

Una plataforma web multi-negocio donde una peluquería de barrio —y después cualquier negocio que trabaja con citas— se registra sola, deja el cuaderno y el WhatsApp manual, agenda por empleado, cuadra la caja diaria y atiende a sus clientes por WhatsApp desde la misma aplicación. La peluquería piloto valida el producto; el producto es la plataforma: un correo puede ser dueño de un negocio y empleado de otro, y ningún negocio puede ver un solo dato de otro, porque el aislamiento lo garantiza la base de datos además del código.

Este bundle construye la **fundación** sobre la que se apoyan los seis siguientes: repositorio y CI, base de datos con roles y Row Level Security probada, cuentas globales con membresías por negocio, inicio de sesión con selección de negocio, registro autoservicio con captcha y verificación de correo, recuperación de contraseña, contrato OpenAPI con cliente TypeScript generado, el esqueleto de la aplicación Angular con su sistema de diseño, una prueba E2E del registro y las imágenes de staging.

### Users

| Persona | Qué viene a hacer | Frecuencia |
|---|---|---|
| Dueña de un negocio (`OWNER`) | Registrar su negocio, configurarlo, elegir entre sus negocios | diaria |
| Recepción (`RECEPTION`) | Entrar al negocio donde trabaja (agenda y caja llegan en bundles 2–3) | diaria |
| Empleado (`EMPLOYEE`) | Entrar a su negocio desde el celular (agenda propia en bundle 2) | diaria |
| Visitante | Registrar un negocio nuevo con prueba gratuita de 30 días | una vez |
| Equipo de la plataforma | Operar staging, desplegar, revisar errores | semanal |

### Goals — v1 scope (bundle 1)

1. Un negocio nuevo se registra solo (captcha + verificación de correo) y queda con sede principal, ajustes de su vertical, prueba de 30 días y aceptación versionada de términos y acuerdo de encargo.
2. Una cuenta con varios negocios inicia sesión, elige negocio, cambia de negocio sin volver a escribir la contraseña y obtiene en cada uno solo los permisos de su membresía.
3. Ningún negocio puede leer, contar, modificar ni borrar datos de otro: `@TenantId` en la aplicación y RLS forzada en PostgreSQL, demostrado por una suite que recorre cada tabla de negocio.
4. Una persona recupera su contraseña por correo con un enlace de un solo uso, y al cambiarla se cierran todas sus sesiones.
5. Un cambio incompatible en la API rompe el build de Angular o el CI antes de llegar a producción (cliente generado + control de deriva).
6. Cada cambio pasa por CI (backend con Testcontainers, frontend, contrato, E2E) y puede desplegarse a staging con un workflow manual.

### Non-Goals — explicitly out of scope for this bundle

| No se construye | Por qué no ahora | Se retoma cuando |
|---|---|---|
| Invitaciones de usuarios (HU-97): pantallas y endpoints `/users` | Necesita la tabla `resource` (empleados) para el vínculo `EMPLOYEE` ↔ empleado | Bundle 2, sobre el repo construido (`/architect-brownfield`) |
| Catálogo de servicios, empleados, horarios, clientes y consentimiento, motor de disponibilidad, agenda | Es el bundle 2; depende de esta fundación | Bundle 2 |
| Caja (ventas, pagos, descuentos, propinas, egresos, cierre), reportes mínimos | Bundle 3 | Bundle 3 |
| Respaldos cifrados a R2 (HU-25) y producción medida (HU-26b) | Bundle 3; aquí solo staging | Bundle 3 |
| Canal de WhatsApp (360dialog), outbox, recordatorios, bot, bandeja SSE, enlaces de calendario | Bundles 4–5; además dependen de contratos con Meta y 360dialog | Contrato partner-paid firmado (puerta humana) y bundle 4 |
| Planes, cobro con Wompi, ciclo de vida de la suscripción, consola de plataforma | Bundle 6; en este bundle la suscripción solo registra `TRIAL` | Bundle 6 |
| Verticales adicionales (consultorio), PWA, push, `webcal`, exportación y borrado | Bundle 7 | Bundle 7 |
| Historia clínica o notas clínicas en cualquier vertical | Datos sensibles (Ley 1581, Res. 1995/1999); decisión permanente | Nunca |
| Fases 4–7 de los documentos (inventario, DIAN, app nativa, varias sedes, marketing) | Fuera de las fases 1–3 aprobadas | Nueva entrevista de arquitectura |

**El constructor no implementa nada de esta tabla**, aunque parezca una pequeña adición mientras trabaja en un paso vecino. Si un paso parece exigir un non-goal, es un defecto del blueprint: se detiene y lo reporta.

#### Hoja de ruta aprobada: 7 bundles

| # | Bundle | Fase | Estado |
|---|---|---|---|
| 1 | Fundación: repo, CI, RLS, suite de aislamiento, cuentas con varios negocios, registro autoservicio, correo | 1 | **este blueprint** |
| 2 | Catálogo, empleados, motor de disponibilidad, clientes y consentimiento, agenda | 1 | se genera después con `/architect-brownfield` sobre el repo construido |
| 3 | Caja (pagos, descuentos, propinas, cierre), reportes mínimos, respaldos, despliegue a producción | 1 | ídem |
| 4 | Outbox, canal 360dialog, recordatorios, bot | 2 | ídem |
| 5 | Bandeja con SSE, enlaces de calendario, medición de consumo | 2 | ídem |
| 6 | Planes y funciones por plan, Wompi, ciclo de vida de la suscripción, consola | 3 | ídem |
| 7 | Reportes avanzados, exportación y borrado, verticales (consultorio), PWA con push y `webcal` | 3 | ídem |

Cada bundle siguiente se genera con versiones verificadas el día en que se genera.

### Success metrics

| Métrica | Meta | Cómo se mide |
|---|---|---|
| Aislamiento entre negocios | 0 filas cruzadas en cada tabla de negocio, en cada corrida de CI | `TenantIsolationIT` (job `backend` de CI) |
| Registro autoservicio completo | flujo registro → verificación → ingreso en verde en cada PR | `e2e/registro.spec.ts` (job `e2e`) |
| Deriva del contrato | 0 PR fusionados con el cliente desactualizado | `scripts/check-api-drift.sh` (job `api-contract`) |
| Staging actualizado | despliegue con un solo workflow, sin tocar el servidor a mano | ejecución de `Deploy staging` en GitHub Actions |
| Fundación lista para el bundle 2 | las 18 etiquetas `step-*` presentes y §20.1 en verde | `git tag -l 'step-*'` y el gate de §20.1 |

---

## 2. Tech Stack

**Runtime track: Java 25 / Spring Boot 4.1 (backend) + TypeScript / Angular 22 (frontend).** Esta tabla nombra decisiones, no versiones: cada versión vive en §11.

| Capa | Elección | Por qué esta y no otra |
|---|---|---|
| Lenguaje / runtime | Java 25 LTS (backend), TypeScript con Node 24 LTS (frontend) | Decisión vinculante de los documentos; LTS en ambos. Se descartó Spring Boot 3.5 (línea anterior) y 4.2-M (prerelease) |
| Framework backend | Spring Boot 4.1 + Spring Modulith (monolito modular) | Un solo despliegue con límites entre módulos verificados en CI; microservicios no se justifican para 2 desarrolladores |
| Framework frontend | Angular 22 standalone, signals, sin zone.js, rutas perezosas | Decisión de los documentos; zoneless y Vitest son el valor por defecto de `ng new` 22 |
| Styling | SCSS + tokens CSS propios (`--c-*`) + tema M3 de Angular Material | Material da accesibilidad y componentes; los tokens dan una identidad propia sin pelear con Material |
| Capa de componentes | Angular Material + CDK | Elegido en la entrevista sobre PrimeNG: menos dependencia de terceros y mejor accesibilidad por defecto |
| Base de datos | PostgreSQL 18 con Row Level Security | RLS es la segunda barrera de aislamiento; `EXCLUDE` para la doble reserva llega en el bundle 2 |
| Acceso a datos | Spring Data JPA/Hibernate 7 (`@TenantId`) + `JdbcClient` para SQL nativo | JPA para entidades de negocio; `JdbcClient` para membresías, tokens y reportes. jOOQ descartado (OSS + PostgreSQL 18 no verificable) |
| Migraciones | Flyway como `app_owner` | Restricciones y políticas RLS que Hibernate no genera van en SQL versionado |
| Auth | Propia: Spring Security 7, JWT HS256 (`NimbusJwtEncoder`) + refresh opaco rotativo en cookie, Argon2id | Las membresías por negocio y el `bid` en el token son el núcleo del producto; un proveedor alojado (Clerk) no modela «una cuenta, varios negocios, rol por negocio» sin una segunda fuente de verdad |
| Trabajo en segundo plano | NOT APPLICABLE en este bundle — no hay tareas programadas; `@Scheduled` + ShedLock + outbox llegan en el bundle 4 | — |
| Pagos | NOT APPLICABLE en este bundle — Wompi llega en el bundle 6 | — |
| Archivos | NOT APPLICABLE en este bundle — Cloudflare R2 solo para respaldos (bundle 3) | — |
| Correo | Puerto `EmailSender` con adaptador SMTP: Mailpit en local y pruebas manuales, Brevo en staging/producción | SMTP estándar: cambiar de proveedor es cambiar variables |
| Captcha | Cloudflare Turnstile detrás de `CaptchaVerifier` | Gratuito, sin rompecabezas para el usuario, claves de prueba oficiales |
| Límites de abuso | Bucket4j en memoria | Una sola instancia de API; Redis no se justifica todavía |
| Contrato | springdoc-openapi → `ng-openapi-gen` | Cliente nativo de Angular; reemplaza al `openapi-generator typescript-angular` de los documentos |
| Hosting | VPS de 4 GB en Hetzner, Docker Compose + Caddy (HTTPS automático), imágenes en GHCR, GitHub Actions | Costo fijo bajo y control total; Caddy resuelve TLS sin configuración |
| Errores | Sentry (backend, opcional por `SENTRY_DSN`) + logs ECS JSON | El frontend se instrumenta en el bundle 3 |
| Gestor de paquetes | Maven (wrapper) y npm | Los que traen Spring Boot y Angular CLI; sin capas extra |

### Compatibility check

Revisado contra `knowledge/stack-compatibility.md`: ninguna de sus filas de combinaciones conocidas como malas aplica a este stack (no hay linter de CSS propio con motor CSS-first, ni dos proveedores de identidad, ni host serverless, ni ORM sobre base documental, ni dos sistemas de migraciones). Advertencias propias del stack que este blueprint arrastra:

- **Spring Boot 4 usa Jackson 3** (`tools.jackson`): toda librería que se agregue debe ser compatible con Jackson 3. ShedLock 7.10.1 (bundle 4) y `com.interaso:webpush` (bundle 7) tienen compatibilidad **sin verificar** y piden una prueba temprana.
- **Testcontainers 2.x renombró artefactos y paquetes:** `testcontainers-postgresql` y la clase `org.testcontainers.postgresql.PostgreSQLContainer` (verificado en el jar 2.0.5).
- **Testcontainers en Windows** necesita Docker Desktop en marcha (WSL 2).
- **Spring Boot 4 es modular:** el personalizador de Hibernate está en `org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer` (verificado en `spring-boot-hibernate-4.1.1.jar`).

---

## 3. Directory Structure

```
plataforma-citas/                          # raíz del repositorio
├── blueprints/plataforma-citas/           # ESTE bundle (se versiona; ninguna herramienta lo recorre, ver §19.6)
├── CLAUDE.md, AGENTS.md                   # instrucciones de agentes (workspace/)
├── .claude/
│   ├── settings.json                      # permisos (workspace/)
│   ├── skills/{agregar-migracion,agregar-endpoint,verificar-todo}/SKILL.md   # (workspace/)
│   └── rules/{backend-datos,backend-api,pruebas-backend,frontend,despliegue}.md  # (workspace/)
├── .github/workflows/
│   ├── ci.yml                             # paso 17
│   └── deploy.yml                         # paso 18
├── .env.example, .gitignore, .gitattributes   # (workspace/)
├── docker-compose.yml                     # PostgreSQL 18 + Mailpit locales (workspace/)
├── infra/db/init/01-roles.sh              # roles app_owner/app_runtime al crear el volumen (workspace/)
├── deploy/
│   ├── compose.prod.yml, Caddyfile        # pila de staging (workspace/)
│   ├── api.Dockerfile, web.Dockerfile     # paso 18
├── scripts/
│   ├── init-env.mjs, smoke-api.sh, smoke-web-proxy.sh, run-e2e.sh, smoke-prod-stack.sh   # (workspace/)
│   ├── check-api-drift.sh                 # paso 13
│   └── check-contrast.mjs                 # paso 14
├── docs/
│   ├── adr/0001-decisiones-fundacion.md   # (workspace/)
│   └── modules/README.md                  # cómo crear un módulo — paso 2
├── backend/
│   ├── mvnw, mvnw.cmd                     # Maven Wrapper 3.3.4 (bootstrap)
│   ├── .mvn/wrapper/maven-wrapper.properties   # (workspace/)
│   ├── pom.xml                            # (workspace/) — finalName plataforma-citas-api
│   ├── target/plataforma-citas-api.jar    # producido por ./mvnw package (no se versiona)
│   └── src/
│       ├── main/java/com/plataformacitas/
│       │   ├── CitasApplication.java      # paso 1
│       │   ├── shared/                    # módulo abierto
│       │   │   ├── package-info.java      # paso 2
│       │   │   ├── money/Money.java       # paso 3
│       │   │   ├── error/{DomainException,ApiExceptionHandler}.java      # paso 3
│       │   │   ├── web/RequestIdFilter.java                              # paso 3
│       │   │   ├── tenancy/{TenantContext,BusinessScopedEntity,TenancyJpaConfig}.java  # paso 4
│       │   │   ├── security/SecurityConfig.java   # paso 1 (editado en 6 y 7)
│       │   │   ├── security/JwtConfig.java        # paso 7
│       │   │   ├── ratelimit/RateLimiter.java     # paso 10
│       │   │   ├── captcha/{CaptchaVerifier,TurnstileCaptchaVerifier}.java   # paso 10
│       │   │   ├── email/{EmailSender,EmailConfig}.java      # paso 11
│       │   │   └── openapi/OpenApiConfig.java     # paso 13
│       │   ├── platform/
│       │   │   ├── Businesses.java        # API pública del módulo — paso 5 (editado en 12)
│       │   │   └── internal/{Business,Location}.java (paso 5), BusinessController.java (paso 9)
│       │   ├── identity/internal/
│       │   │   ├── UserAccount.java, IdentityStore.java          # paso 6
│       │   │   ├── TokenService.java                             # paso 7
│       │   │   ├── AuthService.java, AuthController.java         # paso 8 (editados en 10 y 11)
│       │   │   ├── DemoDataSeeder.java                           # paso 9
│       │   │   ├── PasswordResetService.java                     # paso 11
│       │   │   └── SignupService.java, SignupController.java     # paso 12
│       │   └── vertical/peluqueria/PeluqueriaVerticalSeed.java   # paso 5
│       ├── main/resources/
│       │   ├── application.yml            # (workspace/)
│       │   └── db/migration/V1__shared_baseline.sql (1), V2__platform_business.sql (4),
│       │                    V3__identity_accounts.sql (6), V4__platform_legal_acceptance.sql (12)
│       └── test/
│           ├── resources/application-test.yml, db/test-roles.sql     # (workspace/)
│           ├── java/com/plataformacitas/
│           │   ├── support/IntegrationTest.java   # (workspace/)
│           │   ├── support/TestData.java          # paso 7
│           │   ├── FoundationIT (1), ModularityTest (2), ArchitectureRulesTest (2, editado en 9), OpenApiExportIT (13)
│           │   ├── shared/SharedKernelTest (3), shared/RowLevelSecurityIT (4)
│           │   ├── platform/ProvisioningIT (5), platform/TenantIsolationIT (9)
│           │   └── identity/IdentityPersistenceIT (6), TokenServiceIT (7), AuthFlowIT (8),
│           │                SecurityHardeningIT (10), PasswordResetIT (11), SignupIT (12)
│           └── java/archfixtures/baddomain/domain/BadDomainEntity.java (2), rogue/RogueEntity.java (9)
└── frontend/                              # creado por `ng new` en el bootstrap
    ├── angular.json, package.json, package-lock.json, tsconfig*.json   # scaffold (angular.json editado en 14)
    ├── proxy.conf.json, playwright.config.ts   # (workspace/)
    ├── ng-openapi-gen.json                # paso 13
    ├── openapi/openapi.json               # GENERADO por OpenApiExportIT (paso 13)
    ├── e2e/registro.spec.ts               # paso 17
    ├── dist/frontend/browser/             # producido por `npx ng build` (no se versiona)
    └── src/
        ├── index.html, styles.scss        # scaffold, editados en 14
        ├── styles/_tokens.scss            # paso 14
        └── app/
            ├── api/**                     # GENERADO por ng-openapi-gen (paso 13) — nunca a mano
            ├── app.ts                     # scaffold (sin cambios)
            ├── app.config.ts              # scaffold, editado en 15
            ├── app.routes.ts, app.html, app.spec.ts   # scaffold, reemplazados en 16
            ├── core/{auth.ts, ui-text.ts, core.spec.ts}   # paso 15
            ├── layout/shell.ts            # paso 15 (Shell, HomePage, BusinessPage)
            └── features/auth/{auth-pages.ts, auth-pages.spec.ts}   # paso 16
```

**Reglas de límites**
- Cada paquete directo bajo `com.plataformacitas` es un módulo; lo que está en la raíz del paquete es su API pública y los subpaquetes son internos (Spring Modulith lo verifica en `ModularityTest`).
- `shared` es un módulo abierto y no depende de ningún módulo de negocio; `platform` no depende de `identity`; `identity` usa solo la API pública de `platform`; `vertical/*` implementa `Businesses.VerticalSeed` y nadie lo referencia.
- Solo `identity` y `platform` declaran entidades globales (sin `BusinessScopedEntity`); lo verifica `ArchitectureRulesTest`.
- Nada fuera de `vertical/*` nombra un tipo de negocio concreto.
- En el frontend, `features/*` y `layout/*` llaman a la API solo a través de `src/app/api` y de `core/*`; `src/app/api` es generado.
- La convención de importación del frontend (rutas relativas, sin alias) está reconciliada en §19.6, *Resolution convention matrix*.

Cada archivo de este árbol tiene un origen: lo escribe el paso indicado o llega con la copia de `workspace/` (§19). Los marcados GENERADO los produce una herramienta en el paso indicado.

---

## 4. Data Model

### Entities

**business** — un negocio cliente de la plataforma; global (sin RLS); nunca se borra en este bundle.

| Campo | Tipo | Restricciones | Notas |
|---|---|---|---|
| id | uuid | PK | generado por la aplicación antes de abrir la transacción del alta |
| name | text | 2–120 | nombre visible |
| slug | text | único, `^[a-z0-9]+(-[a-z0-9]+)*$` | derivado del nombre, inmutable después del alta |
| timezone | text | default `America/Bogota` | zona IANA; los horarios del bundle 2 se guardan en hora local |
| currency | char(3) | default `COP` | ISO 4217; `Money` usa sus decimales por defecto (COP = 2) |
| vertical | text | not null | `PELUQUERIA` en este bundle |
| status | text | `ACTIVE`/`SUSPENDED` | suspensión manual hasta la consola del bundle 6 |

**subscription** — estado comercial del negocio; global; `TRIAL` con `trial_ends_at` = alta + 30 días, `plan_code = 'PRO'`.

**location** — sede; RLS; exactamente una con `is_main` por negocio (índice único parcial). Las tablas de agenda, horarios y caja del bundle 2–3 llevarán `location_id`.

**business_settings** — ajustes de agenda y caja con valores del vertical (`slot_step_min` 15, `buffer_min` 0, atención sin cita sí, fondo inicial 0, propinas sí al `EMPLOYEE`, tope de descuento de recepción 10 %) y `labels` (diccionario de etiquetas del vertical); RLS; PK `business_id`. La pantalla para editarlos es HU-96 (bundle 2).

**user_account** — una persona; global; correo único en minúsculas; `password_hash` Argon2id; `email_verified_at` nulo hasta verificar.

**membership** — cuenta × negocio × rol (`OWNER`, `RECEPTION`, `EMPLOYEE`); RLS especial `membership_access`; `resource_id` queda nulo hasta el bundle 2.

**refresh_token** — sesiones; global; solo el SHA-256 del token; familia para detectar reutilización.

**one_time_token** — verificación de correo (24 h), restablecimiento (1 h) e invitación (7 días, usado desde el bundle 2); global; solo el SHA-256; `payload` lleva los datos del registro pendiente.

**legal_acceptance** — aceptación versionada de `TERMS` y `DPA` por negocio y cuenta; RLS.

**schema_info** — fila de la migración base; prueba que Flyway corre como `app_owner`.

Entidades JPA: `Business`, `Location` (extiende `BusinessScopedEntity`), `UserAccount`. El resto se lee y escribe con `JdbcClient`.

### Relationships

- `business` —(1:1)→ `subscription`, `business_settings` · ON DELETE CASCADE (no hay borrado de negocios en este bundle; la cascada solo protege la consistencia)
- `business` —(1:N)→ `location`, `membership`, `refresh_token`, `legal_acceptance` · ON DELETE CASCADE
- `user_account` —(1:N)→ `membership`, `refresh_token`, `one_time_token` · ON DELETE CASCADE
- `user_account` —(1:N)→ `legal_acceptance` · sin cascada (la aceptación es evidencia legal)

### Indexes

| Tabla | Índice | Por qué |
|---|---|---|
| business | `slug` único | URL y búsqueda por slug; garantiza unicidad del alta |
| location | `business_id`; único parcial `business_id WHERE is_main` | consultas por negocio; una sola sede principal |
| user_account | `email` único | login |
| membership | `(user_id, business_id)` único; `business_id` | lista de negocios de una cuenta; miembros de un negocio |
| refresh_token | `token_hash` único; `user_id`; `family_id` | refresh; revocación por cuenta y por familia |
| one_time_token | `token_hash` único; `(user_id, purpose)` | consumo; invalidar tokens abiertos |
| legal_acceptance | `business_id` | consulta de aceptaciones del negocio |

### Schema

#### Modelo de datos — DDL literal de las migraciones

Convenciones que aplican a todo el SQL: ids `uuid` asignados por la aplicación (`UUID.randomUUID()`), fechas `timestamptz` en UTC, dinero en `bigint` de unidad menor (sufijo `_minor`), estados como `text` con `CHECK`. Las políticas RLS usan `NULLIF(current_setting('app.business_id', true), '')::uuid`: después de una transacción con `set_config(..., true)` la sesión del pool devuelve `''` (no `NULL`), y `''::uuid` lanzaría error; con `NULLIF` la política simplemente no devuelve filas (falla cerrada).

`V1__shared_baseline.sql` (paso 1):

```sql
CREATE TABLE schema_info (
    key        text PRIMARY KEY,
    value      text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO schema_info (key, value) VALUES ('baseline', 'plataforma-citas');
```

`V2__platform_business.sql` (paso 4):

```sql
CREATE TABLE business (
    id         uuid PRIMARY KEY,
    name       text NOT NULL CHECK (char_length(name) BETWEEN 2 AND 120),
    slug       text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    timezone   text NOT NULL DEFAULT 'America/Bogota',
    currency   char(3) NOT NULL DEFAULT 'COP',
    vertical   text NOT NULL,
    status     text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE subscription (
    business_id        uuid PRIMARY KEY REFERENCES business (id) ON DELETE CASCADE,
    plan_code          text NOT NULL,
    status             text NOT NULL CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
    trial_ends_at      timestamptz,
    current_period_end timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE location (
    id          uuid PRIMARY KEY,
    business_id uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    name        text NOT NULL,
    address     text,
    phone       text,
    is_main     boolean NOT NULL DEFAULT false,
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX location_business_idx ON location (business_id);
CREATE UNIQUE INDEX location_one_main_per_business ON location (business_id) WHERE is_main;

CREATE TABLE business_settings (
    business_id                uuid PRIMARY KEY REFERENCES business (id) ON DELETE CASCADE,
    slot_step_min              integer NOT NULL DEFAULT 15 CHECK (slot_step_min IN (15, 20, 30, 60)),
    buffer_min                 integer NOT NULL DEFAULT 0 CHECK (buffer_min BETWEEN 0 AND 240),
    walk_ins_enabled           boolean NOT NULL DEFAULT true,
    opening_cash_default_minor bigint NOT NULL DEFAULT 0 CHECK (opening_cash_default_minor >= 0),
    tips_enabled               boolean NOT NULL DEFAULT true,
    tips_recipient             text NOT NULL DEFAULT 'EMPLOYEE' CHECK (tips_recipient IN ('EMPLOYEE', 'BUSINESS')),
    max_discount_pct_reception integer NOT NULL DEFAULT 10 CHECK (max_discount_pct_reception BETWEEN 0 AND 100),
    labels                     jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at                 timestamptz NOT NULL DEFAULT now(),
    updated_at                 timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE location ENABLE ROW LEVEL SECURITY;
ALTER TABLE location FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON location
    USING (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE business_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE business_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON business_settings
    USING (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid);
```

`V3__identity_accounts.sql` (paso 6):

```sql
CREATE TABLE user_account (
    id                uuid PRIMARY KEY,
    email             text NOT NULL CHECK (email = lower(email) AND char_length(email) <= 254),
    password_hash     text NOT NULL,
    name              text NOT NULL CHECK (char_length(name) BETWEEN 2 AND 120),
    status            text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    email_verified_at timestamptz,
    platform_role     text CHECK (platform_role IN ('PLATFORM_ADMIN')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX user_account_email_uq ON user_account (email);

CREATE TABLE membership (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    business_id uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    role        text NOT NULL CHECK (role IN ('OWNER', 'RECEPTION', 'EMPLOYEE')),
    resource_id uuid,
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT membership_user_business_uq UNIQUE (user_id, business_id)
);
CREATE INDEX membership_business_idx ON membership (business_id);

ALTER TABLE membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership FORCE ROW LEVEL SECURITY;
-- La cuenta ve sus propias membresías (login, selector) o las del negocio activo; solo escribe en el negocio activo.
CREATE POLICY membership_access ON membership
    USING (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid
           OR user_id = NULLIF(current_setting('app.user_id', true), '')::uuid)
    WITH CHECK (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid);

CREATE TABLE refresh_token (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    business_id uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    family_id   uuid NOT NULL,
    token_hash  text NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_token_user_idx ON refresh_token (user_id);
CREATE INDEX refresh_token_family_idx ON refresh_token (family_id);

CREATE TABLE one_time_token (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    purpose    text NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'INVITATION')),
    token_hash text NOT NULL UNIQUE,
    payload    jsonb NOT NULL DEFAULT '{}'::jsonb,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX one_time_token_user_purpose_idx ON one_time_token (user_id, purpose);
```

`V4__platform_legal_acceptance.sql` (paso 12):

```sql
CREATE TABLE legal_acceptance (
    id          uuid PRIMARY KEY,
    business_id uuid NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    user_id     uuid NOT NULL REFERENCES user_account (id),
    document    text NOT NULL CHECK (document IN ('TERMS', 'DPA')),
    version     text NOT NULL,
    accepted_at timestamptz NOT NULL,
    ip_address  text
);
CREATE INDEX legal_acceptance_business_idx ON legal_acceptance (business_id);

ALTER TABLE legal_acceptance ENABLE ROW LEVEL SECURITY;
ALTER TABLE legal_acceptance FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON legal_acceptance
    USING (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid);
```

Tablas globales sin RLS y por qué: `business` (no tiene `business_id`; la leen el login y el selector), `subscription` (una fila por negocio, la consola de plataforma la recorre en el bundle 6), `user_account` (una cuenta, varios negocios), `refresh_token` (se busca por hash antes de conocer el negocio), `one_time_token` (verificación y restablecimiento ocurren sin negocio activo), `schema_info`. Solo los módulos `identity` y `platform` las tocan.

#### Prueba de catálogo RLS

La usan `RowLevelSecurityIT` (paso 4) y `TenantIsolationIT` (paso 9). Devuelve, para un esquema, cada tabla que tiene columna `business_id` con su estado de RLS. Una tabla está **protegida** si `relrowsecurity` y `relforcerowsecurity` son verdaderos y `policies >= 1`. Lista permitida (globales con `business_id`, sin RLS a propósito): `subscription`, `refresh_token`.

```sql
SELECT c.relname                                              AS table_name,
       c.relrowsecurity                                       AS rls_enabled,
       c.relforcerowsecurity                                  AS rls_forced,
       (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS policies
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind = 'r'
  AND n.nspname = 'public'            -- para el esquema temporal: n.oid = pg_my_temp_schema()
  AND EXISTS (SELECT 1 FROM pg_attribute a
              WHERE a.attrelid = c.oid AND a.attname = 'business_id' AND NOT a.attisdropped)
ORDER BY c.relname;
```

`TenantIsolationIT` usa la misma consulta filtrando `policies >= 1` para obtener la lista de tablas a recorrer, así que cada tabla nueva con `business_id` entra sola en la suite de aislamiento.

### Migrations

Flyway corre al arrancar la API con el usuario `app_owner` (`spring.flyway.user`), separado del DataSource (`app_runtime`). Nombre `V{n}__{modulo}_{descripcion}.sql`; una migración por cambio; nunca se edita una ya confirmada. Toda tabla con `business_id` trae su política RLS en la misma migración. En producción: solo cambios aditivos en el mismo despliegue que el código (expandir → desplegar → contraer en un despliegue posterior), de modo que la imagen anterior siga funcionando para la reversión.

### Seed data

`DemoDataSeeder` (paso 9) se ejecuta al arrancar con `app.seed.demo=true` (siempre en el perfil `dev`; en staging con `APP_SEED_DEMO=true`) y es idempotente: crea «Peluquería Demo Norte» y «Peluquería Demo Sur», `owner@demo.local` (OWNER en ambos) y `recepcion@demo.local` (RECEPTION en Norte), con la contraseña de `DEV_SEED_PASSWORD`. Se ejecuta con `(cd backend && ./mvnw spring-boot:run)` o `bash scripts/smoke-api.sh`. Las pruebas no usan esta siembra: siembran con `support/TestData`.

---

## 5. API Design

### Conventions
- Base path: `/api/v1`; JSON; nombres en inglés.
- Respuesta de éxito: el recurso o DTO directamente (sin envoltorio). Respuesta de error: siempre `application/problem+json` con `type`, `title`, `status`, `detail`, `code`, `requestId` (y `errors` en validación).
- Códigos de error (estado HTTP): `VALIDATION_ERROR` 400, `INVALID_TOKEN` 400, `CAPTCHA_FAILED` 400, `UNAUTHENTICATED` 401, `INVALID_CREDENTIALS` 401, `INVALID_REFRESH_TOKEN` 401, `FORBIDDEN` 403, `EMAIL_NOT_VERIFIED` 403, `NO_ACTIVE_MEMBERSHIP` 403, `NOT_FOUND` 404, `ACCOUNT_EXISTS` 409, `CONFLICT` 409, `SLOT_TAKEN` 409, `CURRENCY_MISMATCH` 422, `RATE_LIMITED` 429, `INTERNAL_ERROR` 500. `BUSINESS_SELECTION_REQUIRED` no es un error: viaja en el cuerpo 200 del login. Los códigos de los documentos para bundles futuros (`CASH_DAY_CLOSED`, `OPEN_SALES_PENDING`…) se agregan con su bundle.
- Validación: Jakarta Bean Validation en los `record` de entrada, con mensajes en español; errores mapeados por `ApiExceptionHandler`.
- Paginación: NOT APPLICABLE en este bundle — no hay listados; los listados del bundle 2 usan cursor (`cursor`, `limit` por defecto 50, máximo 200).
- Idempotencia: NOT APPLICABLE en este bundle — `Idempotency-Key` llega con cobros y cierres (bundle 3).
- Límites: Bucket4j en memoria por IP: 10 fallos de login por minuto; 5 registros por hora; 5 pedidos de recuperación por hora (más 3 por correo por hora, silencioso).
- Cabeceras: `X-Request-Id` en toda respuesta; `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`.

### Routes

La tabla completa, las formas de cada DTO, el contenido del JWT y la cookie están en el contrato siguiente.

#### Endpoints críticos — contrato

Base `/api/v1`, JSON, nombres en inglés. Todo error es `application/problem+json`:

```json
{ "type": "about:blank", "title": "Unauthorized", "status": 401, "detail": "Correo o contraseña incorrectos.",
  "code": "INVALID_CREDENTIALS", "requestId": "0f6c2a8e-…" }
```

`VALIDATION_ERROR` agrega `"errors": [{ "field": "email", "message": "debe ser un correo válido" }]`. `code` es estable (los clientes ramifican por él); `detail` es texto en español para personas y puede cambiar.

| Método | Ruta | Nombre (operationId) | Acceso | Límite | Respuesta OK | Errores |
|---|---|---|---|---|---|---|
| GET | `/actuator/health` | — | público | — | 200 `{"status":"UP"}` | 503 |
| GET | `/api/v1/public/config` | `publicConfig` | público | — | 200 `PublicConfig` | — |
| POST | `/api/v1/auth/login` | `login` | público | 10 fallos/min/IP | 200 `LoginResponse` + cookie `rt` si `AUTHENTICATED` | 400, 401 `INVALID_CREDENTIALS`, 403 `EMAIL_NOT_VERIFIED`, 403 `NO_ACTIVE_MEMBERSHIP`, 429 `RATE_LIMITED` |
| POST | `/api/v1/auth/select-business` | `selectBusiness` | preauth token | — | 200 `SessionResponse` + cookie `rt` | 401, 403 `FORBIDDEN` |
| POST | `/api/v1/auth/switch-business` | `switchBusiness` | token de acceso | — | 200 `SessionResponse` + cookie `rt` | 401, 403 `FORBIDDEN` |
| POST | `/api/v1/auth/refresh` | `refresh` | cookie `rt` | — | 200 `SessionResponse` + cookie `rt` rotada | 401 `INVALID_REFRESH_TOKEN` |
| POST | `/api/v1/auth/logout` | `logout` | público (cookie opcional) | — | 204 + cookie `rt` borrada | — |
| GET | `/api/v1/auth/me` | `me` | token de acceso | — | 200 `MeResponse` | 401 |
| POST | `/api/v1/auth/password/forgot` | `forgotPassword` | público | 5/h/IP · 3/h/correo (silencioso) | 202 sin cuerpo | 400, 429 `RATE_LIMITED` |
| POST | `/api/v1/auth/password/reset` | `resetPassword` | público (token) | — | 204 | 400 `INVALID_TOKEN`, 400 `VALIDATION_ERROR` |
| POST | `/api/v1/signup` | `signup` | público + captcha | 5/h/IP | 202 `{"status":"VERIFICATION_SENT"}` | 400 `VALIDATION_ERROR`, 400 `CAPTCHA_FAILED`, 409 `ACCOUNT_EXISTS`, 429 `RATE_LIMITED` |
| POST | `/api/v1/signup/verify` | `verifySignup` | público (token) | — | 200 `SessionResponse` + cookie `rt` | 400 `INVALID_TOKEN`, 500 `INTERNAL_ERROR` |
| POST | `/api/v1/businesses` | `createBusiness` | token de acceso (cualquier rol) | — | 201 `BusinessOption` | 400, 401 |
| GET | `/api/v1/business` | `getBusiness` | token de acceso (cualquier rol) | — | 200 `BusinessResponse` | 401 |
| PUT | `/api/v1/business` | `updateBusiness` | token de acceso, rol `OWNER` | — | 200 `BusinessResponse` | 400 `VALIDATION_ERROR`, 401, 403 `FORBIDDEN` |

Formas (records Java anidados en su controlador; springdoc usa el nombre simple):

```text
LoginRequest        { email: string (correo, ≤254), password: string (1..128) }
LoginResponse       { status: "AUTHENTICATED" | "BUSINESS_SELECTION_REQUIRED",
                      code: null | "BUSINESS_SELECTION_REQUIRED",
                      session: SessionResponse | null,
                      preAuthToken: string | null,            // JWT typ=preauth, 2 min
                      businesses: BusinessOption[] | null }
SessionResponse     { accessToken: string, tokenType: "Bearer", expiresIn: 900,
                      business: BusinessOption }
BusinessOption      { id: uuid, name: string, slug: string, role: "OWNER" | "RECEPTION" | "EMPLOYEE" }
SelectBusinessRequest { businessId: uuid }                    // también para switch-business
MeResponse          { user: UserSummary, business: BusinessOption, businesses: BusinessOption[] }
UserSummary         { id: uuid, email: string, name: string }
ForgotPasswordRequest { email: string }
ResetPasswordRequest  { token: string, newPassword: string (10..128) }
SignupRequest       { businessName: string (2..120), vertical: "PELUQUERIA", timezone: string (ZoneId),
                      ownerName: string (2..120), email: string, password: string (10..128),
                      acceptTerms: true, acceptDpa: true, captchaToken: string }
SignupAccepted      { status: "VERIFICATION_SENT" }
VerifySignupRequest { token: string }
CreateBusinessRequest { businessName: string, vertical: "PELUQUERIA", timezone: string,
                        acceptTerms: true, acceptDpa: true }
PublicConfig        { captchaSiteKey: string (vacío = sin widget), termsVersion: string, dpaVersion: string }
BusinessResponse    { id, name, slug, timezone, currency, vertical, status, trialEndsAt: instant,
                      labels: { [clave: string]: string } }
UpdateBusinessRequest { name: string (2..120), timezone: string (ZoneId), currency: string (ISO 4217) }
```

Token de acceso (JWT HS256, cabecera `typ: JWT`): `iss=plataforma-citas`, `sub`=id de cuenta, `bid`=id de negocio, `role`, `typ=access`, `iat`, `exp=iat+15 min`. Preauth: `typ=preauth`, sin `bid`, 2 min, solo vale para `select-business`. Cookie de refresh: `rt=<opaco base64url de 32 bytes>; HttpOnly; Secure (salvo perfiles dev/test); SameSite=Strict; Path=/api/v1/auth; Max-Age=1209600` (14 días); en base solo su SHA-256; rotación en cada uso y reutilización ⇒ revocación de la familia.

Efectos secundarios: `signup` crea `user_account` sin verificar + `one_time_token` `VERIFY_EMAIL` (24 h) y envía «Confirma tu correo» después del commit; `verifySignup` crea en una transacción negocio, sede principal, ajustes del vertical, membresía `OWNER`, suscripción `TRIAL` (+30 días) y `legal_acceptance` `TERMS` y `DPA`; `forgotPassword` crea `RESET_PASSWORD` (1 h) y envía «Restablece tu contraseña»; `resetPassword` revoca todos los refresh tokens de la cuenta.

### Critical endpoints — full detail

Los cinco endpoints sin los que el producto no funciona y su comportamiento exacto:

1. **`POST /api/v1/auth/login`** — límite por IP antes de mirar la contraseña; correo inexistente y contraseña incorrecta dan el mismo 401 con el mismo `detail` y tiempo de respuesta comparable (se ejecuta Argon2 contra un hash fijo); correo sin verificar → 403 solo después de validar la contraseña; una membresía activa → sesión directa; varias → `BUSINESS_SELECTION_REQUIRED` con preauth de 2 minutos.
2. **`POST /api/v1/auth/refresh`** — rotación en cada uso; reutilización de un token ya rotado revoca toda la familia; membresía desactivada → 401.
3. **`POST /api/v1/signup`** — orden: límite → validación → captcha → correo existente → (transacción) cuenta sin verificar + token + correo después del commit.
4. **`POST /api/v1/signup/verify`** — una sola transacción dentro del negocio nuevo: token → correo verificado → negocio → sede principal → ajustes del vertical → membresía `OWNER` → suscripción `TRIAL` → aceptaciones `TERMS`/`DPA` → token usado; si algo falla no queda nada y el token sigue vigente.
5. **`GET/PUT /api/v1/business`** — siempre el negocio del token; `PUT` solo `OWNER`; zona y moneda validadas.

### Script de deriva del contrato

#### Script de deriva del contrato

`scripts/check-api-drift.sh` (paso 13). Regenera el contrato y el cliente en `.tmp/api-drift/` y los compara con lo versionado; no depende del estado de git.

```bash
#!/usr/bin/env bash
# Verifica que frontend/openapi/openapi.json y frontend/src/app/api/ coinciden con el backend actual.
# Salida: 0 = sin deriva · 1 = hay deriva (regenera y confirma) · 2 = error de uso o de herramienta.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2
if [ "$#" -ne 0 ]; then
  echo "uso: bash scripts/check-api-drift.sh" >&2
  exit 2
fi
WORK=.tmp/api-drift
rm -rf "$WORK" && mkdir -p "$WORK" || exit 2
if ! (cd backend && ./mvnw -B -ntp -q test -Dtest=OpenApiExportIT -Dopenapi.export=true "-Dopenapi.output=../$WORK/openapi.json"); then
  echo "no se pudo exportar el contrato OpenAPI" >&2
  exit 2
fi
test -s "$WORK/openapi.json" || { echo "el contrato exportado está vacío" >&2; exit 2; }
if ! (cd frontend && npx ng-openapi-gen -c ng-openapi-gen.json --input "../$WORK/openapi.json" --output "../$WORK/api" >/dev/null); then
  echo "ng-openapi-gen falló" >&2
  exit 2
fi
drift=0
diff -q "$WORK/openapi.json" frontend/openapi/openapi.json >/dev/null || { echo "Deriva: frontend/openapi/openapi.json no coincide con el backend" >&2; drift=1; }
diff -r -q "$WORK/api" frontend/src/app/api >/dev/null || { echo "Deriva: frontend/src/app/api no coincide con el contrato" >&2; drift=1; }
if [ "$drift" -eq 0 ]; then
  echo "Contrato y cliente al día"
else
  echo "Regenera: (cd backend && ./mvnw -B -ntp test -Dtest=OpenApiExportIT -Dopenapi.export=true) && (cd frontend && npx ng-openapi-gen -c ng-openapi-gen.json)" >&2
fi
exit "$drift"
```

---

## 6. Frontend Architecture

### Routes

#### Diccionario de errores

`ERROR_MESSAGES` en `frontend/src/app/core/ui-text.ts` (paso 15). Un código de dominio nuevo en el backend exige su fila aquí (Definition of Done).

| `code` | Mensaje mostrado |
|---|---|
| `VALIDATION_ERROR` | Revisa los datos marcados en el formulario. |
| `INVALID_CREDENTIALS` | Correo o contraseña incorrectos. |
| `EMAIL_NOT_VERIFIED` | Confirma tu correo antes de ingresar. Revisa tu bandeja de entrada. |
| `NO_ACTIVE_MEMBERSHIP` | Tu cuenta no tiene acceso activo a ningún negocio. |
| `BUSINESS_SELECTION_REQUIRED` | Elige el negocio con el que vas a trabajar. |
| `UNAUTHENTICATED` | Tu sesión terminó. Ingresa de nuevo. |
| `INVALID_REFRESH_TOKEN` | Tu sesión terminó. Ingresa de nuevo. |
| `FORBIDDEN` | No tienes permiso para hacer esto. |
| `NOT_FOUND` | No encontramos lo que buscas. |
| `ACCOUNT_EXISTS` | Ya existe una cuenta con ese correo. Ingresa y usa «Crear otro negocio». |
| `INVALID_TOKEN` | El enlace no es válido o ya venció. |
| `CAPTCHA_FAILED` | No pudimos verificar que eres una persona. Intenta de nuevo. |
| `RATE_LIMITED` | Demasiados intentos. Espera un momento y vuelve a intentar. |
| `SLOT_TAKEN` | Ese horario acaba de ser tomado. Elige otro. |
| `CONFLICT` | Alguien cambió estos datos al mismo tiempo. Recarga e intenta de nuevo. |
| `CURRENCY_MISMATCH` | No se pueden combinar montos en monedas distintas. |
| `INTERNAL_ERROR` | Ocurrió un error inesperado. Intenta de nuevo. |

Respaldo por estado HTTP cuando el código no está: 0 → `Sin conexión con el servidor. Revisa tu internet.`; 400 → mensaje de `VALIDATION_ERROR`; 401 → mensaje de `UNAUTHENTICATED`; 403 → mensaje de `FORBIDDEN`; 404 → mensaje de `NOT_FOUND`; 409 → mensaje de `CONFLICT`; 429 → mensaje de `RATE_LIMITED`; cualquier otro → mensaje de `INTERNAL_ERROR`.

#### Contrato de pantallas

Textos exactos (las pruebas unitarias y la E2E del paso 17 los usan como selectores; `<mat-label>` da el nombre accesible a cada campo).

| Pantalla (ruta) | Encabezado `h1` | Campos (`mat-label`) | Casillas | Botón principal | Otros textos |
|---|---|---|---|---|---|
| Ingreso `/ingresar` | `Ingresar` | `Correo electrónico`, `Contraseña` | — | `Ingresar` | enlaces `¿Olvidaste tu contraseña?`, `Crear una cuenta` |
| Elegir negocio `/elegir-negocio` | `Elige un negocio` | — | — | un botón por negocio con su nombre | — |
| Registro `/registro` | `Crea tu negocio` | `Nombre del negocio`, `Tipo de negocio`, `Zona horaria`, `Tu nombre`, `Correo electrónico`, `Contraseña` | `Acepto los términos de servicio`, `Acepto el acuerdo de encargo de datos` | `Crear mi negocio` | tras enviar: `h1` `Revisa tu correo` |
| Verificar `/verificar-correo` | `Verificando tu correo` | — | — | — | error: `El enlace no es válido o ya venció.` |
| Recuperar `/recuperar-contrasena` | `Recuperar contraseña` | `Correo electrónico` | — | `Enviar enlace` | tras enviar: `Si el correo está registrado, te enviamos un enlace.` |
| Restablecer `/restablecer-contrasena` | `Nueva contraseña` | `Nueva contraseña` | — | `Guardar contraseña` | — |
| Crear negocio `/crear-negocio` | `Crear otro negocio` | `Nombre del negocio`, `Tipo de negocio`, `Zona horaria` | las dos de Registro | `Crear negocio` | — |
| Inicio `/inicio` | nombre del negocio | — | — | — | `Tu prueba gratuita vence el <fecha larga es-CO>` |
| Mi negocio `/mi-negocio` | `Mi negocio` | — | — | — | nombre, zona horaria, moneda, fin de la prueba |
| Barra superior (shell) | — | selector `Negocio` (solo con 2+ negocios) | — | botón de menú `Cuenta` → `Crear otro negocio`, `Cerrar sesión` | enlace `Saltar al contenido`; menú lateral `Inicio`, `Mi negocio` (solo `OWNER`) |

Mensajes de error bajo cada campo y el mensaje general del formulario en un elemento con `role="alert"`; nunca solo color.

#### Rutas

| Ruta | Componente (`loadComponent`) | Acceso |
|---|---|---|
| `ingresar` | `LoginPage` — `features/auth/auth-pages.ts` | público |
| `elegir-negocio` | `SelectBusinessPage` | público (requiere preauth pendiente; si no, `/ingresar`) |
| `registro` | `SignupPage` | público |
| `verificar-correo` | `VerifyEmailPage` | público |
| `recuperar-contrasena` | `ForgotPasswordPage` | público |
| `restablecer-contrasena` | `ResetPasswordPage` | público |
| `''` (shell) | `Shell` — `layout/shell.ts` | `authGuard` |
| `inicio` (hija del shell) | `HomePage` — `layout/shell.ts` | `authGuard` |
| `mi-negocio` (hija) | `BusinessPage` — `layout/shell.ts` | `authGuard` + `roleGuard` con `data: { roles: ['OWNER'] }` |
| `crear-negocio` (hija) | `CreateBusinessPage` — `features/auth/auth-pages.ts` | `authGuard` |
| `**` | redirige a `inicio` | — |

### Rendering strategy

Aplicación de una sola página renderizada en el cliente (`ng new --ssr=false`): toda la aplicación vive detrás de inicio de sesión y no hay páginas públicas de contenido que indexar. Build de producción `npx ng build` → `frontend/dist/frontend/browser/`, servido por Caddy con `try_files {path} /index.html`. Sin prerender ni SSR en este bundle. `inlineCritical: false` por la CSP de Caddy (§12). Cada pantalla es un `loadComponent` perezoso.

### Component hierarchy

```
App (router-outlet)
├── LoginPage / SelectBusinessPage / SignupPage / VerifyEmailPage / ForgotPasswordPage / ResetPasswordPage   (públicas)
└── Shell (authGuard)
    ├── barra superior: nombre del negocio · selector «Negocio» (2+ negocios) · menú «Cuenta» (Crear otro negocio, Cerrar sesión)
    ├── mat-sidenav (menú por rol: Inicio, Mi negocio [OWNER])
    └── <main id="contenido"> router-outlet
        ├── HomePage        (h1 = negocio, fin de la prueba, etiquetas del vertical)
        ├── BusinessPage    (roleGuard OWNER; GET /api/v1/business)
        └── CreateBusinessPage
```

### State management

- Estado del servidor: llamadas con el cliente generado (`Api.invoke`), resultados en `signal` locales del componente; sin caché global.
- Sesión: `AuthStore` (signals) con token de acceso **solo en memoria**, negocio activo, lista de negocios y preauth pendiente; se recupera al cargar con `restore()` (cookie `rt` + `GET /auth/me`).
- Etiquetas del vertical: `LabelsService` (signal) cargado desde `GET /api/v1/business`; se reinicia y recarga al cambiar de negocio, que también navega a `/inicio` para descartar datos del negocio anterior.
- Formularios: formularios reactivos tipados; sin NgRx ni store global adicional.

### Loading, empty, and error states

| Superficie | Cargando | Vacío | Error |
|---|---|---|---|
| Arranque (`restore`) | `mat-progress-bar` en la parte superior | sin sesión → pantallas públicas | sin red → snackbar con el mensaje de estado 0 |
| Formularios (login, registro, recuperación, crear negocio) | botón deshabilitado con `mat-progress-spinner` dentro | — | mensaje en `role="alert"` con `messageFor` |
| Elegir negocio | — | sin preauth → redirige a `/ingresar` | — |
| Verificar correo | `h1` «Verificando tu correo» + spinner | — | `El enlace no es válido o ya venció.` + enlace a `/registro` |
| Inicio / Mi negocio | esqueleto de tarjeta | — | snackbar (`errorToastInterceptor`) y botón «Reintentar» |

---

## 7. Design System

Sistema propio y moderno (decisión de la entrevista); `mapa-funcional.html` no es referencia visual. Fuente única: `frontend/src/styles/_tokens.scss`. Tema de Angular Material M3 con paletas `violet` (primaria) y `cyan` (terciaria) y `theme-type: color-scheme`, que sigue el modo claro/oscuro del sistema o `data-theme` en `<html>`.

### Colors

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `--c-primary` | `#5B4FE9` | `#8B83FF` | botones principales, enlaces, anillo de foco |
| `--c-primary-hover` | `#4A3FD6` | `#A39DFF` | hover de primario |
| `--c-on-primary` | `#FFFFFF` | `#0E1018` | texto sobre primario |
| `--c-bg` | `#F7F8FC` | `#0E1018` | fondo de página |
| `--c-surface` | `#FFFFFF` | `#161A25` | tarjetas, paneles, diálogos |
| `--c-surface-2` | `#F1F3F9` | `#1E2331` | zonas secundarias, filas alternas |
| `--c-border` | `#E3E6EF` | `#2A3042` | divisores decorativos |
| `--c-border-strong` | `#767D90` | `#6B748C` | bordes de controles (≥ 3:1) |
| `--c-text` | `#141826` | `#EEF0F6` | texto |
| `--c-text-muted` | `#5B6275` | `#9AA2B5` | texto secundario |
| `--c-accent` | `#0FA3A3` | `#2CC7C7` | acentos, indicadores |
| `--c-success` / `--c-success-text` | `#12A150` / `#0B7A3B` | `#3DD68C` / `#3DD68C` | confirmaciones (el texto usa `-text`) |
| `--c-warning` / `--c-warning-text` | `#D97706` / `#9A5404` | `#F5A524` / `#F5A524` | avisos |
| `--c-danger` / `--c-danger-text` | `#E5484D` / `#C42F35` | `#FF6B6F` / `#FF6B6F` | errores, acciones destructivas |
| `--c-info` / `--c-info-text` | `#2F80ED` / `#1F66C9` | `#5EA2FF` / `#5EA2FF` | información |

**Contraste:** los pares de texto cumplen 4.5:1 y los de interfaz 3:1 en claro y oscuro (lo comprueba `scripts/check-contrast.mjs`). Los tres pares más justos, medidos: `--c-text-muted` sobre `--c-surface-2` claro **5.49:1**; `--c-primary` sobre `--c-bg` claro **5.29:1**; `--c-accent` sobre `--c-surface` claro **3.09:1** (solo como color de interfaz, nunca para texto). La paleta de la entrevista dejaba los colores de estado en claro por debajo de 4.5:1 como texto (`#E5484D` = 3.91:1), por eso se agregaron las variantes `-text`; y `--c-border` (1.25:1) es decorativo, por eso existe `--c-border-strong` para controles.

### Typography

| Rol | Familia | Tamaño / interlínea | Peso | Tracking |
|---|---|---|---|---|
| Display | Inter Variable | 32 px / 1.2 | 700 | -0.01em |
| Heading | Inter Variable | 24 px (h1) · 20 px (h2) / 1.25–1.3 | 650 · 600 | 0 |
| Body | Inter Variable | 16 px / 1.5 (14 px en tablas densas) | 400 | 0 |
| Mono | `ui-monospace, 'Cascadia Code', Menlo, monospace` | 14 px / 1.5 | 400 | 0 |

**Carga de fuentes:** `@fontsource-variable/inter` autohospedada (sin CDN), `font-display: swap`, archivo `index.css` en los `styles` de `angular.json`; respaldo `system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif`. Dinero y horas con `font-variant-numeric: tabular-nums`.

### Spacing, radius, elevation
- Espacio: base 4 px — 4, 8, 12, 16, 24, 32, 48.
- Radio: 10 px controles, 14 px tarjetas, 50 % avatares.
- Sombras: `--shadow-1: 0 1px 2px rgb(20 24 38 / 0.06), 0 1px 3px rgb(20 24 38 / 0.08)`; `--shadow-2: 0 4px 12px rgb(20 24 38 / 0.10)`.
- Ancho máximo de contenido 1200 px · Breakpoints: 600 px (tableta), 1024 px (escritorio, tablas densas −2), 1440 px. Diseño desde 360 px, sin scroll horizontal a 375 px.

### Motion

150 ms para hover y foco, 200 ms para paneles y menús, `cubic-bezier(0.2, 0, 0, 1)`; solo `transform`, `opacity` y colores. Con `prefers-reduced-motion: reduce` toda animación y transición queda en 0.01 ms.

### Component style

Superficies limpias con borde fino y sombra mínima, un solo color de acción (violeta) y mucho espacio en blanco en celular; en escritorio, densidad mayor para recepción. Los componentes de Material se usan tal cual con el tema M3; lo propio se hace con los tokens. Un componente nuevo pertenece si usa solo tokens, tiene foco visible y funciona a 360 px.

#### Archivo de tokens, hoja global y script de contraste

#### Archivo de tokens

`frontend/src/styles/_tokens.scss` (paso 14) — fuente única de color, tipografía, espacio, radio y movimiento. `scripts/check-contrast.mjs` lee los bloques `@mixin light-tokens` y `@mixin dark-tokens` de este archivo.

```scss
// Tokens del sistema de diseño (blueprint §7). Componentes: solo var(--…), nunca hex ni px sueltos.
// Claro por defecto; oscuro por preferencia del sistema o con [data-theme='dark'] en <html>.

@mixin light-tokens {
  --c-bg: #F7F8FC;
  --c-surface: #FFFFFF;
  --c-surface-2: #F1F3F9;
  --c-border: #E3E6EF;
  --c-border-strong: #767D90;
  --c-text: #141826;
  --c-text-muted: #5B6275;
  --c-primary: #5B4FE9;
  --c-primary-hover: #4A3FD6;
  --c-on-primary: #FFFFFF;
  --c-accent: #0FA3A3;
  --c-success: #12A150;
  --c-warning: #D97706;
  --c-danger: #E5484D;
  --c-info: #2F80ED;
  --c-success-text: #0B7A3B;
  --c-warning-text: #9A5404;
  --c-danger-text: #C42F35;
  --c-info-text: #1F66C9;
}

@mixin dark-tokens {
  --c-bg: #0E1018;
  --c-surface: #161A25;
  --c-surface-2: #1E2331;
  --c-border: #2A3042;
  --c-border-strong: #6B748C;
  --c-text: #EEF0F6;
  --c-text-muted: #9AA2B5;
  --c-primary: #8B83FF;
  --c-primary-hover: #A39DFF;
  --c-on-primary: #0E1018;
  --c-accent: #2CC7C7;
  --c-success: #3DD68C;
  --c-warning: #F5A524;
  --c-danger: #FF6B6F;
  --c-info: #5EA2FF;
  --c-success-text: #3DD68C;
  --c-warning-text: #F5A524;
  --c-danger-text: #FF6B6F;
  --c-info-text: #5EA2FF;
}

:root {
  @include light-tokens;
  color-scheme: light;

  --font-sans: 'Inter Variable', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  --fs-12: 0.75rem;
  --fs-14: 0.875rem;
  --fs-16: 1rem;
  --fs-20: 1.25rem;
  --fs-24: 1.5rem;
  --fs-32: 2rem;

  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-6: 24px;
  --space-8: 32px;
  --space-12: 48px;

  --radius-control: 10px;
  --radius-card: 14px;

  --shadow-1: 0 1px 2px rgb(20 24 38 / 0.06), 0 1px 3px rgb(20 24 38 / 0.08);
  --shadow-2: 0 4px 12px rgb(20 24 38 / 0.10);

  --motion-fast: 150ms;
  --motion-base: 200ms;
  --ease-out: cubic-bezier(0.2, 0, 0, 1);

  --content-max: 1200px;
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme='light']) {
    @include dark-tokens;
    color-scheme: dark;
  }
}

:root[data-theme='dark'] {
  @include dark-tokens;
  color-scheme: dark;
}
```

#### Hoja global

`frontend/src/styles.scss` (paso 14) — reemplaza el archivo del scaffold. La fuente Inter Variable la carga `angular.json` (`@fontsource-variable/inter/index.css`, `font-display: swap`, servida desde el propio sitio).

```scss
@use '@angular/material' as mat;
@use './styles/tokens';

html {
  @include mat.theme((
    color: (
      primary: mat.$violet-palette,
      tertiary: mat.$cyan-palette,
      theme-type: color-scheme,
    ),
    typography: 'Inter Variable',
    density: 0,
  ));
}

:root[data-theme='light'] {
  color-scheme: light;
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body {
  height: 100%;
}

body {
  margin: 0;
  background: var(--c-bg);
  color: var(--c-text);
  font-family: var(--font-sans);
  font-size: var(--fs-16);
  line-height: 1.5;
}

h1 {
  font-size: var(--fs-24);
  line-height: 1.25;
  font-weight: 650;
  margin: 0 0 var(--space-4);
}

h2 {
  font-size: var(--fs-20);
  line-height: 1.3;
  font-weight: 600;
  margin: 0 0 var(--space-3);
}

a {
  color: var(--c-primary);
}

.tabular,
.money,
.time {
  font-variant-numeric: tabular-nums;
}

.card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-1);
  padding: var(--space-6);
}

.muted {
  color: var(--c-text-muted);
}

.error-text {
  color: var(--c-danger-text);
}

.skip-link {
  position: absolute;
  left: var(--space-2);
  top: -48px;
  padding: var(--space-2) var(--space-4);
  background: var(--c-surface);
  border-radius: var(--radius-control);
  z-index: 1000;
}

.skip-link:focus {
  top: var(--space-2);
}

:focus-visible {
  outline: 2px solid var(--c-primary);
  outline-offset: 2px;
}

button,
a,
input,
select,
textarea {
  transition:
    background-color var(--motion-fast) var(--ease-out),
    border-color var(--motion-fast) var(--ease-out),
    color var(--motion-fast) var(--ease-out);
}

// Tablas densas en escritorio (recepción); cómodas en celular.
@media (min-width: 1024px) {
  .dense-table {
    @include mat.theme((density: -2));
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

`@use './styles/tokens'` resuelve `src/styles/_tokens.scss` (parcial de Sass, relativo a `src/styles.scss`). `mat.theme`, `mat.$violet-palette` y `mat.$cyan-palette` están exportados por `@angular/material@22.2.2` (`_index.scss`); llamar `mat.theme` solo con `density` dentro de un selector emite únicamente los tokens de densidad. El paso 14 compila esta hoja con `npx ng build`.

#### Script de contraste

`scripts/check-contrast.mjs` (paso 14). Calcula la razón de contraste WCAG 2.2 de cada par de tokens, en claro y en oscuro.

```js
// Contraste WCAG 2.2 AA de los tokens de color (frontend/src/styles/_tokens.scss).
// Texto: >= 4.5:1. Componentes de interfaz y foco: >= 3:1.
// Salida: 0 = todos cumplen · 1 = algún par falla · 2 = falta el archivo o un token.
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const file = resolve(dirname(fileURLToPath(import.meta.url)), '../frontend/src/styles/_tokens.scss');
if (!existsSync(file)) {
  console.error(`no existe ${file}`);
  process.exit(2);
}
const scss = readFileSync(file, 'utf8');

function tokens(mixin) {
  const match = new RegExp(`@mixin ${mixin}\\s*\\{([^}]*)\\}`).exec(scss);
  if (!match) {
    console.error(`no se encontró @mixin ${mixin}`);
    process.exit(2);
  }
  const map = {};
  for (const [, name, hex] of match[1].matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    map[name] = hex;
  }
  return map;
}

function luminance(hex) {
  const channel = (i) => {
    const v = parseInt(hex.slice(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
}

function ratio(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const TEXT = 4.5;
const UI = 3.0;
const pairs = [
  ['c-text', 'c-bg', TEXT],
  ['c-text', 'c-surface', TEXT],
  ['c-text', 'c-surface-2', TEXT],
  ['c-text-muted', 'c-bg', TEXT],
  ['c-text-muted', 'c-surface', TEXT],
  ['c-text-muted', 'c-surface-2', TEXT],
  ['c-on-primary', 'c-primary', TEXT],
  ['c-on-primary', 'c-primary-hover', TEXT],
  ['c-primary', 'c-bg', TEXT],
  ['c-primary', 'c-surface', TEXT],
  ['c-danger-text', 'c-surface', TEXT],
  ['c-success-text', 'c-surface', TEXT],
  ['c-warning-text', 'c-surface', TEXT],
  ['c-info-text', 'c-surface', TEXT],
  ['c-border-strong', 'c-surface', UI],
  ['c-border-strong', 'c-bg', UI],
  ['c-danger', 'c-surface', UI],
  ['c-success', 'c-surface', UI],
  ['c-warning', 'c-surface', UI],
  ['c-accent', 'c-surface', UI],
];

let failures = 0;
for (const mode of ['light', 'dark']) {
  const t = tokens(`${mode}-tokens`);
  for (const [fg, bg, min] of pairs) {
    if (!t[fg] || !t[bg]) {
      console.error(`${mode}: falta --${fg} o --${bg}`);
      process.exit(2);
    }
    const r = ratio(t[fg], t[bg]);
    const ok = r >= min;
    if (!ok) failures += 1;
    console.log(`${ok ? 'OK  ' : 'FALLA'} ${mode.padEnd(5)} --${fg} sobre --${bg}: ${r.toFixed(2)}:1 (mínimo ${min}:1)`);
  }
}
if (failures > 0) {
  console.error(`${failures} par(es) no cumplen WCAG 2.2 AA`);
  process.exit(1);
}
console.log('Contraste OK');
```

---

## 8. Authentication & Authorization

### Provider and rationale

Autenticación propia con Spring Security 7: el modelo central —una cuenta global, varias membresías, un rol por negocio, el negocio activo dentro del token— es el producto, y un proveedor alojado obligaría a mantener dos fuentes de verdad para membresías y roles. Contraseñas con Argon2id (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`), JWT de acceso HS256 de 15 minutos firmado con `NimbusJwtEncoder`, refresh opaco rotativo de 14 días en cookie.

### Flows

- **Registro:** `/registro` → `POST /signup` (límite, captcha Turnstile, correo existente → 409 «Ingresa y usa Crear otro negocio») → cuenta sin verificar + correo «Confirma tu correo» (24 h) → `/verificar-correo?token=` → `POST /signup/verify` (una transacción: negocio, sede, ajustes, OWNER, prueba, aceptaciones) → sesión → `/inicio`. Token usado o vencido → «El enlace no es válido o ya venció.» y se puede registrar de nuevo solo con otro correo (un correo ya usado debe iniciar sesión; si no verificó, su cuenta queda pendiente — limitación conocida, §20.2).
- **Ingreso:** `/ingresar` → `POST /auth/login` → una membresía: sesión; varias: `/elegir-negocio` → `POST /auth/select-business` con el preauth; correo sin verificar: mensaje; 10 fallos/min/IP: 429.
- **Cambio de negocio:** selector de la barra superior → `POST /auth/switch-business` → nueva sesión con el rol de ese negocio; se descartan datos del negocio anterior.
- **Crear otro negocio:** menú «Cuenta» → `/crear-negocio` → `POST /businesses` → cambio automático al negocio nuevo.
- **Recuperación:** `/recuperar-contrasena` → `POST /auth/password/forgot` (202 siempre) → correo «Restablece tu contraseña» (1 h) → `/restablecer-contrasena?token=` → `POST /auth/password/reset` → se revocan todas las sesiones → `/ingresar`.
- **Expiración:** el interceptor intenta un `refresh` ante un 401 y reintenta una vez; si falla, `/ingresar?volver=<url>`.
- **Cierre de sesión:** `POST /auth/logout` revoca la familia del refresh y borra la cookie.
- **Borrado de cuenta:** NOT APPLICABLE en este bundle — exportación y borrado llegan en el bundle 7; mientras tanto, procedimiento manual de la plataforma.

### Route protection

| Superficie | Regla | Dónde se aplica |
|---|---|---|
| `/actuator/health`, `/api/v1/public/**`, `/v3/api-docs/**` (desactivado en `prod`) | público | `shared/security/SecurityConfig.java` |
| `POST /api/v1/auth/{login,refresh,logout,password/forgot,password/reset}`, `POST /api/v1/signup`, `POST /api/v1/signup/verify` | público con límites | `SecurityConfig.java` + `RateLimiter` |
| `POST /api/v1/auth/select-business` | autoridad `PREAUTH` | `SecurityConfig.java` |
| Todo lo demás bajo `/api/**` | autoridad `TENANT` (token de acceso) | `SecurityConfig.java` (`anyRequest().hasAuthority("TENANT")`) |
| `PUT /api/v1/business` | rol `OWNER` | `@PreAuthorize` en `platform/internal/BusinessController.java` |
| Rutas del shell (frontend) | sesión | `authGuard` en `frontend/src/app/core/auth.ts` (cosmético) |
| `/mi-negocio` (frontend) | `OWNER` | `roleGuard` (cosmético) |

**Regla de aplicación:** la autorización se comprueba en el servidor en cada petición. Las guardas del cliente son cosméticas y nunca la única comprobación.

### Roles and permissions

| Rol | Puede | No puede |
|---|---|---|
| `OWNER` | ver y editar los datos de su negocio, crear otros negocios, cambiar de negocio | ver o tocar negocios donde no tiene membresía |
| `RECEPTION` | ver los datos del negocio, crear otros negocios propios | editar los datos del negocio (403) |
| `EMPLOYEE` | ver los datos del negocio, crear otros negocios propios | editar los datos del negocio (403) |
| `PLATFORM_ADMIN` (campo de cuenta) | — en este bundle (la consola llega en el bundle 6) | — |

### Sessions

Access token JWT en memoria del navegador (nunca `localStorage`), 15 minutos. Refresh opaco en cookie `rt`: `HttpOnly`, `Secure` (salvo `dev`/`test`), `SameSite=Strict`, `Path=/api/v1/auth`, 14 días, rotación en cada uso con detección de reutilización por familia, ligado a la pareja cuenta–negocio. CSRF: la API no usa cookies para autenticar peticiones de negocio (Bearer); la única cookie es `SameSite=Strict` y con ruta limitada a `/api/v1/auth`, y el origen es el mismo (Caddy), por eso CSRF de Spring está deshabilitado.

### Multi-tenancy / row-level isolation

Dos barreras independientes:
1. **Aplicación:** `TenantBindingFilter` fija `TenantContext` desde el JWT; Hibernate `@TenantId` en `BusinessScopedEntity` filtra y rellena `business_id`; el negocio nunca viene del cuerpo de la petición.
2. **Base de datos:** la API se conecta como `app_runtime` (`NOSUPERUSER NOBYPASSRLS`, no dueña); cada transacción ejecuta `set_config('app.business_id', …, true)` y `set_config('app.user_id', …, true)` (locales a la transacción, sin fuga por el pool); toda tabla con `business_id` tiene `FORCE ROW LEVEL SECURITY` y política `USING`/`WITH CHECK`. Sin negocio fijado no hay filas y las inserciones fallan (falla cerrada). Procesos sin JWT usan `TenantContext.run(negocio, cuenta, trabajo)`.

Lo prueban `RowLevelSecurityIT` (catálogo + falla cerrada + no fuga entre transacciones) y `TenantIsolationIT` (cada tabla de negocio, cada endpoint).

---

## 9. BUILD ORDER

**Esta es la sección que todo el blueprint existe para producir.** Un constructor que sigue §9 al pie de la letra y se detiene cuando cada gate queda en verde entrega el bundle.

### Las reglas de un paso

1. **Una sesión por paso:** máximo 5 archivos escritos y 6 criterios de aceptación.
2. Todo paso tiene `Do`, `Done when`, `Verify` y `Checkpoint`. El Checkpoint es un bloque de shell que confirma y etiqueta: `step-NN-<slug>`.
3. Los criterios son observables y decidibles por una máquina, en forma EARS (**WHEN** … **THE SYSTEM SHALL** …; palabras clave en inglés, contenido en español).
4. Prohibido «se ve bien», «funciona», «está implementado».
5. `Verify` es shell literal con el resultado esperado en un comentario.
6. Un paso no está hecho hasta que sus verificaciones pasan **y** las de los pasos anteriores siguen pasando (por eso casi todos los pasos del backend terminan con `./mvnw -B -ntp verify`).
7. Reversión: volver a la etiqueta del paso anterior con `git reset --hard step-NN-<slug>` y repetir; nunca depurar hacia adelante sobre un checkpoint roto. **La reversión es una acción humana**: `git reset --hard` no está en el allowlist de `.claude/settings.json` y el agente la propone, no la ejecuta.
8. Nunca adelantarse: si el paso 7 está bloqueado, se detiene y se reporta.
9. Ningún paso introduce un requisito que rompa la verificación de un paso anterior. Las variables de entorno son obligatorias solo desde el paso que indica §10 («Required by step»).
10. Ningún número afirmado sin contarlo: los gates afirman propiedades (exit 0, `Failures: 0`, «cero filas de B»), no totales.
11. Toda línea de `Verify` sale con 0 cuando el paso está bien; los códigos de error esperados se afirman (`; test $? -eq 1`).
12. Cada comprobación es posible en su medio (las pruebas de tipos las decide el compilador; las de datos, la base real).
13. El primer artefacto ejecutable se **ejecuta** en su propio paso: el jar se arranca en el paso 1 (`scripts/smoke-api.sh`), las imágenes en el paso 18 (`scripts/smoke-prod-stack.sh`).
14. Ningún `Verify` depende de lo que produce su propio Checkpoint (nada de `git status`, `git ls-files` ni tags del propio paso).
15. Los literales que se comparan byte a byte están reconciliados contra el blueprint y contra el runtime fijado (§19.6).
16. Ningún gate pasa con cualquier código distinto de cero: se afirma el código exacto.

### One step, one unit — the counting rule

**Un paso de §9 = una tarea de `tasks.json` = un bloque de tarea en un archivo de épica.** Este bundle tiene **18 pasos**, en **3 épicas**: `01-fundacion-datos` (pasos 1–6), `02-identidad-registro` (pasos 7–13) y `03-frontend-entrega` (pasos 14–18). 18 pasos admiten entre `ceil(18/9) = 2` y `floor(18/5) = 3` épicas; las tres siguen las costuras naturales datos / servidor / interfaz y entrega.

### Step map

| # | Paso | Depende de | Toca | Gate principal |
|---|---|---|---|---|
| 1 | Esqueleto backend que arranca contra PostgreSQL con roles | — | `CitasApplication.java`, `SecurityConfig.java`, `V1__shared_baseline.sql`, `FoundationIT.java` | `(cd backend && ./mvnw -B -ntp -DskipTests package)` |
| 2 | Reglas de módulos (Modulith) y ArchUnit con plantilla | 1 | `package-info.java`, `ModularityTest.java`, `ArchitectureRulesTest.java`, `BadDomainEntity.java`, `README.md` | `(cd backend && ./mvnw -B -ntp test -Dtest='ModularityTest,ArchitectureRulesTest')` |
| 3 | Kernel: Money, errores ProblemDetail e id de petición | 1 | `Money.java`, `DomainException.java`, `ApiExceptionHandler.java`, `RequestIdFilter.java`, `SharedKernelTest.java` | `(cd backend && ./mvnw -B -ntp test -Dtest=SharedKernelTest)` |
| 4 | Aislamiento en dos capas: @TenantId, set_config y RLS | 1 | `TenantContext.java`, `BusinessScopedEntity.java`, `TenancyJpaConfig.java`, `V2__platform_business.sql`, `RowLevelSecurityIT.java` | `(cd backend && ./mvnw -B -ntp test -Dtest=RowLevelSecurityIT)` |
| 5 | Alta de negocio con sede principal y ajustes | 3, 4 | `Business.java`, `Location.java`, `Businesses.java`, `PeluqueriaVerticalSeed.java`, `ProvisioningIT.java` | `(cd backend && ./mvnw -B -ntp test -Dtest=ProvisioningIT)` |
| 6 | Cuentas globales, membresías con RLS y Argon2id | 4, 5 | `V3__identity_accounts.sql`, `UserAccount.java`, `IdentityStore.java`, `SecurityConfig.java`, `IdentityPersistenceIT.java` | `(cd backend && ./mvnw -B -ntp test -Dtest=IdentityPersistenceIT)` |
| 7 | JWT de acceso, refresh rotativo y servidor de recursos | 5, 6 | `JwtConfig.java`, `TokenService.java`, `SecurityConfig.java`, `TestData.java`, `TokenServiceIT.java` | `(cd backend && ./mvnw -B -ntp test -Dtest=TokenServiceIT)` |
| 8 | Endpoints de login, selección y cambio de negocio | 7 | `AuthService.java`, `AuthController.java`, `AuthFlowIT.java` | `(cd backend && ./mvnw -B -ntp test -Dtest=AuthFlowIT)` |
| 9 | API del negocio, suite de aislamiento y datos demo | 2, 8 | `BusinessController.java`, `DemoDataSeeder.java`, `TenantIsolationIT.java`, `ArchitectureRulesTest.java`, `RogueEntity.java` | `(cd backend && ./mvnw -B -ntp test -Dtest='TenantIsolationIT,ArchitectureRulesTest')` |
| 10 | Antiabuso: Bucket4j, Turnstile, CORS y cabeceras | 8 | `RateLimiter.java`, `CaptchaVerifier.java`, `TurnstileCaptchaVerifier.java`, `AuthService.java`, `SecurityHardeningIT.java` | `(cd backend && ./mvnw -B -ntp test -Dtest=SecurityHardeningIT)` |
| 11 | Correo transaccional y recuperación de contraseña | 10 | `EmailSender.java`, `EmailConfig.java`, `PasswordResetService.java`, `AuthController.java`, `PasswordResetIT.java` | `(cd backend && ./mvnw -B -ntp test -Dtest=PasswordResetIT)` |
| 12 | Registro autoservicio y crear otro negocio | 9, 11 | `V4__platform_legal_acceptance.sql`, `Businesses.java`, `SignupService.java`, `SignupController.java`, `SignupIT.java` | `(cd backend && ./mvnw -B -ntp test -Dtest='SignupIT,TenantIsolationIT')` |
| 13 | Contrato OpenAPI, cliente generado y deriva | 12 | `OpenApiConfig.java`, `OpenApiExportIT.java`, `ng-openapi-gen.json`, `check-api-drift.sh`, `openapi.json` | `(cd backend && ./mvnw -B -ntp test -Dtest=OpenApiExportIT -Dopenapi.export=true)` |
| 14 | Sistema de diseño: tema Material M3, tokens y contraste | — | `_tokens.scss`, `styles.scss`, `angular.json`, `index.html`, `check-contrast.mjs` | `node scripts/check-contrast.mjs` |
| 15 | Núcleo web: sesión, guardas, etiquetas y shell | 13, 14 | `auth.ts`, `ui-text.ts`, `shell.ts`, `app.config.ts`, `core.spec.ts` | `(cd frontend && npx ng test --watch=false)` |
| 16 | Pantallas de acceso y registro con rutas perezosas | 15 | `auth-pages.ts`, `auth-pages.spec.ts`, `app.routes.ts`, `app.html`, `app.spec.ts` | `(cd frontend && npx ng test --watch=false)` |
| 17 | E2E de registro con Mailpit y pipeline de CI | 16 | `registro.spec.ts`, `ci.yml` | `bash scripts/run-e2e.sh` |
| 18 | Imágenes, pila Caddy y workflow de despliegue | 16 | `api.Dockerfile`, `web.Dockerfile`, `deploy.yml` | `(cd backend && ./mvnw -B -ntp -DskipTests package)` |

Orden: esqueleto que se ejecuta → reglas de módulos → kernel → aislamiento (la verificación temprana de `@TenantId` + RLS de HU-03, antes de cualquier CRUD) → alta de negocio → cuentas → tokens → login → suite de aislamiento → antiabuso → correo → registro → contrato → interfaz → E2E y CI → imágenes. El paso 14 (sistema de diseño) no depende del backend y puede ejecutarse en paralelo con la épica 02.

---

#### Paso 1 — Esqueleto backend que arranca contra PostgreSQL con roles

**Tarea:** `E1-T1` · **Épica:** `01-fundacion-datos` · **Depende de:** — (raíz)

**Do**

Crea el primer artefacto ejecutable de la API y pruébalo contra la base real. Ya están en disco (llegaron con la copia de `workspace/` del bootstrap): `backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/test/resources/application-test.yml`, `backend/src/test/resources/db/test-roles.sql`, `backend/src/test/java/com/plataformacitas/support/IntegrationTest.java`, `docker-compose.yml`, `infra/db/init/01-roles.sh` y `scripts/smoke-api.sh`. No los reescribas.

- `CitasApplication.java` — clase `com.plataformacitas.CitasApplication` con `@SpringBootApplication`, `main` y un `@Bean Clock clock()` que devuelve `Clock.systemUTC()` (el reloj inyectable del kernel; nadie llama `Instant.now()` en lógica de negocio).
- `SecurityConfig.java` — `@Configuration` con un `SecurityFilterChain`: CSRF deshabilitado (API sin sesión de servidor), `SessionCreationPolicy.STATELESS`, `formLogin` y `httpBasic` deshabilitados; `permitAll` para `GET /actuator/health`, `GET /actuator/health/**`, `/api/v1/public/**` y `/error`; `anyRequest().authenticated()`; `exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`. El paso 7 reescribe este archivo con JWT.
- `V1__shared_baseline.sql` — exactamente:
  ```sql
  CREATE TABLE schema_info (
      key        text PRIMARY KEY,
      value      text NOT NULL,
      created_at timestamptz NOT NULL DEFAULT now()
  );
  INSERT INTO schema_info (key, value) VALUES ('baseline', 'plataforma-citas');
  ```
- `FoundationIT.java` — extiende `IntegrationTest`. Usa `JdbcClient` (bean de Spring, conexión de `app_runtime`) y `connectAsRuntime()` para: `select current_user` = `app_runtime`; `select rolsuper, rolbypassrls from pg_roles where rolname = 'app_runtime'` = false/false; `select tableowner from pg_tables where schemaname='public' and tablename='schema_info'` = `app_owner`; `select count(*) from pg_tables where schemaname='public' and tableowner='app_runtime'` = 0; `create table x (id int)` como `app_runtime` lanza `SQLException` con SQLState `42501`; `select value from schema_info where key='baseline'` = `plataforma-citas`. Con `java.net.http.HttpClient` contra `baseUrl()`: `GET /actuator/health` = 200 y `GET /api/v1/cualquier-cosa` = 401.

No agregues tablas de negocio aquí: llegan en los pasos 4 y 6.

Archivos:
- `backend/src/main/java/com/plataformacitas/CitasApplication.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/security/SecurityConfig.java` — nuevo
- `backend/src/main/resources/db/migration/V1__shared_baseline.sql` — nuevo
- `backend/src/test/java/com/plataformacitas/FoundationIT.java` — nuevo

**Done when**
- [ ] WHEN `./mvnw -B -ntp -DskipTests package` runs in `backend/` THE SYSTEM SHALL exit 0 and produce `backend/target/plataforma-citas-api.jar`.
- [ ] WHEN `bash scripts/smoke-api.sh` starts that jar against the `db` service of `docker-compose.yml` THE SYSTEM SHALL answer `GET /actuator/health` with HTTP 200 and a JSON body whose `status` is `UP`.
- [ ] WHEN the API boots THE SYSTEM SHALL apply Flyway migration `V1__shared_baseline.sql` as `app_owner`, so `pg_tables` reports `app_owner` as owner of `schema_info`.
- [ ] WHEN `FoundationIT` queries through the application DataSource THE SYSTEM SHALL report `current_user` = `app_runtime`, `rolsuper` = false, `rolbypassrls` = false and zero tables in schema `public` owned by `app_runtime`.
- [ ] WHEN `FoundationIT` runs `CREATE TABLE` as `app_runtime` THE SYSTEM SHALL fail with SQLState `42501`, while `SELECT value FROM schema_info WHERE key = 'baseline'` returns `plataforma-citas`.
- [ ] WHEN an anonymous request hits any path other than `/actuator/health` or `/api/v1/public/**` THE SYSTEM SHALL answer HTTP 401.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0, BUILD SUCCESS
test -f backend/target/plataforma-citas-api.jar   # expect: exit 0
(cd backend && ./mvnw -B -ntp test -Dtest=FoundationIT)   # expect: exit 0, BUILD SUCCESS, Failures: 0, Errors: 0
bash scripts/smoke-api.sh   # expect: exit 0, imprime "health: UP" y "SMOKE API OK"
```

**Checkpoint**
```bash
git add -A && git commit -m "step 1: esqueleto"
git tag step-01-esqueleto
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-01-esqueleto
```

---

#### Paso 2 — Reglas de módulos (Modulith) y ArchUnit con plantilla

**Tarea:** `E1-T2` · **Épica:** `01-fundacion-datos` · **Depende de:** paso 1

**Do**

Haz que los límites entre módulos rompan el build. Cada paquete directo bajo `com.plataformacitas` es un módulo de Spring Modulith; sus subpaquetes son internos. El módulo `shared` es abierto (todos lo usan).

- `shared/package-info.java` — exactamente:
  ```java
  @org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
  package com.plataformacitas.shared;
  ```
- `ModularityTest.java` — prueba unitaria sin contexto de Spring: `ApplicationModules.of(CitasApplication.class).verify();`.
- `ArchitectureRulesTest.java` — importa producción con `new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.plataformacitas")`. Reglas (constantes `ArchRule` reutilizables):
  1. `DOMAIN_IS_PURE`: `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..application..", "..adapter..").allowEmptyShould(true)`.
  2. `APPLICATION_WITHOUT_ADAPTERS`: `noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat().resideInAPackage("..adapter..").allowEmptyShould(true)`.
  3. `VERTICAL_NAMES_STAY_IN_VERTICAL`: `noClasses().that().resideOutsideOfPackage("com.plataformacitas.vertical..").should().haveSimpleNameContaining("Peluqueria")`.
  Pruebas: las tres reglas pasan sobre producción; `DOMAIN_IS_PURE.check(...)` sobre `new ClassFileImporter().importPackages("archfixtures.baddomain")` lanza `AssertionError` (assertThrows). `allowEmptyShould(true)` es obligatorio: en este bundle aún no existe ningún paquete `domain` ni `application` (llegan con `agenda` y `cash`).
- `archfixtures/baddomain/domain/BadDomainEntity.java` — fixture fuera de `com.plataformacitas` (para que ni Spring ni Hibernate la escaneen): `package archfixtures.baddomain.domain; import jakarta.persistence.Transient; public class BadDomainEntity { @Transient String nombre; }`.
- `docs/modules/README.md` — guía «cómo crear un módulo» en español con los encabezados `## Estructura de un módulo`, `## Hexagonal completa`, `## Módulo ligero` y `## Reglas verificadas`. Describe: API pública en la raíz del paquete del módulo, internos en subpaquetes (`internal/` en módulos ligeros; `domain/`, `application/`, `application/port/out/`, `adapter/in/web/`, `adapter/out/persistence/` en hexagonal), qué módulos son hexagonales (`agenda`, `cash`, en bundles 2 y 3) y cuáles ligeros (`identity`, `platform`, `catalog`, `staff`, `customers`), y la lista de reglas de `ArchitectureRulesTest` y `ModularityTest`.

Archivos:
- `backend/src/main/java/com/plataformacitas/shared/package-info.java` — nuevo
- `backend/src/test/java/com/plataformacitas/ModularityTest.java` — nuevo
- `backend/src/test/java/com/plataformacitas/ArchitectureRulesTest.java` — nuevo
- `backend/src/test/java/archfixtures/baddomain/domain/BadDomainEntity.java` — nuevo
- `docs/modules/README.md` — nuevo

**Done when**
- [ ] WHEN `ModularityTest` runs THE SYSTEM SHALL pass `ApplicationModules.of(CitasApplication.class).verify()`, with module `shared` declared `@ApplicationModule(type = ApplicationModule.Type.OPEN)` in `shared/package-info.java`.
- [ ] WHEN `ArchitectureRulesTest` checks rule `DOMAIN_IS_PURE` against fixture `archfixtures.baddomain.domain.BadDomainEntity`, which imports `jakarta.persistence`, THE SYSTEM SHALL report a violation (the test asserts the `AssertionError`).
- [ ] WHEN `ArchitectureRulesTest` checks `DOMAIN_IS_PURE`, `APPLICATION_WITHOUT_ADAPTERS` and `VERTICAL_NAMES_STAY_IN_VERTICAL` against production classes of `com.plataformacitas` THE SYSTEM SHALL report zero violations.
- [ ] WHEN `docs/modules/README.md` is read THE SYSTEM SHALL contain the headings `## Estructura de un módulo`, `## Hexagonal completa`, `## Módulo ligero` and `## Reglas verificadas`.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest='ModularityTest,ArchitectureRulesTest')   # expect: exit 0, Failures: 0, Errors: 0
grep -q '^## Estructura de un módulo' docs/modules/README.md   # expect: exit 0
grep -q '^## Hexagonal completa' docs/modules/README.md   # expect: exit 0
grep -q '^## Módulo ligero' docs/modules/README.md   # expect: exit 0
grep -q '^## Reglas verificadas' docs/modules/README.md   # expect: exit 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**
```bash
git add -A && git commit -m "step 2: arquitectura"
git tag step-02-arquitectura
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-02-arquitectura
```

---

#### Paso 3 — Kernel: Money, errores ProblemDetail e id de petición

**Tarea:** `E1-T3` · **Épica:** `01-fundacion-datos` · **Depende de:** paso 1

**Do**

Construye las piezas que todos los módulos reutilizan.

- `Money.java` — `record Money(long amountMinor, String currency)` inmutable. `Money.of(long minor, String currencyCode)`, `Money.ofMajor(BigDecimal amount, String currencyCode)` (redondeo `HALF_EVEN` a `Currency.getInstance(code).getDefaultFractionDigits()`), `plus`, `minus`, `times(int)`, `isNegative()`. Operar dos monedas distintas lanza `DomainException(Code.CURRENCY_MISMATCH, …)`. Nunca `double`.
- `DomainException.java` — `RuntimeException` con `Code code()` y un enum anidado `DomainException.Code` que lleva el `HttpStatus`: `VALIDATION_ERROR` 400, `INVALID_TOKEN` 400, `CAPTCHA_FAILED` 400, `UNAUTHENTICATED` 401, `INVALID_CREDENTIALS` 401, `INVALID_REFRESH_TOKEN` 401, `FORBIDDEN` 403, `EMAIL_NOT_VERIFIED` 403, `NO_ACTIVE_MEMBERSHIP` 403, `NOT_FOUND` 404, `ACCOUNT_EXISTS` 409, `CONFLICT` 409, `SLOT_TAKEN` 409, `CURRENCY_MISMATCH` 422, `RATE_LIMITED` 429, `INTERNAL_ERROR` 500.
- `ApiExceptionHandler.java` — `@RestControllerAdvice` que responde siempre `ProblemDetail` (`application/problem+json`) con las propiedades `code` (texto estable del enum), `title`, `detail` y `requestId` (del MDC). Mapea: `DomainException` → su status; `MethodArgumentNotValidException` y `HandlerMethodValidationException` → 400 `VALIDATION_ERROR` con `errors: [{field, message}]`; `HttpMessageNotReadableException` → 400 `VALIDATION_ERROR`; `AccessDeniedException` → 403 `FORBIDDEN`; `DataIntegrityViolationException` cuya causa raíz es un `SQLException` con SQLState `23P01` → 409 `SLOT_TAKEN` y con `23505` → 409 `CONFLICT`; cualquier otra excepción → 500 `INTERNAL_ERROR` (se registra con stack; el cuerpo nunca la incluye).
- `RequestIdFilter.java` — `OncePerRequestFilter` con `@Order(Ordered.HIGHEST_PRECEDENCE)` y `@Component`: toma `X-Request-Id` si cumple `^[A-Za-z0-9-]{1,64}$`, si no genera un UUID; lo pone en el MDC (`requestId`) y en la cabecera de respuesta `X-Request-Id`; en `finally` limpia `requestId` y `businessId` del MDC.
- `SharedKernelTest.java` — prueba unitaria (sin base de datos): pruebas de `Money` y, con `MockMvcBuilders.standaloneSetup(new ProbeController()).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build()`, pruebas del manejo de errores. `ProbeController` es una clase anidada estática anotada solo con `@RequestMapping("/probe")` (sin `@RestController`, para que el escaneo de componentes nunca la registre) con métodos que: validan un `record` con `@NotBlank` y `@Email`, lanzan `DomainException(Code.NOT_FOUND, …)` y lanzan `new DataIntegrityViolationException("x", new SQLException("solapado", "23P01"))`.

Archivos:
- `backend/src/main/java/com/plataformacitas/shared/money/Money.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/error/DomainException.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/error/ApiExceptionHandler.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/web/RequestIdFilter.java` — nuevo
- `backend/src/test/java/com/plataformacitas/shared/SharedKernelTest.java` — nuevo

**Done when**
- [ ] WHEN `Money.of(1000, "COP").plus(Money.of(500, "USD"))` runs THE SYSTEM SHALL throw `DomainException` with code `CURRENCY_MISMATCH`.
- [ ] WHEN `Money.ofMajor(new BigDecimal("10.005"), "USD")` and `Money.ofMajor(new BigDecimal("10.015"), "USD")` run THE SYSTEM SHALL return 1000 and 1002 minor units respectively (HALF_EVEN to the currency's default fraction digits).
- [ ] WHEN a request body fails bean validation THE SYSTEM SHALL answer 400 `application/problem+json` with `code` = `VALIDATION_ERROR` and an `errors` array of `{field, message}` naming every invalid field.
- [ ] WHEN a handler throws `DomainException` with code `NOT_FOUND` THE SYSTEM SHALL answer 404 with a problem body carrying `code`, `title`, `detail` and `requestId`.
- [ ] WHEN a `DataIntegrityViolationException` caused by SQLState `23P01` escapes a handler THE SYSTEM SHALL answer 409 with `code` = `SLOT_TAKEN`.
- [ ] WHEN a request carries `X-Request-Id: abc-123` THE SYSTEM SHALL return response header `X-Request-Id: abc-123` and `requestId` = `abc-123` in any problem body, and without that header it SHALL return a generated UUID.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest=SharedKernelTest)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**
```bash
git add -A && git commit -m "step 3: kernel"
git tag step-03-kernel
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-03-kernel
```

---

#### Paso 4 — Aislamiento en dos capas: @TenantId, set_config y RLS

**Tarea:** `E1-T4` · **Épica:** `01-fundacion-datos` · **Depende de:** paso 1

**Do**

Esta es la verificación temprana de HU-03: `@TenantId` + RLS + Flyway + pool de conexiones, antes de cualquier CRUD.

- `TenantContext.java` — `final class` con `ThreadLocal<UUID>` para negocio y cuenta. API: `static final UUID NO_TENANT = new UUID(0, 0)`; `Optional<UUID> businessId()`, `Optional<UUID> userId()`, `set(UUID businessId, UUID userId)`, `clear()`, y `static <T> T run(UUID businessId, UUID userId, Supplier<T> work)` que fija ambos valores, ejecuta y **restaura los anteriores** en `finally` (cumple el papel de `TenantScope.run` de los documentos). Regla: `run` envuelve la transacción, nunca va dentro de una ya abierta, porque `set_config` se aplica al iniciarla.
- `BusinessScopedEntity.java` — `@MappedSuperclass` con `@TenantId @Column(name = "business_id", nullable = false, updatable = false) private UUID businessId;` y su getter. Toda entidad JPA de negocio la extiende.
- `TenancyJpaConfig.java` — `@Configuration(proxyBeanMethods = false)` con:
  ```java
  @Bean
  HibernatePropertiesCustomizer tenantResolverCustomizer() {
      return properties -> properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new BusinessTenantResolver());
  }

  @Bean
  PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
      return new TenantAwareJpaTransactionManager(entityManagerFactory);
  }

  static final class BusinessTenantResolver implements CurrentTenantIdentifierResolver<UUID> {
      @Override public UUID resolveCurrentTenantIdentifier() { return TenantContext.businessId().orElse(TenantContext.NO_TENANT); }
      @Override public boolean validateExistingCurrentSessions() { return false; }
  }

  static final class TenantAwareJpaTransactionManager extends JpaTransactionManager {
      TenantAwareJpaTransactionManager(EntityManagerFactory emf) { super(emf); }
      @Override
      protected void doBegin(Object transaction, TransactionDefinition definition) {
          super.doBegin(transaction, definition);
          EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
          holder.getEntityManager().unwrap(Session.class).doWork(connection -> {
              try (PreparedStatement ps = connection.prepareStatement(
                      "select set_config('app.business_id', ?, true), set_config('app.user_id', ?, true)")) {
                  ps.setString(1, TenantContext.businessId().map(UUID::toString).orElse(""));
                  ps.setString(2, TenantContext.userId().map(UUID::toString).orElse(""));
                  ps.execute();
              }
          });
      }
  }
  ```
  Imports: `org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer` (paquete de Boot 4), `org.hibernate.cfg.AvailableSettings`, `org.hibernate.context.spi.CurrentTenantIdentifierResolver`, `org.hibernate.Session`, `org.springframework.orm.jpa.*`. `set_config(..., true)` es local a la transacción: no se filtra a otra petición del pool. `JdbcClient` comparte la conexión de la transacción JPA, así que RLS también protege el SQL nativo.
- `V2__platform_business.sql` — el DDL completo de `business`, `subscription`, `location` y `business_settings` con sus políticas, tal como aparece en la sección «Modelo de datos» (copiar literal).
- `RowLevelSecurityIT.java` — extiende `IntegrationTest`. Crea dos negocios A y B (filas en `business` vía `JdbcClient`, globales) y una `location` para cada uno dentro de `TenantContext.run(X, null, () -> transactionTemplate.execute(...))`. Pruebas: (1) verificación de catálogo — la consulta de la sección «Prueba de catálogo RLS» sobre `n.nspname = 'public'` no devuelve ninguna tabla desprotegida fuera de la lista permitida `subscription`, `refresh_token`; (2) la misma consulta sobre el esquema temporal (`n.oid = pg_my_temp_schema()`), tras `create temp table probe_tbl (business_id uuid)` en la misma conexión, reporta `probe_tbl`; (3) con `connectAsRuntime()` y sin `set_config`: `select count(*) from location` = 0 e `insert into location (...)` falla con SQLState `42501`; (4) dentro de `TenantContext.run(A, …)` con transacción: `select count(*) from location` (sin `WHERE`) = 1 y solo ids de A; (5) una transacción posterior sin tenant en el mismo `DataSource` ve 0 filas.

Archivos:
- `backend/src/main/java/com/plataformacitas/shared/tenancy/TenantContext.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/tenancy/BusinessScopedEntity.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/tenancy/TenancyJpaConfig.java` — nuevo
- `backend/src/main/resources/db/migration/V2__platform_business.sql` — nuevo
- `backend/src/test/java/com/plataformacitas/shared/RowLevelSecurityIT.java` — nuevo

**Done when**
- [ ] WHEN Flyway applies `V2__platform_business.sql` THE SYSTEM SHALL create tables `business`, `subscription`, `location` and `business_settings`, with ENABLE and FORCE ROW LEVEL SECURITY and policy `tenant_isolation` on `location` and `business_settings`.
- [ ] WHEN `RowLevelSecurityIT` runs the RLS catalog check over schema `public` THE SYSTEM SHALL find that every table with a `business_id` column, except the allowlist `subscription` and `refresh_token`, has RLS enabled, forced and at least one policy.
- [ ] WHEN the same catalog check runs over the session's temporary schema after creating a temp table with a `business_id` column and no policy THE SYSTEM SHALL report that table as unprotected.
- [ ] WHEN `app_runtime` reads `location` without `app.business_id` set (outside any transaction) THE SYSTEM SHALL return zero rows, and an `INSERT` into `location` SHALL fail with SQLState `42501`.
- [ ] WHEN code runs inside `TenantContext.run(businessA, null, …)` within a Spring transaction THE SYSTEM SHALL see only business A's rows through a native `SELECT count(*) FROM location` without any `WHERE` clause.
- [ ] WHEN that transaction has ended and a new transaction without tenant runs on the same pool THE SYSTEM SHALL see zero `location` rows, because `set_config(…, true)` is transaction-local.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest=RowLevelSecurityIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**
```bash
git add -A && git commit -m "step 4: rls"
git tag step-04-rls
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-04-rls
```

---

#### Paso 5 — Alta de negocio con sede principal y ajustes

**Tarea:** `E1-T5` · **Épica:** `01-fundacion-datos` · **Depende de:** paso 3, paso 4

**Do**

Crea el módulo `platform` (ligero) con su API pública `Businesses` y el primer vertical.

- `Business.java` — `@Entity @Table(name = "business")` global (no extiende `BusinessScopedEntity`): `id` (UUID asignado por la aplicación), `name`, `slug`, `timezone`, `currency`, `vertical`, `status`, `createdAt`, `updatedAt`.
- `Location.java` — `@Entity @Table(name = "location")` que **extiende `BusinessScopedEntity`**: `id`, `name`, `address`, `phone`, `isMain` (columna `is_main`), `active`, timestamps. Hibernate rellena `business_id` desde `TenantContext` al persistir.
- `Businesses.java` — `@Service` en la raíz de `com.plataformacitas.platform` (API pública del módulo). Contiene:
  - `public interface VerticalSeed { String vertical(); Defaults defaults(); record Defaults(int slotStepMin, int bufferMin, boolean walkInsEnabled, long openingCashDefaultMinor, boolean tipsEnabled, String tipsRecipient, int maxDiscountPctReception, Map<String, String> labels) {} }` — puerto que implementan los verticales.
  - `public record BusinessSummary(UUID id, String name, String slug)` y `public record BusinessDetails(UUID id, String name, String slug, String timezone, String currency, String vertical, String status, Instant trialEndsAt, Map<String, String> labels)`.
  - `BusinessSummary provision(UUID businessId, String name, String vertical, String timezone)` — **debe llamarse con una transacción ya abierta dentro de `TenantContext.run(businessId, …)`** (`@Transactional(propagation = MANDATORY)`). Valida que exista un `VerticalSeed` para `vertical` (si no, `DomainException(VALIDATION_ERROR)` antes de escribir), valida la zona con `ZoneId.of`, genera el slug, y escribe: `business` (moneda `COP`, estado `ACTIVE`), `subscription` (`plan_code='PRO'`, `status='TRIAL'`, `trial_ends_at = clock.instant() + app.trial-days`), la sede principal `Location` (`name='Sede principal'`, `is_main=true`) y `business_settings` con los `Defaults` del vertical (`labels` como `jsonb`). `subscription` y `business_settings` se escriben con `JdbcClient`.
  - `List<BusinessSummary> summaries(Collection<UUID> ids)`, `BusinessDetails current()` (negocio de `TenantContext`, con `trialEndsAt` de `subscription` y `labels` de `business_settings`) y `BusinessDetails update(String name, String timezone, String currency)` (`@Transactional`, modifica solo el negocio de `TenantContext`; lo expone el paso 9).
  - Slug: `Normalizer.normalize(name, NFD)`, quitar `\p{M}`, minúsculas, todo lo que no sea `[a-z0-9]` → `-`, colapsar y recortar guiones, máximo 60 caracteres; si existe, sufijos `-2`, `-3`, …
- `PeluqueriaVerticalSeed.java` — `@Component` en `com.plataformacitas.vertical.peluqueria` que implementa `Businesses.VerticalSeed` con `vertical() = "PELUQUERIA"` y `Defaults(15, 0, true, 0, true, "EMPLOYEE", 10, labels)` donde `labels` = `{"resource":"Empleado","resourcePlural":"Empleados","service":"Servicio","servicePlural":"Servicios","customer":"Cliente","customerPlural":"Clientes"}`. En este bundle el seed solo escribe etiquetas y ajustes; el catálogo inicial de servicios llega en el bundle 2.
- `ProvisioningIT.java` — extiende `IntegrationTest`, con un `@TestConfiguration` anidado que publica un `@Bean @Primary Clock` fijo en `2026-10-12T15:00:00Z`. Cada caso abre `TenantContext.run(id, null, () -> transactionTemplate.execute(...))` y comprueba las filas con `JdbcClient` dentro del mismo contexto de negocio. El nombre `Peluquería Doña Ana` lo usa una sola prueba, que provisiona dos veces y comprueba ambos slugs; ninguna otra clase de prueba usa ese nombre (la base del contenedor es compartida por todas las clases de una corrida).

Archivos:
- `backend/src/main/java/com/plataformacitas/platform/internal/Business.java` — nuevo
- `backend/src/main/java/com/plataformacitas/platform/internal/Location.java` — nuevo
- `backend/src/main/java/com/plataformacitas/platform/Businesses.java` — nuevo
- `backend/src/main/java/com/plataformacitas/vertical/peluqueria/PeluqueriaVerticalSeed.java` — nuevo
- `backend/src/test/java/com/plataformacitas/platform/ProvisioningIT.java` — nuevo

**Done when**
- [ ] WHEN `Businesses.provision` runs for name `Peluquería Doña Ana` and vertical `PELUQUERIA` THE SYSTEM SHALL create one `business` row with slug `peluqueria-dona-ana`, timezone `America/Bogota`, currency `COP` and status `ACTIVE`.
- [ ] WHEN a second business named `Peluquería Doña Ana` is provisioned THE SYSTEM SHALL give it slug `peluqueria-dona-ana-2`.
- [ ] WHEN a business is provisioned THE SYSTEM SHALL create exactly one `location` row with `is_main` = true and name `Sede principal`, persisted through entity `Location`, which extends `BusinessScopedEntity`.
- [ ] WHEN a business is provisioned with a fixed `Clock` at `2026-10-12T15:00:00Z` THE SYSTEM SHALL create a `subscription` row with `plan_code` = `PRO`, `status` = `TRIAL` and `trial_ends_at` = `2026-11-11T15:00:00Z`.
- [ ] WHEN a business of vertical `PELUQUERIA` is provisioned THE SYSTEM SHALL write `business_settings` with `slot_step_min` = 15 and `labels` containing `"resource": "Empleado"`, taken from `PeluqueriaVerticalSeed`.
- [ ] WHEN `provision` is called with a vertical that has no registered `Businesses.VerticalSeed` THE SYSTEM SHALL throw `DomainException` with code `VALIDATION_ERROR` and write no `business` row.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest=ProvisioningIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**
```bash
git add -A && git commit -m "step 5: negocio"
git tag step-05-negocio
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-05-negocio
```

---

#### Paso 6 — Cuentas globales, membresías con RLS y Argon2id

**Tarea:** `E1-T6` · **Épica:** `01-fundacion-datos` · **Depende de:** paso 4, paso 5

**Do**

Crea la capa de datos del módulo `identity` (ligero; todo en `identity/internal`).

- `V3__identity_accounts.sql` — el DDL completo de `user_account`, `membership` (con la política especial `membership_access`), `refresh_token` y `one_time_token` de la sección «Modelo de datos» (copiar literal).
- `UserAccount.java` — `@Entity @Table(name = "user_account")` global: `id`, `email`, `passwordHash`, `name`, `status`, `emailVerifiedAt`, `platformRole`, timestamps.
- `IdentityStore.java` — `@Repository` con `EntityManager` y `JdbcClient`:
  - `UserAccount createAccount(String email, String rawPassword, String name, boolean verified)` — normaliza el correo (`trim` + `toLowerCase(Locale.ROOT)`), lanza `DomainException(ACCOUNT_EXISTS)` si ya existe (también si la base responde 23505) y guarda `passwordEncoder.encode(rawPassword)`.
  - `Optional<UserAccount> findByEmail(String email)`, `void markEmailVerified(UUID userId, Instant at)`, `void changePassword(UUID userId, String rawPassword)`.
  - Membresías con `JdbcClient`: `void addMembership(UUID userId, UUID businessId, String role)`, `List<MembershipView> membershipsOf(UUID userId)` (join con `business` para el nombre y el slug, solo activas y negocios `ACTIVE`, orden por nombre), `Optional<String> activeRole(UUID userId, UUID businessId)`. `record MembershipView(UUID businessId, String businessName, String slug, String role)`.
  - Tokens de un solo uso con `JdbcClient`: `UUID createOneTimeToken(UUID userId, String purpose, String tokenHash, String payloadJson, Instant expiresAt)`, `Optional<OneTimeTokenRow> lockUsableToken(String purpose, String tokenHash, Instant now)` (`FOR UPDATE`, sin usar y vigente), `void markTokenUsed(UUID id, Instant at)`, `void invalidateOpenTokens(UUID userId, String purpose, Instant at)`.
  - Utilidades estáticas: `String newOpaqueToken()` (32 bytes de `SecureRandom` en base64url sin relleno) y `String sha256Hex(String)`.
  Todas las operaciones de membresías se llaman dentro de `TenantContext.run(...)` + transacción: la política de `membership` exige `app.business_id` o `app.user_id`.
- `SecurityConfig.java` (editar) — agrega `@Bean PasswordEncoder passwordEncoder()` que devuelve `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` (requiere `bcprov-jdk18on`, ya en el pom).
- `IdentityPersistenceIT.java` — extiende `IntegrationTest`. Usa `Businesses.provision` (paso 5) para crear A y B y `IdentityStore` para cuentas y membresías.

Archivos:
- `backend/src/main/resources/db/migration/V3__identity_accounts.sql` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/UserAccount.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/IdentityStore.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/security/SecurityConfig.java` — editar: agrega el bean PasswordEncoder Argon2id
- `backend/src/test/java/com/plataformacitas/identity/IdentityPersistenceIT.java` — nuevo

**Done when**
- [ ] WHEN Flyway applies `V3__identity_accounts.sql` THE SYSTEM SHALL create `user_account`, `membership`, `refresh_token` and `one_time_token`, with RLS enabled and forced on `membership` under policy `membership_access`.
- [ ] WHEN `IdentityStore.createAccount` stores a password THE SYSTEM SHALL persist a hash that starts with `$argon2id$` and never the plain text.
- [ ] WHEN accounts are created with `Ana@Example.com` and then `ana@example.com` THE SYSTEM SHALL store `ana@example.com` once and reject the second with `DomainException` code `ACCOUNT_EXISTS`.
- [ ] WHEN a user is OWNER in business A and EMPLOYEE in business B and only `app.user_id` is set to that user THE SYSTEM SHALL list both memberships.
- [ ] WHEN only `app.business_id` is set to business A THE SYSTEM SHALL list the memberships of A and none of B.
- [ ] WHEN neither setting is present THE SYSTEM SHALL list zero memberships, and inserting a membership for business B while `app.business_id` = A SHALL fail with SQLState `42501`.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest=IdentityPersistenceIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — incluye RowLevelSecurityIT con membership protegida
```

**Checkpoint**
```bash
git add -A && git commit -m "step 6: cuentas"
git tag step-06-cuentas
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-06-cuentas
```

---

#### Paso 7 — JWT de acceso, refresh rotativo y servidor de recursos

**Tarea:** `E2-T1` · **Épica:** `02-identidad-registro` · **Depende de:** paso 5, paso 6

**Do**

Emite y valida tokens. Desde este paso `JWT_SECRET` es obligatorio (el `.env` del bootstrap ya lo trae).

- `JwtConfig.java` — `@Configuration`:
  - `static SecretKey secretKey(String hex)` — exige 64 caracteres hexadecimales o más; si no, `IllegalStateException("JWT_SECRET debe tener al menos 64 caracteres hexadecimales (32 bytes)")`. Clave `new SecretKeySpec(HexFormat.of().parseHex(hex), "HmacSHA256")`.
  - `@Bean JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String hex)` = `NimbusJwtEncoder.withSecretKey(secretKey(hex)).algorithm(MacAlgorithm.HS256).build()`; `@Bean JwtDecoder jwtDecoder(...)` = `NimbusJwtDecoder.withSecretKey(secretKey(hex)).macAlgorithm(MacAlgorithm.HS256).build()`.
  - `@Bean Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter()` — `typ = preauth` → autoridad `PREAUTH`; `typ = access` → autoridades `TENANT` y `ROLE_<role>`; nombre = `sub`.
  - Clase anidada `public static final class TenantBindingFilter extends OncePerRequestFilter`: si la autenticación actual es un `JwtAuthenticationToken` con `typ = access`, hace `TenantContext.set(bid, sub)` y `MDC.put("businessId", bid)`; en `finally` llama `TenantContext.clear()` y quita `businessId` del MDC.
- `SecurityConfig.java` (reescribir, conservando el bean `PasswordEncoder`):
  ```java
  http.csrf(csrf -> csrf.disable())
      .cors(Customizer.withDefaults())
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .formLogin(f -> f.disable()).httpBasic(b -> b.disable())
      .headers(h -> h.frameOptions(f -> f.deny())
                     .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
      .authorizeHttpRequests(a -> a
          .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
          .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
          .requestMatchers("/api/v1/public/**").permitAll()
          .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                  "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset",
                  "/api/v1/signup", "/api/v1/signup/verify").permitAll()
          .requestMatchers(HttpMethod.POST, "/api/v1/auth/select-business").hasAuthority("PREAUTH")
          .anyRequest().hasAuthority("TENANT"))
      .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)).authenticationEntryPoint(problemEntryPoint))
      .exceptionHandling(e -> e.authenticationEntryPoint(problemEntryPoint).accessDeniedHandler(problemAccessDenied))
      .addFilterAfter(new JwtConfig.TenantBindingFilter(), BearerTokenAuthenticationFilter.class);
  ```
  `@EnableMethodSecurity` en la clase. `problemEntryPoint` y `problemAccessDenied` escriben `application/problem+json` con `code` `UNAUTHENTICATED` (401) y `FORBIDDEN` (403) y el `requestId` del MDC. Bean `CorsConfigurationSource`: orígenes de `app.cors.allowed-origins` (lista separada por comas), métodos `GET, POST, PUT, PATCH, DELETE, OPTIONS`, cabeceras `Authorization, Content-Type, X-Request-Id`, `allowCredentials(true)`, expone `X-Request-Id`, aplicado a `/api/**`.
- `TokenService.java` — `@Service` en `identity/internal`:
  - `IssuedSession issueSession(UUID userId, UUID businessId)` — dentro de `TenantContext.run(businessId, userId, tx)` exige membresía activa (si no, `DomainException(FORBIDDEN)`); firma el JWT de acceso (`JwsHeader.with(MacAlgorithm.HS256).type("JWT")`, claims `iss`, `sub`, `bid`, `role`, `typ = "access"`, `iat`, `exp` = `iat + app.jwt.access-ttl`); crea el refresh opaco (`IdentityStore.newOpaqueToken()`), guarda solo `sha256Hex` en `refresh_token` con `family_id` nuevo y `expires_at = now + app.jwt.refresh-ttl`. `record IssuedSession(String accessToken, long expiresInSeconds, String refreshToken, UUID businessId, String businessName, String slug, String role)`.
  - `String issuePreAuth(UUID userId)` — JWT `typ = "preauth"`, sin `bid`, 2 minutos.
  - `IssuedSession rotate(String rawRefreshToken)` — busca por hash; inexistente o vencido → `INVALID_REFRESH_TOKEN`; ya revocado → revoca toda la familia y `INVALID_REFRESH_TOKEN`; membresía inactiva → revoca y `INVALID_REFRESH_TOKEN`; si no, marca `revoked_at`, `replaced_by` y emite uno nuevo en la misma familia y el mismo negocio.
  - `void revokeFamily(String rawRefreshToken)`, `void revokeAllForUser(UUID userId)`.
  - Usa el `Clock` inyectado para todas las fechas.
- `TestData.java` — `@Component` de pruebas en `com.plataformacitas.support`: `Seeded business(String name)` (provisiona dentro de `TenantContext.run` + `TransactionTemplate`), `SeededUser user(String email, String password, boolean verified)`, `void member(SeededUser u, Seeded b, String role)`. Contraseñas de prueba de al menos 10 caracteres.
- `TokenServiceIT.java` — extiende `IntegrationTest`; decodifica con el `JwtDecoder` del contexto y hace peticiones HTTP con `java.net.http.HttpClient`.

Archivos:
- `backend/src/main/java/com/plataformacitas/shared/security/JwtConfig.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/TokenService.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/security/SecurityConfig.java` — editar: reescritura completa con servidor de recursos JWT
- `backend/src/test/java/com/plataformacitas/support/TestData.java` — nuevo
- `backend/src/test/java/com/plataformacitas/identity/TokenServiceIT.java` — nuevo

**Done when**
- [ ] WHEN `TokenService.issueSession(userId, businessId)` runs for an active membership THE SYSTEM SHALL return an HS256 access JWT with claims `sub` = user id, `bid` = business id, `role` = membership role, `typ` = `access` and an expiry 15 minutes after issue.
- [ ] WHEN a session is issued THE SYSTEM SHALL store in `refresh_token` only the SHA-256 hex hash of the opaque refresh token, bound to that user and business.
- [ ] WHEN a refresh token is rotated THE SYSTEM SHALL revoke the old row and return a new refresh token in the same `family_id` for the same business.
- [ ] WHEN an already-rotated refresh token is presented again THE SYSTEM SHALL fail with code `INVALID_REFRESH_TOKEN` and revoke every token of its family.
- [ ] WHEN a request to a non-public `/api/v1/**` path arrives without a token THE SYSTEM SHALL answer 401 `application/problem+json` with code `UNAUTHENTICATED`, and with a valid preauth token it SHALL answer 403 with code `FORBIDDEN`.
- [ ] WHEN `JwtConfig.secretKey` receives an empty value or fewer than 64 hexadecimal characters THE SYSTEM SHALL throw `IllegalStateException` whose message names `JWT_SECRET`.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest=TokenServiceIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
bash scripts/smoke-api.sh   # expect: exit 0, "SMOKE API OK" — arranca con el JWT_SECRET de .env
```

**Checkpoint**
```bash
git add -A && git commit -m "step 7: tokens"
git tag step-07-tokens
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-07-tokens
```

---

#### Paso 8 — Endpoints de login, selección y cambio de negocio

**Tarea:** `E2-T2` · **Épica:** `02-identidad-registro` · **Depende de:** paso 7

**Do**

Expón la autenticación bajo `/api/v1/auth` (contrato completo en la sección «Endpoints críticos»).

- `AuthService.java` — `@Service`:
  - `LoginResult login(String email, String password)` — normaliza el correo; si la cuenta no existe ejecuta igualmente `passwordEncoder.matches` contra un hash Argon2 fijo (mismo tiempo de respuesta) y lanza `INVALID_CREDENTIALS`; contraseña incorrecta → `INVALID_CREDENTIALS` con el mismo `detail` («Correo o contraseña incorrectos.»); cuenta `DISABLED` → `INVALID_CREDENTIALS`; correo sin verificar (solo después de validar la contraseña) → `EMAIL_NOT_VERIFIED`; sin membresías activas → `NO_ACTIVE_MEMBERSHIP`; una membresía → sesión directa; varias → `BUSINESS_SELECTION_REQUIRED` con la lista y un preauth token.
  - `IssuedSession selectBusiness(UUID userId, UUID businessId)` y `IssuedSession switchBusiness(UUID userId, UUID businessId, String currentRawRefresh)` (revoca la familia del refresh actual si viene) — ambos delegan en `TokenService.issueSession`, que exige membresía activa (si no, 403 `FORBIDDEN`).
  - `MeView me(UUID userId, UUID businessId)` — cuenta, negocio activo con rol y lista de negocios (`IdentityStore.membershipsOf`).
- `AuthController.java` — `@RestController @RequestMapping("/api/v1/auth") @Tag(name = "auth")`. Los DTO son `record` anidados públicos con Bean Validation (`LoginRequest`, `LoginResponse`, `SessionResponse`, `BusinessOption`, `SelectBusinessRequest`, `MeResponse`, `UserSummary`). Métodos (sus nombres son el `operationId` de OpenAPI y deben ser únicos en toda la API): `login`, `selectBusiness`, `switchBusiness`, `refresh`, `logout`, `me`. La cookie de refresh se escribe con `ResponseCookie.from("rt", token).httpOnly(true).secure(app.cookie.secure).sameSite("Strict").path("/api/v1/auth").maxAge(app.jwt.refresh-ttl)`; `logout` la borra con `maxAge(0)` y responde 204 siempre. `refresh` lee la cookie con `@CookieValue(name = "rt", required = false)`; ausente → 401 `INVALID_REFRESH_TOKEN`. `selectBusiness`, `switchBusiness` y `me` toman `sub`/`bid` del `JwtAuthenticationToken`.
- `AuthFlowIT.java` — extiende `IntegrationTest`; siembra con `TestData`; usa `java.net.http.HttpClient` y lee `Set-Cookie` manualmente.

Archivos:
- `backend/src/main/java/com/plataformacitas/identity/internal/AuthService.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/AuthController.java` — nuevo
- `backend/src/test/java/com/plataformacitas/identity/AuthFlowIT.java` — nuevo

**Done when**
- [ ] WHEN `POST /api/v1/auth/login` receives a wrong password or an unknown email THE SYSTEM SHALL answer 401 with code `INVALID_CREDENTIALS` and the same `detail` text in both cases.
- [ ] WHEN a verified account with exactly one active membership logs in THE SYSTEM SHALL answer 200 with `status` = `AUTHENTICATED`, a `session.accessToken` whose `bid` claim is that business, and a `Set-Cookie` for `rt` with `HttpOnly`, `SameSite=Strict` and `Path=/api/v1/auth`.
- [ ] WHEN an account with active memberships in two businesses logs in THE SYSTEM SHALL answer 200 with `status` and `code` = `BUSINESS_SELECTION_REQUIRED`, both businesses and a `preAuthToken`, and `POST /api/v1/auth/select-business` with that token and business B SHALL return a session whose `bid` is B and whose `role` is the role held in B.
- [ ] WHEN an account that is OWNER in A and EMPLOYEE in B calls `POST /api/v1/auth/switch-business` for B THE SYSTEM SHALL return a session with role `EMPLOYEE`, and `GET /api/v1/auth/me` SHALL report business B with role `EMPLOYEE`.
- [ ] WHEN `POST /api/v1/auth/refresh` is called with a valid `rt` cookie THE SYSTEM SHALL return a new access token for the same business and rotate the cookie, and after `POST /api/v1/auth/logout` the same cookie SHALL get 401 `INVALID_REFRESH_TOKEN`.
- [ ] WHEN an account whose email is not verified logs in with the correct password THE SYSTEM SHALL answer 403 with code `EMAIL_NOT_VERIFIED`.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest=AuthFlowIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**
```bash
git add -A && git commit -m "step 8: login"
git tag step-08-login
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-08-login
```

---

#### Paso 9 — API del negocio, suite de aislamiento y datos demo

**Tarea:** `E2-T3` · **Épica:** `02-identidad-registro` · **Depende de:** paso 2, paso 8

**Do**

Cierra HU-03: endpoints del negocio, la suite de aislamiento que crece sola y la regla de entidades.

- `BusinessController.java` — `@RestController @RequestMapping("/api/v1/business") @Tag(name = "business")`: `getBusiness()` (`GET`, cualquier rol) devuelve `BusinessResponse` desde `Businesses.current()`; `updateBusiness(@Valid UpdateBusinessRequest)` (`PUT`, `@PreAuthorize("hasRole('OWNER')")`) valida `name` 2–120, `timezone` con `ZoneId.of` y `currency` con `Currency.getInstance` (inválidos → 400 `VALIDATION_ERROR`) y llama `Businesses.update(...)` (ya existe desde el paso 5). En este bundle no hay citas ni ventas, así que la zona y la moneda se pueden cambiar; las restricciones de HU-96 llegan en el bundle 2.
- `DemoDataSeeder.java` — `ApplicationRunner` con `@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")`. Si `app.seed.demo-password` está vacío, falla el arranque con `IllegalStateException` que nombra `DEV_SEED_PASSWORD`. Si `owner@demo.local` ya existe, no hace nada (idempotente). Si no: provisiona «Peluquería Demo Norte» y «Peluquería Demo Sur» (vertical `PELUQUERIA`), crea `owner@demo.local` (verificado, OWNER en ambos) y `recepcion@demo.local` (verificado, RECEPTION en Norte), ambos con la contraseña de `DEV_SEED_PASSWORD`. Se activa siempre en el perfil `dev` y en staging con `APP_SEED_DEMO=true`.
- `TenantIsolationIT.java` — extiende `IntegrationTest`; siembra A (dueño `ownerA`, recepción `receptionA`) y B (dueño `ownerB`). Casos HTTP: `GET/PUT /api/v1/business` por dueño y recepción, validaciones, `select-business`/`switch-business` hacia un negocio ajeno → 403. Caso de tablas: obtiene con la consulta de catálogo **todas** las tablas de `public` con columna `business_id` y política RLS (así la suite crece sola con cada tabla nueva) y, para cada una, dentro de `TenantContext.run(A, ownerA, tx)` con `JdbcClient`: `select count(*) from <t> where business_id = :b` = 0, `update <t> set business_id = business_id where business_id = :b` = 0 filas, `delete from <t> where business_id = :b` = 0 filas. Además `insert into location (id, business_id, name, is_main, active) values (…, :b, 'x', false, true)` falla con SQLState `42501`.
- `ArchitectureRulesTest.java` (editar) — agrega `ENTITIES_WITH_BUSINESS_ID_ARE_SCOPED`: clases `@Entity` cuyo `getAllFields()` incluye un campo `businessId` deben ser asignables a `BusinessScopedEntity`; y `GLOBAL_ENTITIES_ONLY_IN_IDENTITY_OR_PLATFORM`: clases `@Entity` no asignables a `BusinessScopedEntity` deben residir en `com.plataformacitas.identity..` o `com.plataformacitas.platform..`. Pasan sobre producción; `ENTITIES_WITH_BUSINESS_ID_ARE_SCOPED` falla (assertThrows `AssertionError`) sobre `new ClassFileImporter().importPackages("archfixtures.rogue")`.
- `archfixtures/rogue/RogueEntity.java` — `@jakarta.persistence.Entity public class RogueEntity { @jakarta.persistence.Id java.util.UUID id; java.util.UUID businessId; }` (fuera de `com.plataformacitas`: Hibernate no la registra).

Archivos:
- `backend/src/main/java/com/plataformacitas/platform/internal/BusinessController.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/DemoDataSeeder.java` — nuevo
- `backend/src/test/java/com/plataformacitas/platform/TenantIsolationIT.java` — nuevo
- `backend/src/test/java/com/plataformacitas/ArchitectureRulesTest.java` — editar: agrega las reglas de entidades
- `backend/src/test/java/archfixtures/rogue/RogueEntity.java` — nuevo

**Done when**
- [ ] WHEN the owner of business A calls `GET /api/v1/business` THE SYSTEM SHALL return A's `id`, `name`, `slug`, `timezone`, `currency`, `trialEndsAt` and `labels`, never data of business B.
- [ ] WHEN the owner of A calls `PUT /api/v1/business` with a new name THE SYSTEM SHALL rename A and leave B's name unchanged, and WHEN a RECEPTION member of A calls it THE SYSTEM SHALL answer 403 with code `FORBIDDEN`.
- [ ] WHEN `PUT /api/v1/business` receives timezone `Mars/Base` or currency `XXXX` THE SYSTEM SHALL answer 400 with code `VALIDATION_ERROR`.
- [ ] WHEN `TenantIsolationIT` iterates over every `public` table with a `business_id` column and an RLS policy THE SYSTEM SHALL, inside business A's context, count, update and delete zero rows of business B, and inserting a `location` for B SHALL fail with SQLState `42501`.
- [ ] WHEN `ArchitectureRulesTest` checks `ENTITIES_WITH_BUSINESS_ID_ARE_SCOPED` against fixture `archfixtures.rogue.RogueEntity` THE SYSTEM SHALL report a violation, and against production classes it SHALL report none.
- [ ] WHEN the API starts with profile `dev` THE SYSTEM SHALL seed once `owner@demo.local` as OWNER of two demo businesses with password `DEV_SEED_PASSWORD`, so `bash scripts/smoke-api.sh --expect-demo` sees `BUSINESS_SELECTION_REQUIRED` with both businesses.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest='TenantIsolationIT,ArchitectureRulesTest')   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
bash scripts/smoke-api.sh --expect-demo   # expect: exit 0, "demo: BUSINESS_SELECTION_REQUIRED …" y "SMOKE API OK"
```

**Checkpoint**
```bash
git add -A && git commit -m "step 9: aislamiento"
git tag step-09-aislamiento
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-09-aislamiento
```

---

#### Paso 10 — Antiabuso: Bucket4j, Turnstile, CORS y cabeceras

**Tarea:** `E2-T4` · **Épica:** `02-identidad-registro` · **Depende de:** paso 8

**Do**

HU-91 y la infraestructura antiabuso que usan el login, la recuperación de contraseña (paso 11) y el registro (paso 12).

- `RateLimiter.java` — `@Component` con Bucket4j en memoria (un solo proceso de API). `enum Policy { LOGIN_FAILURES, SIGNUP, FORGOT_PASSWORD_IP, FORGOT_PASSWORD_EMAIL }` con capacidad y recarga desde `app.rate-limit.*` (`login-failures-per-minute` por minuto; los demás por hora) usando `Bandwidth.builder().capacity(n).refillGreedy(n, periodo).build()`. Métodos: `boolean tryConsume(Policy p, String key)`, `boolean isExhausted(Policy p, String key)` (`getAvailableTokens() == 0`), `void resetAll()` (para pruebas). `ConcurrentHashMap<String, Bucket>` con clave `policy + ":" + key`; si supera 100 000 entradas, se vacía.
- `CaptchaVerifier.java` — interfaz `boolean verify(String token, String remoteIp)` con la constante `String DUMMY_TOKEN = "XXXX.DUMMY.TOKEN.XXXX"` y una clase anidada `@Configuration(proxyBeanMethods = false) class Config` cuyo `@Bean CaptchaVerifier captchaVerifier(...)` lee `app.captcha.provider`: `fake` → acepta solo `DUMMY_TOKEN` (y lanza `IllegalStateException` si el perfil `prod` está activo); `turnstile` → exige `app.captcha.secret` no vacío y devuelve `new TurnstileCaptchaVerifier(RestClient.builder(), verifyUrl, secret)`. Expón la fábrica como método estático `create(String provider, String secret, String verifyUrl, Environment env)` para probarla.
- `TurnstileCaptchaVerifier.java` — `POST` `application/x-www-form-urlencoded` a `app.captcha.verify-url` con `secret`, `response` y `remoteip`; lee `{ "success": bool, "error-codes": [...] }` en un `record`; devuelve `success`. Cualquier excepción de red o de parseo → `false` (falla cerrada) y log `warn`. Constructor `(RestClient.Builder builder, String verifyUrl, String secret)` para enlazar `MockRestServiceServer` en pruebas.
- `AuthService.java` (editar) — inyecta `HttpServletRequest` (proxy de Spring con alcance de petición) y toma la IP con `request.getRemoteAddr()`, que en producción ya es la del cliente por `server.forward-headers-strategy: native`; así la firma que usa `AuthController` no cambia. Si `rateLimiter.isExhausted(LOGIN_FAILURES, ip)` → `DomainException(RATE_LIMITED)` antes de mirar la contraseña; cada `INVALID_CREDENTIALS` consume una ficha.
- `SecurityHardeningIT.java` — extiende `IntegrationTest`; `@BeforeEach` llama `rateLimiter.resetAll()`. Cubre los límites de login, CORS, cabeceras y `TurnstileCaptchaVerifier` con `MockRestServiceServer.bindTo(builder).build()` (respuestas con `Cloudflare` falsas: secreto `2x0000000000000000000000000000000AA` → `success:false`, secreto `1x0000000000000000000000000000000AA` → `success:true`), sin red.

Archivos:
- `backend/src/main/java/com/plataformacitas/shared/ratelimit/RateLimiter.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/captcha/CaptchaVerifier.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/captcha/TurnstileCaptchaVerifier.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/AuthService.java` — editar: límite de intentos fallidos por IP
- `backend/src/test/java/com/plataformacitas/identity/SecurityHardeningIT.java` — nuevo

**Done when**
- [ ] WHEN 10 failed logins arrive from one IP within one minute THE SYSTEM SHALL answer the 11th login attempt from that IP with 429 and code `RATE_LIMITED`, even when its password is correct.
- [ ] WHEN a request arrives with `Origin: https://evil.example`, which is not in `CORS_ALLOWED_ORIGINS`, THE SYSTEM SHALL answer 403 without `Access-Control-Allow-Origin`, and with `Origin: http://localhost:4200` it SHALL echo that origin in `Access-Control-Allow-Origin`.
- [ ] WHEN any API response is returned THE SYSTEM SHALL include `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and `Referrer-Policy: strict-origin-when-cross-origin`.
- [ ] WHEN `TurnstileCaptchaVerifier` posts to the siteverify URL THE SYSTEM SHALL send form fields `secret`, `response` and `remoteip`, and return false when the answer is `{"success":false,"error-codes":["invalid-input-response"]}`.
- [ ] WHEN siteverify answers `{"success":true}` THE SYSTEM SHALL return true, and WHEN the call fails with an I/O error THE SYSTEM SHALL return false.
- [ ] WHEN provider `fake` is configured THE SYSTEM SHALL accept only token `XXXX.DUMMY.TOKEN.XXXX`, and WHEN `CaptchaVerifier.Config.create` is called with provider `fake` and profile `prod` active THE SYSTEM SHALL throw `IllegalStateException`.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest=SecurityHardeningIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**
```bash
git add -A && git commit -m "step 10: antiabuso"
git tag step-10-antiabuso
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-10-antiabuso
```

---

#### Paso 11 — Correo transaccional y recuperación de contraseña

**Tarea:** `E2-T5` · **Épica:** `02-identidad-registro` · **Depende de:** paso 10

**Do**

HU-101. Desde este paso las variables `SMTP_*`, `EMAIL_FROM` y `APP_BASE_URL` se usan (en local apuntan a Mailpit).

- `EmailSender.java` — interfaz `void send(Message message)` con `record Message(String to, String subject, String text, String html)` anidado.
- `EmailConfig.java` — `@Configuration` con:
  - `@Bean @ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp") EmailSender smtpEmailSender(JavaMailSender mailSender, @Value("${app.email.from}") String from)` — `MimeMessageHelper(message, true, "UTF-8")` con texto y HTML.
  - `@Bean @ConditionalOnProperty(name = "app.email.provider", havingValue = "fake") RecordingEmailSender recordingEmailSender()` — `public static final class RecordingEmailSender implements EmailSender` anidada que guarda los mensajes en una lista sincronizada con `sent()` y `clear()`.
  - Un método `@TransactionalEventListener(fallbackExecution = true) void dispatch(EmailSender.Message message)` que llama `emailSender.send(message)` y registra (sin relanzar) cualquier error. Los servicios **nunca** llaman `send` directamente: publican `EmailSender.Message` con `ApplicationEventPublisher`, así el correo sale solo después del commit.
- `PasswordResetService.java` — `@Service`:
  - `void requestReset(String email, String ip)` — `rateLimiter.tryConsume(FORGOT_PASSWORD_IP, ip)` falso → `DomainException(RATE_LIMITED)`. Si la cuenta existe, está `ACTIVE` y `tryConsume(FORGOT_PASSWORD_EMAIL, email)`: invalida los tokens `RESET_PASSWORD` abiertos, crea uno nuevo (hash SHA-256, vence en `app.tokens.reset-password-ttl`) y publica el correo con asunto exacto `Restablece tu contraseña`, texto en español y enlace `{app.base-url}/restablecer-contrasena?token=<token>`. Si no existe, no hace nada visible. Todo en una transacción.
  - `void reset(String token, String newPassword)` — `lockUsableToken("RESET_PASSWORD", sha256Hex(token), now)`; ausente, usado o vencido → `INVALID_TOKEN`; si no, cambia la contraseña (Argon2id), marca el token usado y `TokenService.revokeAllForUser(userId)`.
- `AuthController.java` (editar) — `forgotPassword(@Valid ForgotPasswordRequest)` → `POST /api/v1/auth/password/forgot` responde 202 sin cuerpo siempre (salvo 429); `resetPassword(@Valid ResetPasswordRequest)` → `POST /api/v1/auth/password/reset` responde 204. `ResetPasswordRequest.newPassword` exige 10–128 caracteres.
- `PasswordResetIT.java` — extiende `IntegrationTest` (perfil `test` = proveedor `fake`); obtiene el token del enlace guardado en `RecordingEmailSender`; para vencer un token actualiza `one_time_token.expires_at` con `JdbcClient`; `@BeforeEach` limpia el emisor y los límites.

Archivos:
- `backend/src/main/java/com/plataformacitas/shared/email/EmailSender.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/email/EmailConfig.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/PasswordResetService.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/AuthController.java` — editar: endpoints forgotPassword y resetPassword
- `backend/src/test/java/com/plataformacitas/identity/PasswordResetIT.java` — nuevo

**Done when**
- [ ] WHEN a service publishes an `EmailSender.Message` inside a transaction that commits THE SYSTEM SHALL deliver it exactly once after the commit, and WHEN the transaction rolls back THE SYSTEM SHALL deliver nothing.
- [ ] WHEN `POST /api/v1/auth/password/forgot` receives a registered or an unregistered email THE SYSTEM SHALL answer 202 in both cases and send one email with subject `Restablece tu contraseña` only to the registered address, and the 6th request from one IP within an hour SHALL get 429 `RATE_LIMITED`.
- [ ] WHEN the emailed token is posted to `POST /api/v1/auth/password/reset` within 1 hour THE SYSTEM SHALL answer 204, accept the new password at login and reject the old one with 401 `INVALID_CREDENTIALS`.
- [ ] WHEN the same token is used a second time, or after its 1-hour expiry, THE SYSTEM SHALL answer 400 with code `INVALID_TOKEN`.
- [ ] WHEN a password reset completes THE SYSTEM SHALL revoke every active refresh token of the account, so a previous `rt` cookie gets 401 `INVALID_REFRESH_TOKEN`.
- [ ] WHEN `bash scripts/smoke-api.sh --expect-demo --expect-mail` runs against Mailpit (SMTP `localhost:1025`) THE SYSTEM SHALL deliver a message to `owner@demo.local` with subject `Restablece tu contraseña`.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest=PasswordResetIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
bash scripts/smoke-api.sh --expect-demo --expect-mail   # expect: exit 0, "correo: Mailpit recibió …" y "SMOKE API OK"
```

**Checkpoint**
```bash
git add -A && git commit -m "step 11: correo"
git tag step-11-correo
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-11-correo
```

---

#### Paso 12 — Registro autoservicio y crear otro negocio

**Tarea:** `E2-T6` · **Épica:** `02-identidad-registro` · **Depende de:** paso 9, paso 11

**Do**

HU-98 completa en el backend.

- `V4__platform_legal_acceptance.sql` — el DDL de `legal_acceptance` con su política de la sección «Modelo de datos» (copiar literal).
- `Businesses.java` (editar) — `void recordLegalAcceptance(UUID userId, String document, String version, Instant acceptedAt, String ip)` (`MANDATORY`, negocio de `TenantContext`; `document` ∈ `TERMS`, `DPA`).
- `SignupService.java` — `@Service`:
  - `void signup(SignupCommand c, String ip)` — orden fijo: `rateLimiter.tryConsume(SIGNUP, ip)` falso → `RATE_LIMITED`; `captchaVerifier.verify(c.captchaToken(), ip)` falso → `CAPTCHA_FAILED`; correo existente → `ACCOUNT_EXISTS`; en una transacción: `IdentityStore.createAccount(email, password, ownerName, false)`, token `VERIFY_EMAIL` (24 h) con `payload` JSON `{businessName, vertical, timezone, termsVersion, dpaVersion, acceptedAt, ip}` (versiones de `app.legal.*`), y el correo con asunto exacto `Confirma tu correo` y enlace `{app.base-url}/verificar-correo?token=<token>`.
  - `IssuedSession verify(String token)` — genera `businessId = UUID.randomUUID()` y ejecuta **una sola transacción** dentro de `TenantContext.run(businessId, null, …)`: `lockUsableToken("VERIFY_EMAIL", …)` (si falta → `INVALID_TOKEN`), `markEmailVerified`, `Businesses.provision(businessId, …)` (sede principal, ajustes del vertical, suscripción `TRIAL` a 30 días), `addMembership(userId, businessId, "OWNER")`, `recordLegalAcceptance` para `TERMS` y `DPA`, `markTokenUsed`. Después del commit, `TokenService.issueSession(userId, businessId)`.
  - `BusinessOption createAnotherBusiness(UUID userId, CreateBusinessCommand c, String ip)` — exige `acceptTerms` y `acceptDpa`; misma transacción de alta (sin token ni verificación) dentro de `TenantContext.run(nuevoId, userId, …)`.
- `SignupController.java` — `@RestController @Tag(name = "signup")`: `signup` (`POST /api/v1/signup` → 202 `{"status":"VERIFICATION_SENT"}`), `verifySignup` (`POST /api/v1/signup/verify` → 200 `SessionResponse` + cookie `rt` igual que el login), `createBusiness` (`POST /api/v1/businesses` → 201 `BusinessOption`, autenticado), `publicConfig` (`GET /api/v1/public/config` → `{captchaSiteKey, termsVersion, dpaVersion}`). `SignupRequest`: `businessName` 2–120, `vertical` = `PELUQUERIA`, `timezone` válida, `ownerName` 2–120, `email` válido ≤ 254, `password` 10–128, `acceptTerms` y `acceptDpa` `@AssertTrue`, `captchaToken` no vacío.
- `SignupIT.java` — extiende `IntegrationTest`; `@BeforeEach` limpia `RecordingEmailSender` y `RateLimiter`. Incluye una clase `@Nested` con `@MockitoBean Businesses.VerticalSeed` (de `org.springframework.test.context.bean.override.mockito`) cuyo `vertical()` devuelve `PELUQUERIA` y cuyo `defaults()` lanza `IllegalStateException`, para el caso de reversión.

Archivos:
- `backend/src/main/resources/db/migration/V4__platform_legal_acceptance.sql` — nuevo
- `backend/src/main/java/com/plataformacitas/platform/Businesses.java` — editar: recordLegalAcceptance
- `backend/src/main/java/com/plataformacitas/identity/internal/SignupService.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/SignupController.java` — nuevo
- `backend/src/test/java/com/plataformacitas/identity/SignupIT.java` — nuevo

**Done when**
- [ ] WHEN `POST /api/v1/signup` receives valid data with `acceptTerms` and `acceptDpa` true and captcha token `XXXX.DUMMY.TOKEN.XXXX` THE SYSTEM SHALL answer 202 with `{"status":"VERIFICATION_SENT"}`, create an unverified `user_account` and send one email with subject `Confirma tu correo` containing a link with `/verificar-correo?token=`.
- [ ] WHEN the email already has an account THE SYSTEM SHALL answer 409 `ACCOUNT_EXISTS` without creating a second account, WHEN the captcha token is rejected it SHALL answer 400 `CAPTCHA_FAILED`, and WHEN a 6th signup arrives from one IP within an hour it SHALL answer 429 `RATE_LIMITED`.
- [ ] WHEN `POST /api/v1/signup/verify` receives the emailed token THE SYSTEM SHALL, in one transaction, mark the email verified and create the business, its main location, its settings, an OWNER membership, a `TRIAL` subscription ending 30 days later and `legal_acceptance` rows `TERMS` and `DPA` with versions `borrador-2026-10`, and answer 200 with a session for the new business.
- [ ] WHEN the vertical seed throws during verification THE SYSTEM SHALL answer 500 `INTERNAL_ERROR` and leave no business, membership or legal acceptance, the account unverified and the token unused.
- [ ] WHEN an authenticated account calls `POST /api/v1/businesses` THE SYSTEM SHALL answer 201 and create another business with that same account as OWNER, and `GET /api/v1/auth/me` SHALL list it among `businesses`.
- [ ] WHEN a verification token is used a second time THE SYSTEM SHALL answer 400 `INVALID_TOKEN`, and `TenantIsolationIT` SHALL include table `legal_acceptance` in its per-table checks.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest='SignupIT,TenantIsolationIT')   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**
```bash
git add -A && git commit -m "step 12: registro"
git tag step-12-registro
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-12-registro
```

---

#### Paso 13 — Contrato OpenAPI, cliente generado y deriva

**Tarea:** `E2-T7` · **Épica:** `02-identidad-registro` · **Depende de:** paso 12

**Do**

HU-28. El contrato del backend genera el cliente de Angular; el control de deriva rompe CI si alguien cambia un DTO sin regenerar. Generador: `ng-openapi-gen` (nativo de Angular, funciones + servicio `Api`), en lugar del `openapi-generator typescript-angular` que mencionan los documentos.

- `OpenApiConfig.java` — `@Bean OpenAPI api()` con `info` (título `Plataforma de citas API`, versión `v1`), **un único servidor `/`** (sin esto springdoc publica `http://localhost:<puerto aleatorio>` y el archivo nunca sería estable), esquema de seguridad `bearerAuth` (HTTP bearer, formato JWT) y requisito global `bearerAuth`.
- `OpenApiExportIT.java` — extiende `IntegrationTest`, con `@EnabledIfSystemProperty(named = "openapi.export", matches = "true")`. Hace `GET /v3/api-docs`, reemplaza `\r\n` por `\n`, asegura un salto de línea final y escribe en la ruta de la propiedad de sistema `openapi.output` (el pom pasa por defecto `../frontend/openapi/openapi.json`, relativo a `backend/`), creando los directorios.
- `frontend/ng-openapi-gen.json` — exactamente:
  ```json
  {
    "$schema": "node_modules/ng-openapi-gen/ng-openapi-gen-schema.json",
    "input": "openapi/openapi.json",
    "output": "src/app/api",
    "ignoreUnusedModels": false,
    "removeStaleFiles": true
  }
  ```
- `scripts/check-api-drift.sh` — exactamente el contenido de la sección «Script de deriva del contrato». Salida 0 = sin deriva, 1 = deriva, 2 = error de uso o de herramienta.
- Genera los artefactos (son producidos por herramientas y se versionan; nunca se editan a mano): `(cd backend && ./mvnw -B -ntp test -Dtest=OpenApiExportIT -Dopenapi.export=true)` escribe `frontend/openapi/openapi.json`; `(cd frontend && npx ng-openapi-gen -c ng-openapi-gen.json)` escribe `frontend/src/app/api/` (servicio `Api` en `api.ts`, `api-configuration.ts`, `models.ts` y una función por operación en `fn/<tag>/<operación>.ts`). Nombres observados con `ng-openapi-gen` 1.1.0: la carpeta es el tag de OpenAPI y el archivo el `operationId` en kebab-case (tag `auth` + operación `login` → `fn/auth/login.ts`; tag `signup` + `signup` → `fn/signup/signup.ts`), por eso los controladores declaran `@Tag(name = …)` y nombres de método únicos.

Archivos:
- `backend/src/main/java/com/plataformacitas/shared/openapi/OpenApiConfig.java` — nuevo
- `backend/src/test/java/com/plataformacitas/OpenApiExportIT.java` — nuevo
- `frontend/ng-openapi-gen.json` — nuevo
- `scripts/check-api-drift.sh` — nuevo
- `frontend/openapi/openapi.json` + `frontend/src/app/api/**` — generados por los comandos de este paso (no se escriben a mano)

**Done when**
- [ ] WHEN `OpenApiExportIT` runs with `-Dopenapi.export=true` THE SYSTEM SHALL write the `GET /v3/api-docs` document with keys sorted and LF line endings to `frontend/openapi/openapi.json`, declaring the single server `/`.
- [ ] WHEN `npx ng-openapi-gen -c ng-openapi-gen.json` runs in `frontend/` THE SYSTEM SHALL generate `src/app/api/api.ts`, `src/app/api/models.ts`, `src/app/api/fn/auth/login.ts` and `src/app/api/fn/signup/signup.ts`.
- [ ] WHEN `bash scripts/check-api-drift.sh` runs on a tree whose committed contract and client match the backend THE SYSTEM SHALL exit 0.
- [ ] WHEN the committed client differs from what the current backend generates THE SYSTEM SHALL make `scripts/check-api-drift.sh` exit with code 1, keeping code 2 for usage or tool errors.
- [ ] WHEN `npx ng build` runs in `frontend/` with the generated client present THE SYSTEM SHALL exit 0.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp test -Dtest=OpenApiExportIT -Dopenapi.export=true)   # expect: exit 0; escribe frontend/openapi/openapi.json
(cd frontend && npx ng-openapi-gen -c ng-openapi-gen.json)   # expect: exit 0; escribe frontend/src/app/api/
test -f frontend/src/app/api/api.ts && test -f frontend/src/app/api/models.ts   # expect: exit 0
test -f frontend/src/app/api/fn/auth/login.ts && test -f frontend/src/app/api/fn/signup/signup.ts   # expect: exit 0
node -e 'const j=require("./frontend/openapi/openapi.json"); process.exit(j.servers.length===1 && j.servers[0].url==="/" ? 0 : 1)'   # expect: exit 0 — servidor fijo, sin puerto aleatorio
bash scripts/check-api-drift.sh   # expect: exit 0, "Contrato y cliente al día"
mkdir -p .tmp && cp frontend/src/app/api/api.ts .tmp/api.ts.bak && echo '// deriva' >> frontend/src/app/api/api.ts; rc=0; bash scripts/check-api-drift.sh || rc=$?; cp .tmp/api.ts.bak frontend/src/app/api/api.ts; test "$rc" -eq 1   # expect: exit 0 — la deriva simulada produce exactamente el código 1
(cd frontend && npx ng build)   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "step 13: contrato-api"
git tag step-13-contrato-api
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-13-contrato-api
```

---

#### Paso 14 — Sistema de diseño: tema Material M3, tokens y contraste

**Tarea:** `E3-T1` · **Épica:** `03-frontend-entrega` · **Depende de:** — (raíz)

**Do**

Aplica el sistema de diseño propio (tokens literales en la sección «Sistema de diseño») sobre el scaffold de Angular que creó el bootstrap. Este paso no depende del backend.

- `frontend/src/styles/_tokens.scss` — exactamente el contenido de la sección «Archivo de tokens».
- `frontend/src/styles.scss` — exactamente el contenido de la sección «Hoja global».
- `frontend/angular.json` — en `projects.frontend.architect.build.options.styles` deja `["@fontsource-variable/inter/index.css", "src/styles.scss"]` (en ese orden); en `build.configurations.production` agrega `"optimization": { "scripts": true, "styles": { "minify": true, "inlineCritical": false }, "fonts": true }` (el CSS crítico en línea usa un manejador `onload` que la CSP de Caddy bloquearía); en `serve.options` agrega `"proxyConfig": "proxy.conf.json"` (el archivo ya existe, llegó con `workspace/`).
- `frontend/src/index.html` — `<html lang="es-CO">`, `<title>Plataforma de citas</title>`, `<meta name="theme-color" content="#5B4FE9">`; conserva `<app-root></app-root>`.
- `scripts/check-contrast.mjs` — exactamente el contenido de la sección «Script de contraste». Salida 0 = todos los pares cumplen, 1 = algún par falla, 2 = falta un token o el archivo.

Archivos:
- `frontend/src/styles/_tokens.scss` — nuevo
- `frontend/src/styles.scss` — editar: reemplazo completo
- `frontend/angular.json` — editar: estilos, inlineCritical y proxy
- `frontend/src/index.html` — editar: lang, título y theme-color
- `scripts/check-contrast.mjs` — nuevo

**Done when**
- [ ] WHEN `node scripts/check-contrast.mjs` runs THE SYSTEM SHALL read `frontend/src/styles/_tokens.scss` and exit 0 only if every listed text pair reaches 4.5:1 and every listed UI pair reaches 3:1, in both the light and the dark token sets.
- [ ] WHEN `npx ng build` runs in `frontend/` THE SYSTEM SHALL exit 0 with the Angular Material M3 theme (`mat.theme` with `mat.$violet-palette`, `mat.$cyan-palette` and `Inter Variable`) and the `--c-*` tokens compiled into the global stylesheet.
- [ ] WHEN `frontend/src/index.html` is read THE SYSTEM SHALL contain `<html lang="es-CO">`.
- [ ] WHEN `frontend/angular.json` is parsed THE SYSTEM SHALL list `@fontsource-variable/inter/index.css` before `src/styles.scss` in the build styles, set `inlineCritical` to false in the production configuration and set `proxyConfig` to `proxy.conf.json` for `serve`.
- [ ] WHEN `frontend/src/styles.scss` is read THE SYSTEM SHALL contain a `@media (prefers-reduced-motion: reduce)` block and a `:focus-visible` outline that uses `var(--c-primary)`.
- [ ] WHEN `npx ng test --watch=false` runs in `frontend/` THE SYSTEM SHALL exit 0.

**Verify**
```bash
node scripts/check-contrast.mjs   # expect: exit 0, "Contraste OK"
(cd frontend && npx ng build)   # expect: exit 0
grep -q '<html lang="es-CO">' frontend/src/index.html   # expect: exit 0
node -e 'const a=require("./frontend/angular.json").projects.frontend.architect; const s=a.build.options.styles; const ok=s.indexOf("@fontsource-variable/inter/index.css")===0 && s.indexOf("src/styles.scss")===1 && a.build.configurations.production.optimization.styles.inlineCritical===false && a.serve.options.proxyConfig==="proxy.conf.json"; process.exit(ok?0:1)'   # expect: exit 0
grep -q 'prefers-reduced-motion: reduce' frontend/src/styles.scss && grep -q 'var(--c-primary)' frontend/src/styles.scss   # expect: exit 0
(cd frontend && npx ng test --watch=false)   # expect: exit 0, todas las pruebas pasan
```

**Checkpoint**
```bash
git add -A && git commit -m "step 14: diseno"
git tag step-14-diseno
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-14-diseno
```

---

#### Paso 15 — Núcleo web: sesión, guardas, etiquetas y shell

**Tarea:** `E3-T2` · **Épica:** `03-frontend-entrega` · **Depende de:** paso 13, paso 14

**Do**

HU-92 (parte de infraestructura). Todo usa el cliente generado (`src/app/api`): `inject(Api).invoke(fn, params)` devuelve promesas y lanza `HttpErrorResponse`. Componentes standalone, `signal`/`computed`, plantillas en línea, `ChangeDetectionStrategy.OnPush`, sin NgRx.

- `core/ui-text.ts` —
  - `ERROR_MESSAGES: Record<string, string>` con los mensajes en español de la tabla «Diccionario de errores» y `messageFor(error: unknown): string` (lee `error.error.code` de un `HttpErrorResponse`; si el código no está, usa el mensaje del estado HTTP; si no, el genérico).
  - `DEFAULT_LABELS` (las mismas etiquetas del vertical peluquería, como respaldo antes de cargar el negocio) y `LabelsService` (`providedIn: 'root'`): `labels = signal(DEFAULT_LABELS)`, `load()` que llama `getBusiness` y fusiona `labels`, `reset()`.
- `core/auth.ts` —
  - `AuthStore` (`providedIn: 'root'`): `session = signal<Session | null>(null)` con `{ accessToken, business: { id, name, slug, role }, businesses, user }`; `pendingPreAuth`; métodos `login(email, password)` (devuelve `'AUTHENTICATED' | 'BUSINESS_SELECTION_REQUIRED'`), `selectBusiness(id)`, `switchBusiness(id)` (llama `switchBusiness`, reemplaza la sesión, `labels.reset()` y `labels.load()`), `acceptSession(SessionResponse)`, `restore()` (refresh + `me`, sin lanzar si no hay cookie), `logout()`.
  - `authInterceptor: HttpInterceptorFn` — agrega `Authorization: Bearer <accessToken>` salvo en `/api/v1/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/password/*`, `/api/v1/signup*` y `/api/v1/public/*`; en `/auth/select-business` usa el preauth token. Ante 401 en otra ruta: un solo `refresh`; si funciona reintenta con el token nuevo; si falla, limpia la sesión y navega a `/ingresar`.
  - `errorToastInterceptor` — para estados 0, 409, 429 y 5xx muestra `MatSnackBar` con `messageFor(error)` y relanza.
  - `authGuard: CanActivateFn` (sin sesión → `UrlTree` `/ingresar?volver=<url>`) y `roleGuard: CanActivateFn` (lee `route.data['roles']`; rol ausente → `UrlTree` `/inicio`).
- `layout/shell.ts` — `Shell` (barra superior con el nombre del negocio, selector de negocio `mat-select` visible solo si hay 2+ negocios, menú `Cuenta` con `Crear otro negocio` y `Cerrar sesión`; navegación lateral `mat-sidenav` que en móvil es `mode="over"`; ítems desde `MENU = [{ label: 'Inicio', path: '/inicio', roles: ['OWNER','RECEPTION','EMPLOYEE'] }, { label: 'Mi negocio', path: '/mi-negocio', roles: ['OWNER'] }]` filtrados por rol; `<main id="contenido">` con enlace «Saltar al contenido»), `HomePage` (`h1` = nombre del negocio, prueba vence `{{ trialEndsAt | date: 'longDate' }}`, texto que usa `labels().resourcePlural`) y `BusinessPage` (lectura de `GET /api/v1/business`: nombre, zona, moneda, fin de prueba). Ningún texto de pantalla escribe «Empleado»: usa `LabelsService`.
- `app.config.ts` — conserva lo del scaffold y agrega: `provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorToastInterceptor]))`, `provideApiConfiguration('')`, `{ provide: LOCALE_ID, useValue: 'es-CO' }`, `{ provide: DEFAULT_CURRENCY_CODE, useValue: 'COP' }`, `registerLocaleData(localeEsCO)` (`@angular/common/locales/es-CO`), `provideAppInitializer(() => inject(AuthStore).restore())`.
- `core/core.spec.ts` — Vitest con `TestBed`, `provideHttpClient()`, `provideHttpClientTesting()` y `provideRouter([])`.

Archivos:
- `frontend/src/app/core/auth.ts` — nuevo
- `frontend/src/app/core/ui-text.ts` — nuevo
- `frontend/src/app/layout/shell.ts` — nuevo
- `frontend/src/app/app.config.ts` — editar: proveedores
- `frontend/src/app/core/core.spec.ts` — nuevo

**Done when**
- [ ] WHEN `messageFor` receives an `HttpErrorResponse` with status 409 and body code `SLOT_TAKEN` THE SYSTEM SHALL return `Ese horario acaba de ser tomado. Elige otro.`, and for an unknown code it SHALL return the message mapped to the HTTP status.
- [ ] WHEN `authGuard` runs without a session THE SYSTEM SHALL return a `UrlTree` to `/ingresar?volver=<url>`, and WHEN `roleGuard` runs for a route with `data.roles = ['OWNER']` while the session role is `RECEPTION` THE SYSTEM SHALL return a `UrlTree` to `/inicio`.
- [ ] WHEN an API call returns 401 and the refresh call succeeds THE SYSTEM SHALL retry the original request once with the new `Authorization: Bearer` token, and WHEN the refresh fails THE SYSTEM SHALL clear the session and navigate to `/ingresar`.
- [ ] WHEN `AuthStore.switchBusiness(id)` succeeds THE SYSTEM SHALL replace the session business, reset the labels to `DEFAULT_LABELS` and reload them with `GET /api/v1/business`.
- [ ] WHEN `Shell` renders for a session with role `RECEPTION` THE SYSTEM SHALL show menu item `Inicio` and hide `Mi negocio`, and for role `OWNER` with two businesses it SHALL show both items and the business selector.
- [ ] WHEN `npx ng build` runs in `frontend/` THE SYSTEM SHALL exit 0 with `LOCALE_ID` `es-CO` and `DEFAULT_CURRENCY_CODE` `COP` provided in `src/app/app.config.ts`.

**Verify**
```bash
(cd frontend && npx ng test --watch=false)   # expect: exit 0, todas las pruebas pasan (incluye core.spec.ts)
(cd frontend && npx ng build)   # expect: exit 0
grep -q "'es-CO'" frontend/src/app/app.config.ts && grep -q "'COP'" frontend/src/app/app.config.ts   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "step 15: nucleo-web"
git tag step-15-nucleo-web
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-15-nucleo-web
```

---

#### Paso 16 — Pantallas de acceso y registro con rutas perezosas

**Tarea:** `E3-T3` · **Épica:** `03-frontend-entrega` · **Depende de:** paso 15

**Do**

Pantallas públicas y de cuenta (HU-02, HU-98, HU-101 en la interfaz). Textos y etiquetas exactamente como en la tabla «Contrato de pantallas» (las pruebas E2E del paso 17 los usan como selectores).

- `features/auth/auth-pages.ts` — componentes standalone con plantilla en línea y Angular Material (`mat-form-field` con `<mat-label>`, `mat-button`, `mat-checkbox`), formularios reactivos tipados:
  - `LoginPage` (`/ingresar`) — `AUTHENTICATED` → navega a `volver` o `/inicio`; `BUSINESS_SELECTION_REQUIRED` → `/elegir-negocio`; error → mensaje de `messageFor` en un `<p role="alert">`. Enlaces `¿Olvidaste tu contraseña?` y `Crear una cuenta`.
  - `SelectBusinessPage` (`/elegir-negocio`) — un `button` por negocio con su nombre; sin preauth pendiente → `/ingresar`.
  - `SignupPage` (`/registro`) — campos y casillas de la tabla; zona por defecto `America/Bogota`, vertical por defecto `PELUQUERIA` (única opción, «Peluquería»); obtiene `GET /api/v1/public/config`: si `captchaSiteKey` está vacío usa el token `XXXX.DUMMY.TOKEN.XXXX`; si no, carga `https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit` y renderiza el widget en un contenedor y toma el token de su callback. Botón `Crear mi negocio` deshabilitado hasta formulario válido y ambas casillas marcadas. Éxito → muestra `h1` `Revisa tu correo`.
  - `VerifyEmailPage` (`/verificar-correo?token=`) — llama `verifySignup`, `AuthStore.acceptSession`, navega a `/inicio`; con 400 `INVALID_TOKEN` muestra `El enlace no es válido o ya venció.` en `role="alert"`.
  - `ForgotPasswordPage` (`/recuperar-contrasena`) — siempre muestra `Si el correo está registrado, te enviamos un enlace.` tras enviar.
  - `ResetPasswordPage` (`/restablecer-contrasena?token=`) — nueva contraseña (10–128) → 204 → navega a `/ingresar` con aviso.
  - `CreateBusinessPage` (`/crear-negocio`, autenticada) — mismo formulario sin cuenta ni captcha; éxito → `AuthStore.switchBusiness(nuevoId)` → `/inicio`.
- `app.routes.ts` — rutas perezosas (`loadComponent`) de la tabla «Rutas», cada una escrita como `path: '<ruta>'` con comillas simples (la verificación las busca así): públicas fuera del shell; `Shell` con `canActivate: [authGuard]` y como hijas `inicio`, `mi-negocio` (`canActivate: [roleGuard]`, `data: { roles: ['OWNER'] }`), `crear-negocio`; `''` redirige a `inicio`; `**` redirige a `inicio`.
- `app.html` — exactamente `<router-outlet />` (elimina la página de bienvenida del scaffold).
- `app.spec.ts` — reemplaza el spec del scaffold (que buscaba el `h1` «Hello, frontend»): verifica que `App` se crea y contiene un `router-outlet`.
- `features/auth/auth-pages.spec.ts` — Vitest con `HttpTestingController` y `provideRouter`.
- `scripts/smoke-web-proxy.sh` ya existe (llegó con `workspace/`): arranca la API y `ng serve`, consulta `http://localhost:4200/api/v1/public/config` a través de `frontend/proxy.conf.json` y detiene ambos procesos.

Archivos:
- `frontend/src/app/features/auth/auth-pages.ts` — nuevo
- `frontend/src/app/features/auth/auth-pages.spec.ts` — nuevo
- `frontend/src/app/app.routes.ts` — editar: reemplazo completo
- `frontend/src/app/app.html` — editar: reemplazo completo
- `frontend/src/app/app.spec.ts` — editar: reemplazo completo

**Done when**
- [ ] WHEN the login form receives 401 with code `INVALID_CREDENTIALS` THE SYSTEM SHALL show `Correo o contraseña incorrectos.` in an element with `role="alert"`.
- [ ] WHEN the login call answers `BUSINESS_SELECTION_REQUIRED` THE SYSTEM SHALL navigate to `/elegir-negocio`, which lists each business as a button with its name.
- [ ] WHEN the signup page renders THE SYSTEM SHALL keep the `Crear mi negocio` button disabled until the form is valid and both checkboxes `Acepto los términos de servicio` and `Acepto el acuerdo de encargo de datos` are checked, and with an empty `captchaSiteKey` it SHALL send captcha token `XXXX.DUMMY.TOKEN.XXXX`.
- [ ] WHEN `/verificar-correo?token=…` loads THE SYSTEM SHALL call `POST /api/v1/signup/verify`, store the session and navigate to `/inicio`, and on 400 `INVALID_TOKEN` it SHALL show `El enlace no es válido o ya venció.` in an element with `role="alert"`.
- [ ] WHEN `grep -rn "Empleado" frontend/src/app/features frontend/src/app/layout` runs THE SYSTEM SHALL find no match and exit with code 1, because vertical terms come from `LabelsService`.
- [ ] WHEN `npx ng test --watch=false` and `npx ng build` run in `frontend/` THE SYSTEM SHALL exit 0, with `src/app/app.html` equal to `<router-outlet />` and the lazy routes `/ingresar`, `/elegir-negocio`, `/registro`, `/verificar-correo`, `/recuperar-contrasena`, `/restablecer-contrasena`, `/crear-negocio`, `/inicio` and `/mi-negocio` declared in `src/app/app.routes.ts`, and `bash scripts/smoke-web-proxy.sh` prints `SMOKE WEB PROXY OK`.

**Verify**
```bash
(cd frontend && npx ng test --watch=false)   # expect: exit 0, todas las pruebas pasan
(cd frontend && npx ng build)   # expect: exit 0
rc=0; grep -rn "Empleado" frontend/src/app/features frontend/src/app/layout || rc=$?; test "$rc" -eq 1   # expect: exit 0 — grep sale 1 = sin coincidencias (2 sería error de uso y falla)
for r in ingresar elegir-negocio registro verificar-correo recuperar-contrasena restablecer-contrasena crear-negocio inicio mi-negocio; do grep -q "path: '$r'" frontend/src/app/app.routes.ts || exit 1; done   # expect: exit 0
bash scripts/check-api-drift.sh   # expect: exit 0 — el cliente generado sigue al día
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
bash scripts/smoke-web-proxy.sh   # expect: exit 0, "SMOKE WEB PROXY OK" — ng serve reenvía /api a la API y ambos procesos se detienen
```

**Checkpoint**
```bash
git add -A && git commit -m "step 16: pantallas-auth"
git tag step-16-pantallas-auth
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-16-pantallas-auth
```

---

#### Paso 17 — E2E de registro con Mailpit y pipeline de CI

**Tarea:** `E3-T4` · **Épica:** `03-frontend-entrega` · **Depende de:** paso 16

**Do**

HU-95 (solo el flujo 1) y el CI de HU-01. `frontend/playwright.config.ts` y `scripts/run-e2e.sh` ya existen (llegaron con `workspace/`).

- `frontend/e2e/registro.spec.ts` — un `test` serial: correo único `e2e+<Date.now()>@example.test`, contraseña `Clave-E2E-segura-1`, negocio `Peluquería E2E <Date.now()>`. Pasos: `page.goto('/registro')`; llena los campos por `getByLabel` con los textos de la tabla «Contrato de pantallas»; marca ambas casillas; clic en `Crear mi negocio`; espera `getByRole('heading', { name: 'Revisa tu correo' })`. Lee el correo con el fixture `request`: `GET http://localhost:8025/api/v1/messages` hasta encontrar (con `expect.poll`, 15 s) un mensaje cuyo `To[].Address` sea el correo; `GET http://localhost:8025/api/v1/message/{ID}`; extrae el enlace del campo `Text` con `/\/verificar-correo\?token=([A-Za-z0-9_-]+)/`; `page.goto('/verificar-correo?token=' + token)`; espera URL `/inicio` y `h1` con el nombre del negocio. Cierra sesión con el botón `Cuenta` → `Cerrar sesión`; espera `/ingresar`; inicia sesión con el mismo correo y contraseña (botón `Ingresar`); espera `/inicio` y el mismo `h1`.
- `.github/workflows/ci.yml` — exactamente el contenido de la sección «Workflow de CI».

Archivos:
- `frontend/e2e/registro.spec.ts` — nuevo
- `.github/workflows/ci.yml` — nuevo

**Done when**
- [ ] WHEN `bash scripts/run-e2e.sh` runs THE SYSTEM SHALL pass `e2e/registro.spec.ts`, in which a visitor registers a business at `/registro` and sees the heading `Revisa tu correo`.
- [ ] WHEN that E2E test reads the verification email through the Mailpit API at `http://localhost:8025/api/v1/messages` and opens its `/verificar-correo?token=` link THE SYSTEM SHALL land on `/inicio` with the business name as the `h1`.
- [ ] WHEN the same test signs out through `Cuenta` then `Cerrar sesión` and signs in again at `/ingresar` with the same credentials THE SYSTEM SHALL land on `/inicio` with the same `h1`.
- [ ] WHEN `.github/workflows/ci.yml` is read THE SYSTEM SHALL define jobs `backend`, `frontend`, `api-contract` and `e2e` that run `./mvnw -B -ntp verify`, `npx ng build`, `npx ng test --watch=false`, `node scripts/check-contrast.mjs`, `bash scripts/check-api-drift.sh` and `bash scripts/run-e2e.sh` on `pull_request` and on pushes to `main`.

**Verify**
```bash
bash scripts/run-e2e.sh   # expect: exit 0, 1 passed, 0 failed
for c in './mvnw -B -ntp verify' 'npx ng build' 'npx ng test --watch=false' 'node scripts/check-contrast.mjs' 'bash scripts/check-api-drift.sh' 'bash scripts/run-e2e.sh'; do grep -qF "$c" .github/workflows/ci.yml || exit 1; done   # expect: exit 0
for j in 'backend:' 'frontend:' 'api-contract:' 'e2e:'; do grep -qE "^  $j" .github/workflows/ci.yml || exit 1; done   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "step 17: e2e-ci"
git tag step-17-e2e-ci
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-17-e2e-ci
```

---

#### Paso 18 — Imágenes, pila Caddy y workflow de despliegue

**Tarea:** `E3-T5` · **Épica:** `03-frontend-entrega` · **Depende de:** paso 16

**Do**

HU-26a sin el servidor (VPS y DNS son puertas humanas, §20.1). `deploy/compose.prod.yml`, `deploy/Caddyfile` y `scripts/smoke-prod-stack.sh` ya existen (llegaron con `workspace/`).

- `deploy/api.Dockerfile` — contexto de build: `backend/target`:
  ```dockerfile
  FROM eclipse-temurin:25-jre-alpine
  RUN addgroup -S app && adduser -S -G app app
  WORKDIR /app
  COPY --chown=app:app plataforma-citas-api.jar /app/app.jar
  USER app
  ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
  EXPOSE 8080
  ENTRYPOINT ["java", "-jar", "/app/app.jar"]
  ```
- `deploy/web.Dockerfile` — contexto de build: `frontend/dist/frontend/browser`:
  ```dockerfile
  FROM caddy:2.11.7-alpine
  COPY . /srv
  ```
  (El `Caddyfile` se monta desde `deploy/Caddyfile` en `compose.prod.yml`.)
- `.github/workflows/deploy.yml` — exactamente el contenido de la sección «Workflow de despliegue».

Archivos:
- `deploy/api.Dockerfile` — nuevo
- `deploy/web.Dockerfile` — nuevo
- `.github/workflows/deploy.yml` — nuevo

**Done when**
- [ ] WHEN `docker build -f deploy/api.Dockerfile -t plataforma-citas-api:local backend/target` runs THE SYSTEM SHALL produce an image based on `eclipse-temurin:25-jre-alpine` whose configured user is `app`.
- [ ] WHEN `docker build -f deploy/web.Dockerfile -t plataforma-citas-web:local frontend/dist/frontend/browser` runs THE SYSTEM SHALL produce a `caddy:2.11.7-alpine` image that contains `/srv/index.html`.
- [ ] WHEN `bash scripts/smoke-prod-stack.sh` starts `deploy/compose.prod.yml` with those images THE SYSTEM SHALL bring every service up healthy, answer `/` with 401 without credentials and with 200 containing `<app-root` with basic auth, and answer `GET /api/v1/public/config` with 200 and `captchaSiteKey` = `1x00000000000000000000AA`.
- [ ] WHEN the API container runs with profile `prod` THE SYSTEM SHALL write ECS JSON log lines that contain the field `@timestamp`, as asserted by `scripts/smoke-prod-stack.sh`.
- [ ] WHEN `.github/workflows/deploy.yml` is read THE SYSTEM SHALL be triggered only by `workflow_dispatch`, build with `deploy/api.Dockerfile` and `deploy/web.Dockerfile`, push `plataforma-citas-api` and `plataforma-citas-web` to `ghcr.io`, and run `docker compose` `pull` and `up -d --wait` over SSH.

**Verify**
```bash
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
(cd frontend && npx ng build)   # expect: exit 0; deja frontend/dist/frontend/browser/index.html
test -f frontend/dist/frontend/browser/index.html   # expect: exit 0
docker build -f deploy/api.Dockerfile -t plataforma-citas-api:local backend/target   # expect: exit 0
docker build -f deploy/web.Dockerfile -t plataforma-citas-web:local frontend/dist/frontend/browser   # expect: exit 0
test "$(docker image inspect plataforma-citas-api:local --format '{{.Config.User}}')" = app   # expect: exit 0
docker run --rm --entrypoint test plataforma-citas-web:local -f /srv/index.html   # expect: exit 0
bash scripts/smoke-prod-stack.sh   # expect: exit 0, "SMOKE PROD STACK OK"
grep -q 'workflow_dispatch' .github/workflows/deploy.yml && grep -q 'deploy/api.Dockerfile' .github/workflows/deploy.yml && grep -q 'deploy/web.Dockerfile' .github/workflows/deploy.yml   # expect: exit 0
grep -q 'up -d --wait' .github/workflows/deploy.yml && grep -q 'ghcr.io' .github/workflows/deploy.yml   # expect: exit 0
```

**Checkpoint**
```bash
git add -A && git commit -m "step 18: despliegue"
git tag step-18-despliegue
# punto de reversión (acción humana, fuera del allowlist): git reset --hard step-18-despliegue
```


---

### 9.1 Parity and cutover

NOT APPLICABLE — greenfield build, no system is being replaced.

---

## 10. Environment Setup

### Prerequisites

| Herramienta | Versión | Comprobación |
|---|---|---|
| JDK | 25 (Temurin u Oracle; la máquina del usuario tiene Oracle JDK 25.0.4) | `java -version` → `version "25…"` |
| Docker Desktop (WSL 2 en Windows) | motor en marcha (verificado con 29.8.2) | `docker info` sale con 0 |
| Docker Compose | v2 (incluido en Docker Desktop) | `docker compose version` |
| Node.js | 24 LTS (24.21.0) | `node --version` → `v24.…` |
| npm | 11 (incluido con Node 24) | `npm --version` |
| git | ≥ 2.28 (`git init -b`) | `git --version` |
| bash | Git Bash en Windows; bash en Linux/macOS | `bash --version` |
| curl, unzip | requeridos; el Bootstrap los comprueba (Git for Windows 2.55 trae ambos — verificado en esta máquina) | `curl --version`, `unzip -v` |

Maven no se instala: lo descarga `./mvnw` (Maven 3.9.16). Python no se usa.

**Windows:** ubica el repositorio en una ruta corta, por ejemplo `C:\dev\plataforma-citas`. Las rutas del bundle (`blueprints/plataforma-citas/workspace/backend/src/test/java/…`) superan el límite de 260 caracteres (MAX_PATH) bajo carpetas largas; el Bootstrap además activa `git config core.longpaths true` antes del primer commit.

### Accounts to create first

Ninguna cuenta externa es necesaria para los pasos 1–18: el desarrollo y las verificaciones usan Mailpit, el captcha falso y las claves de prueba de Turnstile. Para el **despliegue real a staging** (puertas humanas, §20.1) se crean:

| Servicio | Para qué | Dónde |
|---|---|---|
| Hetzner Cloud | VPS de 4 GB para staging | https://console.hetzner.cloud |
| Proveedor de DNS del dominio (`APP_DOMAIN`) | registro A de staging | el registrador del dominio |
| GitHub (repo, GHCR, ambiente `staging`) | CI, imágenes, secretos de despliegue | https://github.com |
| Cloudflare Turnstile | `TURNSTILE_SITE_KEY` / `TURNSTILE_SECRET` reales | https://dash.cloudflare.com → Turnstile |
| Brevo | SMTP transaccional | https://app.brevo.com → SMTP & API |
| Sentry (opcional) | `SENTRY_DSN` | https://sentry.io |

### Environment variables

| Variable | Propósito | Dónde se obtiene | Required by step | ¿Secreta? |
|---|---|---|---|---|
| `DB_URL` | JDBC de PostgreSQL | local `jdbc:postgresql://localhost:5432/citas`; servidor `jdbc:postgresql://db:5432/citas` (fijo en `compose.prod.yml`) | 1 | no |
| `DB_SUPERUSER_PASSWORD` | superusuario del contenedor `postgres` | generada por `scripts/init-env.mjs` | Bootstrap (docker compose) | sí |
| `DB_OWNER_PASSWORD` | `app_owner` (Flyway) | generada por `scripts/init-env.mjs` | 1 | sí |
| `DB_RUNTIME_PASSWORD` | `app_runtime` (la API) | generada por `scripts/init-env.mjs` | 1 | sí |
| `JWT_SECRET` | firma HS256, 64 hex | generada por `scripts/init-env.mjs`; servidor: `openssl rand -hex 32` | 7 | sí |
| `COOKIE_SECURE` | flag `Secure` de la cookie `rt` | `false` en local; `true` en servidor (fijo en `compose.prod.yml`) | 8 | no |
| `APP_SEED_DEMO` | siembra de demostración fuera del perfil `dev` | `true` en staging, `false` en producción | 9 | no |
| `DEV_SEED_PASSWORD` | contraseña de las cuentas demo | generada por `scripts/init-env.mjs`; servidor: elegida por el equipo | 9 (perfil `dev` o `APP_SEED_DEMO=true`) | sí |
| `CORS_ALLOWED_ORIGINS` | orígenes permitidos (coma) | local `http://localhost:4200`; servidor `https://<staging>` | 10 | no |
| `CAPTCHA_PROVIDER` | `turnstile` o `fake` (el perfil `dev` fuerza `fake`; `prod` rechaza `fake`) | `turnstile` | 10 | no |
| `APP_BASE_URL` | base de los enlaces de los correos | local `http://localhost:4200`; servidor `https://<staging>` | 11 | no |
| `EMAIL_PROVIDER` | `smtp` o `fake` | `smtp` (el perfil `test` usa `fake`) | 11 | no |
| `EMAIL_FROM` | remitente | dirección verificada en Brevo | 11 | no |
| `SMTP_HOST` / `SMTP_PORT` | servidor SMTP | local `localhost` / `1025` (Mailpit); Brevo `smtp-relay.brevo.com` / `587` | 11 | no |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | credenciales SMTP | Brevo → SMTP & API (vacías con Mailpit) | 11 (solo servidor) | sí (contraseña) |
| `SMTP_AUTH` / `SMTP_STARTTLS` | autenticación y STARTTLS | local `false`/`false`; Brevo `true`/`true` | 11 | no |
| `SIGNUP_LIMIT_PER_HOUR` | límite de registros por IP | `5` (el perfil `dev` usa 100) | 12 | no |
| `LEGAL_TERMS_VERSION` / `LEGAL_DPA_VERSION` | versión aceptada de términos y acuerdo | `borrador-2026-10` hasta la revisión legal | 12 | no |
| `TURNSTILE_SITE_KEY` | clave pública del widget (vacía = sin widget) | Cloudflare Turnstile; prueba `1x00000000000000000000AA` | 12 (servida por `/public/config`); obligatoria en 18 | no |
| `TURNSTILE_SECRET` | verificación servidor a servidor | Cloudflare Turnstile; prueba `1x0000000000000000000000000000000AA` | 18 (staging/producción) | sí |
| `SENTRY_DSN` | errores del backend (vacío = desactivado) | Sentry → proyecto → DSN | nunca obligatoria | sí |
| `APP_ENV` | ambiente en Sentry | `local`, `staging` | nunca obligatoria | no |
| `PORT` | puerto de la API (por defecto 8080) | — | nunca obligatoria | no |
| `API_IMAGE` / `WEB_IMAGE` | imágenes de la pila de servidor | `ghcr.io/<owner>/plataforma-citas-api:staging` / `…-web:staging` | 18 | no |
| `SITE_ADDRESS` | dirección del sitio en Caddy | dominio de staging (HTTPS automático); `:80` en la prueba local | 18 | no |
| `CADDY_HTTP_PORT` / `CADDY_HTTPS_PORT` | puertos publicados | `80` / `443` (`8088`/`8443` en la prueba local) | 18 | no |
| `BASIC_AUTH_USER` / `BASIC_AUTH_HASH` | protección de staging | `docker run --rm caddy:2.11.7-alpine caddy hash-password --plaintext '<clave>'` | 18 | sí (hash) |
| `CI` | modo CI de Playwright | lo define GitHub Actions | 17 | no |
| `APP_DOMAIN` | dominio de la plataforma (decisión de la entrevista: no se asume un nombre) | solo documentación: de él se derivan `SITE_ADDRESS`, `APP_BASE_URL` y `CORS_ALLOWED_ORIGINS` del servidor; ningún archivo lo lee | — (documental) | no |
| `APP_OWNER_PASSWORD` / `APP_RUNTIME_PASSWORD` | contraseñas que recibe `infra/db/init/01-roles.sh` dentro del contenedor `db` | internas: las fija Compose a partir de `DB_OWNER_PASSWORD`/`DB_RUNTIME_PASSWORD`; nunca van en `.env` | Bootstrap (internas) | sí |
| `SPRING_PROFILES_ACTIVE` | perfil de Spring (`prod` en el servidor) | interna: la fija `deploy/compose.prod.yml` como literal `prod`; en local no se define (perfil por defecto `dev`) | 18 (interna) | no |

`.env.example` se versiona con todas las claves (salvo las internas y documentales) y los secretos vacíos; `.env` y `.env.*` (salvo `.env.example`) se ignoran. La aplicación valida al arrancar solo lo que usa el código que ya existe: `JwtConfig` exige `JWT_SECRET` desde el paso 7, `DemoDataSeeder` exige `DEV_SEED_PASSWORD` desde el paso 9 cuando la siembra está activa, `CaptchaVerifier` exige `TURNSTILE_SECRET` con el proveedor `turnstile` (que solo usan staging y producción). Ningún paso anterior a esos falla por su ausencia.

**Carga de variables:** la API las lee de `../.env` (`spring.config.import: optional:file:../.env[.properties]` en `application.yml`, cargador en el propio archivo de configuración) porque `./mvnw` y `java -jar` corren desde `backend/`; `docker compose` lee `.env` de la raíz por sí mismo; `scripts/smoke-api.sh` hace `set -a; . ./.env`; Playwright arranca el jar con `cwd ../backend`, que vuelve a usar `../.env`; las pruebas no leen `.env` para nada que importe (todo lo fija `application-test.yml` y `IntegrationTest`).

### Files that must be committed

| Archivo | Por qué se versiona | Línea de excepción en `.gitignore` |
|---|---|---|
| `.env.example` | plantilla de variables | `!.env.example` después de `.env.*` |
| `.gitignore`, `.gitattributes` | gobiernan el primer commit y los saltos de línea | — ningún patrón los cubre |
| `CLAUDE.md`, `AGENTS.md`, `.claude/**` | configuración de agentes | — ningún patrón los cubre |
| `docker-compose.yml`, `infra/db/init/01-roles.sh` | servicios locales y roles | — ningún patrón los cubre |
| `backend/pom.xml`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties` | build reproducible | — solo se ignora `backend/.mvn/wrapper/maven-wrapper.jar`, que no existe en el modo only-script |
| `backend/src/main/resources/application.yml`, `backend/src/test/resources/{application-test.yml,db/test-roles.sql}`, `backend/src/test/java/com/plataformacitas/support/IntegrationTest.java` | configuración verificada | — ningún patrón los cubre |
| `frontend/package.json`, `frontend/package-lock.json`, `frontend/proxy.conf.json`, `frontend/playwright.config.ts`, `frontend/ng-openapi-gen.json` | dependencias y configuración de herramientas | — ningún patrón los cubre (`frontend/.gitignore` de Angular ignora `dist`, `node_modules`, `.angular/cache`) |
| `frontend/openapi/openapi.json`, `frontend/src/app/api/**` | contrato y cliente generados (el control de deriva los compara) | — ningún patrón los cubre |
| `scripts/*`, `deploy/*`, `.github/workflows/*`, `docs/**` | operación y CI | — ningún patrón los cubre |
| `blueprints/plataforma-citas/**` | el bundle viaja con el código | — no se ignora a propósito |

### Bootstrap

Se ejecuta desde la raíz del repositorio nuevo, con el bundle ya en `blueprints/plataforma-citas/`. Es no interactivo y se puede ejecutar dos veces: la segunda corrida no cambia nada y sale con 0.

```bash
set -euo pipefail
# Orden (importa): prerrequisitos → scaffold de Angular (ng new exige que frontend/ no exista)
#   → copia NO destructiva de workspace/ (trae .gitignore y .gitattributes) → .env → Maven Wrapper
#   → repo git → dependencias npm y navegador → servicios → dependencias Maven → primer commit.
# El .gitignore está en disco desde la copia de workspace/, mucho antes del primer commit (último paso).
B=blueprints/plataforma-citas

# 0. Prerrequisitos
java -version 2>&1 | grep -q 'version "25'
node --version | grep -q '^v24\.'
docker info >/dev/null
command -v unzip >/dev/null
command -v curl >/dev/null

# 1. Frontend Angular 22 (zoneless, Vitest, sin SSR); NG_CLI_ANALYTICS evita la pregunta interactiva
test -f frontend/angular.json || NG_CLI_ANALYTICS=false npx -y @angular/cli@22.2.2 new frontend --defaults --skip-git --style=scss --ssr=false --routing --zoneless --ai-config=none --package-manager=npm

# 2. Copia de workspace/ que NUNCA pisa un archivo existente (pom, configs y scripts que los pasos editan
#    sobreviven a una segunda corrida). Portable: sin cp -n (en BSD sale con 1) ni rsync (no viene en Git Bash).
( cd "$B/workspace" && find . -type f ) | while IFS= read -r f; do
  [ -e "$f" ] || { mkdir -p "$(dirname "$f")"; cp "$B/workspace/$f" "$f"; }
done

# 3. .env local con secretos generados (si ya existe, no lo toca y sale con 0)
node scripts/init-env.mjs

# 4. Maven Wrapper 3.3.4 only-script: el zip trae solo mvnw y mvnw.cmd; las propiedades llegaron con workspace/
if [ ! -f backend/mvnw ]; then
  mkdir -p .tmp/mvnw-dist
  curl -fsSL -o .tmp/mvnw-dist/wrapper.zip https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/maven-wrapper-distribution-3.3.4-only-script.zip
  unzip -o -q .tmp/mvnw-dist/wrapper.zip -d .tmp/mvnw-dist
  cp .tmp/mvnw-dist/mvnw .tmp/mvnw-dist/mvnw.cmd backend/
fi
chmod +x backend/mvnw

# 5. Repositorio git (cada paso termina en un tag) e identidad local si falta
git rev-parse --git-dir >/dev/null 2>&1 || git init -b main
git config core.longpaths true   # Windows: rutas del bundle > 260 caracteres (MAX_PATH); idempotente e inocuo en Linux/macOS
git config user.email >/dev/null || git config user.email "constructor@plataforma-citas.local"
git config user.name >/dev/null || git config user.name "Constructor plataforma-citas"

# 6. Dependencias exactas del frontend (§11) y navegador de Playwright
( cd frontend && { test -d node_modules/@angular/material || npm install --save-exact @angular/material@22.2.2 @angular/cdk@22.2.2 @fontsource-variable/inter@5.3.0; } )
( cd frontend && { test -d node_modules/ng-openapi-gen || npm install --save-dev --save-exact ng-openapi-gen@1.1.0 @playwright/test@1.64.0; } )
( cd frontend && npx playwright install chromium )

# 7. Servicios locales: PostgreSQL 18 (crea app_owner/app_runtime al iniciar el volumen) y Mailpit
docker compose up -d --wait db mailpit

# 8. Dependencias de Maven (descarga Maven 3.9.16 y las librerías de backend/pom.xml)
( cd backend && ./mvnw -B -ntp -q dependency:resolve )

# 9. Primer commit, solo si el repo aún no tiene ninguno (los tags de los pasos necesitan un commit base)
git rev-parse --verify -q HEAD >/dev/null || { git add -A && git update-index --chmod=+x backend/mvnw && git commit -q -m "chore: bootstrap plataforma-citas"; }
```

Comandos de servicios: subir `docker compose up -d --wait db mailpit` · bajar `docker compose down` · reiniciar la base desde cero `docker compose down -v && docker compose up -d --wait db mailpit` (vuelve a ejecutar `infra/db/init/01-roles.sh`). Archivos que el bootstrap nunca sobrescribe: todo lo que ya exista, en particular `backend/pom.xml`, `frontend/package.json`, `frontend/package-lock.json` y cualquier archivo que un paso haya editado.

---

## 11. Dependencies

Procedencia: informe de `stack-researcher` del 2026-10-12 incluido en el prompt de generación (no existe archivo de runtime track para Java/Spring). Además, el generador comprobó el 2026-10-12 en los artefactos publicados: la clase `org.testcontainers.postgresql.PostgreSQLContainer` y `withInitScript` (jars 2.0.5), `ApplicationModule.Type.OPEN` (spring-modulith-api 2.1.1), `HibernatePropertiesCustomizer` en `org.springframework.boot.hibernate.autoconfigure` (spring-boot-hibernate 4.1.1), `tools.jackson.databind.json.JsonMapper` (jackson-databind 3.1.5), `NimbusJwtEncoder.withSecretKey`/`NimbusJwtDecoder.withSecretKey` (spring-security-oauth2-jose 7.1.1), `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8` (spring-security-crypto 7.1.1), el `BandwidthBuilder` de Bucket4j 8.21.0, las opciones de `ng new` 22.2.2, los mixins `mat.theme`, `$violet-palette`, `$cyan-palette` de Material 22.2.2, la CLI de ng-openapi-gen 1.1.0, la familia `Inter Variable` de fontsource 5.3.0 y el contenido del zip del Maven Wrapper 3.3.4.

### Runtime

| Paquete | Versión | Fuente | Checked | Installed by | Propósito |
|---|---|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-parent` (gestiona Spring Framework 7.0.9, Spring Security 7.1.1, Hibernate ORM 7.4.5.Final, Jackson 3.1.5, Flyway 12.4.0, PostgreSQL JDBC 42.7.13, Testcontainers 2.0.5, Angus Mail) | 4.1.1 | https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` (workspace) resuelto por `./mvnw -B -ntp -q dependency:resolve` | padre y BOM de todo el backend |
| starters `spring-boot-starter-webmvc`, `-data-jpa`, `-jdbc`, `-security`, `-oauth2-resource-server`, `-validation`, `-actuator`, `-flyway`, `-mail` | 4.1.1 (gestionada) | Maven Central, poms 4.1.1 (HTTP 200) | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | web, JPA, SQL nativo, seguridad, JWT, validación, salud, migraciones, correo |
| `org.flywaydb:flyway-database-postgresql` | 12.4.0 (`${flyway.version}`) | spring-boot-dependencies 4.1.1 + Maven Central | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | soporte de PostgreSQL en Flyway 12 |
| `org.postgresql:postgresql` | 42.7.13 (gestionada) | spring-boot-dependencies 4.1.1 | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | driver JDBC |
| `org.springframework.modulith:spring-modulith-bom` → `spring-modulith-starter-core` | 2.1.1 | https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith-bom/maven-metadata.xml | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | módulos y verificación |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 3.1.1 | https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | contrato OpenAPI |
| `com.bucket4j:bucket4j_jdk17-core` | 8.21.0 | https://repo1.maven.org/maven2/com/bucket4j/bucket4j_jdk17-core/maven-metadata.xml | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | límites de abuso |
| `org.bouncycastle:bcprov-jdk18on` | 1.86 | Maven Central metadata | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | Argon2id para `Argon2PasswordEncoder` |
| `com.nimbusds:nimbus-jose-jwt` | 10.9.1 (transitiva) | spring-security-oauth2-jose-7.1.1.pom | 2026-10-12 | transitiva de `spring-boot-starter-oauth2-resource-server` (§10 Bootstrap) — fijada solo para documentar la versión resuelta | firma y validación JWT |
| `io.sentry:sentry-spring-boot-4` | 8.60.0 | Maven Central pom | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | errores del backend (inactivo sin `SENTRY_DSN`) |
| `@angular/core`, `@angular/common`, `@angular/router`, `@angular/forms`, `@angular/platform-browser`, `@angular/cli`, `@angular/build` | 22.2.2 (`ng new` escribe el rango `^22.2.0`; el lockfile fija la versión resuelta) | npm registry dist-tags | 2026-10-12 | §10 Bootstrap — `npx -y @angular/cli@22.2.2 new frontend …` | framework del frontend |
| `@angular/material` | 22.2.2 | npm registry + unpkg package.json | 2026-10-12 | §10 Bootstrap — `npm install --save-exact @angular/material@22.2.2 …` | componentes y tema M3 |
| `@angular/cdk` | 22.2.2 (par exacto de Material) | npm registry; peer de Material 22.2.2 verificado | 2026-10-12 | §10 Bootstrap — mismo `npm install --save-exact` | primitivas de Material |
| `@fontsource-variable/inter` | 5.3.0 | npm registry | 2026-10-12 | §10 Bootstrap — mismo `npm install --save-exact` | fuente Inter Variable autohospedada |
| Imagen `postgres:18-alpine` (18.6) | 18-alpine | https://hub.docker.com/v2/repositories/library/postgres/tags?name=18 | 2026-10-12 | `docker-compose.yml` y `deploy/compose.prod.yml` (emitidos) + Testcontainers en `IntegrationTest` | base de datos |
| Imagen `axllent/mailpit:v1.31.5` | v1.31.5 | Docker Hub tags | 2026-10-12 | `docker-compose.yml` (emitido) | SMTP y API de correo locales (1025 / 8025) |
| Imagen `eclipse-temurin:25-jre-alpine` | 25-jre-alpine | https://api.adoptium.net/v3/info/available_releases ; Docker Hub | 2026-10-12 | paso 18 — `deploy/api.Dockerfile` | runtime de la API |
| Imagen `caddy:2.11.7-alpine` | 2.11.7-alpine | Docker Hub | 2026-10-12 | paso 18 — `deploy/web.Dockerfile`; `scripts/smoke-prod-stack.sh` (hash de basic auth) | proxy, TLS y archivos estáticos |
| Brevo SMTP `smtp-relay.brevo.com:587` STARTTLS | servicio (sin versión) | UNVERIFIED — verify before install (solo resumen de búsqueda) | 2026-10-12 | `.env` del servidor (puerta humana) | correo transaccional en staging/producción |
| Cloudflare Turnstile siteverify + claves de prueba | servicio (sin versión) | https://developers.cloudflare.com/turnstile/get-started/server-side-validation/ ; https://developers.cloudflare.com/turnstile/troubleshooting/testing/ | 2026-10-12 | paso 10 — `TurnstileCaptchaVerifier` | captcha del registro |

### Development

| Paquete | Versión | Fuente | Checked | Installed by | Propósito |
|---|---|---|---|---|---|
| JDK (Temurin/Oracle) | 25 LTS | https://api.adoptium.net/v3/info/available_releases | 2026-10-12 | prerrequisito instalado por la persona (§10 Prerequisites) | compilar y ejecutar |
| Apache Maven (vía wrapper) | 3.9.16 | Maven Central metadata | 2026-10-12 | `backend/.mvn/wrapper/maven-wrapper.properties` (emitido); lo descarga `./mvnw` en §10 Bootstrap | build |
| Maven Wrapper (only-script) | 3.3.4 | https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/ | 2026-10-12 | §10 Bootstrap — `curl … maven-wrapper-distribution-3.3.4-only-script.zip` | `mvnw` sin Maven del sistema |
| Node.js / npm | 24.21.0 LTS / 11.19.0 | https://nodejs.org/dist/index.json | 2026-10-12 | prerrequisito instalado por la persona; CI `actions/setup-node` | frontend y scripts |
| starters de prueba `spring-boot-starter-test`, `-security-test`, `-webmvc-test`, `spring-boot-testcontainers` | 4.1.1 (gestionada) | Maven Central poms 4.1.1 | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | JUnit, MockMvc, utilidades de prueba |
| `org.testcontainers:testcontainers-postgresql`, `testcontainers-junit-jupiter` | 2.0.5 (gestionada) | testcontainers-bom-2.0.5.pom; jar 2.0.5 inspeccionado | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | PostgreSQL real en pruebas |
| `org.springframework.modulith:spring-modulith-starter-test` | 2.1.1 | spring-modulith-bom 2.1.1 | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | `ApplicationModules.verify()` |
| `com.tngtech.archunit:archunit-junit5` | 1.5.1 | Maven Central metadata | 2026-10-12 | §10 Bootstrap — `backend/pom.xml` + `./mvnw dependency:resolve` | reglas de arquitectura |
| `ng-openapi-gen` | 1.1.0 | https://registry.npmjs.org/ng-openapi-gen/1.1.0 | 2026-10-12 | §10 Bootstrap — `npm install --save-dev --save-exact ng-openapi-gen@1.1.0 …` | cliente TypeScript generado |
| `@playwright/test` (+ Chromium) | 1.64.0 | npm dist-tags | 2026-10-12 | §10 Bootstrap — mismo `npm install --save-dev --save-exact` y `npx playwright install chromium` | E2E |
| Vitest, jsdom, TypeScript | los que fija `ng new` 22.2.2 | escritos por el scaffold de Angular 22.2.2 (sin pin propio) | 2026-10-12 | §10 Bootstrap — `ng new` | pruebas unitarias del frontend |
| `actions/checkout` / `actions/setup-java` / `actions/setup-node` / `actions/upload-artifact` | v7 / v6 / v7 / v7 (mayor) | https://api.github.com/repos/actions/{checkout,setup-java,setup-node,upload-artifact}/releases (v7.0.1 · v6.0.1 · v7.0.0 del 2026-07-14 · v7.0.2) | 2026-10-12 | pasos 17 y 18 — `.github/workflows/*.yml` | CI/CD |

### Deliberately not used

| Descartado | En su lugar | Por qué |
|---|---|---|
| Spring Boot 4.2.0-M2, Modulith 2.2.0-M2, Maven 4.0.0-rc, `postgres:19beta`, Angular 22.3.0-next, Playwright 1.65-alpha | las versiones estables de arriba | PRERELEASE |
| jOOQ | `JdbcClient` con SQL nativo | jOOQ OSS + PostgreSQL 18 no verificable |
| `openapi-generator` (`typescript-angular`) | `ng-openapi-gen` | nativo de Angular y sin Java en el frontend |
| `spring-modulith-starter-jpa` / `-jdbc` (registro de eventos) | eventos síncronos | exigen la tabla `event_publication`; el outbox llega en el bundle 4 |
| `net.javacrumbs.shedlock` 7.10.1 | — | sin tareas programadas en este bundle; compatibilidad con Spring 7 sin verificar (prueba temprana en el bundle 4) |
| `@angular/service-worker` 22.2.2 | — | PWA en el bundle 7 |
| `@sentry/angular` 11.6.0 | — | instrumentación del frontend en el bundle 3 (HU-26b) |
| restic 0.19.1 | — | respaldos en el bundle 3 |
| `maven:3.9-eclipse-temurin-25`, `node:24-alpine` (imágenes de build) | build con `./mvnw` y `npx ng build` en el runner; las imágenes copian los artefactos | menos capas y el mismo build que el gate |
| Prettier / ESLint | el compilador de TypeScript estricto y `ng build` | no hay versión verificada en el informe de esta sesión; se agrega en un bundle posterior con pin verificado |
| Redis | Bucket4j en memoria | una sola instancia de API |
| `nl.martijndwars:web-push`, `biweekly` | `com.interaso:webpush`, `ical4j` (bundles 5 y 7) | obsoletas |
| Clerk / Auth0 / Supabase Auth | autenticación propia | el modelo cuenta–membresía–rol por negocio es el producto |

---

## 12. Deployment Strategy

### Hosting

VPS de 4 GB en Hetzner (región Europa/EE. UU., la que el equipo elija al crear la cuenta), Ubuntu LTS con Docker Engine y el plugin de Compose. Pila: `db` (PostgreSQL 18, 1 GB), `api` (imagen de la API, 1 GB, `-XX:MaxRAMPercentage=60`), `web` (Caddy con la SPA, 256 MB), definida en `deploy/compose.prod.yml`. Build: `./mvnw -B -ntp -DskipTests package` → `backend/target/plataforma-citas-api.jar`; `npx ng build` → `frontend/dist/frontend/browser`. En el servidor: `/opt/plataforma-citas/{deploy/compose.prod.yml, deploy/Caddyfile, infra/db/init/01-roles.sh, .env}`. En este bundle solo existe **staging**; la producción medida (HU-26b) y los respaldos (HU-25) son del bundle 3.

### Environments

| Ambiente | Rama | URL | Base de datos | Terceros |
|---|---|---|---|---|
| Local | — | http://localhost:4200 (API :8080) | contenedor `db` de `docker-compose.yml` | Mailpit, captcha falso |
| Pruebas | cualquiera | — | Testcontainers por corrida | correo y captcha falsos |
| CI | PR y `main` | — | Testcontainers y `docker compose` en el runner | Mailpit, captcha falso |
| Staging | `main` (despliegue manual) | `https://<subdominio de APP_DOMAIN>` detrás de autenticación básica | `db` en el VPS | Brevo, Turnstile con claves reales, siembra de demostración |
| Producción | — | bundle 3 | — | — |

### CI/CD

#### Workflow de CI

`.github/workflows/ci.yml` (paso 17). GitHub ejecuta cada `run:` con `bash -e`, así que un comando cuyo resultado esperado es un código distinto de cero se escribe capturando el código (`rc=0; … || rc=$?; test "$rc" -eq 1`). Corre los mismos comandos que el gate global de §20.1. Que un PR quede bloqueado si falla es una regla de protección de rama en GitHub (puerta humana: la configura el dueño del repositorio).

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '25'
          cache: maven
      - name: Build, pruebas, Modulith y ArchUnit (Testcontainers)
        working-directory: backend
        run: ./mvnw -B -ntp verify

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-node@v7
        with:
          node-version: '24.21.0'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - working-directory: frontend
        run: npm ci
      - working-directory: frontend
        run: npx ng build
      - working-directory: frontend
        run: npx ng test --watch=false
      - run: node scripts/check-contrast.mjs
      - name: Sin términos del vertical escritos a mano en pantallas
        run: rc=0; grep -rn "Empleado" frontend/src/app/features frontend/src/app/layout || rc=$?; test "$rc" -eq 1

  api-contract:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '25'
          cache: maven
      - uses: actions/setup-node@v7
        with:
          node-version: '24.21.0'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - working-directory: frontend
        run: npm ci
      - name: El cliente TypeScript coincide con el contrato del backend
        run: bash scripts/check-api-drift.sh

  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '25'
          cache: maven
      - uses: actions/setup-node@v7
        with:
          node-version: '24.21.0'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - working-directory: frontend
        run: npm ci
      - working-directory: frontend
        run: npx playwright install --with-deps chromium
      - run: node scripts/init-env.mjs
      - name: Registro → verificación por Mailpit → ingreso
        run: bash scripts/run-e2e.sh
      - if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: playwright-report
          path: frontend/playwright-report
```

#### Workflow de despliegue

`.github/workflows/deploy.yml` (paso 18). Manual (`workflow_dispatch`): construye las dos imágenes, las publica en GHCR con la etiqueta del commit y `staging`, copia la pila al VPS y la actualiza. Requiere los secretos del ambiente `staging` de GitHub (puerta humana, §20.1): `STAGING_SSH_HOST`, `STAGING_SSH_USER`, `STAGING_SSH_KEY` (clave privada) y `STAGING_SSH_KNOWN_HOSTS` (línea de `ssh-keyscan` del servidor). El `.env` del servidor vive solo en el servidor (`/opt/plataforma-citas/.env`) y define `API_IMAGE=ghcr.io/<owner>/plataforma-citas-api:staging` y `WEB_IMAGE=ghcr.io/<owner>/plataforma-citas-web:staging`.

```yaml
name: Deploy staging

on:
  workflow_dispatch:

permissions:
  contents: read
  packages: write

concurrency:
  group: deploy-staging
  cancel-in-progress: false

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    outputs:
      owner: ${{ steps.owner.outputs.owner }}
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '25'
          cache: maven
      - uses: actions/setup-node@v7
        with:
          node-version: '24.21.0'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - id: owner
        run: echo "owner=${GITHUB_REPOSITORY_OWNER,,}" >> "$GITHUB_OUTPUT"
      - name: Jar de la API
        working-directory: backend
        run: ./mvnw -B -ntp -DskipTests package
      - name: Build de Angular
        working-directory: frontend
        run: npm ci && npx ng build
      - name: Login en GHCR
        run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
      - name: Imagen de la API
        run: |
          IMAGE=ghcr.io/${{ steps.owner.outputs.owner }}/plataforma-citas-api
          docker build -f deploy/api.Dockerfile -t "$IMAGE:${{ github.sha }}" -t "$IMAGE:staging" backend/target
          docker push "$IMAGE:${{ github.sha }}"
          docker push "$IMAGE:staging"
      - name: Imagen web (Caddy + Angular)
        run: |
          IMAGE=ghcr.io/${{ steps.owner.outputs.owner }}/plataforma-citas-web
          docker build -f deploy/web.Dockerfile -t "$IMAGE:${{ github.sha }}" -t "$IMAGE:staging" frontend/dist/frontend/browser
          docker push "$IMAGE:${{ github.sha }}"
          docker push "$IMAGE:staging"

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - uses: actions/checkout@v7
      - name: Llave SSH
        run: |
          install -m 700 -d ~/.ssh
          echo "${{ secrets.STAGING_SSH_KEY }}" > ~/.ssh/id_deploy
          chmod 600 ~/.ssh/id_deploy
          echo "${{ secrets.STAGING_SSH_KNOWN_HOSTS }}" > ~/.ssh/known_hosts
      - name: Copiar la pila al servidor
        run: |
          TARGET="${{ secrets.STAGING_SSH_USER }}@${{ secrets.STAGING_SSH_HOST }}"
          ssh -i ~/.ssh/id_deploy "$TARGET" 'mkdir -p /opt/plataforma-citas/deploy /opt/plataforma-citas/infra/db/init'
          scp -i ~/.ssh/id_deploy deploy/compose.prod.yml deploy/Caddyfile "$TARGET:/opt/plataforma-citas/deploy/"
          scp -i ~/.ssh/id_deploy infra/db/init/01-roles.sh "$TARGET:/opt/plataforma-citas/infra/db/init/"
      - name: Actualizar la pila
        run: |
          TARGET="${{ secrets.STAGING_SSH_USER }}@${{ secrets.STAGING_SSH_HOST }}"
          ssh -i ~/.ssh/id_deploy "$TARGET" 'cd /opt/plataforma-citas && docker compose -f deploy/compose.prod.yml --env-file .env pull && docker compose -f deploy/compose.prod.yml --env-file .env up -d --wait'
```

Reversión: en el servidor, cambiar `API_IMAGE`/`WEB_IMAGE` del `.env` a la etiqueta `<sha>` anterior y repetir `docker compose -f deploy/compose.prod.yml --env-file .env up -d --wait` (≈1 minuto). Las migraciones de Flyway son solo aditivas (expandir y luego contraer), así que la imagen anterior sigue funcionando sobre el esquema nuevo.

### Release and rollback

Promoción: fusionar a `main` con CI en verde → ejecutar el workflow `Deploy staging` → publica `:<sha>` y `:staging` → el servidor hace `pull` y `up -d --wait`. Reversión: en el servidor, fijar `API_IMAGE`/`WEB_IMAGE` del `.env` a la etiqueta `<sha>` anterior y repetir `docker compose -f deploy/compose.prod.yml --env-file .env up -d --wait` (≈1 minuto). Migraciones: Flyway corre al arrancar la API nueva; solo cambios aditivos en el mismo despliegue, para que la imagen anterior funcione sobre el esquema nuevo.

### Domain, DNS, TLS

Registro `A` del subdominio de staging hacia la IP del VPS (puerta humana). Caddy obtiene y renueva el certificado automáticamente cuando `SITE_ADDRESS` es ese dominio; puertos 80 y 443 abiertos en el firewall del VPS. Sin redirección apex ↔ www en este bundle (no hay sitio público de marketing).

---

## 13. Testing Strategy

| Capa | Framework | Qué cubre | Dónde | Corre |
|---|---|---|---|---|
| Unitaria backend | JUnit 5 + MockMvc standalone | `Money`, mapeo de errores, id de petición, reglas de arquitectura, módulos | `backend/src/test/java/**/*Test.java` | cada commit (`./mvnw verify`) |
| Integración backend | JUnit 5 + Testcontainers (PostgreSQL 18) + `java.net.http.HttpClient` | roles, RLS, aislamiento, alta de negocio, cuentas, tokens, login, límites, CORS, correo, registro, contrato | `backend/src/test/java/**/*IT.java` | cada commit |
| Unitaria frontend | Vitest (`ng test`) + TestBed | mensajes de error, guardas, interceptor de refresh, cambio de negocio, shell por rol, pantallas | `frontend/src/**/*.spec.ts` | cada commit |
| Contrato | `scripts/check-api-drift.sh` | contrato y cliente generados al día | job `api-contract` | cada commit |
| E2E | Playwright (Chromium) + Mailpit | registro → verificación → ingreso | `frontend/e2e/` | cada commit (job `e2e`) |
| Humo | `scripts/smoke-api.sh`, `scripts/smoke-prod-stack.sh` | jar real contra servicios reales; imágenes y pila de staging | local, pasos 1/7/9/11/18 | por paso |

### Critical flows to cover E2E
1. Registro de negocio → verificación por correo (Mailpit) → ingreso (este bundle, `e2e/registro.spec.ts`).
2. Login → crear cita → aparece en la agenda (bundle 2).
3. Atender → cobro mixto; cierre diario con diferencia (bundle 3).

### Test data

Integración: un contenedor PostgreSQL 18 por corrida de Maven (`IntegrationTest`), creado con `withInitScript("db/test-roles.sql")`, la aplicación conectada como `app_runtime` y Flyway como `app_owner`; cada prueba siembra con `TestData` datos únicos (sufijo `UUID`) y afirma propiedades, no totales, porque las clases comparten la base. No hay orden entre pruebas. E2E: base de `docker-compose.yml` (`db`) y Mailpit (`mailpit`, SMTP 1025 / API 8025), con correos únicos por corrida (`e2e+<marca de tiempo>@example.test`); la API corre en perfil `dev`. Ambos servicios se emiten en §19.6 y sus variables están en §10.

### What is deliberately not tested
- El envío real por Brevo y el captcha real de Cloudflare (requieren red y cuentas): se prueban con los adaptadores contra servidores simulados y se comprueban a mano en staging (§20.1).
- Rendimiento y memoria del VPS: se miden en el bundle 3 (HU-26b).
- Pruebas visuales por captura: el sistema de diseño se verifica con contraste calculado y build.

---

## 14. Security & Secrets

| Tema | Control | Dónde |
|---|---|---|
| Almacenamiento de secretos | `.env` local generado e ignorado; en el servidor `/opt/plataforma-citas/.env` (permisos 600); en GitHub, secretos del ambiente `staging` | `scripts/init-env.mjs`, `.gitignore`, `deploy.yml` |
| Rotación | `JWT_SECRET` y contraseñas de base de datos se rotan editando el `.env` del servidor y reiniciando (`JWT_SECRET` invalida los accesos vigentes; los refresh siguen sirviendo); cada 6 meses o ante sospecha | runbook del equipo |
| Validación de entrada | Bean Validation en cada DTO; `ApiExceptionHandler` | controladores |
| XSS | Angular escapa por defecto; nada de `innerHTML` con datos; CSP en Caddy | `deploy/Caddyfile` |
| Inyección SQL | solo consultas parametrizadas (`JdbcClient` con parámetros nombrados, JPA) | backend |
| AuthN / AuthZ | §8 — en el servidor, en cada petición | `SecurityConfig`, `@PreAuthorize` |
| CSRF | sin cookies de autenticación de negocio; cookie `rt` `SameSite=Strict` con ruta `/api/v1/auth`; mismo origen | §8 |
| Límites / abuso | Bucket4j: login 10 fallos/min/IP, registro 5/h/IP, recuperación 5/h/IP + 3/h/correo; captcha Turnstile en registro; verificación de correo | `RateLimiter`, `CaptchaVerifier` |
| Webhooks | NOT APPLICABLE en este bundle — los de 360dialog (cabecera `Authorization` secreta) y Wompi (checksum SHA-256) llegan en los bundles 4 y 6 | — |
| Auditoría de dependencias | `npm audit --omit=dev` y revisión de alertas de Dependabot de GitHub en cada sprint; la corrige quien esté de turno | CI / GitHub |
| Cabeceras | API: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`; Caddy además `Strict-Transport-Security: max-age=31536000` y la CSP de `deploy/Caddyfile` | `SecurityConfig`, `Caddyfile` |
| Datos personales | correo, nombre y contraseña (Argon2id) de las cuentas; IP de aceptación legal; ninguna historia clínica | §4 |
| Higiene de logs | nunca contraseñas, tokens, cookies ni cuerpos de autenticación; MDC solo `requestId` y `businessId` | `.claude/rules/backend-api.md` |

**Reglas duras**
- Ningún secreto se versiona, se imprime en un log, se envía a Sentry ni se incrusta en el bundle del navegador; lo que llega al navegador es público.
- Toda autorización del servidor ocurre antes del trabajo.
- Los webhooks de terceros (bundles 4 y 6) se autentican antes de parsear el cuerpo como confiable.

**Régimen legal:** Ley 1581 de 2012 (Habeas Data, Colombia). La plataforma es **encargada** del tratamiento y cada negocio **responsable** de los datos de sus clientes; el registro guarda la aceptación versionada de términos y del acuerdo de encargo (`legal_acceptance`). Los textos legales (términos de servicio, política de tratamiento de datos, acuerdo de encargo, aviso de privacidad) **no forman parte de este bundle**: siguen como borradores fuera de este repositorio, pendientes de revisión por abogado (puerta humana), y llegan al repositorio con el bundle 2 o con esa revisión. En este bundle el registro solo guarda la cadena de versión aceptada, `borrador-2026-10` (`LEGAL_TERMS_VERSION`/`LEGAL_DPA_VERSION`). Nunca se almacena información clínica.

---

## 15. Accessibility

**Meta: WCAG 2.2 nivel AA.**

### Baseline requirements
| Requisito | Regla |
|---|---|
| HTML semántico | `header`/`nav`/`main`/`footer`, un `h1` por pantalla, encabezados en orden |
| Teclado | todo operable por teclado; enlace «Saltar al contenido» hacia `<main id="contenido">`; sin trampas de foco (los menús de Material las gestionan) |
| Foco visible | `:focus-visible` con `2px solid var(--c-primary)` (≥ 3:1) |
| Contraste | texto 4.5:1, interfaz 3:1 — comprobado por `scripts/check-contrast.mjs` |
| Formularios | `<mat-label>` en cada campo; errores como texto en `role="alert"`, no solo color |
| Imágenes | en este bundle no hay imágenes de contenido; los íconos decorativos llevan `aria-hidden="true"` |
| Movimiento | `prefers-reduced-motion: reduce` desactiva animaciones |
| Zoom / reflujo | usable a 200 % y a 320 px sin scroll horizontal |
| Regiones vivas | mensajes de formulario en `role="alert"`; snackbars de Material anuncian por `aria-live` |
| Idioma | `<html lang="es-CO">` |

### WCAG 2.2 additions
| SC | Requisito |
|---|---|
| 2.4.11 Focus Not Obscured | la barra superior no es pegajosa sobre el contenido enfocado; los snackbars aparecen abajo sin tapar el foco |
| 2.5.7 Dragging Movements | no hay arrastre en este bundle (la agenda del bundle 2 deberá ofrecer alternativa con botones) |
| 2.5.8 Target Size | botones y enlaces de al menos 24×24 px (Material usa 40 px o más) |
| 3.3.7 Redundant Entry | «Crear otro negocio» no vuelve a pedir correo ni nombre de la cuenta |
| 3.3.8 Accessible Authentication | se permite pegar y usar gestores de contraseñas; el captcha Turnstile no es una prueba cognitiva |

### Verification
```bash
node scripts/check-contrast.mjs   # expect: exit 0, "Contraste OK"
grep -q '<html lang="es-CO">' frontend/src/index.html   # expect: exit 0
bash scripts/run-e2e.sh   # expect: exit 0 — la E2E encuentra cada campo por su nombre accesible (getByLabel/getByRole)
```
No hay ejecución automática de axe en este bundle: `@axe-core/playwright` no tiene versión verificada en el informe de esta sesión y se agrega en el bundle 2 con pin verificado. Antes del lanzamiento: recorrido solo con teclado de los flujos de §8, una pasada con lector de pantalla (NVDA o VoiceOver) del registro y el ingreso, y una pasada a 200 % de zoom a 360 px.

---

## 16. Observability & Cost

### Instrumentation
| Señal | Herramienta | Qué captura | Quién la mira |
|---|---|---|---|
| Errores | Sentry (`sentry-spring-boot-4`, activo con `SENTRY_DSN`) | excepciones no manejadas con ambiente; sin cuerpos ni cabeceras de autenticación | equipo de desarrollo |
| Logs | logging estructurado de Spring Boot, formato ECS en `prod` (`logging.structured.format.console=ecs`) | JSON con `@timestamp`, nivel, logger y MDC `requestId`, `businessId` | equipo (vía `docker compose logs`) |
| Métricas | Actuator `health` (otras métricas en el bundle 3) | estado de la API y de la base | monitor externo |
| Disponibilidad | monitor gratuito externo (UptimeRobot u otro, puerta humana) | `GET https://<staging>/api/v1/public/config` cada 5 min | equipo |

### The metrics that matter for this project
| Métrica | Meta | Alerta en |
|---|---|---|
| Disponibilidad de staging (`/api/v1/public/config`) | ≥ 99 % semanal | 2 fallos seguidos |
| Errores 5xx en Sentry | 0 por día | cualquier error nuevo |
| Registros completados vs iniciados (verificaciones / registros) | ≥ 70 % | < 50 % en una semana |
| Respuestas 429 de login y registro | < 20 por día | > 100 por día (posible abuso) |
| Memoria del contenedor `api` | < 75 % de 1 GB | > 85 % (medición formal en el bundle 3) |

### Health check
`GET /actuator/health` responde `{"status":"UP"}` solo si la base de datos responde (indicador de DataSource de Boot); el indicador de correo está desactivado para que un SMTP caído no tumbe la API. No se publica por Caddy; lo usan el healthcheck del contenedor y la prueba de humo. El monitor externo consulta `/api/v1/public/config`, que atraviesa Caddy, la API y la configuración.

### Cost model
| Servicio | Plan gratuito | Costo a escala v1 (piloto + ~20 negocios) | Costo a 10× | Línea a vigilar |
|---|---|---|---|---|
| VPS Hetzner 4 GB | — | ≈ USD 8/mes (staging; producción en bundle 3) | otro VPS o uno más grande | memoria con JVM + PostgreSQL |
| GHCR | gratis para el volumen de imágenes de este proyecto | USD 0 | USD 0 | almacenamiento de imágenes privadas viejas |
| GitHub Actions | minutos incluidos del plan | USD 0 | posible pago por minutos | jobs de E2E y Testcontainers en cada PR |
| Brevo | 300 correos/día en el plan gratuito (UNVERIFIED) | USD 0 | plan de pago por volumen | registros y recuperaciones masivos |
| Cloudflare Turnstile | gratis | USD 0 | USD 0 | — |
| Sentry | plan Developer gratuito | USD 0 | plan Team | volumen de eventos |

**Costo mensual estimado al lanzar este bundle: ≈ USD 8.** La partida mayor es el VPS; la palanca más barata es mantener staging y producción en VPS separados pequeños en vez de uno grande. El costo que escala de forma peligrosa es WhatsApp (360dialog por número, bundles 4–5), no este bundle (§20.2).

---

## 17. Model Routing

NOT APPLICABLE — this project does not call an LLM at runtime.

---

## 18. Skills to Use During Build

| Skill | Pasos | Por qué | Instalación |
|---|---|---|---|
| frontend-design (se activa sola) | 14, 15, 16 | interfaz de producción con identidad propia sobre los tokens de §7 | `/plugin marketplace add anthropics/skills` y luego `/plugin install example-skills@anthropic-agent-skills` |
| ui-ux-pro-max (se activa sola) | 14 | revisar escala tipográfica y estilo de componentes con los valores ya fijados en §7 | `/plugin marketplace add nextlevelbuilder/ui-ux-pro-max-skill` y luego `/plugin install ui-ux-pro-max@ui-ux-pro-max-skill` |
| emil-design-eng (se activa sola) | 15, 16 | transiciones del shell y estados de carga dentro del presupuesto de 150–200 ms | `npx skills@latest add emilkowalski/skills` |
| playwright-cli (se activa sola) | 17 | escribir y depurar la E2E del registro | `npm install -g @playwright/cli@latest` y luego `playwright-cli install --skills` |

Ninguna es obligatoria: si una no está instalada, el constructor sigue la guía de este blueprint, lo anota en una línea y continúa. Las skills del propio proyecto están en §19.4.

---

## 19. Agent Workspace

El proyecto trae su propia configuración de agentes como archivos reales bajo `blueprints/plataforma-citas/workspace/`, que replica la estructura del repositorio:

```
workspace/
├── CLAUDE.md                              # §19.1
├── AGENTS.md                              # §19.2
├── .claude/settings.json                  # §19.3
├── .claude/skills/<nombre>/SKILL.md       # §19.4
├── .claude/rules/<nombre>.md              # §19.5
└── <configuración crítica para las verificaciones>   # §19.6
```

**El constructor copia el contenido de `workspace/` a la raíz del repositorio una sola vez, con la copia no destructiva del bloque Bootstrap de §10**: recorre los archivos de `workspace/` y copia solo los que no existen (`[ -e "$f" ] || cp …`). Una segunda corrida no pisa nada —en especial `backend/pom.xml`, los configs y los scripts que los pasos editan— y sale con 0 (no usa `cp -n`, que en BSD/macOS sale con 1 al omitir, ni `rsync`, que Git Bash no trae). Nada más del bundle se copia al proyecto. **No se emite `.claude/commands/`**: el constructor es autónomo y no escribe comandos.

**Estilo de los archivos emitidos:** este bundle no impone formateador (no hay versión verificada de Prettier/ESLint en el informe de esta sesión, §11), así que ningún gate puede rechazar estos archivos por formato; están escritos con sangría de 2 espacios (JSON/YAML/TS/SCSS), 4 en Java (estilo de Spring), salto de línea LF final (`.gitattributes`) y JSON válido.

### 19.1 `CLAUDE.md`

````markdown
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
````

### 19.2 `AGENTS.md`

````markdown
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
````

### 19.3 `.claude/settings.json`

Cada comando de los bloques `Verify` de §9, de §20.1 y del Bootstrap tiene una entrada que lo cubre (por prefijo o por subcomando: `cd`, `./mvnw`, `npx ng`, `npx ng-openapi-gen`, `node -e`, `bash scripts/*`, `docker build -f deploy/*`, `test`, `grep`, `for`, …). Se niegan la lectura de `.env`, `git push`, borrar volúmenes y `psql` directo.

```json
{
  "permissions": {
    "allow": [
      "Bash(cd:*)",
      "Bash(./mvnw:*)",
      "Bash(cd backend && ./mvnw:*)",
      "Bash((cd backend && ./mvnw:*)",
      "Bash(cd frontend && npx:*)",
      "Bash((cd frontend && npx:*)",
      "Bash(cd frontend && npm:*)",
      "Bash((cd frontend && npm:*)",
      "Bash(npx ng:*)",
      "Bash(npx ng-openapi-gen:*)",
      "Bash(npx playwright:*)",
      "Bash(npx -y @angular/cli@22.2.2 new:*)",
      "Bash(NG_CLI_ANALYTICS=false npx -y @angular/cli@22.2.2 new:*)",
      "Bash(npm ci:*)",
      "Bash(npm install:*)",
      "Bash(node scripts/init-env.mjs)",
      "Bash(node scripts/check-contrast.mjs)",
      "Bash(node -e:*)",
      "Bash(node --version)",
      "Bash(bash scripts/smoke-api.sh:*)",
      "Bash(bash scripts/check-api-drift.sh:*)",
      "Bash(bash scripts/run-e2e.sh:*)",
      "Bash(bash scripts/smoke-prod-stack.sh:*)",
      "Bash(bash scripts/smoke-web-proxy.sh:*)",
      "Bash(docker compose up:*)",
      "Bash(docker compose down)",
      "Bash(docker compose ps:*)",
      "Bash(docker compose logs:*)",
      "Bash(docker compose -f deploy/compose.prod.yml config:*)",
      "Bash(docker build -f deploy/api.Dockerfile:*)",
      "Bash(docker build -f deploy/web.Dockerfile:*)",
      "Bash(docker image inspect:*)",
      "Bash(docker run --rm --entrypoint test plataforma-citas-web:local:*)",
      "Bash(docker info:*)",
      "Bash(java -version:*)",
      "Bash(java -jar target/plataforma-citas-api.jar:*)",
      "Bash(curl:*)",
      "Bash(test:*)",
      "Bash(grep:*)",
      "Bash(for:*)",
      "Bash(mkdir:*)",
      "Bash(cp:*)",
      "Bash(echo:*)",
      "Bash(rc=0:*)",
      "Bash(ls:*)",
      "Bash(cat:*)",
      "Bash(diff:*)",
      "Bash(find:*)",
      "Bash(unzip:*)",
      "Bash(chmod +x backend/mvnw)",
      "Bash(git status:*)",
      "Bash(git diff:*)",
      "Bash(git log:*)",
      "Bash(git add:*)",
      "Bash(git commit:*)",
      "Bash(git tag:*)",
      "Bash(git ls-files:*)",
      "Bash(git check-ignore:*)",
      "Bash(git rev-parse:*)",
      "Bash(git init:*)",
      "Bash(git config core.longpaths true)",
      "Bash(git config user.email:*)",
      "Bash(git config user.name:*)",
      "Bash(git update-index --chmod=+x backend/mvnw)"
    ],
    "deny": [
      "Read(./.env)",
      "Read(./.tmp/**)",
      "Bash(git push:*)",
      "Bash(git push --force:*)",
      "Bash(docker volume rm:*)",
      "Bash(docker system prune:*)",
      "Bash(docker compose down -v)",
      "Bash(rm -rf:*)",
      "Bash(psql:*)"
    ]
  }
}
```

### 19.4 Project skills — `.claude/skills/<name>/SKILL.md`

| Skill | Se activa con | Qué automatiza |
|---|---|---|
| `agregar-migracion` | «nueva tabla», «agregar columna», «migración», «tabla con business_id» | migración Flyway con RLS y su cobertura en la suite de aislamiento |
| `agregar-endpoint` | «nuevo endpoint», «cambiar DTO», «regenerar cliente» | controlador, seguridad, errores, prueba y regeneración del cliente |
| `verificar-todo` | «verifica todo», «corre el gate», «antes del PR» | el gate completo en orden |

````markdown
---
name: agregar-migracion
description: Crear una tabla o cambiar el esquema de PostgreSQL con Flyway sin romper el aislamiento entre negocios. Úsala cuando digas "nueva tabla", "agregar columna", "migración", "Flyway" o "tabla con business_id".
---

# Agregar una migración

## Cuándo usarla
- Una tabla nueva, una columna, un índice, una restricción o una política RLS.
- Cualquier tabla que vaya a llevar `business_id`.

## Pasos
1. Mira el número más alto en `backend/src/main/resources/db/migration/` y crea `V{n+1}__{modulo}_{descripcion}.sql` (módulo: `platform`, `identity`, `catalog`…; descripción en snake_case).
2. Ids `uuid` asignados por la aplicación, fechas `timestamptz`, dinero `bigint` con sufijo `_minor`, estados `text` con `CHECK`, `created_at`/`updated_at` con `DEFAULT now()`.
3. Si la tabla tiene `business_id`: índice por `business_id`, y en la MISMA migración
   ```sql
   ALTER TABLE t ENABLE ROW LEVEL SECURITY;
   ALTER TABLE t FORCE ROW LEVEL SECURITY;
   CREATE POLICY tenant_isolation ON t
       USING (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid)
       WITH CHECK (business_id = NULLIF(current_setting('app.business_id', true), '')::uuid);
   ```
4. Si la tabla es global a propósito, agrégala a la lista permitida de `RowLevelSecurityIT` con el motivo en un comentario, y solo `identity` o `platform` la tocan.
5. La entidad JPA de una tabla con `business_id` extiende `BusinessScopedEntity`.
6. Agrega el caso a la suite: `TenantIsolationIT` recorre sola las tablas con política; si la tabla admite inserción directa en la prueba, agrega su inserción cruzada que debe fallar con `42501`.

## Verify
```bash
(cd backend && ./mvnw -B -ntp test -Dtest='RowLevelSecurityIT,TenantIsolationIT,ArchitectureRulesTest')   # expect: exit 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0
```

## No hagas
- Editar una migración ya confirmada: crea otra.
- Usar `current_setting('app.business_id')::uuid` sin `NULLIF`: tras una transacción vale `''` y la consulta revienta en vez de devolver cero filas.
- Hacer migraciones destructivas en el mismo despliegue que el código que deja de usar la columna (expandir, desplegar, luego contraer).
````

````markdown
---
name: agregar-endpoint
description: Agregar o cambiar un endpoint REST /api/v1 y mantener sincronizado el cliente TypeScript generado. Úsala cuando digas "nuevo endpoint", "cambiar DTO", "nuevo campo en la respuesta", "regenerar cliente" u "OpenAPI".
---

# Agregar o cambiar un endpoint

## Cuándo usarla
- Un controlador nuevo, un método nuevo o un cambio en un DTO de entrada o salida.

## Pasos
1. Controlador en el paquete `internal` del módulo dueño, `@Tag(name = "<módulo>")`; el nombre del método es el `operationId`: en inglés y único en toda la API.
2. DTO como `record` con Bean Validation y mensajes en español; nunca devuelvas entidades JPA.
3. Autorización: si es público, agrégalo a propósito a la lista de `SecurityConfig`; si es por rol, `@PreAuthorize("hasRole('OWNER')")`; el negocio sale de `TenantContext`, nunca del cuerpo.
4. Errores con `DomainException(Code.X, …)`; un `Code` nuevo lleva su mensaje en `frontend/src/app/core/ui-text.ts`.
5. Prueba de integración que extiende `IntegrationTest` (caso feliz, validación, 401/403, y el caso cruzado en `TenantIsolationIT` si toca datos de negocio).
6. Regenera el contrato y el cliente y confírmalos en el mismo commit:
   ```bash
   (cd backend && ./mvnw -B -ntp test -Dtest=OpenApiExportIT -Dopenapi.export=true)
   (cd frontend && npx ng-openapi-gen -c ng-openapi-gen.json)
   ```

## Verify
```bash
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0
bash scripts/check-api-drift.sh   # expect: exit 0
(cd frontend && npx ng build)   # expect: exit 0
```

## No hagas
- Editar a mano `frontend/src/app/api/**` o `frontend/openapi/openapi.json`.
- Devolver 200 con un error dentro: usa el estado y el `code` correctos.
````

````markdown
---
name: verificar-todo
description: Correr el gate completo del proyecto antes de cerrar una tarea, abrir un PR o desplegar. Úsala cuando digas "verifica todo", "corre el gate", "¿está listo?", "antes del PR" o "antes de desplegar".
---

# Verificar todo

## Cuándo usarla
- Antes de marcar una tarea como `done`, antes de un PR y antes de lanzar el workflow de despliegue.

## Pasos
1. Docker Desktop en marcha y `.env` presente (`node scripts/init-env.mjs` si falta).
2. Corre los comandos de abajo en orden; detente en el primero que falle y arréglalo en el código, nunca en el comando.

## Verify
```bash
docker compose up -d --wait db mailpit   # expect: exit 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0
(cd frontend && npx ng build)   # expect: exit 0
(cd frontend && npx ng test --watch=false)   # expect: exit 0
node scripts/check-contrast.mjs   # expect: exit 0
bash scripts/check-api-drift.sh   # expect: exit 0
rc=0; grep -rn "Empleado" frontend/src/app/features frontend/src/app/layout || rc=$?; test "$rc" -eq 1   # expect: exit 0
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
bash scripts/smoke-api.sh --expect-demo --expect-mail   # expect: exit 0
bash scripts/smoke-web-proxy.sh   # expect: exit 0
bash scripts/run-e2e.sh   # expect: exit 0
```

## No hagas
- Saltarte un comando porque "no tocaste esa parte": el gate existe para lo que no tocaste.
- Editar un comando `verify` de `tasks.json` para que pase.
````

### 19.5 `.claude/rules/*.md`

| Archivo | `paths` | Cubre |
|---|---|---|
| `.claude/rules/backend-datos.md` | `backend/src/main/resources/db/migration/**`, `backend/src/main/java/**/tenancy/**`, `backend/src/main/java/**/internal/**`, `infra/db/**` | migraciones, RLS, entidades |
| `.claude/rules/backend-api.md` | `backend/src/main/java/**` | controladores, seguridad, errores, contrato |
| `.claude/rules/pruebas-backend.md` | `backend/src/test/**` | pruebas de integración y arquitectura |
| `.claude/rules/frontend.md` | `frontend/src/**`, `frontend/e2e/**` | Angular, diseño, accesibilidad, E2E |
| `.claude/rules/despliegue.md` | `deploy/**`, `.github/**`, `docker-compose.yml`, `infra/**`, `scripts/**` | Docker, Caddy, CI/CD, scripts |

````markdown
---
description: Esquema, migraciones Flyway, aislamiento por negocio (RLS + @TenantId) y entidades JPA
paths:
  - "backend/src/main/resources/db/migration/**"
  - "backend/src/main/java/**/tenancy/**"
  - "backend/src/main/java/**/internal/**"
  - "infra/db/**"
---

# Datos y aislamiento entre negocios

- Migración nueva = `V{n}__{modulo}_{descripcion}.sql`; nunca se edita una confirmada.
- Toda tabla con `business_id`: `ENABLE` + `FORCE ROW LEVEL SECURITY` y política `tenant_isolation` con `NULLIF(current_setting('app.business_id', true), '')::uuid` en `USING` y `WITH CHECK`, en la misma migración.
- Globales a propósito (sin RLS): `business`, `subscription`, `user_account`, `refresh_token`, `one_time_token`, `schema_info`. Solo `identity` y `platform` las tocan. Agregar otra exige agregarla a la lista permitida de `RowLevelSecurityIT` con el motivo.
- `membership` usa la política especial `membership_access` (negocio activo O cuenta propia); se lee con `JdbcClient`, no es entidad JPA.
- Entidad JPA con `businessId` ⇒ extiende `BusinessScopedEntity` (`@TenantId`). Lo verifica `ArchitectureRulesTest`.
- `TenantContext.run(negocio, cuenta, trabajo)` envuelve la transacción; dentro de una transacción ya abierta no cambia nada.
- Procesos sin JWT (registro, tareas programadas y webhooks de bundles futuros) fijan el negocio con `TenantContext.run` antes de abrir la transacción.
- `JdbcClient` y JPA comparten la conexión de la transacción: el SQL nativo también queda bajo RLS.
- Ids `uuid` generados en la aplicación; `timestamptz` UTC; dinero `bigint` `_minor`; nunca `double`.
- La aplicación nunca hace DDL: `app_runtime` no tiene `CREATE`; el esquema lo cambia Flyway como `app_owner`.
- Producción: migraciones solo aditivas en el mismo despliegue (expandir → desplegar → contraer después).
````

````markdown
---
description: Controladores REST, seguridad, errores ProblemDetail y contrato OpenAPI
paths:
  - "backend/src/main/java/**"
---

# API, seguridad y errores

- Rutas bajo `/api/v1`, nombres en inglés, JSON. El nombre del método del controlador es el `operationId`: único en toda la API.
- DTO como `record` anidado en el controlador, con Bean Validation y mensajes en español; nunca se serializan entidades.
- Deny-by-default: rutas públicas solo en la lista de `SecurityConfig`; el resto exige autoridad `TENANT` (token de acceso). Roles con `@PreAuthorize("hasRole('OWNER')")`.
- El negocio y la cuenta salen de `TenantContext` (llenado desde el JWT), nunca del cuerpo ni de la URL.
- Errores: `throw new DomainException(Code.X, "detalle en español")`. Todo sale como `application/problem+json` con `code`, `title`, `detail`, `requestId`. Un `Code` nuevo exige su mensaje en `frontend/src/app/core/ui-text.ts`.
- Mismo mensaje para correo inexistente y contraseña incorrecta (`INVALID_CREDENTIALS`).
- Correos: publicar `EmailSender.Message` con `ApplicationEventPublisher`; nunca `send` directo.
- Límites con `RateLimiter` (Bucket4j en memoria, un solo proceso); IP con `request.getRemoteAddr()` (`forward-headers-strategy: native` en `prod`).
- Fechas y horas con el `Clock` inyectado; nunca `Instant.now()`.
- Logs: MDC `requestId` y `businessId`; nunca contraseñas, tokens, cookies ni el cuerpo de peticiones de autenticación.
- Cambiaste un DTO o una ruta ⇒ regenera `frontend/openapi/openapi.json` y `frontend/src/app/api` en el mismo commit (`bash scripts/check-api-drift.sh` debe salir 0).
- Módulos: `identity` usa solo la API pública de `platform` (raíz del paquete); `platform` no conoce `identity`; `shared` no conoce módulos de negocio.
````

````markdown
---
description: Convenciones de pruebas del backend (Testcontainers, aislamiento, ArchUnit)
paths:
  - "backend/src/test/**"
---

# Pruebas del backend

- Pruebas de integración: nombre `*IT`, extienden `com.plataformacitas.support.IntegrationTest` (PostgreSQL 18 real, la app como `app_runtime`, Flyway como `app_owner`). Unitarias: `*Test`, sin Spring si no lo necesitan.
- Peticiones HTTP con `java.net.http.HttpClient` contra `baseUrl()`; JSON con Jackson 3 (`tools.jackson.databind.json.JsonMapper`).
- El contenedor se comparte en toda la corrida: datos con sufijo `UUID` y afirmaciones de propiedad («solo filas de A», «cero filas de B»), nunca conteos globales.
- Siembra con `support.TestData`; dentro de `TenantContext.run(...)` + `TransactionTemplate` cuando la tabla tiene RLS.
- Toda tabla nueva con `business_id` queda cubierta por `TenantIsolationIT` (la recorre sola desde el catálogo).
- Correo: perfil `test` usa `EmailConfig.RecordingEmailSender`; límites: `rateLimiter.resetAll()` en `@BeforeEach`; captcha: proveedor `fake` o `MockRestServiceServer`. Ninguna prueba sale a internet.
- Reemplazos de beans con `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`); cada uso crea otro contexto, úsalo con moderación.
- Fixtures de ArchUnit en `archfixtures.*`, fuera de `com.plataformacitas`.
- Nada de `@Disabled` ni pruebas omitidas para pasar el gate.
````

````markdown
---
description: Convenciones de Angular, diseño, accesibilidad y E2E
paths:
  - "frontend/src/**"
  - "frontend/e2e/**"
---

# Frontend

- Componentes standalone, `signal`/`computed`, `ChangeDetectionStrategy.OnPush`, plantillas en línea; sin NgRx; estado de sesión solo en `AuthStore`.
- Llamadas HTTP solo con el cliente generado: `inject(Api).invoke(fn, params)`. `src/app/api/**` nunca se edita a mano.
- Rutas perezosas (`loadComponent`) escritas como `path: '<ruta>'`; rutas protegidas con `authGuard`, por rol con `roleGuard` y `data: { roles: [...] }`.
- Textos en español es-CO; fechas y moneda con los pipes de Angular (`LOCALE_ID` `es-CO`, `COP`).
- Términos del vertical («Empleado», «Cliente», «Servicio») solo desde `LabelsService`; nunca escritos en `features/` ni `layout/`.
- Errores al usuario con `messageFor(error)`; mensajes en `ERROR_MESSAGES` de `core/ui-text.ts`.
- Colores, espacio, radio y movimiento solo con `var(--…)` de `src/styles/_tokens.scss`; un color nuevo entra también a `scripts/check-contrast.mjs`.
- Accesibilidad WCAG 2.2 AA: `<mat-label>` en cada campo, errores en `role="alert"` y en texto (no solo color), objetivos de 24×24 px o más, foco visible, enlace «Saltar al contenido», sin scroll horizontal a 375 px.
- El access token vive en memoria; nunca `localStorage`. La sesión se recupera con la cookie `rt` (`AuthStore.restore()`).
- E2E: selectores accesibles (`getByLabel`, `getByRole`) con los textos de la tabla «Contrato de pantallas» del blueprint; el correo se lee de Mailpit, nunca se simula.
````

````markdown
---
description: Docker, Caddy, compose de staging, CI/CD de GitHub Actions y servicios locales
paths:
  - "deploy/**"
  - ".github/**"
  - "docker-compose.yml"
  - "infra/**"
  - "scripts/**"
---

# Despliegue e infraestructura

- Imágenes con etiqueta exacta (`postgres:18-alpine`, `axllent/mailpit:v1.31.5`, `caddy:2.11.7-alpine`, `eclipse-temurin:25-jre-alpine`); cambiar una exige actualizar §11 del blueprint.
- La imagen de la API se construye desde `backend/target` (jar `plataforma-citas-api.jar`) y la web desde `frontend/dist/frontend/browser`. Solo la imagen de la API corre sin root (usuario `app`); la imagen web conserva el usuario por defecto de `caddy:2.11.7-alpine` para escuchar en 80/443.
- Staging y producción usan el mismo `deploy/compose.prod.yml`; solo cambia el `.env` del servidor, que nunca se versiona.
- Caddy publica solo `/api/*` hacia la API; `/actuator/*` nunca sale a internet. La SPA va detrás de autenticación básica mientras staging no sea público.
- Límites de memoria por contenedor y `-XX:MaxRAMPercentage=60` en la API (VPS de 4 GB).
- Los scripts de `scripts/` son bash portable (Git Bash en Windows, Linux en CI): sin `cp -n`, sin python, rutas entre comillas, `set -euo pipefail`, códigos de salida documentados en la cabecera.
- Todo comando de un guard sale con 0 en la segunda ejecución (bootstrap re-ejecutable).
- Acciones de GitHub fijadas por versión mayor (`actions/checkout@v7`, `actions/setup-java@v6`, `actions/setup-node@v7`, `actions/upload-artifact@v7`); secretos solo en el ambiente `staging` de GitHub.
- El despliegue es manual (`workflow_dispatch`); el VPS, el DNS y los secretos son puertas humanas.
````

### 19.6 Verify-critical config and local infrastructure

Cada archivo de configuración que necesita un `Verify` de §9 se emite completo bajo `workspace/`, en la ruta que ocupa en el proyecto:

| Archivo | Ruta en el proyecto | Lo necesitan los `Verify` de | Resolución / carga de variables que lleva | Exclusión del bundle |
|---|---|---|---|---|
| `pom.xml` | `backend/pom.xml` | todos los pasos de backend (1–13, 17, 18) | Surefire incluye `*Test` y `*IT` y pasa `openapi.export`/`openapi.output` como propiedades de sistema; `finalName` = `plataforma-citas-api` | n/a — Maven solo recorre `backend/` |
| `maven-wrapper.properties` | `backend/.mvn/wrapper/maven-wrapper.properties` | todos los que usan `./mvnw` | `distributionType=only-script`, Maven 3.9.16 | n/a — no recorre el árbol |
| `application.yml` | `backend/src/main/resources/application.yml` | 1, 7, 9, 11 (humo), todos los IT | cargador de variables: `spring.config.import: optional:file:../.env[.properties]` | n/a — no recorre el árbol |
| `application-test.yml` | `backend/src/test/resources/application-test.yml` | todos los IT | valores literales de prueba (JWT, correo y captcha falsos, límites) | n/a |
| `test-roles.sql` | `backend/src/test/resources/db/test-roles.sql` | todos los IT | crea `app_owner`/`app_runtime` en el contenedor | n/a |
| `IntegrationTest.java` | `backend/src/test/java/com/plataformacitas/support/IntegrationTest.java` | todos los IT | Testcontainers `postgres:18-alpine`, DataSource `app_runtime`, Flyway `app_owner` | n/a — está en `backend/src/test` |
| `docker-compose.yml` | `docker-compose.yml` | 1, 7, 9, 11, 17 (servicios `db`, `mailpit`) | lee `.env` de la raíz (Compose lo hace solo); healthchecks para `--wait` | n/a — Compose no recorre el árbol |
| `01-roles.sh` | `infra/db/init/01-roles.sh` | 1 (primer arranque del volumen), 18 | contraseñas por variables del contenedor | n/a |
| `.env.example` | `.env.example` | Bootstrap (`init-env.mjs`) | plantilla de todas las variables de §10 | n/a |
| `.gitignore` | `.gitignore` | Bootstrap (antes del primer commit) | ignora `.env`, `.tmp/`, artefactos | **no** ignora `blueprints/` a propósito: el bundle se versiona |
| `.gitattributes` | `.gitattributes` | Bootstrap; 1 (`01-roles.sh` con LF dentro del contenedor) | `eol=lf` | n/a |
| `init-env.mjs` | `scripts/init-env.mjs` | Bootstrap, 17 (CI) | genera `.env` desde `.env.example` | n/a — lee dos archivos explícitos |
| `smoke-api.sh` | `scripts/smoke-api.sh` | 1, 7, 9, 11 | `set -a; . ./.env` antes de arrancar el jar | n/a |
| `run-e2e.sh` | `scripts/run-e2e.sh` | 17 | crea `.env` si falta; levanta servicios | n/a |
| `smoke-web-proxy.sh` | `scripts/smoke-web-proxy.sh` | 16 | `set -euo pipefail`; usa el `.env` a través de la API (que lo importa); límites de espera en cada bucle y parada forzada de ambos procesos | n/a |
| `smoke-prod-stack.sh` | `scripts/smoke-prod-stack.sh` | 18 | escribe `.tmp/smoke-prod.env` y lo pasa con `--env-file` | n/a |
| `compose.prod.yml` | `deploy/compose.prod.yml` | 18 | todas las variables por `--env-file`; `:?` falla si falta una obligatoria | n/a |
| `Caddyfile` | `deploy/Caddyfile` | 18 | `{$SITE_ADDRESS}`, `{$BASIC_AUTH_USER}`, `{$BASIC_AUTH_HASH}` del entorno del contenedor | n/a — Caddy sirve `/srv`, que solo contiene el build |
| `proxy.conf.json` | `frontend/proxy.conf.json` | 17 (`ng serve` de Playwright) | `/api` → `http://localhost:8080` | n/a |
| `playwright.config.ts` | `frontend/playwright.config.ts` | 17 | arranca el jar con `cwd ../backend` (lee `../.env` por `application.yml`) y `ng serve` | `testIgnore: ['**/blueprints/**', '**/node_modules/**']`; `testDir: './e2e'` |

Configuraciones que genera un scaffold y de las que dependen gates: `frontend/angular.json` y `frontend/tsconfig*.json` (de `ng new`); el compilador y Vitest solo incluyen `frontend/src/**`, así que nunca ven `blueprints/`. Los contextos de `docker build` son `backend/target` y `frontend/dist/frontend/browser`, que no contienen el bundle.

Servicios de los que dependen gates (las cuatro piezas): **(1)** `docker-compose.yml` con `postgres:18-alpine` y `axllent/mailpit:v1.31.5` fijados, volumen con nombre y healthchecks; **(2)** variables `DB_URL` (`jdbc:postgresql://localhost:5432/citas`), `DB_*_PASSWORD`, `SMTP_HOST=localhost`, `SMTP_PORT=1025` en §10 y `.env.example` — las pruebas de integración no usan esta base (cada corrida crea la suya con Testcontainers); **(3)** comandos `docker compose up -d --wait db mailpit` / `docker compose down` / `docker compose down -v` en §10 y §19.1; **(4)** esos comandos en `permissions.allow` (salvo `down -v`, negado a propósito: borra datos).

#### Resolution convention matrix

**La convención, una sola vez:** backend — paquetes Java bajo `com.plataformacitas`, un módulo por paquete directo, API pública en la raíz del paquete del módulo, internos en subpaquetes; frontend — importaciones **relativas** sin alias de ruta (`import { Api } from '../api/api'`), y el cliente generado se importa desde `src/app/api` por ruta relativa.

| Contexto | Comando que lo ejercita | Forma de la convención ahí | Config + ajuste que la hace funcionar |
|---|---|---|---|
| Código de la aplicación (backend) | `(cd backend && ./mvnw -B -ntp -DskipTests package)` | paquetes Java | `backend/pom.xml` — fuentes estándar de Maven; `ModularityTest` impone los límites |
| Pruebas backend | `(cd backend && ./mvnw -B -ntp verify)` | mismos paquetes + `archfixtures.*` fuera del raíz | `pom.xml` — Surefire `includes` `**/*Test.java`, `**/*IT.java`; el escaneo de Spring/Hibernate parte de `com.plataformacitas`, por eso los fixtures viven fuera |
| Scripts | `bash scripts/*.sh`, `node scripts/*.mjs` | Node ESM con `node:` y rutas desde `import.meta.url`; bash con `ROOT` calculado | ningún import de código del proyecto; `.mjs` fuerza ESM sin `package.json` en la raíz |
| Código de la aplicación (frontend) | `(cd frontend && npx ng build)` | rutas relativas | `frontend/tsconfig.json` del scaffold (resolución `bundler`), sin `paths` |
| Pruebas frontend | `(cd frontend && npx ng test --watch=false)` | rutas relativas | el builder `@angular/build:unit-test` usa la misma `tsconfig.spec.json` del scaffold |
| E2E | `bash scripts/run-e2e.sh` | `@playwright/test` desde `frontend/node_modules` | `frontend/playwright.config.ts` — transpila Playwright; `testDir: './e2e'` |
| Generación del cliente | `(cd frontend && npx ng-openapi-gen -c ng-openapi-gen.json)` | escribe importaciones relativas dentro de `src/app/api` | `frontend/ng-openapi-gen.json` — `output: src/app/api` |
| Build de imágenes | `docker build -f deploy/api.Dockerfile … backend/target` | `COPY plataforma-citas-api.jar` | contexto `backend/target`; jar ejecutable de Spring Boot |

#### Cross-artifact value reconciliation

| Valor compartido | Fuente única | Valor literal | Otros lugares donde aparece | Comparado |
|---|---|---|---|---|
| Jar de la API | `backend/pom.xml` — `<finalName>` | `plataforma-citas-api` → `backend/target/plataforma-citas-api.jar` | `scripts/smoke-api.sh` (`JAR=`, `java -jar target/…`) · `frontend/playwright.config.ts` (`java -jar target/plataforma-citas-api.jar`, `cwd: '../backend'`) · `deploy/api.Dockerfile` (`COPY … plataforma-citas-api.jar`, contexto `backend/target`) · `deploy.yml` (contexto `backend/target`) · pasos 1, 18 · CLAUDE.md | sí |
| Puerto de la API | `application.yml` — `server.port: ${PORT:8080}` | `8080` | `proxy.conf.json` · `playwright.config.ts` (`http://localhost:8080/actuator/health`) · `smoke-api.sh` (`API=`) · `smoke-web-proxy.sh` (comprobación de puerto libre) · `Caddyfile` (`api:8080`) · `compose.prod.yml` (healthcheck) | sí |
| Base de datos | `docker-compose.yml` — `POSTGRES_DB` | `citas` | `application.yml` (`DB_URL` por defecto) · `.env.example` · `compose.prod.yml` (`POSTGRES_DB`, `DB_URL`, `pg_isready -d citas`) · `IntegrationTest` (`withDatabaseName("citas")`) · `test-roles.sql` (`ALTER DATABASE citas`) | sí |
| Roles | `infra/db/init/01-roles.sh` | `app_owner`, `app_runtime` | `application.yml` (`username`, `flyway.user`) · `test-roles.sql` · `IntegrationTest` (constantes) | sí |
| Contraseñas de prueba | `test-roles.sql` | `owner_test`, `runtime_test` | `IntegrationTest` (`OWNER_PASSWORD`, `RUNTIME_PASSWORD`) | sí |
| Puertos de Mailpit | `docker-compose.yml` | `1025` (SMTP), `8025` (UI/API) | `application.yml` (`SMTP_PORT` por defecto) · `.env.example` · `smoke-api.sh` (`MAILPIT=`) · `e2e/registro.spec.ts` (paso 17) | sí |
| Origen del frontend local | `frontend/playwright.config.ts` — `baseURL` | `http://localhost:4200` | `smoke-web-proxy.sh` (`ng serve --port 4200`, `curl http://localhost:4200/api/v1/public/config`) · `application.yml` (`app.base-url`, `cors.allowed-origins`) · `application-test.yml` · `.env.example` | sí |
| Salida del build web | Angular (`ng new frontend` ⇒ proyecto `frontend`) | `frontend/dist/frontend/browser` | paso 18 · `deploy.yml` · comentario de `smoke-prod-stack.sh` | sí |
| Nombres de imagen | `deploy.yml` | `plataforma-citas-api`, `plataforma-citas-web` (`ghcr.io/<owner>/…`; local `:local`) | `smoke-prod-stack.sh` · paso 18 · `.env` del servidor (`API_IMAGE`, `WEB_IMAGE`) | sí |
| Contrato OpenAPI | `backend/pom.xml` — `openapi.output` | `../frontend/openapi/openapi.json` (relativo a `backend/`) | `ng-openapi-gen.json` (`input: openapi/openapi.json`, relativo a `frontend/`) · `check-api-drift.sh` | sí |
| Cliente generado | `ng-openapi-gen.json` — `output` | `src/app/api` | `check-api-drift.sh` (`frontend/src/app/api`) · CLAUDE.md · reglas | sí |
| Directorio de init de la base | `docker-compose.yml` | `./infra/db/init` → `/docker-entrypoint-initdb.d` | `compose.prod.yml` (`../infra/db/init`, relativo a `deploy/`) · `deploy.yml` (`scp infra/db/init/01-roles.sh`) | sí |
| Volumen de datos | `docker-compose.yml` | `/var/lib/postgresql` (convención de las imágenes 18+) | `compose.prod.yml` | sí |
| Cookie de refresh | `AuthController` (paso 8) | `rt`, `Path=/api/v1/auth` | contrato de §5 · interceptor del frontend (paso 15) | sí |
| Claves de prueba de Turnstile | documentación de Cloudflare | sitio `1x00000000000000000000AA`, secreto `1x0000000000000000000000000000000AA`, token `XXXX.DUMMY.TOKEN.XXXX` | `smoke-prod-stack.sh` · criterios de los pasos 10, 16, 18 · `CaptchaVerifier.DUMMY_TOKEN` | sí |
| Imagen de PostgreSQL | `docker-compose.yml` — `services.db.image` | `postgres:18-alpine` | `deploy/compose.prod.yml` (`services.db.image`) · `IntegrationTest` (`DockerImageName.parse("postgres:18-alpine")`) · §11 · `.claude/rules/despliegue.md` | sí |
| Imagen de Caddy | `deploy/web.Dockerfile` — `FROM` (paso 18) | `caddy:2.11.7-alpine` | `scripts/smoke-prod-stack.sh` (`docker run --rm caddy:2.11.7-alpine caddy hash-password`) · §10 (`BASIC_AUTH_HASH`) · §11 · `.claude/rules/despliegue.md` | sí |
| Imagen de Mailpit | `docker-compose.yml` — `services.mailpit.image` | `axllent/mailpit:v1.31.5` | §11 · `.claude/rules/despliegue.md` | sí |
| Imagen de ejecución de la API | `deploy/api.Dockerfile` — `FROM` (paso 18) | `eclipse-temurin:25-jre-alpine` | criterio del paso 18 · §11 · `.claude/rules/despliegue.md` | sí |
| Puerto de PostgreSQL | `docker-compose.yml` — `ports: "5432:5432"` | `5432` | `application.yml` (`DB_URL` por defecto `jdbc:postgresql://localhost:5432/citas`) · `.env.example` (`DB_URL`) · `compose.prod.yml` (`DB_URL: jdbc:postgresql://db:5432/citas`, sin publicar el puerto) | sí |
| Ruta del bundle | estructura del bundle | `blueprints/plataforma-citas` | Bootstrap (`B=`) · `playwright.config.ts` (`testIgnore`) · CLAUDE.md · AGENTS.md | sí |

Contratos ejercitados en el primer paso donde existen ambos lados: jar ↔ `smoke-api.sh` (paso 1); `application.yml` ↔ `docker-compose.yml` ↔ `01-roles.sh` (paso 1); `pom.xml` `openapi.output` ↔ `ng-openapi-gen.json` ↔ `check-api-drift.sh` (paso 13); `proxy.conf.json` ↔ API (paso 16, `smoke-web-proxy.sh`); `playwright.config.ts` ↔ API (paso 17); Dockerfiles ↔ `compose.prod.yml` ↔ `Caddyfile` ↔ `smoke-prod-stack.sh` (paso 18).

#### Byte-exact artifact reconciliation

| Artefacto byte a byte | Escrito por | Primera comparación | Reglas del blueprint que lo restringen | Llamada del runtime que lo produce, sobre el pin de §11 | Ambas confirmadas |
|---|---|---|---|---|---|
| `frontend/openapi/openapi.json` frente al exportado | herramienta (`OpenApiExportIT`), no a mano | paso 13 (`check-api-drift.sh`) | servidor único `/` (`OpenApiConfig`), claves ordenadas (`springdoc.writer-with-order-by-keys`), LF y salto final (`OpenApiExportIT`) | los dos lados los produce el mismo springdoc 3.1.1 sobre el mismo código; no hay literal escrito por el blueprint | sí |
| `frontend/src/app/api/**` frente al regenerado | herramienta (`ng-openapi-gen` 1.1.0) | paso 13 | `output: src/app/api`, `removeStaleFiles: true` | ambos lados de la misma versión instalada en `node_modules`; sin literal escrito | sí |
| Slug `peluqueria-dona-ana` (y `-2`) | criterio del paso 5 | paso 5 (`ProvisioningIT`) | regla de slug de `Businesses` (NFD, sin marcas, minúsculas, `[^a-z0-9]+` → `-`) y `CHECK` de §4 | ejecutado en JDK 25.0.4: `Normalizer.normalize("Peluquería Doña Ana", NFD)` + la regla ⇒ `peluqueria-dona-ana` | sí |
| `trial_ends_at` = `2026-11-11T15:00:00Z` | criterio del paso 5 | paso 5 | `app.trial-days: 30`, reloj fijo `2026-10-12T15:00:00Z` | ejecutado en JDK 25.0.4: `Instant.parse("2026-10-12T15:00:00Z").plus(Duration.ofDays(30))` ⇒ `2026-11-11T15:00:00Z` | sí |
| Unidades menores 1000 / 1002 | criterio del paso 3 | paso 3 (`SharedKernelTest`) | `Money.ofMajor` con `HALF_EVEN` a los decimales de la moneda | ejecutado en JDK 25.0.4: `Currency.getInstance("USD").getDefaultFractionDigits()` = 2; `10.005` ⇒ 1000, `10.015` ⇒ 1002 | sí |
| Asuntos `Restablece tu contraseña`, `Confirma tu correo` y textos de pantalla | §6 «Contrato de pantallas», pasos 11, 12, 16, 17 | pasos 11 (`smoke-api.sh --expect-mail`), 12, 16, 17 | definidos solo por este blueprint; el código los copia literalmente | ningún runtime los altera (Mailpit devuelve `Subject` decodificado en UTF-8) | sí |
| Campo `@timestamp` del log ECS | Spring Boot 4.1.1 (`logging.structured.format.console=ecs`) | paso 18 (`smoke-prod-stack.sh`) | §16: logs ECS en `prod` | formato ECS de Spring Boot 4.1.1 (documentado en https://docs.spring.io/spring-boot/reference/features/logging.html); la comprobación busca solo la presencia del campo, no bytes | sí — propiedad, no literal (la ejecución real ocurre en el paso 18) |

---

## 20. Acceptance Gate, Risks & Decision Log

### 20.1 Global acceptance gate

El bundle está **hecho** cuando cada comando de abajo sale con 0 en una copia limpia (después del Bootstrap de §10), y no antes. Es el mismo conjunto que corre CI.

```bash
docker compose up -d --wait db mailpit   # expect: exit 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS, Failures: 0, Errors: 0 (incluye Modulith, ArchUnit, RLS y aislamiento)
(cd frontend && npm ci)   # expect: exit 0, sin cambios en package-lock.json
(cd frontend && npx ng build)   # expect: exit 0
(cd frontend && npx ng test --watch=false)   # expect: exit 0, 0 failed
node scripts/check-contrast.mjs   # expect: exit 0, "Contraste OK"
rc=0; grep -rn "Empleado" frontend/src/app/features frontend/src/app/layout || rc=$?; test "$rc" -eq 1   # expect: exit 0 (1 = sin coincidencias; 2 = error de uso y falla)
bash scripts/check-api-drift.sh   # expect: exit 0 (1 = deriva, 2 = error)
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
bash scripts/smoke-api.sh --expect-demo --expect-mail   # expect: exit 0 — ejecuta el jar que declara el pom
bash scripts/smoke-web-proxy.sh   # expect: exit 0, "SMOKE WEB PROXY OK"
bash scripts/run-e2e.sh   # expect: exit 0, 0 failed
docker build -f deploy/api.Dockerfile -t plataforma-citas-api:local backend/target   # expect: exit 0
docker build -f deploy/web.Dockerfile -t plataforma-citas-web:local frontend/dist/frontend/browser   # expect: exit 0
bash scripts/smoke-prod-stack.sh   # expect: exit 0 — ejecuta las imágenes que construyó el paso 18
```

Accesibilidad: no hay comando axe en este bundle (§15); el contraste y los nombres accesibles los cubren `check-contrast.mjs` y la E2E.

Puertas manuales, cada una revisada una vez antes de dar el bundle por cerrado:

- [ ] `git tag -l 'step-*'` lista las 18 etiquetas de §9 (`step-01-esqueleto` … `step-18-despliegue`); el repositorio lo crea el Bootstrap de §10.
- [ ] Cada archivo de §10 *Files that must be committed* está en una copia limpia: `git ls-files --error-unmatch <ruta>` sale con 0 **una ruta por invocación**, y `git check-ignore -q .env.example; test $? -eq 1` sale con 0 (1 = no ignorado; 128 = error de uso, que falla).
- [ ] El `.gitignore` entró en el primer commit: `git log --diff-filter=A --format=%s -- .gitignore` muestra `chore: bootstrap plataforma-citas`.
- [ ] Cada fila de *Byte-exact artifact reconciliation* dice «sí» (§19.6).
- [ ] El Bootstrap de §10 se ejecutó una segunda vez sobre el árbol ya construido, **salió con 0** y `backend/pom.xml` y `frontend/package.json` siguen intactos (el siguiente `./mvnw verify` encuentra todo).
- [ ] Cada fila de *Cross-artifact value reconciliation* dice «sí», y los gates se ejecutaron desde la raíz con el bundle presente.
- [ ] Ningún non-goal de §1 fue construido (no existen `/api/v1/users`, `resource`, `appointment`, `sale`, canal de WhatsApp ni cobros).
- [ ] **Puertas humanas** (no son pasos de construcción):
  - [ ] Verificación de la empresa en Meta y borradores de plantillas (confirmación, recordatorio) iniciados — antes del bundle 4.
  - [ ] Contrato partner-paid con 360dialog con la tarifa por número negociada — antes del bundle 4 (riesgo principal).
  - [ ] Cuenta de comercio en Wompi — antes del bundle 6.
  - [ ] Revisión por abogado de los textos legales (términos, política de datos, acuerdo de encargo, aviso de privacidad), que este bundle no incluye; entrega de los textos revisados al repositorio (bundle 2) y nueva versión en `LEGAL_TERMS_VERSION`/`LEGAL_DPA_VERSION`.
  - [ ] VPS de 4 GB en Hetzner con Docker, firewall 80/443/22, `docker login ghcr.io` y `/opt/plataforma-citas/.env` completo; registro DNS de staging.
  - [ ] Cuentas y claves de Cloudflare Turnstile, Brevo (remitente verificado) y Sentry; secretos del ambiente `staging` en GitHub; regla de protección de `main` que exige los cuatro jobs de CI.
  - [ ] Primer `Deploy staging` ejecutado; un registro real completado en staging con el correo de Brevo y el widget de Turnstile.
  - [ ] Error de prueba provocado y recibido en Sentry.
  - [ ] Reversión ejecutada una vez a propósito en staging (cambiar `API_IMAGE` a la etiqueta anterior).
  - [ ] Recorrido solo con teclado y una pasada con lector de pantalla del registro y el ingreso (§15).
  - [ ] Piloto de 2 semanas con la peluquería — al cierre del bundle 3 (agenda y caja).

**Ninguna advertencia se ignora.**

### 20.2 Risk register

| Riesgo | Probabilidad | Impacto | Primera señal | Mitigación |
|---|---|---|---|---|
| Costo por número de 360dialog (tarifas directas 49/99 EUR/mes) supera el precio del plan Agenda + WhatsApp | A | A | cotización de 360dialog | aceptado hasta tener contrato; negociar partner-paid (USD 15–49) antes del bundle 4; precios viven en la tabla `plan` (bundle 6) — dueño: producto |
| Cambio de tarifas de Meta del 2026-10-01 (servicio y utilidad dentro de la ventana ahora se cobran) | A | M | factura de Meta | medir el consumo real con `message_usage` (bundle 5) y revisar precios trimestralmente — dueño: producto |
| Fuga entre negocios | B | A | una fila de otro negocio en `TenantIsolationIT` o en soporte | `@TenantId` + RLS forzada + `app_runtime` + suite por tabla desde el paso 9; en bundles futuros cubrir SSE, tareas y webhooks — dueño: backend |
| RLS en falla cerrada bloquea funciones (consultas vacías o `42501` inesperados) | M | M | prueba de integración vacía tras agregar un módulo | pruebas siempre como `app_runtime`; verificación temprana en el paso 4; `TenantContext.run` envuelve la transacción (skill `agregar-migracion`) — dueño: backend |
| Interacción `@TenantId` + `set_config` con el pool | B | A | datos de un negocio en la petición de otro | `set_config(…, true)` local a la transacción; el paso 4 prueba que la transacción siguiente no ve filas — dueño: backend |
| Abuso del registro público (spam, cuentas falsas) | M | M | picos de 429 y registros sin verificar | Turnstile, verificación de correo, 5/h/IP, suspensión manual (`business.status`) — dueño: operación |
| Memoria del VPS de 4 GB (JVM + PostgreSQL + Caddy) | M | M | `docker stats` > 85 % | límites por contenedor y `MaxRAMPercentage=60`; medición con carga en el bundle 3 — dueño: operación |
| ShedLock 7.10.1 y Web Push con Spring 7 / Jackson 3 sin verificar | M | M | fallo de arranque al agregarlos | prueba de humo temprana en el bundle 4 (ShedLock) y 7 (push) — dueño: backend |
| Cuenta registrada que nunca verificó su correo queda bloqueada (el registro con ese correo responde `ACCOUNT_EXISTS`) | M | B | soporte | reenvío de verificación en el bundle 2 (junto con invitaciones); mientras tanto, procedimiento manual — dueño: producto |
| Alcance del bundle 1 crece hacia catálogo o agenda | M | M | archivos fuera de los subárboles de las épicas | tabla de Non-Goals y regla «detente y reporta» — dueño: quien construye |

### 20.3 Decision log

| # | Decisión | Alternativa real descartada | Por qué | Se revierte si |
|---|---|---|---|---|
| 1 | Java 25 + Spring Boot 4.1.1 | Spring Boot 3.5 (línea estable anterior) | soporte más largo y Spring 7/Hibernate 7 desde el inicio | una librería crítica no soporta Jackson 3 a tiempo |
| 2 | Monolito modular con Spring Modulith; hexagonal solo en agenda/caja | microservicios; hexagonal en todo | 2 desarrolladores, un despliegue, límites verificados en CI | un módulo necesita escalar aparte |
| 3 | Aislamiento `@TenantId` + RLS forzada + `app_runtime` | solo filtro en la aplicación | dos barreras; RLS cubre SQL nativo y errores de código | un cliente exige base dedicada |
| 4 | `set_config` en `doBegin` de un `JpaTransactionManager` propio | `SET` de sesión en un `ConnectionCustomizer` de Hikari | local a la transacción: imposible que se filtre por el pool | Hibernate ofrezca un gancho oficial de inicio de transacción |
| 5 | Autenticación propia (JWT HS256 + refresh rotativo en cookie) | Clerk / Supabase Auth | membresías y rol por negocio en el token sin doble fuente de verdad | aparece SSO empresarial (SAML/SCIM) |
| 6 | HS256 con secreto compartido | RS256 con par de llaves | un solo emisor y verificador | otro servicio debe validar tokens |
| 7 | Bucket4j en memoria | Bucket4j sobre Redis/PostgreSQL | una sola instancia de API | se despliega más de una instancia |
| 8 | Cloudflare Turnstile | hCaptcha | gratuito, sin acertijos, claves de prueba oficiales | Turnstile cambia su modelo gratuito |
| 9 | Brevo por SMTP detrás de `EmailSender` | API HTTP de un proveedor | cambiar de proveedor es cambiar variables | se necesitan webhooks de rebote |
| 10 | `ng-openapi-gen` | `openapi-generator typescript-angular` (de los documentos) | nativo de Angular, funciones por operación | el generador deja de mantenerse |
| 11 | Angular Material + tokens propios | PrimeNG | accesibilidad por defecto, menos dependencia de terceros | se necesita un componente complejo que Material no tiene |
| 12 | SPA sin SSR | Angular SSR | todo está detrás de inicio de sesión; nada que indexar | se agrega la página pública de reservas |
| 13 | Paquete raíz `com.plataformacitas` | `com.tuorg.citas` (marcador de los documentos) | nombre real y neutral | — |
| 14 | `business_settings.labels` como diccionario del vertical servido por la API | diccionario estático en el frontend | un vertical nuevo no exige desplegar el frontend | las etiquetas se vuelven traducibles por idioma |
| 15 | VPS Hetzner + Compose + Caddy | PaaS de contenedores | costo fijo bajo y TLS automático | el equipo no puede operar servidores |
| 16 | Staging con autenticación básica solo en la SPA | lista de IP permitidas | funciona desde cualquier red para la dueña piloto | se necesita cerrar también la API |
| 17 | Sin formateador ni linter en este bundle | Prettier/ESLint con versiones de memoria | no hay versiones verificadas; una versión adivinada rompe el primer gate | se verifica un pin en el bundle 2 |

#### Entrega pendiente: textos legales

Este bundle no crea ni publica textos legales; solo registra en `legal_acceptance` la versión aceptada (`borrador-2026-10`). Los textos revisados por el abogado los incorpora el bundle 2 (páginas o enlaces de términos y acuerdo de encargo en el registro) o la puerta humana de revisión legal de §20.1, junto con la nueva cadena de versión.

### 20.4 What to build next

1. **Bundle 2 — catálogo, empleados, motor de disponibilidad, clientes y agenda** (incluye invitaciones HU-97 y reenvío de verificación): en cuanto §20.1 esté en verde.
2. **Bundle 3 — caja, reportes mínimos, respaldos y producción**: al terminar el bundle 2; cierra la fase 1 y habilita el piloto de 2 semanas.
3. **Bundle 4 — outbox, canal 360dialog, recordatorios y bot**: cuando el contrato partner-paid con 360dialog y la verificación de Meta estén aprobados.
4. **Bundle 5 — bandeja SSE, enlaces de calendario y medición de consumo**: después del bundle 4.
5. **Bundles 6 y 7 — planes y Wompi; reportes avanzados, verticales y PWA**: con la cuenta de comercio de Wompi y el piloto validado.

---

*Fin del blueprint. El orden de construcción es §9. Se detiene cuando §20.1 está en verde.*
