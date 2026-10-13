import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../api/api';
import { getBusiness } from '../api/fn/business/get-business';
import { BusinessResponse } from '../api/models/business-response';

/** Mensajes en español por `code` estable del backend. Un código nuevo exige su fila aquí. */
export const ERROR_MESSAGES: Record<string, string> = {
  VALIDATION_ERROR: 'Revisa los datos marcados en el formulario.',
  INVALID_CREDENTIALS: 'Correo o contraseña incorrectos.',
  EMAIL_NOT_VERIFIED: 'Confirma tu correo antes de ingresar. Revisa tu bandeja de entrada.',
  NO_ACTIVE_MEMBERSHIP: 'Tu cuenta no tiene acceso activo a ningún negocio.',
  BUSINESS_SELECTION_REQUIRED: 'Elige el negocio con el que vas a trabajar.',
  UNAUTHENTICATED: 'Tu sesión terminó. Ingresa de nuevo.',
  INVALID_REFRESH_TOKEN: 'Tu sesión terminó. Ingresa de nuevo.',
  FORBIDDEN: 'No tienes permiso para hacer esto.',
  NOT_FOUND: 'No encontramos lo que buscas.',
  ACCOUNT_EXISTS: 'Ya existe una cuenta con ese correo. Ingresa y usa «Crear otro negocio».',
  INVALID_TOKEN: 'El enlace no es válido o ya venció.',
  CAPTCHA_FAILED: 'No pudimos verificar que eres una persona. Intenta de nuevo.',
  RATE_LIMITED: 'Demasiados intentos. Espera un momento y vuelve a intentar.',
  SLOT_TAKEN: 'Ese horario acaba de ser tomado. Elige otro.',
  CONFLICT: 'Alguien cambió estos datos al mismo tiempo. Recarga e intenta de nuevo.',
  CURRENCY_MISMATCH: 'No se pueden combinar montos en monedas distintas.',
  INTERNAL_ERROR: 'Ocurrió un error inesperado. Intenta de nuevo.',
};

const OFFLINE_MESSAGE = 'Sin conexión con el servidor. Revisa tu internet.';

const STATUS_FALLBACK: Record<number, string> = {
  400: 'VALIDATION_ERROR',
  401: 'UNAUTHENTICATED',
  403: 'FORBIDDEN',
  404: 'NOT_FOUND',
  409: 'CONFLICT',
  429: 'RATE_LIMITED',
};

/** Mensaje para mostrar a la persona: por `code`, si no por estado HTTP, si no el genérico. */
export function messageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const code = codeOf(error);
    if (code && ERROR_MESSAGES[code]) {
      return ERROR_MESSAGES[code];
    }
    if (error.status === 0) {
      return OFFLINE_MESSAGE;
    }
    const fallback = STATUS_FALLBACK[error.status];
    return ERROR_MESSAGES[fallback ?? 'INTERNAL_ERROR'];
  }
  return ERROR_MESSAGES['INTERNAL_ERROR'];
}

/** `code` del cuerpo problem+json, si lo hay. */
export function codeOf(error: unknown): string | undefined {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    const code = (error.error as { code?: unknown }).code;
    return typeof code === 'string' ? code : undefined;
  }
  return undefined;
}

/** Etiquetas del vertical peluquería: respaldo hasta que carga el negocio. */
export const DEFAULT_LABELS: Readonly<Record<string, string>> = {
  resource: 'Empleado',
  resourcePlural: 'Empleados',
  service: 'Servicio',
  servicePlural: 'Servicios',
  customer: 'Cliente',
  customerPlural: 'Clientes',
};

/** Términos del vertical activo. Las pantallas nunca escriben «Empleado», «Cliente» o «Servicio» a mano. */
@Injectable({ providedIn: 'root' })
export class LabelsService {
  private readonly api = inject(Api);

  readonly labels = signal<Record<string, string>>({ ...DEFAULT_LABELS });
  readonly business = signal<BusinessResponse | null>(null);

  async load(): Promise<void> {
    const business = await this.api.invoke(getBusiness);
    this.business.set(business);
    this.labels.set({ ...DEFAULT_LABELS, ...(business.labels ?? {}) });
  }

  reset(): void {
    this.labels.set({ ...DEFAULT_LABELS });
    this.business.set(null);
  }
}
