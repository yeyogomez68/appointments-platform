#!/usr/bin/env bash
# Prueba de humo de la API: arranca el jar ya compilado contra los servicios de docker-compose.yml,
# verifica y lo detiene. No compila: antes corre (cd backend && ./mvnw -B -ntp -DskipTests package).
#
# Uso: bash scripts/smoke-api.sh [--expect-demo] [--expect-mail]
#   (sin flags)     GET /actuator/health responde 200 con status UP                         (paso 1+)
#   --expect-demo   owner@demo.local inicia sesión y recibe BUSINESS_SELECTION_REQUIRED con
#                   los dos negocios de demostración                                         (paso 9+)
#   --expect-mail   POST /api/v1/auth/password/forgot entrega en Mailpit un correo a
#                   owner@demo.local con asunto "Restablece tu contraseña"                  (paso 11+)
# Salida: 0 = todo bien · 1 = una verificación falló · 2 = uso incorrecto o falta un prerrequisito.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

EXPECT_DEMO=false
EXPECT_MAIL=false
for arg in "$@"; do
  case "$arg" in
    --expect-demo) EXPECT_DEMO=true ;;
    --expect-mail) EXPECT_MAIL=true ;;
    *) echo "argumento desconocido: $arg" >&2; exit 2 ;;
  esac
done

test -f .env || { echo "falta .env: corre node scripts/init-env.mjs" >&2; exit 2; }
set -a
# shellcheck disable=SC1091
. ./.env
set +a

JAR=backend/target/plataforma-citas-api.jar
test -f "$JAR" || { echo "falta $JAR: corre (cd backend && ./mvnw -B -ntp -DskipTests package)" >&2; exit 2; }

API=http://localhost:8080
MAILPIT=http://localhost:8025
mkdir -p .tmp

if curl -s -o /dev/null "$API/actuator/health"; then
  echo "ya hay un proceso escuchando en el puerto 8080; deténlo antes de la prueba de humo" >&2
  exit 2
fi

docker compose up -d --wait db mailpit

cd backend
java -jar target/plataforma-citas-api.jar > ../.tmp/smoke-api.log 2>&1 &
PID=$!
cd ..

stop_api() {
  if kill -0 "$PID" 2>/dev/null; then
    # Git Bash (Windows): matar el proceso nativo de java por su PID de Windows.
    if [ -r "/proc/$PID/winpid" ]; then
      taskkill //F //T //PID "$(cat "/proc/$PID/winpid")" >/dev/null 2>&1 || true
    fi
    kill "$PID" 2>/dev/null || true
  fi
  wait "$PID" 2>/dev/null || true
}
trap stop_api EXIT

code=000
for _ in $(seq 1 90); do
  code=$(curl -s -o .tmp/smoke-health.json -w '%{http_code}' "$API/actuator/health" || true)
  [ "$code" = 200 ] && break
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "la API terminó antes de estar lista; últimas líneas de .tmp/smoke-api.log:" >&2
    tail -n 60 .tmp/smoke-api.log >&2
    exit 1
  fi
  sleep 2
done
if [ "$code" != 200 ]; then
  echo "GET /actuator/health no respondió 200 (último código: $code)" >&2
  tail -n 60 .tmp/smoke-api.log >&2
  exit 1
fi
node -e 'const j=JSON.parse(require("fs").readFileSync(".tmp/smoke-health.json","utf8")); process.exit(j.status==="UP"?0:1)'
echo "health: UP"

if [ "$EXPECT_DEMO" = true ]; then
  test -n "${DEV_SEED_PASSWORD:-}" || { echo "DEV_SEED_PASSWORD vacío en .env" >&2; exit 2; }
  login_code=$(curl -s -o .tmp/smoke-login.json -w '%{http_code}' -X POST "$API/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"owner@demo.local\",\"password\":\"$DEV_SEED_PASSWORD\"}")
  test "$login_code" = 200 || { echo "login de owner@demo.local respondió $login_code" >&2; cat .tmp/smoke-login.json >&2; exit 1; }
  node -e 'const j=JSON.parse(require("fs").readFileSync(".tmp/smoke-login.json","utf8")); process.exit(j.status==="BUSINESS_SELECTION_REQUIRED" && Array.isArray(j.businesses) && j.businesses.length===2 ? 0 : 1)'
  echo "demo: BUSINESS_SELECTION_REQUIRED con los dos negocios de demostración"
fi

if [ "$EXPECT_MAIL" = true ]; then
  curl -s -o /dev/null -X DELETE "$MAILPIT/api/v1/messages"
  forgot_code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/v1/auth/password/forgot" \
    -H 'Content-Type: application/json' -d '{"email":"owner@demo.local"}')
  test "$forgot_code" = 202 || { echo "forgot respondió $forgot_code" >&2; exit 1; }
  found=false
  for _ in $(seq 1 15); do
    curl -s "$MAILPIT/api/v1/messages" > .tmp/smoke-mail.json
    if node -e 'const j=JSON.parse(require("fs").readFileSync(".tmp/smoke-mail.json","utf8")); const ok=(j.messages||[]).some(m=>m.Subject==="Restablece tu contraseña" && (m.To||[]).some(t=>t.Address==="owner@demo.local")); process.exit(ok?0:1)'; then
      found=true
      break
    fi
    sleep 1
  done
  test "$found" = true || { echo "Mailpit no recibió el correo de restablecimiento" >&2; exit 1; }
  echo "correo: Mailpit recibió 'Restablece tu contraseña' para owner@demo.local"
fi

echo "SMOKE API OK"
