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
