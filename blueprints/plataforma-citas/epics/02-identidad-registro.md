# Épica 02: Identidad, aislamiento probado, correo, registro y contrato

> Al terminar, una persona se registra con captcha, verifica su correo, entra a su negocio con JWT y refresh rotativo, cambia de negocio, recupera su contraseña; la suite de aislamiento recorre cada tabla de negocio; y el contrato OpenAPI genera el cliente TypeScript con control de deriva.

| | |
|---|---|
| **Epic id** | `02-identidad-registro` |
| **Tasks** | `E2-T1` … `E2-T7` (pasos 7–13 del blueprint) |
| **Depends on** | `01-fundacion-datos` |
| **Unlocks** | `03-frontend-entrega` (desde `E3-T2`) |
| **Parallel with** | `E3-T1` (sistema de diseño), que no comparte archivos con esta épica |

No necesitas ningún otro archivo para completar esta épica. Todo lo de abajo se repite aquí a propósito.

---

## Stack

Java 25 · Spring Boot 4.1.1 (Spring Security 7 con servidor de recursos OAuth2 y `NimbusJwtEncoder`, Hibernate 7, Jackson 3 en `tools.jackson`) · Spring Modulith 2.1.1 · Bucket4j · BouncyCastle (Argon2id) · Spring Mail (Mailpit en local, Brevo en producción) · springdoc-openapi · Testcontainers 2 · Angular 22 + `ng-openapi-gen` solo para generar el cliente en `E2-T7`.
Paquete raíz `com.plataformacitas`. Versiones en `backend/pom.xml` y `frontend/package-lock.json`: léelas allí, nunca las adivines.

| Tarea | Comando (desde la raíz del repo) |
|---|---|
| Servicios locales | `docker compose up -d --wait db mailpit` · `docker compose down` |
| Buzón de Mailpit (UI y API) | http://localhost:8025 · `GET http://localhost:8025/api/v1/messages` |
| Compilar el jar | `(cd backend && ./mvnw -B -ntp -DskipTests package)` |
| Todas las pruebas + Modulith + ArchUnit | `(cd backend && ./mvnw -B -ntp verify)` |
| Una clase de prueba | `(cd backend && ./mvnw -B -ntp test -Dtest=NombreIT)` |
| Prueba de humo del jar | `bash scripts/smoke-api.sh [--expect-demo] [--expect-mail]` |
| Exportar contrato | `(cd backend && ./mvnw -B -ntp test -Dtest=OpenApiExportIT -Dopenapi.export=true)` |
| Regenerar cliente | `(cd frontend && npx ng-openapi-gen -c ng-openapi-gen.json)` |
| Control de deriva | `bash scripts/check-api-drift.sh` (0 al día · 1 deriva · 2 error) |

**Gate:** `(cd backend && ./mvnw -B -ntp verify)` pasa antes de marcar cualquier tarea como hecha; desde `E2-T7`, también `bash scripts/check-api-drift.sh`.

`docker-compose.yml`, `scripts/smoke-api.sh`, `application.yml` y `application-test.yml` llegaron con `workspace/` y ya están en la raíz: no los reescribes. Las pruebas usan el emisor de correo falso (`app.email.provider=fake` en el perfil `test`) y el captcha falso; la prueba de humo usa Mailpit de verdad.

## Directory subtree

```
backend/src/main/java/com/plataformacitas/
  shared/
    security/SecurityConfig.java            # existe — se reescribe en E2-T1
    security/JwtConfig.java                 # NUEVO (E2-T1)
    ratelimit/RateLimiter.java              # NUEVO (E2-T4)
    captcha/CaptchaVerifier.java            # NUEVO (E2-T4)
    captcha/TurnstileCaptchaVerifier.java   # NUEVO (E2-T4)
    email/EmailSender.java                  # NUEVO (E2-T5)
    email/EmailConfig.java                  # NUEVO (E2-T5)
    openapi/OpenApiConfig.java              # NUEVO (E2-T7)
  platform/
    Businesses.java                         # existe — se edita en E2-T6
    internal/BusinessController.java        # NUEVO (E2-T3)
  identity/internal/
    IdentityStore.java, UserAccount.java    # existen — solo lectura
    TokenService.java                       # NUEVO (E2-T1)
    AuthService.java                        # NUEVO (E2-T2), se edita en E2-T4
    AuthController.java                     # NUEVO (E2-T2), se edita en E2-T5
    DemoDataSeeder.java                     # NUEVO (E2-T3)
    PasswordResetService.java               # NUEVO (E2-T5)
    SignupService.java                      # NUEVO (E2-T6)
    SignupController.java                   # NUEVO (E2-T6)
backend/src/main/resources/db/migration/V4__platform_legal_acceptance.sql   # NUEVO (E2-T6)
backend/src/test/java/
  com/plataformacitas/support/TestData.java          # NUEVO (E2-T1)
  com/plataformacitas/identity/TokenServiceIT.java   # NUEVO (E2-T1)
  com/plataformacitas/identity/AuthFlowIT.java       # NUEVO (E2-T2)
  com/plataformacitas/platform/TenantIsolationIT.java   # NUEVO (E2-T3)
  com/plataformacitas/ArchitectureRulesTest.java     # existe — se edita en E2-T3
  archfixtures/rogue/RogueEntity.java                # NUEVO (E2-T3)
  com/plataformacitas/identity/SecurityHardeningIT.java # NUEVO (E2-T4)
  com/plataformacitas/identity/PasswordResetIT.java  # NUEVO (E2-T5)
  com/plataformacitas/identity/SignupIT.java         # NUEVO (E2-T6)
  com/plataformacitas/OpenApiExportIT.java           # NUEVO (E2-T7)
frontend/ng-openapi-gen.json                         # NUEVO (E2-T7)
frontend/openapi/openapi.json                        # GENERADO (E2-T7) por OpenApiExportIT
frontend/src/app/api/**                              # GENERADO (E2-T7) por ng-openapi-gen — nunca a mano
scripts/check-api-drift.sh                           # NUEVO (E2-T7)
```

Todo lo que esté fuera de este árbol queda fuera de alcance. Si una tarea parece exigir otro archivo, detente y repórtalo.

## Data model touched here

| Entidad | Campos que esta épica lee o agrega | Notas |
|---|---|---|
| `user_account`, `membership`, `refresh_token`, `one_time_token` | todos (creados en la épica 01) | `refresh_token` y `one_time_token` guardan solo SHA-256 |
| `business`, `subscription`, `location`, `business_settings` | lectura y alta vía `Businesses` | — |
| `legal_acceptance` | NUEVA: `id`, `business_id`, `user_id`, `document`, `version`, `accepted_at`, `ip_address` | RLS `tenant_isolation`; entra sola en `TenantIsolationIT` |

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

## Contracts

**Consumed** — ya existe, no lo reconstruyas:

| De | Interfaz | Garantía |
|---|---|---|
| `01-fundacion-datos` | `TenantContext.run(businessId, userId, work)` | fija negocio y cuenta para la transacción que se abra dentro; restaura al salir |
| `01-fundacion-datos` | `Businesses.provision(id, name, vertical, timezone)` | requiere transacción abierta dentro de `TenantContext.run(id, …)`; crea negocio, sede principal, ajustes y prueba de 30 días |
| `01-fundacion-datos` | `IdentityStore` (cuentas, membresías, tokens de un solo uso, `newOpaqueToken`, `sha256Hex`) | correo en minúsculas, `ACCOUNT_EXISTS` si se repite |
| `01-fundacion-datos` | `DomainException` + `ApiExceptionHandler` | cada `Code` sale como `application/problem+json` con su estado |
| `workspace/` | `IntegrationTest`, `application-test.yml` | perfil `test`: `email.provider=fake`, `captcha.provider=fake`, `signup-per-hour=5`, JWT de prueba |

**Produced** — las épicas y bundles siguientes dependen exactamente de esto:

| Export | Firma | Usado por |
|---|---|---|
| API REST | la tabla «Endpoints críticos — contrato» | `03`, bundles 2–7 |
| `frontend/src/app/api` | `Api.invoke(fn, params)`, `provideApiConfiguration`, modelos de `models.ts`, funciones `fn/<tag>/<operación>.ts` | `03` |
| `shared.email.EmailSender` | publicar `EmailSender.Message` como evento ⇒ envío después del commit | bundles 2–7 (invitaciones, resumen diario) |
| `shared.ratelimit.RateLimiter` | `tryConsume`, `isExhausted`, `resetAll` | bundles 4–6 |
| `shared.captcha.CaptchaVerifier` | `verify(token, ip)` | registro |
| JWT de acceso | claims `sub`, `bid`, `role`, `typ=access` | todo endpoint autenticado |

## Conventions that bite in this area

- **Los correos se publican, no se envían.** `applicationEventPublisher.publishEvent(new EmailSender.Message(...))` dentro de la transacción; `EmailConfig` los despacha después del commit. Llamar `send` directamente envía correos de transacciones que luego se revierten.
- **Ningún endpoint queda abierto por omisión:** la lista de rutas públicas está en `SecurityConfig` y todo lo demás exige autoridad `TENANT`. Un endpoint nuevo público se agrega a esa lista a propósito.
- **El nombre del método del controlador es el `operationId`** del contrato y el nombre de la función generada en el frontend: único en toda la API, en inglés.
- **Los límites de Bucket4j viven en memoria del proceso y las pruebas comparten el contexto de Spring:** toda clase que los toque llama `rateLimiter.resetAll()` en `@BeforeEach`.
- **El cliente generado no se edita.** Si cambia el contrato, se regenera y se confirma junto con el cambio del backend.
- **Errores de credenciales sin pistas:** mismo `code` y mismo `detail` para correo inexistente y contraseña incorrecta; `EMAIL_NOT_VERIFIED` solo después de validar la contraseña.

Reglas completas: `CLAUDE.md`. Reglas por área: `.claude/rules/backend-api.md`, `.claude/rules/backend-datos.md`, `.claude/rules/pruebas-backend.md`.

---

## Tasks

En el mismo orden que `tasks.json`. Ese orden es el de construcción: de arriba abajo, sin reordenar por prioridad.

### `E2-T1` — JWT de acceso, refresh rotativo y servidor de recursos

**Paso del blueprint:** 7 · **Depende de:** `E1-T5`, `E1-T6` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `backend/src/main/java/com/plataformacitas/shared/security/JwtConfig.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/TokenService.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/security/SecurityConfig.java` — editar: reescritura completa con servidor de recursos JWT
- `backend/src/test/java/com/plataformacitas/support/TestData.java` — nuevo
- `backend/src/test/java/com/plataformacitas/identity/TokenServiceIT.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `TokenService.issueSession(userId, businessId)` runs for an active membership **THE SYSTEM SHALL** return an HS256 access JWT with claims `sub` = user id, `bid` = business id, `role` = membership role, `typ` = `access` and an expiry 15 minutes after issue.
2. **WHEN** a session is issued **THE SYSTEM SHALL** store in `refresh_token` only the SHA-256 hex hash of the opaque refresh token, bound to that user and business.
3. **WHEN** a refresh token is rotated **THE SYSTEM SHALL** revoke the old row and return a new refresh token in the same `family_id` for the same business.
4. **WHEN** an already-rotated refresh token is presented again **THE SYSTEM SHALL** fail with code `INVALID_REFRESH_TOKEN` and revoke every token of its family.
5. **WHEN** a request to a non-public `/api/v1/**` path arrives without a token **THE SYSTEM SHALL** answer 401 `application/problem+json` with code `UNAUTHENTICATED`, and with a valid preauth token it SHALL answer 403 with code `FORBIDDEN`.
6. **WHEN** `JwtConfig.secretKey` receives an empty value or fewer than 64 hexadecimal characters **THE SYSTEM SHALL** throw `IllegalStateException` whose message names `JWT_SECRET`.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp test -Dtest=TokenServiceIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
bash scripts/smoke-api.sh   # expect: exit 0, "SMOKE API OK" — arranca con el JWT_SECRET de .env
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T1: JWT de acceso, refresh rotativo y servidor de recursos"
git tag step-07-tokens
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-07-tokens` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E2-T2` — Endpoints de login, selección y cambio de negocio

**Paso del blueprint:** 8 · **Depende de:** `E2-T1` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

Expón la autenticación bajo `/api/v1/auth` (contrato completo en la sección «Endpoints críticos»).

- `AuthService.java` — `@Service`:
  - `LoginResult login(String email, String password)` — normaliza el correo; si la cuenta no existe ejecuta igualmente `passwordEncoder.matches` contra un hash Argon2 fijo (mismo tiempo de respuesta) y lanza `INVALID_CREDENTIALS`; contraseña incorrecta → `INVALID_CREDENTIALS` con el mismo `detail` («Correo o contraseña incorrectos.»); cuenta `DISABLED` → `INVALID_CREDENTIALS`; correo sin verificar (solo después de validar la contraseña) → `EMAIL_NOT_VERIFIED`; sin membresías activas → `NO_ACTIVE_MEMBERSHIP`; una membresía → sesión directa; varias → `BUSINESS_SELECTION_REQUIRED` con la lista y un preauth token.
  - `IssuedSession selectBusiness(UUID userId, UUID businessId)` y `IssuedSession switchBusiness(UUID userId, UUID businessId, String currentRawRefresh)` (revoca la familia del refresh actual si viene) — ambos delegan en `TokenService.issueSession`, que exige membresía activa (si no, 403 `FORBIDDEN`).
  - `MeView me(UUID userId, UUID businessId)` — cuenta, negocio activo con rol y lista de negocios (`IdentityStore.membershipsOf`).
- `AuthController.java` — `@RestController @RequestMapping("/api/v1/auth") @Tag(name = "auth")`. Los DTO son `record` anidados públicos con Bean Validation (`LoginRequest`, `LoginResponse`, `SessionResponse`, `BusinessOption`, `SelectBusinessRequest`, `MeResponse`, `UserSummary`). Métodos (sus nombres son el `operationId` de OpenAPI y deben ser únicos en toda la API): `login`, `selectBusiness`, `switchBusiness`, `refresh`, `logout`, `me`. La cookie de refresh se escribe con `ResponseCookie.from("rt", token).httpOnly(true).secure(app.cookie.secure).sameSite("Strict").path("/api/v1/auth").maxAge(app.jwt.refresh-ttl)`; `logout` la borra con `maxAge(0)` y responde 204 siempre. `refresh` lee la cookie con `@CookieValue(name = "rt", required = false)`; ausente → 401 `INVALID_REFRESH_TOKEN`. `selectBusiness`, `switchBusiness` y `me` toman `sub`/`bid` del `JwtAuthenticationToken`.
- `AuthFlowIT.java` — extiende `IntegrationTest`; siembra con `TestData`; usa `java.net.http.HttpClient` y lee `Set-Cookie` manualmente.

**Files**
- `backend/src/main/java/com/plataformacitas/identity/internal/AuthService.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/AuthController.java` — nuevo
- `backend/src/test/java/com/plataformacitas/identity/AuthFlowIT.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `POST /api/v1/auth/login` receives a wrong password or an unknown email **THE SYSTEM SHALL** answer 401 with code `INVALID_CREDENTIALS` and the same `detail` text in both cases.
2. **WHEN** a verified account with exactly one active membership logs in **THE SYSTEM SHALL** answer 200 with `status` = `AUTHENTICATED`, a `session.accessToken` whose `bid` claim is that business, and a `Set-Cookie` for `rt` with `HttpOnly`, `SameSite=Strict` and `Path=/api/v1/auth`.
3. **WHEN** an account with active memberships in two businesses logs in **THE SYSTEM SHALL** answer 200 with `status` and `code` = `BUSINESS_SELECTION_REQUIRED`, both businesses and a `preAuthToken`, and `POST /api/v1/auth/select-business` with that token and business B SHALL return a session whose `bid` is B and whose `role` is the role held in B.
4. **WHEN** an account that is OWNER in A and EMPLOYEE in B calls `POST /api/v1/auth/switch-business` for B **THE SYSTEM SHALL** return a session with role `EMPLOYEE`, and `GET /api/v1/auth/me` SHALL report business B with role `EMPLOYEE`.
5. **WHEN** `POST /api/v1/auth/refresh` is called with a valid `rt` cookie **THE SYSTEM SHALL** return a new access token for the same business and rotate the cookie, and after `POST /api/v1/auth/logout` the same cookie SHALL get 401 `INVALID_REFRESH_TOKEN`.
6. **WHEN** an account whose email is not verified logs in with the correct password **THE SYSTEM SHALL** answer 403 with code `EMAIL_NOT_VERIFIED`.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp test -Dtest=AuthFlowIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T2: Endpoints de login, selección y cambio de negocio"
git tag step-08-login
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-08-login` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E2-T3` — API del negocio, suite de aislamiento y datos demo

**Paso del blueprint:** 9 · **Depende de:** `E1-T2`, `E2-T2` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

Cierra HU-03: endpoints del negocio, la suite de aislamiento que crece sola y la regla de entidades.

- `BusinessController.java` — `@RestController @RequestMapping("/api/v1/business") @Tag(name = "business")`: `getBusiness()` (`GET`, cualquier rol) devuelve `BusinessResponse` desde `Businesses.current()`; `updateBusiness(@Valid UpdateBusinessRequest)` (`PUT`, `@PreAuthorize("hasRole('OWNER')")`) valida `name` 2–120, `timezone` con `ZoneId.of` y `currency` con `Currency.getInstance` (inválidos → 400 `VALIDATION_ERROR`) y llama `Businesses.update(...)` (ya existe desde el paso 5). En este bundle no hay citas ni ventas, así que la zona y la moneda se pueden cambiar; las restricciones de HU-96 llegan en el bundle 2.
- `DemoDataSeeder.java` — `ApplicationRunner` con `@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")`. Si `app.seed.demo-password` está vacío, falla el arranque con `IllegalStateException` que nombra `DEV_SEED_PASSWORD`. Si `owner@demo.local` ya existe, no hace nada (idempotente). Si no: provisiona «Peluquería Demo Norte» y «Peluquería Demo Sur» (vertical `PELUQUERIA`), crea `owner@demo.local` (verificado, OWNER en ambos) y `recepcion@demo.local` (verificado, RECEPTION en Norte), ambos con la contraseña de `DEV_SEED_PASSWORD`. Se activa siempre en el perfil `dev` y en staging con `APP_SEED_DEMO=true`.
- `TenantIsolationIT.java` — extiende `IntegrationTest`; siembra A (dueño `ownerA`, recepción `receptionA`) y B (dueño `ownerB`). Casos HTTP: `GET/PUT /api/v1/business` por dueño y recepción, validaciones, `select-business`/`switch-business` hacia un negocio ajeno → 403. Caso de tablas: obtiene con la consulta de catálogo **todas** las tablas de `public` con columna `business_id` y política RLS (así la suite crece sola con cada tabla nueva) y, para cada una, dentro de `TenantContext.run(A, ownerA, tx)` con `JdbcClient`: `select count(*) from <t> where business_id = :b` = 0, `update <t> set business_id = business_id where business_id = :b` = 0 filas, `delete from <t> where business_id = :b` = 0 filas. Además `insert into location (id, business_id, name, is_main, active) values (…, :b, 'x', false, true)` falla con SQLState `42501`.
- `ArchitectureRulesTest.java` (editar) — agrega `ENTITIES_WITH_BUSINESS_ID_ARE_SCOPED`: clases `@Entity` cuyo `getAllFields()` incluye un campo `businessId` deben ser asignables a `BusinessScopedEntity`; y `GLOBAL_ENTITIES_ONLY_IN_IDENTITY_OR_PLATFORM`: clases `@Entity` no asignables a `BusinessScopedEntity` deben residir en `com.plataformacitas.identity..` o `com.plataformacitas.platform..`. Pasan sobre producción; `ENTITIES_WITH_BUSINESS_ID_ARE_SCOPED` falla (assertThrows `AssertionError`) sobre `new ClassFileImporter().importPackages("archfixtures.rogue")`.
- `archfixtures/rogue/RogueEntity.java` — `@jakarta.persistence.Entity public class RogueEntity { @jakarta.persistence.Id java.util.UUID id; java.util.UUID businessId; }` (fuera de `com.plataformacitas`: Hibernate no la registra).

**Files**
- `backend/src/main/java/com/plataformacitas/platform/internal/BusinessController.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/DemoDataSeeder.java` — nuevo
- `backend/src/test/java/com/plataformacitas/platform/TenantIsolationIT.java` — nuevo
- `backend/src/test/java/com/plataformacitas/ArchitectureRulesTest.java` — editar: agrega las reglas de entidades
- `backend/src/test/java/archfixtures/rogue/RogueEntity.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** the owner of business A calls `GET /api/v1/business` **THE SYSTEM SHALL** return A's `id`, `name`, `slug`, `timezone`, `currency`, `trialEndsAt` and `labels`, never data of business B.
2. **WHEN** the owner of A calls `PUT /api/v1/business` with a new name **THE SYSTEM SHALL** rename A and leave B's name unchanged, and **WHEN** a RECEPTION member of A calls it **THE SYSTEM SHALL** answer 403 with code `FORBIDDEN`.
3. **WHEN** `PUT /api/v1/business` receives timezone `Mars/Base` or currency `XXXX` **THE SYSTEM SHALL** answer 400 with code `VALIDATION_ERROR`.
4. **WHEN** `TenantIsolationIT` iterates over every `public` table with a `business_id` column and an RLS policy **THE SYSTEM SHALL**, inside business A's context, count, update and delete zero rows of business B, and inserting a `location` for B SHALL fail with SQLState `42501`.
5. **WHEN** `ArchitectureRulesTest` checks `ENTITIES_WITH_BUSINESS_ID_ARE_SCOPED` against fixture `archfixtures.rogue.RogueEntity` **THE SYSTEM SHALL** report a violation, and against production classes it SHALL report none.
6. **WHEN** the API starts with profile `dev` **THE SYSTEM SHALL** seed once `owner@demo.local` as OWNER of two demo businesses with password `DEV_SEED_PASSWORD`, so `bash scripts/smoke-api.sh --expect-demo` sees `BUSINESS_SELECTION_REQUIRED` with both businesses.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp test -Dtest='TenantIsolationIT,ArchitectureRulesTest')   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
bash scripts/smoke-api.sh --expect-demo   # expect: exit 0, "demo: BUSINESS_SELECTION_REQUIRED …" y "SMOKE API OK"
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T3: API del negocio, suite de aislamiento y datos demo"
git tag step-09-aislamiento
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-09-aislamiento` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E2-T4` — Antiabuso: Bucket4j, Turnstile, CORS y cabeceras

**Paso del blueprint:** 10 · **Depende de:** `E2-T2` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

HU-91 y la infraestructura antiabuso que usan el login, la recuperación de contraseña (paso 11) y el registro (paso 12).

- `RateLimiter.java` — `@Component` con Bucket4j en memoria (un solo proceso de API). `enum Policy { LOGIN_FAILURES, SIGNUP, FORGOT_PASSWORD_IP, FORGOT_PASSWORD_EMAIL }` con capacidad y recarga desde `app.rate-limit.*` (`login-failures-per-minute` por minuto; los demás por hora) usando `Bandwidth.builder().capacity(n).refillGreedy(n, periodo).build()`. Métodos: `boolean tryConsume(Policy p, String key)`, `boolean isExhausted(Policy p, String key)` (`getAvailableTokens() == 0`), `void resetAll()` (para pruebas). `ConcurrentHashMap<String, Bucket>` con clave `policy + ":" + key`; si supera 100 000 entradas, se vacía.
- `CaptchaVerifier.java` — interfaz `boolean verify(String token, String remoteIp)` con la constante `String DUMMY_TOKEN = "XXXX.DUMMY.TOKEN.XXXX"` y una clase anidada `@Configuration(proxyBeanMethods = false) class Config` cuyo `@Bean CaptchaVerifier captchaVerifier(...)` lee `app.captcha.provider`: `fake` → acepta solo `DUMMY_TOKEN` (y lanza `IllegalStateException` si el perfil `prod` está activo); `turnstile` → exige `app.captcha.secret` no vacío y devuelve `new TurnstileCaptchaVerifier(RestClient.builder(), verifyUrl, secret)`. Expón la fábrica como método estático `create(String provider, String secret, String verifyUrl, Environment env)` para probarla.
- `TurnstileCaptchaVerifier.java` — `POST` `application/x-www-form-urlencoded` a `app.captcha.verify-url` con `secret`, `response` y `remoteip`; lee `{ "success": bool, "error-codes": [...] }` en un `record`; devuelve `success`. Cualquier excepción de red o de parseo → `false` (falla cerrada) y log `warn`. Constructor `(RestClient.Builder builder, String verifyUrl, String secret)` para enlazar `MockRestServiceServer` en pruebas.
- `AuthService.java` (editar) — inyecta `HttpServletRequest` (proxy de Spring con alcance de petición) y toma la IP con `request.getRemoteAddr()`, que en producción ya es la del cliente por `server.forward-headers-strategy: native`; así la firma que usa `AuthController` no cambia. Si `rateLimiter.isExhausted(LOGIN_FAILURES, ip)` → `DomainException(RATE_LIMITED)` antes de mirar la contraseña; cada `INVALID_CREDENTIALS` consume una ficha.
- `SecurityHardeningIT.java` — extiende `IntegrationTest`; `@BeforeEach` llama `rateLimiter.resetAll()`. Cubre los límites de login, CORS, cabeceras y `TurnstileCaptchaVerifier` con `MockRestServiceServer.bindTo(builder).build()` (respuestas con `Cloudflare` falsas: secreto `2x0000000000000000000000000000000AA` → `success:false`, secreto `1x0000000000000000000000000000000AA` → `success:true`), sin red.

**Files**
- `backend/src/main/java/com/plataformacitas/shared/ratelimit/RateLimiter.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/captcha/CaptchaVerifier.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/captcha/TurnstileCaptchaVerifier.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/AuthService.java` — editar: límite de intentos fallidos por IP
- `backend/src/test/java/com/plataformacitas/identity/SecurityHardeningIT.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** 10 failed logins arrive from one IP within one minute **THE SYSTEM SHALL** answer the 11th login attempt from that IP with 429 and code `RATE_LIMITED`, even when its password is correct.
2. **WHEN** a request arrives with `Origin: https://evil.example`, which is not in `CORS_ALLOWED_ORIGINS`, **THE SYSTEM SHALL** answer 403 without `Access-Control-Allow-Origin`, and with `Origin: http://localhost:4200` it SHALL echo that origin in `Access-Control-Allow-Origin`.
3. **WHEN** any API response is returned **THE SYSTEM SHALL** include `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and `Referrer-Policy: strict-origin-when-cross-origin`.
4. **WHEN** `TurnstileCaptchaVerifier` posts to the siteverify URL **THE SYSTEM SHALL** send form fields `secret`, `response` and `remoteip`, and return false when the answer is `{"success":false,"error-codes":["invalid-input-response"]}`.
5. **WHEN** siteverify answers `{"success":true}` **THE SYSTEM SHALL** return true, and **WHEN** the call fails with an I/O error **THE SYSTEM SHALL** return false.
6. **WHEN** provider `fake` is configured **THE SYSTEM SHALL** accept only token `XXXX.DUMMY.TOKEN.XXXX`, and **WHEN** `CaptchaVerifier.Config.create` is called with provider `fake` and profile `prod` active **THE SYSTEM SHALL** throw `IllegalStateException`.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp test -Dtest=SecurityHardeningIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T4: Antiabuso: Bucket4j, Turnstile, CORS y cabeceras"
git tag step-10-antiabuso
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-10-antiabuso` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E2-T5` — Correo transaccional y recuperación de contraseña

**Paso del blueprint:** 11 · **Depende de:** `E2-T4` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `backend/src/main/java/com/plataformacitas/shared/email/EmailSender.java` — nuevo
- `backend/src/main/java/com/plataformacitas/shared/email/EmailConfig.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/PasswordResetService.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/AuthController.java` — editar: endpoints forgotPassword y resetPassword
- `backend/src/test/java/com/plataformacitas/identity/PasswordResetIT.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** a service publishes an `EmailSender.Message` inside a transaction that commits **THE SYSTEM SHALL** deliver it exactly once after the commit, and **WHEN** the transaction rolls back **THE SYSTEM SHALL** deliver nothing.
2. **WHEN** `POST /api/v1/auth/password/forgot` receives a registered or an unregistered email **THE SYSTEM SHALL** answer 202 in both cases and send one email with subject `Restablece tu contraseña` only to the registered address, and the 6th request from one IP within an hour SHALL get 429 `RATE_LIMITED`.
3. **WHEN** the emailed token is posted to `POST /api/v1/auth/password/reset` within 1 hour **THE SYSTEM SHALL** answer 204, accept the new password at login and reject the old one with 401 `INVALID_CREDENTIALS`.
4. **WHEN** the same token is used a second time, or after its 1-hour expiry, **THE SYSTEM SHALL** answer 400 with code `INVALID_TOKEN`.
5. **WHEN** a password reset completes **THE SYSTEM SHALL** revoke every active refresh token of the account, so a previous `rt` cookie gets 401 `INVALID_REFRESH_TOKEN`.
6. **WHEN** `bash scripts/smoke-api.sh --expect-demo --expect-mail` runs against Mailpit (SMTP `localhost:1025`) **THE SYSTEM SHALL** deliver a message to `owner@demo.local` with subject `Restablece tu contraseña`.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp test -Dtest=PasswordResetIT)   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
bash scripts/smoke-api.sh --expect-demo --expect-mail   # expect: exit 0, "correo: Mailpit recibió …" y "SMOKE API OK"
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T5: Correo transaccional y recuperación de contraseña"
git tag step-11-correo
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-11-correo` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E2-T6` — Registro autoservicio y crear otro negocio

**Paso del blueprint:** 12 · **Depende de:** `E2-T3`, `E2-T5` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

HU-98 completa en el backend.

- `V4__platform_legal_acceptance.sql` — el DDL de `legal_acceptance` con su política de la sección «Modelo de datos» (copiar literal).
- `Businesses.java` (editar) — `void recordLegalAcceptance(UUID userId, String document, String version, Instant acceptedAt, String ip)` (`MANDATORY`, negocio de `TenantContext`; `document` ∈ `TERMS`, `DPA`).
- `SignupService.java` — `@Service`:
  - `void signup(SignupCommand c, String ip)` — orden fijo: `rateLimiter.tryConsume(SIGNUP, ip)` falso → `RATE_LIMITED`; `captchaVerifier.verify(c.captchaToken(), ip)` falso → `CAPTCHA_FAILED`; correo existente → `ACCOUNT_EXISTS`; en una transacción: `IdentityStore.createAccount(email, password, ownerName, false)`, token `VERIFY_EMAIL` (24 h) con `payload` JSON `{businessName, vertical, timezone, termsVersion, dpaVersion, acceptedAt, ip}` (versiones de `app.legal.*`), y el correo con asunto exacto `Confirma tu correo` y enlace `{app.base-url}/verificar-correo?token=<token>`.
  - `IssuedSession verify(String token)` — genera `businessId = UUID.randomUUID()` y ejecuta **una sola transacción** dentro de `TenantContext.run(businessId, null, …)`: `lockUsableToken("VERIFY_EMAIL", …)` (si falta → `INVALID_TOKEN`), `markEmailVerified`, `Businesses.provision(businessId, …)` (sede principal, ajustes del vertical, suscripción `TRIAL` a 30 días), `addMembership(userId, businessId, "OWNER")`, `recordLegalAcceptance` para `TERMS` y `DPA`, `markTokenUsed`. Después del commit, `TokenService.issueSession(userId, businessId)`.
  - `BusinessOption createAnotherBusiness(UUID userId, CreateBusinessCommand c, String ip)` — exige `acceptTerms` y `acceptDpa`; misma transacción de alta (sin token ni verificación) dentro de `TenantContext.run(nuevoId, userId, …)`.
- `SignupController.java` — `@RestController @Tag(name = "signup")`: `signup` (`POST /api/v1/signup` → 202 `{"status":"VERIFICATION_SENT"}`), `verifySignup` (`POST /api/v1/signup/verify` → 200 `SessionResponse` + cookie `rt` igual que el login), `createBusiness` (`POST /api/v1/businesses` → 201 `BusinessOption`, autenticado), `publicConfig` (`GET /api/v1/public/config` → `{captchaSiteKey, termsVersion, dpaVersion}`). `SignupRequest`: `businessName` 2–120, `vertical` = `PELUQUERIA`, `timezone` válida, `ownerName` 2–120, `email` válido ≤ 254, `password` 10–128, `acceptTerms` y `acceptDpa` `@AssertTrue`, `captchaToken` no vacío.
- `SignupIT.java` — extiende `IntegrationTest`; `@BeforeEach` limpia `RecordingEmailSender` y `RateLimiter`. Incluye una clase `@Nested` con `@MockitoBean Businesses.VerticalSeed` (de `org.springframework.test.context.bean.override.mockito`) cuyo `vertical()` devuelve `PELUQUERIA` y cuyo `defaults()` lanza `IllegalStateException`, para el caso de reversión.

**Files**
- `backend/src/main/resources/db/migration/V4__platform_legal_acceptance.sql` — nuevo
- `backend/src/main/java/com/plataformacitas/platform/Businesses.java` — editar: recordLegalAcceptance
- `backend/src/main/java/com/plataformacitas/identity/internal/SignupService.java` — nuevo
- `backend/src/main/java/com/plataformacitas/identity/internal/SignupController.java` — nuevo
- `backend/src/test/java/com/plataformacitas/identity/SignupIT.java` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `POST /api/v1/signup` receives valid data with `acceptTerms` and `acceptDpa` true and captcha token `XXXX.DUMMY.TOKEN.XXXX` **THE SYSTEM SHALL** answer 202 with `{"status":"VERIFICATION_SENT"}`, create an unverified `user_account` and send one email with subject `Confirma tu correo` containing a link with `/verificar-correo?token=`.
2. **WHEN** the email already has an account **THE SYSTEM SHALL** answer 409 `ACCOUNT_EXISTS` without creating a second account, **WHEN** the captcha token is rejected it SHALL answer 400 `CAPTCHA_FAILED`, and **WHEN** a 6th signup arrives from one IP within an hour it SHALL answer 429 `RATE_LIMITED`.
3. **WHEN** `POST /api/v1/signup/verify` receives the emailed token **THE SYSTEM SHALL**, in one transaction, mark the email verified and create the business, its main location, its settings, an OWNER membership, a `TRIAL` subscription ending 30 days later and `legal_acceptance` rows `TERMS` and `DPA` with versions `borrador-2026-10`, and answer 200 with a session for the new business.
4. **WHEN** the vertical seed throws during verification **THE SYSTEM SHALL** answer 500 `INTERNAL_ERROR` and leave no business, membership or legal acceptance, the account unverified and the token unused.
5. **WHEN** an authenticated account calls `POST /api/v1/businesses` **THE SYSTEM SHALL** answer 201 and create another business with that same account as OWNER, and `GET /api/v1/auth/me` SHALL list it among `businesses`.
6. **WHEN** a verification token is used a second time **THE SYSTEM SHALL** answer 400 `INVALID_TOKEN`, and `TenantIsolationIT` SHALL include table `legal_acceptance` in its per-table checks.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd backend && ./mvnw -B -ntp test -Dtest='SignupIT,TenantIsolationIT')   # expect: exit 0, Failures: 0, Errors: 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0, BUILD SUCCESS — los pasos anteriores siguen verdes
```

**Checkpoint**

```bash
git add -A && git commit -m "E2-T6: Registro autoservicio y crear otro negocio"
git tag step-12-registro
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-12-registro` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E2-T7` — Contrato OpenAPI, cliente generado y deriva

**Paso del blueprint:** 13 · **Depende de:** `E2-T6` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `backend/src/main/java/com/plataformacitas/shared/openapi/OpenApiConfig.java` — nuevo
- `backend/src/test/java/com/plataformacitas/OpenApiExportIT.java` — nuevo
- `frontend/ng-openapi-gen.json` — nuevo
- `scripts/check-api-drift.sh` — nuevo
- `frontend/openapi/openapi.json` + `frontend/src/app/api/**` — generados por los comandos de este paso (no se escriben a mano)

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `OpenApiExportIT` runs with `-Dopenapi.export=true` **THE SYSTEM SHALL** write the `GET /v3/api-docs` document with keys sorted and LF line endings to `frontend/openapi/openapi.json`, declaring the single server `/`.
2. **WHEN** `npx ng-openapi-gen -c ng-openapi-gen.json` runs in `frontend/` **THE SYSTEM SHALL** generate `src/app/api/api.ts`, `src/app/api/models.ts`, `src/app/api/fn/auth/login.ts` and `src/app/api/fn/signup/signup.ts`.
3. **WHEN** `bash scripts/check-api-drift.sh` runs on a tree whose committed contract and client match the backend **THE SYSTEM SHALL** exit 0.
4. **WHEN** the committed client differs from what the current backend generates **THE SYSTEM SHALL** make `scripts/check-api-drift.sh` exit with code 1, keeping code 2 for usage or tool errors.
5. **WHEN** `npx ng build` runs in `frontend/` with the generated client present **THE SYSTEM SHALL** exit 0.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

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
git add -A && git commit -m "E2-T7: Contrato OpenAPI, cliente generado y deriva"
git tag step-13-contrato-api
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-13-contrato-api` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

---

## Epic acceptance

La épica está hecha cuando todas sus tareas están `done` **y**:

1. **WHEN** `(cd backend && ./mvnw -B -ntp verify)` runs **THE SYSTEM SHALL** exit 0 with `TokenServiceIT`, `AuthFlowIT`, `TenantIsolationIT`, `SecurityHardeningIT`, `PasswordResetIT`, `SignupIT` and every test of epic 01 passing.
2. **WHEN** `bash scripts/smoke-api.sh --expect-demo --expect-mail` runs against the dev services **THE SYSTEM SHALL** exit 0, proving demo login with business selection and email delivery through Mailpit.
3. **WHEN** `bash scripts/check-api-drift.sh` runs **THE SYSTEM SHALL** exit 0.

```bash
(cd backend && ./mvnw -B -ntp verify)
(cd backend && ./mvnw -B -ntp -DskipTests package) && bash scripts/smoke-api.sh --expect-demo --expect-mail
bash scripts/check-api-drift.sh
```

## Pitfalls

- **El token preauth pasa como autenticado** — por eso las rutas normales exigen la autoridad `TENANT`, no `authenticated()`. Un `anyRequest().authenticated()` dejaría que un token de 2 minutos sin negocio use toda la API.
- **Leer la membresía con `@TenantId`** — `membership` no es entidad JPA a propósito: el login necesita ver las membresías de la cuenta en todos sus negocios y el filtro de Hibernate lo impediría. Se lee con `JdbcClient` bajo `app.user_id`.
- **`verify` del registro fuera de `TenantContext.run`** — si el negocio no se fija antes de abrir la transacción, la inserción de la sede principal falla con `42501`.
- **Turnstile en pruebas** — nunca llames a Cloudflare desde una prueba: usa el proveedor `fake` o `MockRestServiceServer`.
- **Deriva por saltos de línea** — springdoc imprime con el separador del sistema; `OpenApiExportIT` normaliza a `\n` antes de escribir, o Windows y Linux nunca coincidirían.
- **Puerto aleatorio en el contrato** — sin el servidor fijo `/` de `OpenApiConfig`, `openapi.json` cambia en cada corrida y el control de deriva falla siempre.

## Before moving on

- [ ] Cada tarea de esta épica está `done` en `tasks.json` — ninguna queda `in_progress`.
- [ ] Pasaron todos los comandos `verify` de cada tarea, no solo el primero.
- [ ] No se editó ningún comando `verify` ni se saltó uno porque faltara un archivo.
- [ ] **Cada tarea tiene su tag `checkpoint`** (`step-07-tokens` … `step-13-contrato-api`).
- [ ] El gate pasa limpio desde la raíz del proyecto.
- [ ] Cada contrato «Produced» existe con la firma indicada.
- [ ] No se modificó ningún archivo fuera del subárbol.
- [ ] `.env.example` ya contiene todas las variables que usa esta épica (`JWT_SECRET`, `DEV_SEED_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `SMTP_*`, `EMAIL_FROM`, `APP_BASE_URL`, `CAPTCHA_PROVIDER`, `TURNSTILE_*`, `SIGNUP_LIMIT_PER_HOUR`, `LEGAL_*`); si agregaste otra, agrégala allí.
- [ ] Un commit por tarea, con el prefijo del id de la tarea, seguido de su tag.
