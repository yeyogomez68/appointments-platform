---
name: verificar-todo
description: Correr el gate completo del proyecto antes de cerrar una tarea, abrir un PR o desplegar. Úsala cuando digas "verifica todo", "corre el gate", "¿está listo?", "antes del PR" o "antes de desplegar".
---

# Verificar todo

## Cuándo usarla
- Antes de marcar una tarea como `done`, antes de un PR y antes de lanzar el workflow de despliegue.

## Pasos
1. Docker Desktop en marcha y `.env` presente (`node scripts/init-env.mjs` si falta).
2. Corre los comandos de abajo en orden; detente en el primero que falle y arréglalo en el código, nunca en el comando.

## Verify
```bash
docker compose up -d --wait db mailpit   # expect: exit 0
(cd backend && ./mvnw -B -ntp verify)   # expect: exit 0
(cd frontend && npx ng build)   # expect: exit 0
(cd frontend && npx ng test --watch=false)   # expect: exit 0
node scripts/check-contrast.mjs   # expect: exit 0
bash scripts/check-api-drift.sh   # expect: exit 0
rc=0; grep -rn "Empleado" frontend/src/app/features frontend/src/app/layout || rc=$?; test "$rc" -eq 1   # expect: exit 0
(cd backend && ./mvnw -B -ntp -DskipTests package)   # expect: exit 0
bash scripts/smoke-api.sh --expect-demo --expect-mail   # expect: exit 0
bash scripts/smoke-web-proxy.sh   # expect: exit 0
bash scripts/run-e2e.sh   # expect: exit 0
```

## No hagas
- Saltarte un comando porque "no tocaste esa parte": el gate existe para lo que no tocaste.
- Editar un comando `verify` de `tasks.json` para que pase.
