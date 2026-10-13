---
description: Docker, Caddy, compose de staging, CI/CD de GitHub Actions y servicios locales
paths:
  - "deploy/**"
  - ".github/**"
  - "docker-compose.yml"
  - "infra/**"
  - "scripts/**"
---

# Despliegue e infraestructura

- Imágenes con etiqueta exacta (`postgres:18-alpine`, `axllent/mailpit:v1.31.5`, `caddy:2.11.7-alpine`, `eclipse-temurin:25-jre-alpine`); cambiar una exige actualizar §11 del blueprint.
- La imagen de la API se construye desde `backend/target` (jar `plataforma-citas-api.jar`) y la web desde `frontend/dist/frontend/browser`. Solo la imagen de la API corre sin root (usuario `app`); la imagen web conserva el usuario por defecto de `caddy:2.11.7-alpine` para escuchar en 80/443.
- Staging y producción usan el mismo `deploy/compose.prod.yml`; solo cambia el `.env` del servidor, que nunca se versiona.
- Caddy publica solo `/api/*` hacia la API; `/actuator/*` nunca sale a internet. La SPA va detrás de autenticación básica mientras staging no sea público.
- Límites de memoria por contenedor y `-XX:MaxRAMPercentage=60` en la API (VPS de 4 GB).
- Los scripts de `scripts/` son bash portable (Git Bash en Windows, Linux en CI): sin `cp -n`, sin python, rutas entre comillas, `set -euo pipefail`, códigos de salida documentados en la cabecera.
- Todo comando de un guard sale con 0 en la segunda ejecución (bootstrap re-ejecutable).
- Acciones de GitHub fijadas por versión mayor (`actions/checkout@v7`, `actions/setup-java@v6`, `actions/setup-node@v7`, `actions/upload-artifact@v7`); secretos solo en el ambiente `staging` de GitHub.
- El despliegue es manual (`workflow_dispatch`); el VPS, el DNS y los secretos son puertas humanas.
