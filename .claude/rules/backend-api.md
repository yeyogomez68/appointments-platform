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
