# ADR 0001 — Decisiones de la fundación multi-negocio

- **Estado:** aceptada (2026-10-12)
- **Origen:** entrevista de arquitectura y `blueprints/plataforma-citas/blueprint.md` §20.3

Este registro agrupa las primeras decisiones de arquitectura. Cada decisión nueva va en su propio archivo `docs/adr/NNNN-titulo.md`.

| # | Decisión | Alternativa descartada | Por qué | Se revisa si |
|---|---|---|---|---|
| 1 | Java 25 + Spring Boot 4.1 en un monolito modular con Spring Modulith | Microservicios; NestJS | Un despliegue, límites verificados en CI, ecosistema maduro para seguridad y datos | Un módulo debe escalar aparte o hay varios equipos |
| 2 | Hexagonal completa solo en `agenda` y `cash`; módulos ligeros en el resto | Hexagonal en todo | La lógica de negocio fuerte está en agenda y caja; el resto es CRUD | Un módulo ligero acumula reglas de negocio |
| 3 | Multi-negocio por fila con `@TenantId` **y** RLS de PostgreSQL forzada, `set_config` local a la transacción | Solo filtro en código; una base por negocio | Dos barreras: un error de código no cruza negocios | Un cliente exige aislamiento físico o hay cientos de negocios grandes |
| 4 | Usuarios `app_owner` (Flyway) y `app_runtime` (API, sin `BYPASSRLS`) | Un solo usuario dueño | Un dueño de tablas no queda sujeto a RLS sin `FORCE`; separarlos hace la prueba honesta | Nunca |
| 5 | Cuenta global + membresía por negocio con rol | Un usuario por negocio | Una persona puede ser dueña de un negocio y empleada de otro con el mismo correo | Nunca |
| 6 | JWT HS256 de 15 min firmado con `NimbusJwtEncoder` + refresh opaco rotativo en cookie `HttpOnly; SameSite=Strict` | Sesión de servidor; JWT RS256 | Una sola API emite y valida; el refresh se revoca en una escritura y detecta reutilización | Otro servicio necesita validar tokens sin el secreto |
| 7 | Cliente TypeScript generado con `ng-openapi-gen` | `openapi-generator` `typescript-angular` (lo proponían los documentos) | Nativo de Angular, funciones por operación, sin dependencia de Java en el frontend | El generador deja de mantenerse |
| 8 | Paquete raíz `com.plataformacitas` | `com.tuorg.citas` (marcador de los documentos) | Nombre real y neutral al vertical | — |
| 9 | Reportes con SQL nativo (`JdbcClient`) | jOOQ | jOOQ OSS + PostgreSQL 18 no verificable | jOOQ soporta oficialmente la versión de PostgreSQL en uso |
