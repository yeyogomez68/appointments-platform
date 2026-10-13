#!/usr/bin/env bash
# Prueba de humo del proxy de desarrollo: arranca la API (jar ya compilado) y "ng serve",
# y comprueba que http://localhost:4200/api/v1/public/config llega a la API a través de
# frontend/proxy.conf.json. Detiene ambos procesos al terminar; cada espera tiene límite.
# Requiere: (cd backend && ./mvnw -B -ntp -DskipTests package) y frontend/node_modules instalado.
# Salida: 0 = el proxy responde · 1 = una verificación falló · 2 = falta un prerrequisito.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

[ "$#" -eq 0 ] || { echo "uso: bash scripts/smoke-web-proxy.sh" >&2; exit 2; }
test -f .env || { echo "falta .env: corre node scripts/init-env.mjs" >&2; exit 2; }
test -f backend/target/plataforma-citas-api.jar || { echo "falta backend/target/plataforma-citas-api.jar" >&2; exit 2; }
test -f frontend/node_modules/@angular/cli/bin/ng.js || { echo "falta frontend/node_modules: corre (cd frontend && npm ci)" >&2; exit 2; }
for port in 8080 4200; do
  if curl -s -o /dev/null "http://localhost:$port/"; then
    echo "ya hay un proceso escuchando en el puerto $port; deténlo antes de la prueba" >&2
    exit 2
  fi
done

docker compose up -d --wait db mailpit
mkdir -p .tmp

cd backend
java -jar target/plataforma-citas-api.jar > ../.tmp/smoke-web-api.log 2>&1 &
API_PID=$!
cd ../frontend
node node_modules/@angular/cli/bin/ng.js serve --port 4200 > ../.tmp/smoke-web-ng.log 2>&1 &
NG_PID=$!
cd ..

stop_pid() {
  local pid="$1"
  if kill -0 "$pid" 2>/dev/null; then
    # Git Bash (Windows): terminar el árbol del proceso nativo por su PID de Windows.
    if [ -r "/proc/$pid/winpid" ]; then
      taskkill //F //T //PID "$(cat "/proc/$pid/winpid")" >/dev/null 2>&1 || true
    fi
    kill "$pid" 2>/dev/null || true
    for _ in 1 2 3 4 5; do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    kill -9 "$pid" 2>/dev/null || true
  fi
  wait "$pid" 2>/dev/null || true
}
cleanup() { stop_pid "$NG_PID"; stop_pid "$API_PID"; }
trap cleanup EXIT

code=000
for _ in $(seq 1 120); do
  code=$(curl -s -o .tmp/smoke-web-config.json -w '%{http_code}' http://localhost:4200/api/v1/public/config || true)
  [ "$code" = 200 ] && break
  kill -0 "$API_PID" 2>/dev/null || { echo "la API terminó; ver .tmp/smoke-web-api.log" >&2; exit 1; }
  kill -0 "$NG_PID" 2>/dev/null || { echo "ng serve terminó; ver .tmp/smoke-web-ng.log" >&2; exit 1; }
  sleep 2
done
test "$code" = 200 || { echo "GET http://localhost:4200/api/v1/public/config no respondió 200 (último: $code)" >&2; exit 1; }
node -e 'const j=JSON.parse(require("fs").readFileSync(".tmp/smoke-web-config.json","utf8")); process.exit(typeof j.captchaSiteKey==="string"?0:1)' \
  || { echo "la respuesta del proxy no es la configuración pública de la API" >&2; exit 1; }

echo "SMOKE WEB PROXY OK"
