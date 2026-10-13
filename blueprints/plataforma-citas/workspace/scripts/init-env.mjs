// Crea .env en la raíz a partir de .env.example y genera los secretos locales que estén vacíos.
// Idempotente: si .env ya existe no lo toca y sale con 0. Uso: node scripts/init-env.mjs
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const target = resolve(root, '.env');
const template = resolve(root, '.env.example');

if (existsSync(target)) {
  console.log('.env ya existe: no se modifica');
  process.exit(0);
}
if (!existsSync(template)) {
  console.error('falta .env.example en la raíz del repositorio');
  process.exit(2);
}

const hex = (bytes) => randomBytes(bytes).toString('hex');
const generators = {
  DB_SUPERUSER_PASSWORD: () => hex(24),
  DB_OWNER_PASSWORD: () => hex(24),
  DB_RUNTIME_PASSWORD: () => hex(24),
  JWT_SECRET: () => hex(32),
  DEV_SEED_PASSWORD: () => `Demo-${hex(6)}`,
};

const lines = readFileSync(template, 'utf8')
  .split(/\r?\n/)
  .map((line) => {
    const match = /^([A-Z0-9_]+)=(.*)$/.exec(line);
    if (!match) return line;
    const [, key, value] = match;
    return value === '' && generators[key] ? `${key}=${generators[key]()}` : line;
  });

writeFileSync(target, lines.join('\n'));
console.log('.env creado con secretos locales generados');
