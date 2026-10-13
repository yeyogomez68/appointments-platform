import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { Api } from '../api/api';
import { login } from '../api/fn/auth/login';
import { logout } from '../api/fn/auth/logout';
import { me } from '../api/fn/auth/me';
import { refresh } from '../api/fn/auth/refresh';
import { selectBusiness } from '../api/fn/auth/select-business';
import { switchBusiness } from '../api/fn/auth/switch-business';
import { BusinessOption } from '../api/models/business-option';
import { SessionResponse } from '../api/models/session-response';
import { LabelsService, messageFor } from './ui-text';

export type Role = 'OWNER' | 'RECEPTION' | 'EMPLOYEE';

export interface SessionBusiness {
  id: string;
  name: string;
  slug: string;
  role: Role;
}

export interface Session {
  accessToken: string;
  business: SessionBusiness;
  businesses: SessionBusiness[];
  user: { id: string; email: string; name: string } | null;
}

export interface PendingPreAuth {
  token: string;
  businesses: SessionBusiness[];
}

function toBusiness(option: BusinessOption | undefined): SessionBusiness {
  return {
    id: option?.id ?? '',
    name: option?.name ?? '',
    slug: option?.slug ?? '',
    role: (option?.role ?? 'EMPLOYEE') as Role,
  };
}

/** Sesión en memoria (el access token nunca va a localStorage); se recupera con la cookie `rt`. */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(Api);
  private readonly labels = inject(LabelsService);
  private refreshing: Promise<string | null> | null = null;

  readonly session = signal<Session | null>(null);
  readonly pendingPreAuth = signal<PendingPreAuth | null>(null);

  async login(email: string, password: string): Promise<'AUTHENTICATED' | 'BUSINESS_SELECTION_REQUIRED'> {
    const result = await this.api.invoke(login, { body: { email, password } });
    if (result.status === 'AUTHENTICATED' && result.session) {
      await this.acceptSession(result.session);
      return 'AUTHENTICATED';
    }
    this.pendingPreAuth.set({
      token: result.preAuthToken ?? '',
      businesses: (result.businesses ?? []).map(toBusiness),
    });
    return 'BUSINESS_SELECTION_REQUIRED';
  }

  async selectBusiness(businessId: string): Promise<void> {
    const session = await this.api.invoke(selectBusiness, { body: { businessId } });
    this.pendingPreAuth.set(null);
    await this.acceptSession(session);
  }

  async switchBusiness(businessId: string): Promise<void> {
    const response = await this.api.invoke(switchBusiness, { body: { businessId } });
    const current = this.session();
    const business = toBusiness(response.business);
    const known = current?.businesses ?? [];
    this.session.set({
      accessToken: response.accessToken ?? '',
      business,
      businesses: known.some((b) => b.id === business.id) ? known : [...known, business],
      user: current?.user ?? null,
    });
    this.labels.reset();
    await this.labels.load();
  }

  /** Fija la sesión recibida y completa cuenta, negocios y etiquetas. */
  async acceptSession(response: SessionResponse): Promise<void> {
    this.session.set({
      accessToken: response.accessToken ?? '',
      business: toBusiness(response.business),
      businesses: [toBusiness(response.business)],
      user: null,
    });
    const profile = await this.api.invoke(me);
    this.session.update((s) =>
      s && {
        ...s,
        business: toBusiness(profile.business),
        businesses: (profile.businesses ?? []).map(toBusiness),
        user: profile.user
          ? { id: profile.user.id ?? '', email: profile.user.email ?? '', name: profile.user.name ?? '' }
          : null,
      },
    );
    this.labels.reset();
    await this.labels.load();
  }

  /** Al cargar la app: intenta recuperar la sesión con la cookie `rt`. Nunca lanza. */
  async restore(): Promise<void> {
    try {
      const response = await this.api.invoke(refresh);
      await this.acceptSession(response);
    } catch {
      this.clear();
    }
  }

  /** Un solo refresh a la vez; devuelve el token nuevo o `null` si la sesión terminó. */
  refreshAccessToken(): Promise<string | null> {
    if (!this.refreshing) {
      this.refreshing = this.api
        .invoke(refresh)
        .then((response) => {
          const current = this.session();
          const business = toBusiness(response.business);
          this.session.set({
            accessToken: response.accessToken ?? '',
            business,
            businesses: current?.businesses ?? [business],
            user: current?.user ?? null,
          });
          return response.accessToken ?? null;
        })
        .catch(() => null)
        .finally(() => (this.refreshing = null));
    }
    return this.refreshing;
  }

  async logout(): Promise<void> {
    try {
      await this.api.invoke(logout);
    } finally {
      this.clear();
    }
  }

  clear(): void {
    this.session.set(null);
    this.pendingPreAuth.set(null);
    this.labels.reset();
  }
}

const NO_BEARER = [
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
  '/api/v1/auth/password/',
  '/api/v1/signup',
  '/api/v1/public/',
];

function isPublic(url: string): boolean {
  return NO_BEARER.some((path) => url.includes(path));
}

function withBearer(req: HttpRequest<unknown>, token: string | undefined | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/** Bearer en cada llamada autenticada; ante un 401, un solo refresh y un reintento. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const router = inject(Router);
  if (isPublic(req.url)) {
    return next(req);
  }
  if (req.url.includes('/api/v1/auth/select-business')) {
    return next(withBearer(req, store.pendingPreAuth()?.token));
  }
  return next(withBearer(req, store.session()?.accessToken)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return from(store.refreshAccessToken()).pipe(
        switchMap((token) => {
          if (token) {
            return next(withBearer(req, token));
          }
          store.clear();
          void router.navigate(['/ingresar']);
          return throwError(() => error);
        }),
      );
    }),
  );
};

/** Errores de red, conflictos, límites y fallos del servidor: aviso breve y se relanza. */
export const errorToastInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && (error.status === 0 || error.status === 409 || error.status === 429 || error.status >= 500)) {
        snackBar.open(messageFor(error), 'Cerrar', { duration: 6000 });
      }
      return throwError(() => error);
    }),
  );
};

export const authGuard: CanActivateFn = (_route, state) => {
  const store = inject(AuthStore);
  const router = inject(Router);
  return store.session() ? true : router.createUrlTree(['/ingresar'], { queryParams: { volver: state.url } });
};

export const roleGuard: CanActivateFn = (route) => {
  const store = inject(AuthStore);
  const router = inject(Router);
  const roles = (route.data['roles'] as Role[] | undefined) ?? [];
  const role = store.session()?.business.role;
  return role && roles.includes(role) ? true : router.createUrlTree(['/inicio']);
};
