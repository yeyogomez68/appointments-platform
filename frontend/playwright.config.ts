import { defineConfig, devices } from '@playwright/test';

// Pruebas E2E (blueprint §13). Se ejecutan con: bash scripts/run-e2e.sh (desde la raíz del repo).
// Playwright arranca la API (jar ya compilado, perfil dev, lee ../.env) y ng serve (proxy /api -> :8080).
// Mailpit (http://localhost:8025) y PostgreSQL los levanta docker compose antes, en run-e2e.sh.
const isCI = !!process.env['CI'];

export default defineConfig({
  testDir: './e2e',
  testIgnore: ['**/blueprints/**', '**/node_modules/**'],
  fullyParallel: false,
  workers: 1,
  retries: isCI ? 1 : 0,
  timeout: 60_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:4200',
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: 'java -jar target/plataforma-citas-api.jar',
      cwd: '../backend',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: !isCI,
      timeout: 180_000,
    },
    {
      command: 'npx ng serve --port 4200',
      url: 'http://localhost:4200',
      reuseExistingServer: !isCI,
      timeout: 180_000,
    },
  ],
});
