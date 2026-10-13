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
