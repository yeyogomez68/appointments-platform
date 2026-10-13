#!/usr/bin/env bash
# Ejecuta las pruebas E2E de Playwright (frontend/e2e/) contra la API real y Mailpit.
# Playwright arranca la API (jar) y ng serve según frontend/playwright.config.ts.
# Uso: bash scripts/run-e2e.sh [argumentos de "npx playwright test"]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

test -f .env || node scripts/init-env.mjs
docker compose up -d --wait db mailpit
(cd backend && ./mvnw -B -ntp -q -DskipTests package)
cd frontend
npx playwright test "$@"
