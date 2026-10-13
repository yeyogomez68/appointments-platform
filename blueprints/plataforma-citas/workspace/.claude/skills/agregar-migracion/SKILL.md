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
