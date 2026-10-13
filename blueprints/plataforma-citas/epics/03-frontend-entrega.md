# Épica 03: Frontend Angular, E2E y entrega

> Al terminar existe la aplicación Angular con el sistema de diseño propio, el shell por rol con selector de negocio, las pantallas de ingreso, registro y recuperación, la prueba E2E del registro con Mailpit, el pipeline de CI y las imágenes de staging con su pila Caddy probada localmente.

| | |
|---|---|
| **Epic id** | `03-frontend-entrega` |
| **Tasks** | `E3-T1` … `E3-T5` (pasos 14–18 del blueprint) |
| **Depends on** | `02-identidad-registro` (desde `E3-T2`; `E3-T1` solo necesita el bootstrap) |
| **Unlocks** | el bundle 2 (catálogo, empleados, agenda), que se genera con `/architect-brownfield` sobre el repo construido |
| **Parallel with** | `E3-T1` puede correr junto a la épica 02; el resto, nada |

No necesitas ningún otro archivo para completar esta épica. Todo lo de abajo se repite aquí a propósito.

---

## Stack

Angular 22 (standalone, signals, sin zone.js, rutas perezosas, Vitest como corredor de `ng test`) · Angular Material 22 + CDK con tema M3 · Inter Variable (`@fontsource-variable/inter`) · cliente generado con `ng-openapi-gen` en `src/app/api` · Playwright para E2E · npm · Node 24. Backend: API Spring Boot de las épicas 01–02. Despliegue: Docker, Caddy, GHCR, GitHub Actions.
Versiones en `frontend/package-lock.json` y en `deploy/*`: léelas allí, nunca las adivines.

| Tarea | Comando (desde la raíz del repo) |
|---|---|
| Instalar dependencias del frontend | `(cd frontend && npm ci)` |
| Servidor de desarrollo (proxy `/api` → :8080) | `(cd frontend && npx ng serve)` — http://localhost:4200 |
| Build | `(cd frontend && npx ng build)` → `frontend/dist/frontend/browser/` |
| Pruebas unitarias (Vitest) | `(cd frontend && npx ng test --watch=false)` |
| Contraste de tokens | `node scripts/check-contrast.mjs` |
| Regenerar cliente / deriva | `(cd frontend && npx ng-openapi-gen -c ng-openapi-gen.json)` · `bash scripts/check-api-drift.sh` |
| API para desarrollo | `docker compose up -d --wait db mailpit` y `(cd backend && ./mvnw spring-boot:run)` |
| Humo del proxy (`ng serve` → API) | `bash scripts/smoke-web-proxy.sh` |
| E2E | `bash scripts/run-e2e.sh` |
| Pila de staging local | `bash scripts/smoke-prod-stack.sh` (requiere las imágenes `:local` del paso 18) |

**Gate:** `(cd frontend && npx ng build) && (cd frontend && npx ng test --watch=false)` pasa antes de marcar cualquier tarea como hecha.

`frontend/proxy.conf.json`, `frontend/playwright.config.ts`, `scripts/smoke-web-proxy.sh`, `scripts/run-e2e.sh`, `scripts/smoke-prod-stack.sh`, `deploy/compose.prod.yml` y `deploy/Caddyfile` llegaron con `workspace/` y ya están en su lugar: no los reescribes. Mailpit y PostgreSQL los levanta `docker-compose.yml`; nunca sustituyes Mailpit por un correo falso en la E2E.

## Directory subtree

```
frontend/                                  # creado por ng new en el bootstrap
  angular.json                             # existe (scaffold) — se edita en E3-T1
  proxy.conf.json, playwright.config.ts    # existen (workspace/) — solo lectura
  src/
    index.html                             # existe (scaffold) — se edita en E3-T1
    styles.scss                            # existe (scaffold) — se reemplaza en E3-T1
    styles/_tokens.scss                    # NUEVO (E3-T1)
    app/
      api/**                               # existe (generado en el paso 13) — solo lectura
      app.config.ts                        # existe (scaffold) — se edita en E3-T2
      app.routes.ts, app.html, app.spec.ts # existen (scaffold) — se reemplazan en E3-T3
      app.ts                               # existe (scaffold) — no se toca
      core/auth.ts                         # NUEVO (E3-T2)
      core/ui-text.ts                      # NUEVO (E3-T2)
      core/core.spec.ts                    # NUEVO (E3-T2)
      layout/shell.ts                      # NUEVO (E3-T2)
      features/auth/auth-pages.ts          # NUEVO (E3-T3)
      features/auth/auth-pages.spec.ts     # NUEVO (E3-T3)
  e2e/registro.spec.ts                     # NUEVO (E3-T4)
scripts/check-contrast.mjs                 # NUEVO (E3-T1)
.github/workflows/ci.yml                   # NUEVO (E3-T4)
.github/workflows/deploy.yml               # NUEVO (E3-T5)
deploy/api.Dockerfile                      # NUEVO (E3-T5)
deploy/web.Dockerfile                      # NUEVO (E3-T5)
deploy/compose.prod.yml, deploy/Caddyfile  # existen (workspace/) — solo lectura
```

Todo lo que esté fuera de este árbol queda fuera de alcance. Si una tarea parece exigir otro archivo, detente y repórtalo.

## Data model touched here

Ninguna tabla nueva. El frontend consume, a través del cliente generado: `BusinessResponse` (`labels` incluido), `SessionResponse`, `LoginResponse`, `MeResponse`, `BusinessOption`, `PublicConfig` y los pedidos de registro y recuperación, con las formas de la tabla siguiente.

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

## Sistema de diseño

Paleta propia, moderna, modo claro y oscuro (no se toma `mapa-funcional.html` como referencia visual). Escala tipográfica 12/14/16/20/24/32 px; `font-variant-numeric: tabular-nums` para dinero y horas (`.money`, `.time`, `.tabular`). Radio 10 px en controles y 14 px en tarjetas; espacio base 4 px. Movimiento 150–200 ms `ease-out`, desactivado con `prefers-reduced-motion`. Tablas con densidad −2 en escritorio. Diseño desde móvil: funciona a 360 px y sin scroll horizontal a 375 px. Foco visible con `--c-primary`. Los componentes usan `var(--c-*)` y nunca hex sueltos.

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

## Contracts

**Consumed** — ya existe, no lo reconstruyas:

| De | Interfaz | Garantía |
|---|---|---|
| `02-identidad-registro` | `src/app/api` (`Api`, `provideApiConfiguration`, `models.ts`, `fn/auth/*`, `fn/signup/*`, `fn/business/*`) | tipado desde el contrato; romper un DTO rompe `ng build` |
| `02-identidad-registro` | API REST (tabla de endpoints) | cookie `rt` `HttpOnly; SameSite=Strict; Path=/api/v1/auth`; mismo origen vía proxy o Caddy |
| `workspace/` | `proxy.conf.json`, `playwright.config.ts`, `run-e2e.sh`, `compose.prod.yml`, `Caddyfile`, `smoke-prod-stack.sh` | ver sus comentarios de cabecera |

**Produced** — los bundles siguientes dependen exactamente de esto:

| Export | Firma | Usado por |
|---|---|---|
| `core/auth.ts` → `AuthStore` | `session()`, `login`, `selectBusiness`, `switchBusiness`, `acceptSession`, `restore`, `logout` | toda pantalla autenticada |
| `core/auth.ts` → `authGuard`, `roleGuard` | `CanActivateFn`; `roleGuard` lee `data.roles` | rutas de los bundles 2–7 |
| `core/ui-text.ts` → `messageFor`, `ERROR_MESSAGES`, `LabelsService` | `messageFor(error: unknown): string`; `labels()` | toda pantalla |
| `layout/shell.ts` → `Shell`, `MENU` | ítems `{ label, path, roles }` | los bundles agregan sus ítems |
| Imágenes | `ghcr.io/<owner>/plataforma-citas-api`, `ghcr.io/<owner>/plataforma-citas-web` | bundle 3 (producción) |

## Conventions that bite in this area

- **Ningún término del vertical escrito a mano en pantallas** («Empleado», «Cliente», «Servicio»): sale de `LabelsService`. CI lo verifica con `grep`.
- **Textos exactos de la tabla «Contrato de pantallas»**: la E2E los usa como selectores accesibles (`getByLabel`, `getByRole`).
- **Siempre a través del cliente generado** (`inject(Api).invoke(...)`); nada de `HttpClient` contra rutas escritas a mano.
- **Rutas perezosas** con `loadComponent` y `path: '<ruta>'` en comillas simples.
- **Tokens, no hex:** un color nuevo se agrega a `_tokens.scss` y a los pares de `check-contrast.mjs`.
- **El access token vive solo en memoria** (`AuthStore`); nunca en `localStorage`. La sesión se recupera al cargar con la cookie `rt` (`restore()`).

Reglas completas: `CLAUDE.md`. Reglas por área: `.claude/rules/frontend.md`, `.claude/rules/despliegue.md`.

---

## Tasks

En el mismo orden que `tasks.json`. Ese orden es el de construcción: de arriba abajo, sin reordenar por prioridad.

### `E3-T1` — Sistema de diseño: tema Material M3, tokens y contraste

**Paso del blueprint:** 14 · **Depende de:** nada · **Prioridad:** p1 — metadato para recortes de alcance, no un orden de ejecución

Aplica el sistema de diseño propio (tokens literales en la sección «Sistema de diseño») sobre el scaffold de Angular que creó el bootstrap. Este paso no depende del backend.

- `frontend/src/styles/_tokens.scss` — exactamente el contenido de la sección «Archivo de tokens».
- `frontend/src/styles.scss` — exactamente el contenido de la sección «Hoja global».
- `frontend/angular.json` — en `projects.frontend.architect.build.options.styles` deja `["@fontsource-variable/inter/index.css", "src/styles.scss"]` (en ese orden); en `build.configurations.production` agrega `"optimization": { "scripts": true, "styles": { "minify": true, "inlineCritical": false }, "fonts": true }` (el CSS crítico en línea usa un manejador `onload` que la CSP de Caddy bloquearía); en `serve.options` agrega `"proxyConfig": "proxy.conf.json"` (el archivo ya existe, llegó con `workspace/`).
- `frontend/src/index.html` — `<html lang="es-CO">`, `<title>Plataforma de citas</title>`, `<meta name="theme-color" content="#5B4FE9">`; conserva `<app-root></app-root>`.
- `scripts/check-contrast.mjs` — exactamente el contenido de la sección «Script de contraste». Salida 0 = todos los pares cumplen, 1 = algún par falla, 2 = falta un token o el archivo.

**Files**
- `frontend/src/styles/_tokens.scss` — nuevo
- `frontend/src/styles.scss` — editar: reemplazo completo
- `frontend/angular.json` — editar: estilos, inlineCritical y proxy
- `frontend/src/index.html` — editar: lang, título y theme-color
- `scripts/check-contrast.mjs` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `node scripts/check-contrast.mjs` runs **THE SYSTEM SHALL** read `frontend/src/styles/_tokens.scss` and exit 0 only if every listed text pair reaches 4.5:1 and every listed UI pair reaches 3:1, in both the light and the dark token sets.
2. **WHEN** `npx ng build` runs in `frontend/` **THE SYSTEM SHALL** exit 0 with the Angular Material M3 theme (`mat.theme` with `mat.$violet-palette`, `mat.$cyan-palette` and `Inter Variable`) and the `--c-*` tokens compiled into the global stylesheet.
3. **WHEN** `frontend/src/index.html` is read **THE SYSTEM SHALL** contain `<html lang="es-CO">`.
4. **WHEN** `frontend/angular.json` is parsed **THE SYSTEM SHALL** list `@fontsource-variable/inter/index.css` before `src/styles.scss` in the build styles, set `inlineCritical` to false in the production configuration and set `proxyConfig` to `proxy.conf.json` for `serve`.
5. **WHEN** `frontend/src/styles.scss` is read **THE SYSTEM SHALL** contain a `@media (prefers-reduced-motion: reduce)` block and a `:focus-visible` outline that uses `var(--c-primary)`.
6. **WHEN** `npx ng test --watch=false` runs in `frontend/` **THE SYSTEM SHALL** exit 0.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

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
git add -A && git commit -m "E3-T1: Sistema de diseño: tema Material M3, tokens y contraste"
git tag step-14-diseno
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-14-diseno` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E3-T2` — Núcleo web: sesión, guardas, etiquetas y shell

**Paso del blueprint:** 15 · **Depende de:** `E2-T7`, `E3-T1` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `frontend/src/app/core/auth.ts` — nuevo
- `frontend/src/app/core/ui-text.ts` — nuevo
- `frontend/src/app/layout/shell.ts` — nuevo
- `frontend/src/app/app.config.ts` — editar: proveedores
- `frontend/src/app/core/core.spec.ts` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `messageFor` receives an `HttpErrorResponse` with status 409 and body code `SLOT_TAKEN` **THE SYSTEM SHALL** return `Ese horario acaba de ser tomado. Elige otro.`, and for an unknown code it SHALL return the message mapped to the HTTP status.
2. **WHEN** `authGuard` runs without a session **THE SYSTEM SHALL** return a `UrlTree` to `/ingresar?volver=<url>`, and **WHEN** `roleGuard` runs for a route with `data.roles = ['OWNER']` while the session role is `RECEPTION` **THE SYSTEM SHALL** return a `UrlTree` to `/inicio`.
3. **WHEN** an API call returns 401 and the refresh call succeeds **THE SYSTEM SHALL** retry the original request once with the new `Authorization: Bearer` token, and **WHEN** the refresh fails **THE SYSTEM SHALL** clear the session and navigate to `/ingresar`.
4. **WHEN** `AuthStore.switchBusiness(id)` succeeds **THE SYSTEM SHALL** replace the session business, reset the labels to `DEFAULT_LABELS` and reload them with `GET /api/v1/business`.
5. **WHEN** `Shell` renders for a session with role `RECEPTION` **THE SYSTEM SHALL** show menu item `Inicio` and hide `Mi negocio`, and for role `OWNER` with two businesses it SHALL show both items and the business selector.
6. **WHEN** `npx ng build` runs in `frontend/` **THE SYSTEM SHALL** exit 0 with `LOCALE_ID` `es-CO` and `DEFAULT_CURRENCY_CODE` `COP` provided in `src/app/app.config.ts`.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
(cd frontend && npx ng test --watch=false)   # expect: exit 0, todas las pruebas pasan (incluye core.spec.ts)
(cd frontend && npx ng build)   # expect: exit 0
grep -q "'es-CO'" frontend/src/app/app.config.ts && grep -q "'COP'" frontend/src/app/app.config.ts   # expect: exit 0
```

**Checkpoint**

```bash
git add -A && git commit -m "E3-T2: Núcleo web: sesión, guardas, etiquetas y shell"
git tag step-15-nucleo-web
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-15-nucleo-web` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E3-T3` — Pantallas de acceso y registro con rutas perezosas

**Paso del blueprint:** 16 · **Depende de:** `E3-T2` · **Prioridad:** p0 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `frontend/src/app/features/auth/auth-pages.ts` — nuevo
- `frontend/src/app/features/auth/auth-pages.spec.ts` — nuevo
- `frontend/src/app/app.routes.ts` — editar: reemplazo completo
- `frontend/src/app/app.html` — editar: reemplazo completo
- `frontend/src/app/app.spec.ts` — editar: reemplazo completo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** the login form receives 401 with code `INVALID_CREDENTIALS` **THE SYSTEM SHALL** show `Correo o contraseña incorrectos.` in an element with `role="alert"`.
2. **WHEN** the login call answers `BUSINESS_SELECTION_REQUIRED` **THE SYSTEM SHALL** navigate to `/elegir-negocio`, which lists each business as a button with its name.
3. **WHEN** the signup page renders **THE SYSTEM SHALL** keep the `Crear mi negocio` button disabled until the form is valid and both checkboxes `Acepto los términos de servicio` and `Acepto el acuerdo de encargo de datos` are checked, and with an empty `captchaSiteKey` it SHALL send captcha token `XXXX.DUMMY.TOKEN.XXXX`.
4. **WHEN** `/verificar-correo?token=…` loads **THE SYSTEM SHALL** call `POST /api/v1/signup/verify`, store the session and navigate to `/inicio`, and on 400 `INVALID_TOKEN` it SHALL show `El enlace no es válido o ya venció.` in an element with `role="alert"`.
5. **WHEN** `grep -rn "Empleado" frontend/src/app/features frontend/src/app/layout` runs **THE SYSTEM SHALL** find no match and exit with code 1, because vertical terms come from `LabelsService`.
6. **WHEN** `npx ng test --watch=false` and `npx ng build` run in `frontend/` **THE SYSTEM SHALL** exit 0, with `src/app/app.html` equal to `<router-outlet />` and the lazy routes `/ingresar`, `/elegir-negocio`, `/registro`, `/verificar-correo`, `/recuperar-contrasena`, `/restablecer-contrasena`, `/crear-negocio`, `/inicio` and `/mi-negocio` declared in `src/app/app.routes.ts`, and `bash scripts/smoke-web-proxy.sh` prints `SMOKE WEB PROXY OK`.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

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
git add -A && git commit -m "E3-T3: Pantallas de acceso y registro con rutas perezosas"
git tag step-16-pantallas-auth
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-16-pantallas-auth` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E3-T4` — E2E de registro con Mailpit y pipeline de CI

**Paso del blueprint:** 17 · **Depende de:** `E3-T3` · **Prioridad:** p1 — metadato para recortes de alcance, no un orden de ejecución

HU-95 (solo el flujo 1) y el CI de HU-01. `frontend/playwright.config.ts` y `scripts/run-e2e.sh` ya existen (llegaron con `workspace/`).

- `frontend/e2e/registro.spec.ts` — un `test` serial: correo único `e2e+<Date.now()>@example.test`, contraseña `Clave-E2E-segura-1`, negocio `Peluquería E2E <Date.now()>`. Pasos: `page.goto('/registro')`; llena los campos por `getByLabel` con los textos de la tabla «Contrato de pantallas»; marca ambas casillas; clic en `Crear mi negocio`; espera `getByRole('heading', { name: 'Revisa tu correo' })`. Lee el correo con el fixture `request`: `GET http://localhost:8025/api/v1/messages` hasta encontrar (con `expect.poll`, 15 s) un mensaje cuyo `To[].Address` sea el correo; `GET http://localhost:8025/api/v1/message/{ID}`; extrae el enlace del campo `Text` con `/\/verificar-correo\?token=([A-Za-z0-9_-]+)/`; `page.goto('/verificar-correo?token=' + token)`; espera URL `/inicio` y `h1` con el nombre del negocio. Cierra sesión con el botón `Cuenta` → `Cerrar sesión`; espera `/ingresar`; inicia sesión con el mismo correo y contraseña (botón `Ingresar`); espera `/inicio` y el mismo `h1`.
- `.github/workflows/ci.yml` — exactamente el contenido de la sección «Workflow de CI».

**Files**
- `frontend/e2e/registro.spec.ts` — nuevo
- `.github/workflows/ci.yml` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `bash scripts/run-e2e.sh` runs **THE SYSTEM SHALL** pass `e2e/registro.spec.ts`, in which a visitor registers a business at `/registro` and sees the heading `Revisa tu correo`.
2. **WHEN** that E2E test reads the verification email through the Mailpit API at `http://localhost:8025/api/v1/messages` and opens its `/verificar-correo?token=` link **THE SYSTEM SHALL** land on `/inicio` with the business name as the `h1`.
3. **WHEN** the same test signs out through `Cuenta` then `Cerrar sesión` and signs in again at `/ingresar` with the same credentials **THE SYSTEM SHALL** land on `/inicio` with the same `h1`.
4. **WHEN** `.github/workflows/ci.yml` is read **THE SYSTEM SHALL** define jobs `backend`, `frontend`, `api-contract` and `e2e` that run `./mvnw -B -ntp verify`, `npx ng build`, `npx ng test --watch=false`, `node scripts/check-contrast.mjs`, `bash scripts/check-api-drift.sh` and `bash scripts/run-e2e.sh` on `pull_request` and on pushes to `main`.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

```bash
bash scripts/run-e2e.sh   # expect: exit 0, 1 passed, 0 failed
for c in './mvnw -B -ntp verify' 'npx ng build' 'npx ng test --watch=false' 'node scripts/check-contrast.mjs' 'bash scripts/check-api-drift.sh' 'bash scripts/run-e2e.sh'; do grep -qF "$c" .github/workflows/ci.yml || exit 1; done   # expect: exit 0
for j in 'backend:' 'frontend:' 'api-contract:' 'e2e:'; do grep -qE "^  $j" .github/workflows/ci.yml || exit 1; done   # expect: exit 0
```

**Checkpoint**

```bash
git add -A && git commit -m "E3-T4: E2E de registro con Mailpit y pipeline de CI"
git tag step-17-e2e-ci
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-17-e2e-ci` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

### `E3-T5` — Imágenes, pila Caddy y workflow de despliegue

**Paso del blueprint:** 18 · **Depende de:** `E3-T3` · **Prioridad:** p1 — metadato para recortes de alcance, no un orden de ejecución

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

**Files**
- `deploy/api.Dockerfile` — nuevo
- `deploy/web.Dockerfile` — nuevo
- `.github/workflows/deploy.yml` — nuevo

**Acceptance**

Copiado del arreglo `acceptance` de esta tarea en `tasks.json`. Cada criterio lo decide un comando de abajo, en esta máquina, durante la construcción.

1. **WHEN** `docker build -f deploy/api.Dockerfile -t plataforma-citas-api:local backend/target` runs **THE SYSTEM SHALL** produce an image based on `eclipse-temurin:25-jre-alpine` whose configured user is `app`.
2. **WHEN** `docker build -f deploy/web.Dockerfile -t plataforma-citas-web:local frontend/dist/frontend/browser` runs **THE SYSTEM SHALL** produce a `caddy:2.11.7-alpine` image that contains `/srv/index.html`.
3. **WHEN** `bash scripts/smoke-prod-stack.sh` starts `deploy/compose.prod.yml` with those images **THE SYSTEM SHALL** bring every service up healthy, answer `/` with 401 without credentials and with 200 containing `<app-root` with basic auth, and answer `GET /api/v1/public/config` with 200 and `captchaSiteKey` = `1x00000000000000000000AA`.
4. **WHEN** the API container runs with profile `prod` **THE SYSTEM SHALL** write ECS JSON log lines that contain the field `@timestamp`, as asserted by `scripts/smoke-prod-stack.sh`.
5. **WHEN** `.github/workflows/deploy.yml` is read **THE SYSTEM SHALL** be triggered only by `workflow_dispatch`, build with `deploy/api.Dockerfile` and `deploy/web.Dockerfile`, push `plataforma-citas-api` and `plataforma-citas-web` to `ghcr.io`, and run `docker compose` `pull` and `up -d --wait` over SSH.

**Verify** — cada comando, en orden, desde la raíz del proyecto. Cada uno sale con 0 cuando la tarea está bien; la tarea está hecha cuando el último sale con 0.

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
git add -A && git commit -m "E3-T5: Imágenes, pila Caddy y workflow de despliegue"
git tag step-18-despliegue
```

Se ejecuta después de que el último comando de Verify sale con 0 y antes de empezar la siguiente tarea. Si la siguiente tarea sale mal, una persona decide volver a este punto con `git reset --hard step-18-despliegue` (acción humana: no está en el allowlist de `.claude/settings.json` y el agente no la ejecuta por su cuenta).

---

## Epic acceptance

La épica está hecha cuando todas sus tareas están `done` **y**:

1. **WHEN** `bash scripts/run-e2e.sh` runs **THE SYSTEM SHALL** pass the registration flow from `/registro` through Mailpit verification to a fresh login, with 0 failed tests.
2. **WHEN** `bash scripts/smoke-prod-stack.sh` runs with the locally built images **THE SYSTEM SHALL** exit 0, proving the staging stack serves the SPA behind basic auth and proxies `/api/*`.

```bash
(cd frontend && npx ng build) && (cd frontend && npx ng test --watch=false)
node scripts/check-contrast.mjs
bash scripts/run-e2e.sh
bash scripts/smoke-prod-stack.sh
```

## Pitfalls

- **El spec del scaffold** busca un `h1` «Hello, frontend»; al dejar `app.html` en `<router-outlet />` hay que reemplazar `app.spec.ts` en la misma tarea o `ng test` falla.
- **CSS crítico en línea** — Angular lo inserta con un `onload` que la CSP de Caddy bloquea y la página quedaría sin estilos en staging; por eso `inlineCritical: false`.
- **Turnstile en desarrollo y E2E** — con `captchaSiteKey` vacío no se carga el widget (no hay red en las pruebas) y se envía el token de prueba; el backend en perfil `dev` usa el verificador falso.
- **`withCredentials` innecesario** — dev (proxy) y staging (Caddy) son mismo origen; las cookies viajan solas.
- **Basic auth y `Authorization: Bearer`** — Caddy solo protege la SPA; `/api/*` queda sin basic auth para que el Bearer de la aplicación no choque con el de Caddy.
- **Imágenes con `docker build` desde artefactos ya compilados** — el contexto de la API es `backend/target` y el de la web `frontend/dist/frontend/browser`; si el build previo no corrió, el `COPY` falla.

## Before moving on

- [ ] Cada tarea de esta épica está `done` en `tasks.json` — ninguna queda `in_progress`.
- [ ] Pasaron todos los comandos `verify` de cada tarea, no solo el primero.
- [ ] No se editó ningún comando `verify` ni se saltó uno porque faltara un archivo.
- [ ] **Cada tarea tiene su tag `checkpoint`** (`step-14-diseno` … `step-18-despliegue`).
- [ ] El gate pasa limpio desde la raíz del proyecto.
- [ ] Cada contrato «Produced» existe con la firma indicada.
- [ ] No se modificó ningún archivo fuera del subárbol.
- [ ] `.env.example` ya trae las variables de staging (`API_IMAGE`, `WEB_IMAGE`, `SITE_ADDRESS`, `CADDY_*`, `BASIC_AUTH_*`); esta épica no agrega otras.
- [ ] Un commit por tarea, con el prefijo del id de la tarea, seguido de su tag.
