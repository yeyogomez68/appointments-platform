#!/usr/bin/env bash
# Prueba de humo de la pila de staging/producción (deploy/compose.prod.yml) en esta máquina.
# Requiere las imágenes locales construidas en el paso 18:
#   plataforma-citas-api:local  (docker build -f deploy/api.Dockerfile -t plataforma-citas-api:local backend/target)
#   plataforma-citas-web:local  (docker build -f deploy/web.Dockerfile -t plataforma-citas-web:local frontend/dist/frontend/browser)
# Usa secretos generados al vuelo, el proyecto compose "citas-smoke" (volúmenes propios) y lo destruye al terminar.
# Salida: 0 = pila sana · 1 = una verificación falló · 2 = falta un prerrequisito.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for image in plataforma-citas-api:local plataforma-citas-web:local; do
  docker image inspect "$image" >/dev/null 2>&1 || { echo "falta la imagen $image" >&2; exit 2; }
done

mkdir -p .tmp
ENV_FILE=.tmp/smoke-prod.env
COMPOSE=(docker compose -p citas-smoke -f deploy/compose.prod.yml --env-file "$ENV_FILE")
URL=http://localhost:8088

hex() { node -e "process.stdout.write(require('crypto').randomBytes($1).toString('hex'))"; }
HASH=$(docker run --rm caddy:2.11.7-alpine caddy hash-password --plaintext smoke-pass | tr -d '\r\n')

cat > "$ENV_FILE" <<EOF
API_IMAGE=plataforma-citas-api:local
WEB_IMAGE=plataforma-citas-web:local
SITE_ADDRESS=:80
CADDY_HTTP_PORT=8088
CADDY_HTTPS_PORT=8443
DB_SUPERUSER_PASSWORD=$(hex 24)
DB_OWNER_PASSWORD=$(hex 24)
DB_RUNTIME_PASSWORD=$(hex 24)
JWT_SECRET=$(hex 32)
APP_BASE_URL=$URL
CORS_ALLOWED_ORIGINS=$URL
EMAIL_FROM=no-responder@plataforma-citas.local
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS=false
CAPTCHA_PROVIDER=turnstile
TURNSTILE_SITE_KEY=1x00000000000000000000AA
TURNSTILE_SECRET=1x0000000000000000000000000000000AA
SENTRY_DSN=
APP_ENV=smoke
APP_SEED_DEMO=false
DEV_SEED_PASSWORD=
BASIC_AUTH_USER=smoke
BASIC_AUTH_HASH='$HASH'
EOF

cleanup() { "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true; }
trap cleanup EXIT

"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up -d --wait

test "$(curl -s -o /dev/null -w '%{http_code}' "$URL/")" = 401 || { echo "/ sin credenciales no respondió 401" >&2; exit 1; }
test "$(curl -s -o /dev/null -w '%{http_code}' -u smoke:smoke-pass "$URL/")" = 200 || { echo "/ con credenciales no respondió 200" >&2; exit 1; }
curl -s -u smoke:smoke-pass "$URL/" | grep -q '<app-root' || { echo "/ no sirve el index.html de Angular" >&2; exit 1; }
test "$(curl -s -o .tmp/smoke-config.json -w '%{http_code}' "$URL/api/v1/public/config")" = 200 || { echo "/api/v1/public/config no respondió 200" >&2; exit 1; }
node -e 'const j=JSON.parse(require("fs").readFileSync(".tmp/smoke-config.json","utf8")); process.exit(j.captchaSiteKey==="1x00000000000000000000AA"?0:1)' \
  || { echo "captchaSiteKey inesperado" >&2; exit 1; }
"${COMPOSE[@]}" logs --no-color --no-log-prefix api > .tmp/smoke-api-prod.log
node -e 'const lines=require("fs").readFileSync(".tmp/smoke-api-prod.log","utf8").split(/\r?\n/).filter(l=>l.startsWith("{")); const ok=lines.some(l=>{try{return "@timestamp" in JSON.parse(l)}catch{return false}}); process.exit(ok?0:1)' \
  || { echo "la API en perfil prod no escribe logs ECS JSON" >&2; exit 1; }

echo "SMOKE PROD STACK OK"
