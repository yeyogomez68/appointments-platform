// Contraste WCAG 2.2 AA de los tokens de color (frontend/src/styles/_tokens.scss).
// Texto: >= 4.5:1. Componentes de interfaz y foco: >= 3:1.
// Salida: 0 = todos cumplen · 1 = algún par falla · 2 = falta el archivo o un token.
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const file = resolve(dirname(fileURLToPath(import.meta.url)), '../frontend/src/styles/_tokens.scss');
if (!existsSync(file)) {
  console.error(`no existe ${file}`);
  process.exit(2);
}
const scss = readFileSync(file, 'utf8');

function tokens(mixin) {
  const match = new RegExp(`@mixin ${mixin}\\s*\\{([^}]*)\\}`).exec(scss);
  if (!match) {
    console.error(`no se encontró @mixin ${mixin}`);
    process.exit(2);
  }
  const map = {};
  for (const [, name, hex] of match[1].matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    map[name] = hex;
  }
  return map;
}

function luminance(hex) {
  const channel = (i) => {
    const v = parseInt(hex.slice(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
}

function ratio(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const TEXT = 4.5;
const UI = 3.0;
const pairs = [
  ['c-text', 'c-bg', TEXT],
  ['c-text', 'c-surface', TEXT],
  ['c-text', 'c-surface-2', TEXT],
  ['c-text-muted', 'c-bg', TEXT],
  ['c-text-muted', 'c-surface', TEXT],
  ['c-text-muted', 'c-surface-2', TEXT],
  ['c-on-primary', 'c-primary', TEXT],
  ['c-on-primary', 'c-primary-hover', TEXT],
  ['c-primary', 'c-bg', TEXT],
  ['c-primary', 'c-surface', TEXT],
  ['c-danger-text', 'c-surface', TEXT],
  ['c-success-text', 'c-surface', TEXT],
  ['c-warning-text', 'c-surface', TEXT],
  ['c-info-text', 'c-surface', TEXT],
  ['c-border-strong', 'c-surface', UI],
  ['c-border-strong', 'c-bg', UI],
  ['c-danger', 'c-surface', UI],
  ['c-success', 'c-surface', UI],
  ['c-warning', 'c-surface', UI],
  ['c-accent', 'c-surface', UI],
];

let failures = 0;
for (const mode of ['light', 'dark']) {
  const t = tokens(`${mode}-tokens`);
  for (const [fg, bg, min] of pairs) {
    if (!t[fg] || !t[bg]) {
      console.error(`${mode}: falta --${fg} o --${bg}`);
      process.exit(2);
    }
    const r = ratio(t[fg], t[bg]);
    const ok = r >= min;
    if (!ok) failures += 1;
    console.log(`${ok ? 'OK  ' : 'FALLA'} ${mode.padEnd(5)} --${fg} sobre --${bg}: ${r.toFixed(2)}:1 (mínimo ${min}:1)`);
  }
}
if (failures > 0) {
  console.error(`${failures} par(es) no cumplen WCAG 2.2 AA`);
  process.exit(1);
}
console.log('Contraste OK');
