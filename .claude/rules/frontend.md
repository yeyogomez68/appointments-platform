---
description: Convenciones de Angular, diseño, accesibilidad y E2E
paths:
  - "frontend/src/**"
  - "frontend/e2e/**"
---

# Frontend

- Componentes standalone, `signal`/`computed`, `ChangeDetectionStrategy.OnPush`, plantillas en línea; sin NgRx; estado de sesión solo en `AuthStore`.
- Llamadas HTTP solo con el cliente generado: `inject(Api).invoke(fn, params)`. `src/app/api/**` nunca se edita a mano.
- Rutas perezosas (`loadComponent`) escritas como `path: '<ruta>'`; rutas protegidas con `authGuard`, por rol con `roleGuard` y `data: { roles: [...] }`.
- Textos en español es-CO; fechas y moneda con los pipes de Angular (`LOCALE_ID` `es-CO`, `COP`).
- Términos del vertical («Empleado», «Cliente», «Servicio») solo desde `LabelsService`; nunca escritos en `features/` ni `layout/`.
- Errores al usuario con `messageFor(error)`; mensajes en `ERROR_MESSAGES` de `core/ui-text.ts`.
- Colores, espacio, radio y movimiento solo con `var(--…)` de `src/styles/_tokens.scss`; un color nuevo entra también a `scripts/check-contrast.mjs`.
- Accesibilidad WCAG 2.2 AA: `<mat-label>` en cada campo, errores en `role="alert"` y en texto (no solo color), objetivos de 24×24 px o más, foco visible, enlace «Saltar al contenido», sin scroll horizontal a 375 px.
- El access token vive en memoria; nunca `localStorage`. La sesión se recupera con la cookie `rt` (`AuthStore.restore()`).
- E2E: selectores accesibles (`getByLabel`, `getByRole`) con los textos de la tabla «Contrato de pantallas» del blueprint; el correo se lee de Mailpit, nunca se simula.
