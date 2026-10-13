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
