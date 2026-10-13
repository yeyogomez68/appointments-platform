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
