# Épica 01: Fundación del backend y datos multi-negocio

> Al terminar existe una API Spring Boot que arranca contra PostgreSQL 18 con los roles `app_owner`/`app_runtime`, límites de módulos verificados, el kernel compartido, aislamiento por negocio en dos capas (`@TenantId` + RLS) probado, el alta de un negocio con su sede principal y las cuentas globales con membresías.

| | |
|---|---|
| **Epic id** | `01-fundacion-datos` |
| **Tasks** | `E1-T1` … `E1-T6` (pasos 1–6 del blueprint) |
| **Depends on** | nada — se empieza aquí, después del bootstrap de §10 del blueprint |
| **Unlocks** | `02-identidad-registro` |
| **Parallel with** | `03-frontend-entrega` solo su primera tarea (`E3-T1`, sistema de diseño), que no toca el backend |

No necesitas ningún otro archivo para completar esta épica. Todo lo de abajo se repite aquí a propósito.

---

## Stack

Java 25 · Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3 en el paquete `tools.jackson`) · Spring Modulith 2.1.1 · Spring Data JPA + `JdbcClient` · Flyway 12 · PostgreSQL 18 con Row Level Security · Testcontainers 2 · ArchUnit · Maven 3.9.16 vía `./mvnw` (en `backend/`).
Paquete raíz `com.plataformacitas`; clase principal `com.plataformacitas.CitasApplication`. Las versiones están en `backend/pom.xml` (copiado desde `workspace/`): léelas allí, nunca las adivines. Con Jackson 3, toda librería nueva debe ser compatible con `tools.jackson`.

| Tarea | Comando (desde la raíz del repo) |
|---|---|
| Servicios locales (arriba / abajo / reinicio con datos borrados) | `docker compose up -d --wait db mailpit` · `docker compose down` · `docker compose down -v` |
| Compilar el jar | `(cd backend && ./mvnw -B -ntp -DskipTests package)` → `backend/target/plataforma-citas-api.jar` |
| Todas las pruebas + Modulith + ArchUnit | `(cd backend && ./mvnw -B -ntp verify)` |
| Una clase de prueba | `(cd backend && ./mvnw -B -ntp test -Dtest=NombreIT)` |
| API en local (perfil `dev`, puerto 8080) | `(cd backend && ./mvnw spring-boot:run)` |
| Prueba de humo del jar | `bash scripts/smoke-api.sh` |

**Gate:** `(cd backend && ./mvnw -B -ntp verify)` pasa antes de marcar cualquier tarea como hecha. Las pruebas de integración necesitan Docker en marcha (Testcontainers levanta su propio PostgreSQL; no usa el de `docker compose`).

Los servicios que usan las verificaciones están definidos en `docker-compose.yml` (raíz), que llegó con `workspace/`: no lo escribes tú y nunca sustituyes un servicio real por uno falso.

## Directory subtree

Solo lo que toca esta épica:

```
backend/
  pom.xml                                   # existe (workspace/) — solo lectura
  src/main/resources/
    application.yml                         # existe (workspace/) — solo lectura
    db/migration/
      V1__shared_baseline.sql               # NUEVO (E1-T1)
      V2__platform_business.sql             # NUEVO (E1-T4)
      V3__identity_accounts.sql             # NUEVO (E1-T6)
  src/main/java/com/plataformacitas/
    CitasApplication.java                   # NUEVO (E1-T1)
    shared/
      package-info.java                     # NUEVO (E1-T2) — módulo abierto
      security/SecurityConfig.java          # NUEVO (E1-T1), se edita en E1-T6
      money/Money.java                      # NUEVO (E1-T3)
      error/DomainException.java            # NUEVO (E1-T3)
      error/ApiExceptionHandler.java        # NUEVO (E1-T3)
      web/RequestIdFilter.java              # NUEVO (E1-T3)
      tenancy/TenantContext.java            # NUEVO (E1-T4)
      tenancy/BusinessScopedEntity.java     # NUEVO (E1-T4)
      tenancy/TenancyJpaConfig.java         # NUEVO (E1-T4)
    platform/
      Businesses.java                       # NUEVO (E1-T5) — API pública del módulo
      internal/Business.java                # NUEVO (E1-T5)
      internal/Location.java                # NUEVO (E1-T5)
    identity/internal/
      UserAccount.java                      # NUEVO (E1-T6)
      IdentityStore.java                    # NUEVO (E1-T6)
    vertical/peluqueria/
      PeluqueriaVerticalSeed.java           # NUEVO (E1-T5)
  src/test/
    resources/application-test.yml          # existe (workspace/)
    resources/db/test-roles.sql             # existe (workspace/)
    java/com/plataformacitas/
      support/IntegrationTest.java          # existe (workspace/) — base de toda prueba de integración
      FoundationIT.java                     # NUEVO (E1-T1)
      ModularityTest.java                   # NUEVO (E1-T2)
      ArchitectureRulesTest.java            # NUEVO (E1-T2)
      shared/SharedKernelTest.java          # NUEVO (E1-T3)
      shared/RowLevelSecurityIT.java        # NUEVO (E1-T4)
      platform/ProvisioningIT.java          # NUEVO (E1-T5)
      identity/IdentityPersistenceIT.java   # NUEVO (E1-T6)
    java/archfixtures/baddomain/domain/BadDomainEntity.java   # NUEVO (E1-T2) — fixture fuera del paquete raíz
docs/modules/README.md                      # NUEVO (E1-T2)
docker-compose.yml, infra/db/init/01-roles.sh, scripts/smoke-api.sh   # existen (workspace/) — solo lectura
```

Todo lo que esté fuera de este árbol queda fuera de alcance. Si una tarea parece exigir editar otro archivo, detente y repórtalo: el límite de la épica está mal.

## Data model touched here

| Entidad | Campos que esta épica agrega | Notas |
|---|---|---|
| `schema_info` | `key`, `value`, `created_at` | global; prueba que Flyway corre como `app_owner` |
| `business` | `id`, `name`, `slug`, `timezone`, `currency`, `vertical`, `status`, timestamps | global, sin RLS |
| `subscription` | `business_id` (PK), `plan_code`, `status`, `trial_ends_at`, `current_period_end` | global; `TRIAL` 30 días |
| `location` | `id`, `business_id`, `name`, `address`, `phone`, `is_main`, `active` | RLS; una sede principal por negocio |
| `business_settings` | `business_id` (PK), agenda, caja, `labels jsonb` | RLS; valores del vertical |
| `user_account` | `id`, `email` (minúsculas, único), `password_hash`, `name`, `status`, `email_verified_at`, `platform_role` | global |
| `membership` | `id`, `user_id`, `business_id`, `role`, `resource_id`, `active` | RLS especial `membership_access` |
| `refresh_token` | `id`, `user_id`, `business_id`, `family_id`, `token_hash`, `expires_at`, `revoked_at`, `replaced_by` | global (se busca por hash) |
| `one_time_token` | `id`, `user_id`, `purpose`, `token_hash`, `payload jsonb`, `expires_at`, `used_at` | global |

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

## Contracts

**Consumed** — ya existe, no lo reconstruyas:

| De | Interfaz | Garantía |
|---|---|---|
| `workspace/` | `support.IntegrationTest` | PostgreSQL 18 de Testcontainers con la base `citas`; DataSource como `app_runtime`, Flyway como `app_owner`; `baseUrl()`, `connectAsRuntime()`, constantes de usuario y contraseña de prueba |
| `workspace/` | `application.yml` | `spring.jpa.open-in-view=false`, `ddl-auto=validate`, importa `../.env`, perfil por defecto `dev` |

**Produced** — las épicas siguientes dependen exactamente de estas firmas:

| Export | Firma | Usado por |
|---|---|---|
| `shared.tenancy.TenantContext` | `static <T> T run(UUID businessId, UUID userId, Supplier<T> work)`, `businessId()`, `userId()`, `set`, `clear`, `NO_TENANT` | `02`, bundles 2–7 |
| `shared.tenancy.BusinessScopedEntity` | `@MappedSuperclass` con `@TenantId UUID businessId` | toda entidad de negocio |
| `shared.error.DomainException` | `DomainException(Code code, String detail)`, `Code` con `HttpStatus` | todos |
| `shared.money.Money` | `of`, `ofMajor`, `plus`, `minus`, `times`, `isNegative` | bundles 2–3 |
| `platform.Businesses` | `provision(UUID, String name, String vertical, String timezone)` (transacción obligatoria), `summaries`, `current`, `update`, `VerticalSeed`, `BusinessSummary`, `BusinessDetails` | `02` |
| `identity.internal.IdentityStore` | `createAccount`, `findByEmail`, `markEmailVerified`, `changePassword`, `addMembership`, `membershipsOf`, `activeRole`, tokens de un solo uso, `newOpaqueToken`, `sha256Hex` | `02` |
| `PasswordEncoder` (bean) | Argon2id `defaultsForSpringSecurity_v5_8()` | `02` |

## Conventions that bite in this area

- **`TenantContext.run` envuelve la transacción, nunca va dentro.** `set_config` se ejecuta al iniciar la transacción (`TenantAwareJpaTransactionManager.doBegin`) y Hibernate fija el tenant al abrir la sesión. Fijar el negocio a mitad de una transacción no cambia nada.
- **Toda prueba de integración extiende `IntegrationTest`** y por lo tanto corre como `app_runtime`. Nunca conectes la aplicación como `app_owner` ni como el superusuario del contenedor: RLS no se estaría probando.
- **El contenedor de PostgreSQL es uno por corrida de Maven** y lo comparten todas las clases: usa nombres, slugs y correos únicos (sufijo con `UUID`) y nunca supongas tablas vacías; afirma propiedades («solo filas de A», «cero filas de B»), no totales globales.
- **Fixtures de ArchUnit fuera de `com.plataformacitas`** (`archfixtures.*`): así ni el escaneo de componentes ni Hibernate las registran.
- **Migraciones `V{n}__{modulo}_{descripcion}.sql` nunca se editan una vez confirmadas**; toda tabla nueva con `business_id` trae su política RLS en la misma migración.
- **`ddl-auto=validate`:** una entidad JPA cuya tabla o columna no exista rompe el arranque de todas las pruebas.

Reglas completas: `CLAUDE.md`. Reglas por área: `.claude/rules/backend-datos.md`, `.claude/rules/pruebas-backend.md`. Ambos están en la raíz del proyecto (llegaron con `workspace/`).

---

## Tasks

En el mismo orden que `tasks.json`. Ese orden es el de construcción: de arriba abajo, sin reordenar por prioridad.

### `E1-T1` — Esqueleto backend que arranca contra PostgreSQL con roles

**Paso del blueprint:** 1 · **Depende de:** nada · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `backend/src/main/java/com/plataformacitas/CitasApplication.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/security/SecurityConfig.java` — nuevo
- `backend/src/main/resources/db/migration/V1__shared_baseline.sql` — nuevo
- `backend/src/test/java/com/plataformacitas/FoundationIT.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `./mvnw -B -ntp -DskipTests package` runs in `backend/` **THE SYSTEM SHALL** exit 0 and produce `backend/target/plataforma-citas-api.jar`.
2. **WHEN** `bash scripts/smoke-api.sh` starts that jar against the `db` service of `docker-compose.yml` **THE SYSTEM SHALL** answer `GET /actuator/health` with HTTP 200 and a JSON body whose `status` is `UP`.
3. **WHEN** the API boots **THE SYSTEM SHALL** apply Flyway migration `V1__shared_baseline.sql` as `app_owner`, so `pg_tables` reports `app_owner` as owner of `schema_info`.
4. **WHEN** `FoundationIT` queries through the application DataSource **THE SYSTEM SHALL** report `current_user` = `app_runtime`, `rolsuper` = false, `rolbypassrls` = false and zero tables in schema `public` owned by `app_runtime`.
5. **WHEN** `FoundationIT` runs `CREATE TABLE` as `app_runtime` **THE SYSTEM SHALL** fail with SQLState `42501`, while `SELECT value FROM schema_info WHERE key = 'baseline'` returns `plataforma-citas`.
6. **WHEN** an anonymous request hits any path other than `/actuator/health` or `/api/v1/public/**` **THE SYSTEM SHALL** answer HTTP 401.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0, BUILD SUCCESS
test -f backend/target/plataforma-citas-api.jar   # expect: exit 0
(cd backend && ./mvnw -B -ntp test -Dtest=FoundationIT)   # expect: exit 0, BUILD SUCCESS, Failures: 0, Errors: 0
bash scripts/smoke-api.sh   # expect: exit 0, imprime "health: UP" y "SMOKE API OK"
```

**Checkpoint**

```bash
git add -A && git commit -m "E1-T1: Esqueleto backend que arranca contra PostgreSQL con roles"
git tag step-01-esqueleto
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-01-esqueleto` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E1-T2` — Reglas de módulos (Modulith) y ArchUnit con plantilla

**Paso del blueprint:** 2 · **Depende de:** `E1-T1` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `backend/src/main/java/com/plataformacitas/shared/package-info.java` — nuevo
- `backend/src/test/java/com/plataformacitas/ModularityTest.java` — nuevo
- `backend/src/test/java/com/plataformacitas/ArchitectureRulesTest.java` — nuevo
- `backend/src/test/java/archfixtures/baddomain/domain/BadDomainEntity.java` — nuevo
- `docs/modules/README.md` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `ModularityTest` runs **THE SYSTEM SHALL** pass `ApplicationModules.of(CitasApplication.class).verify()`, with module `shared` declared `@ApplicationModule(type = ApplicationModule.Type.OPEN)` in `shared/package-info.java`.
2. **WHEN** `ArchitectureRulesTest` checks rule `DOMAIN_IS_PURE` against fixture `archfixtures.baddomain.domain.BadDomainEntity`, which imports `jakarta.persistence`, **THE SYSTEM SHALL** report a violation (the test asserts the `AssertionError`).
3. **WHEN** `ArchitectureRulesTest` checks `DOMAIN_IS_PURE`, `APPLICATION_WITHOUT_ADAPTERS` and `VERTICAL_NAMES_STAY_IN_VERTICAL` against production classes of `com.plataformacitas` **THE SYSTEM SHALL** report zero violations.
4. **WHEN** `docs/modules/README.md` is read **THE SYSTEM SHALL** contain the headings `## Estructura de un módulo`, `## Hexagonal completa`, `## Módulo ligero` and `## Reglas verificadas`.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

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
git add -A && git commit -m "E1-T2: Reglas de módulos (Modulith) y ArchUnit con plantilla"
git tag step-02-arquitectura
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-02-arquitectura` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E1-T3` — Kernel: Money, errores ProblemDetail e id de petición

**Paso del blueprint:** 3 · **Depende de:** `E1-T1` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

Construye las piezas que todos los módulos reutilizan.

- `Money.java` — `record Money(long amountMinor, String currency)` inmutable. `Money.of(long minor, String currencyCode)`, `Money.ofMajor(BigDecimal amount, String currencyCode)` (redondeo `HALF_EVEN` a `Currency.getInstance(code).getDefaultFractionDigits()`), `plus`, `minus`, `times(int)`, `isNegative()`. Operar dos monedas distintas lanza `DomainException(Code.CURRENCY_MISMATCH, …)`. Nunca `double`.
- `DomainException.java` — `RuntimeException` con `Code code()` y un enum anidado `DomainException.Code` que lleva el `HttpStatus`: `VALIDATION_ERROR` 400, `INVALID_TOKEN` 400, `CAPTCHA_FAILED` 400, `UNAUTHENTICATED` 401, `INVALID_CREDENTIALS` 401, `INVALID_REFRESH_TOKEN` 401, `FORBIDDEN` 403, `EMAIL_NOT_VERIFIED` 403, `NO_ACTIVE_MEMBERSHIP` 403, `NOT_FOUND` 404, `ACCOUNT_EXISTS` 409, `CONFLICT` 409, `SLOT_TAKEN` 409, `CURRENCY_MISMATCH` 422, `RATE_LIMITED` 429, `INTERNAL_ERROR` 500.
- `ApiExceptionHandler.java` — `@RestControllerAdvice` que responde siempre `ProblemDetail` (`application/problem+json`) con las propiedades `code` (texto estable del enum), `title`, `detail` y `requestId` (del MDC). Mapea: `DomainException` → su status; `MethodArgumentNotValidException` y `HandlerMethodValidationException` → 400 `VALIDATION_ERROR` con `errors: [{field, message}]`; `HttpMessageNotReadableException` → 400 `VALIDATION_ERROR`; `AccessDeniedException` → 403 `FORBIDDEN`; `DataIntegrityViolationException` cuya causa raíz es un `SQLException` con SQLState `23P01` → 409 `SLOT_TAKEN` y con `23505` → 409 `CONFLICT`; cualquier otra excepción → 500 `INTERNAL_ERROR` (se registra con stack; el cuerpo nunca la incluye).
- `RequestIdFilter.java` — `OncePerRequestFilter` con `@Order(Ordered.HIGHEST_PRECEDENCE)` y `@Component`: toma `X-Request-Id` si cumple `^[A-Za-z0-9-]{1,64}$`, si no genera un UUID; lo pone en el MDC (`requestId`) y en la cabecera de respuesta `X-Request-Id`; en `finally` limpia `requestId` y `businessId` del MDC.
- `SharedKernelTest.java` — prueba unitaria (sin base de datos): pruebas de `Money` y, con `MockMvcBuilders.standaloneSetup(new ProbeController()).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build()`, pruebas del manejo de errores. `ProbeController` es una clase anidada estática anotada solo con `@RequestMapping("/probe")` (sin `@RestController`, para que el escaneo de componentes nunca la registre) con métodos que: validan un `record` con `@NotBlank` y `@Email`, lanzan `DomainException(Code.NOT_FOUND, …)` y lanzan `new DataIntegrityViolationException("x", new SQLException("solapado", "23P01"))`.

**Files**
- `backend/src/main/java/com/plataformacitas/shared/money/Money.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/error/DomainException.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/error/ApiExceptionHandler.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/web/RequestIdFilter.java` — nuevo
- `backend/src/test/java/com/plataformacitas/shared/SharedKernelTest.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `Money.of(1000, "COP").plus(Money.of(500, "USD"))` runs **THE SYSTEM SHALL** throw `DomainException` with code `CURRENCY_MISMATCH`.
2. **WHEN** `Money.ofMajor(new BigDecimal("10.005"), "USD")` and `Money.ofMajor(new BigDecimal("10.015"), "USD")` run **THE SYSTEM SHALL** return 1000 and 1002 minor units respectively (HALF_EVEN to the currency's default fraction digits).
3. **WHEN** a request body fails bean validation **THE SYSTEM SHALL** answer 400 `application/problem+json` with `code` = `VALIDATION_ERROR` and an `errors` array of `{field, message}` naming every invalid field.
4. **WHEN** a handler throws `DomainException` with code `NOT_FOUND` **THE SYSTEM SHALL** answer 404 with a problem body carrying `code`, `title`, `detail` and `requestId`.
5. **WHEN** a `DataIntegrityViolationException` caused by SQLState `23P01` escapes a handler **THE SYSTEM SHALL** answer 409 with `code` = `SLOT_TAKEN`.
6. **WHEN** a request carries `X-Request-Id: abc-123` **THE SYSTEM SHALL** return response header `X-Request-Id: abc-123` and `requestId` = `abc-123` in any problem body, and without that header it SHALL return a generated UUID.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp test -Dtest=SharedKernelTest)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**

```bash
git add -A && git commit -m "E1-T3: Kernel: Money, errores ProblemDetail e id de petición"
git tag step-03-kernel
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-03-kernel` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E1-T4` — Aislamiento en dos capas: @TenantId, set_config y RLS

**Paso del blueprint:** 4 · **Depende de:** `E1-T1` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `backend/src/main/java/com/plataformacitas/shared/tenancy/TenantContext.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/tenancy/BusinessScopedEntity.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/tenancy/TenancyJpaConfig.java` — nuevo
- `backend/src/main/resources/db/migration/V2__platform_business.sql` — nuevo
- `backend/src/test/java/com/plataformacitas/shared/RowLevelSecurityIT.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** Flyway applies `V2__platform_business.sql` **THE SYSTEM SHALL** create tables `business`, `subscription`, `location` and `business_settings`, with ENABLE and FORCE ROW LEVEL SECURITY and policy `tenant_isolation` on `location` and `business_settings`.
2. **WHEN** `RowLevelSecurityIT` runs the RLS catalog check over schema `public` **THE SYSTEM SHALL** find that every table with a `business_id` column, except the allowlist `subscription` and `refresh_token`, has RLS enabled, forced and at least one policy.
3. **WHEN** the same catalog check runs over the session's temporary schema after creating a temp table with a `business_id` column and no policy **THE SYSTEM SHALL** report that table as unprotected.
4. **WHEN** `app_runtime` reads `location` without `app.business_id` set (outside any transaction) **THE SYSTEM SHALL** return zero rows, and an `INSERT` into `location` SHALL fail with SQLState `42501`.
5. **WHEN** code runs inside `TenantContext.run(businessA, null, …)` within a Spring transaction **THE SYSTEM SHALL** see only business A's rows through a native `SELECT count(*) FROM location` without any `WHERE` clause.
6. **WHEN** that transaction has ended and a new transaction without tenant runs on the same pool **THE SYSTEM SHALL** see zero `location` rows, because `set_config(…, true)` is transaction-local.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp test -Dtest=RowLevelSecurityIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**

```bash
git add -A && git commit -m "E1-T4: Aislamiento en dos capas: @TenantId, set_config y RLS"
git tag step-04-rls
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-04-rls` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E1-T5` — Alta de negocio con sede principal y ajustes

**Paso del blueprint:** 5 · **Depende de:** `E1-T3`, `E1-T4` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `backend/src/main/java/com/plataformacitas/platform/internal/Business.java` — nuevo
- `backend/src/main/java/com/plataformacitas/platform/internal/Location.java` — nuevo
- `backend/src/main/java/com/plataformacitas/platform/Businesses.java` — nuevo
- `backend/src/main/java/com/plataformacitas/vertical/peluqueria/PeluqueriaVerticalSeed.java` — nuevo
- `backend/src/test/java/com/plataformacitas/platform/ProvisioningIT.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `Businesses.provision` runs for name `Peluquería Doña Ana` and vertical `PELUQUERIA` **THE SYSTEM SHALL** create one `business` row with slug `peluqueria-dona-ana`, timezone `America/Bogota`, currency `COP` and status `ACTIVE`.
2. **WHEN** a second business named `Peluquería Doña Ana` is provisioned **THE SYSTEM SHALL** give it slug `peluqueria-dona-ana-2`.
3. **WHEN** a business is provisioned **THE SYSTEM SHALL** create exactly one `location` row with `is_main` = true and name `Sede principal`, persisted through entity `Location`, which extends `BusinessScopedEntity`.
4. **WHEN** a business is provisioned with a fixed `Clock` at `2026-10-12T15:00:00Z` **THE SYSTEM SHALL** create a `subscription` row with `plan_code` = `PRO`, `status` = `TRIAL` and `trial_ends_at` = `2026-11-11T15:00:00Z`.
5. **WHEN** a business of vertical `PELUQUERIA` is provisioned **THE SYSTEM SHALL** write `business_settings` with `slot_step_min` = 15 and `labels` containing `"resource": "Empleado"`, taken from `PeluqueriaVerticalSeed`.
6. **WHEN** `provision` is called with a vertical that has no registered `Businesses.VerticalSeed` **THE SYSTEM SHALL** throw `DomainException` with code `VALIDATION_ERROR` and write no `business` row.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp test -Dtest=ProvisioningIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**

```bash
git add -A && git commit -m "E1-T5: Alta de negocio con sede principal y ajustes"
git tag step-05-negocio
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-05-negocio` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E1-T6` — Cuentas globales, membresías con RLS y Argon2id

**Paso del blueprint:** 6 · **Depende de:** `E1-T4`, `E1-T5` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `backend/src/main/resources/db/migration/V3__identity_accounts.sql` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/UserAccount.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/IdentityStore.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/security/SecurityConfig.java` — editar: agrega el bean PasswordEncoder Argon2id
- `backend/src/test/java/com/plataformacitas/identity/IdentityPersistenceIT.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** Flyway applies `V3__identity_accounts.sql` **THE SYSTEM SHALL** create `user_account`, `membership`, `refresh_token` and `one_time_token`, with RLS enabled and forced on `membership` under policy `membership_access`.
2. **WHEN** `IdentityStore.createAccount` stores a password **THE SYSTEM SHALL** persist a hash that starts with `$argon2id$` and never the plain text.
3. **WHEN** accounts are created with `Ana@Example.com` and then `ana@example.com` **THE SYSTEM SHALL** store `ana@example.com` once and reject the second with `DomainException` code `ACCOUNT_EXISTS`.
4. **WHEN** a user is OWNER in business A and EMPLOYEE in business B and only `app.user_id` is set to that user **THE SYSTEM SHALL** list both memberships.
5. **WHEN** only `app.business_id` is set to business A **THE SYSTEM SHALL** list the memberships of A and none of B.
6. **WHEN** neither setting is present **THE SYSTEM SHALL** list zero memberships, and inserting a membership for business B while `app.business_id` = A SHALL fail with SQLState `42501`.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp test -Dtest=IdentityPersistenceIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — incluye RowLevelSecurityIT con membership protegida
```

**Checkpoint**

```bash
git add -A && git commit -m "E1-T6: Cuentas globales, membresías con RLS y Argon2id"
git tag step-06-cuentas
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-06-cuentas` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

---

## Epic acceptance

La épica está hecha cuando todas sus tareas están `done` **y**:

1. **WHEN** `(cd backend && ./mvnw -B -ntp verify)` runs **THE SYSTEM SHALL** exit 0 with `FoundationIT`, `ModularityTest`, `ArchitectureRulesTest`, `SharedKernelTest`, `RowLevelSecurityIT`, `ProvisioningIT` and `IdentityPersistenceIT` passing.
2. **WHEN** the API runs as `app_runtime` with no business context **THE SYSTEM SHALL** return zero rows from every RLS table and reject inserts with SQLState `42501` (fail closed).

```bash
(cd backend && ./mvnw -B -ntp verify)
(cd backend && ./mvnw -B -ntp -DskipTests package) && bash scripts/smoke-api.sh
```

## Pitfalls

- **`''::uuid` en una política** — tras una transacción con `set_config(..., true)`, `current_setting('app.business_id', true)` devuelve `''` en esa conexión del pool. Por eso las políticas usan `NULLIF(..., '')`. No lo "simplifiques".
- **Crear tablas desde la aplicación** — `app_runtime` no tiene `CREATE` en `public`; todo DDL va en Flyway (que corre como `app_owner`).
- **Reglas ArchUnit vacías** — en este bundle no hay paquetes `domain`/`application`; sin `allowEmptyShould(true)` ArchUnit falla con «Rule failed to check any classes».
- **Bean `transactionManager` duplicado** — el de `TenancyJpaConfig` reemplaza al de Boot (que es `@ConditionalOnMissingBean`); no declares otro.
- **Windows y saltos de línea** — `infra/db/init/01-roles.sh` debe quedar con LF (lo garantiza `.gitattributes`); con CRLF el contenedor de PostgreSQL falla al inicializar.

## Before moving on

- [ ] Cada tarea de esta épica está `done` en `tasks.json` — ninguna queda `in_progress`.
- [ ] Pasaron todos los comandos `verify` de cada tarea, no solo el primero.
- [ ] No se editó ningún comando `verify` ni se saltó uno porque faltara un archivo.
- [ ] **Cada tarea tiene su tag `checkpoint`** (`git tag -l 'step-*'` lista `step-01-esqueleto` … `step-06-cuentas`).
- [ ] El gate pasa limpio desde la raíz del proyecto.
- [ ] Cada contrato «Produced» existe con la firma indicada.
- [ ] No se modificó ningún archivo fuera del subárbol.
- [ ] `.env.example` no cambió (esta épica no agrega variables: usa `DB_URL`, `DB_OWNER_PASSWORD`, `DB_RUNTIME_PASSWORD` que ya están).
- [ ] Un commit por tarea, con el prefijo del id de la tarea, seguido de su tag.
