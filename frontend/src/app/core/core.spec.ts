import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { provideApiConfiguration } from '../api/api-configuration';
import { Shell } from '../layout/shell';
import { AuthStore, Session, authGuard, authInterceptor, roleGuard } from './auth';
import { DEFAULT_LABELS, ERROR_MESSAGES, LabelsService, messageFor } from './ui-text';

function session(role: Session['business']['role'], businesses = 1): Session {
  const list = Array.from({ length: businesses }, (_, i) => ({
    id: `b${i + 1}`,
    name: `Negocio ${i + 1}`,
    slug: `negocio-${i + 1}`,
    role,
  }));
  return { accessToken: 'token-viejo', business: list[0], businesses: list, user: null };
}

describe('núcleo web', () => {
  let http: HttpTestingController;
  let store: AuthStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        provideApiConfiguration(''),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
  });

  describe('messageFor', () => {
    it('usa el code del cuerpo y, si no lo conoce, el estado HTTP', () => {
      const slot = new HttpErrorResponse({ status: 409, error: { code: 'SLOT_TAKEN' } });
      expect(messageFor(slot)).toBe('Ese horario acaba de ser tomado. Elige otro.');
      const unknown = new HttpErrorResponse({ status: 404, error: { code: 'ALGO_NUEVO' } });
      expect(messageFor(unknown)).toBe(ERROR_MESSAGES['NOT_FOUND']);
      expect(messageFor(new HttpErrorResponse({ status: 0 }))).toBe('Sin conexión con el servidor. Revisa tu internet.');
      expect(messageFor(new Error('x'))).toBe(ERROR_MESSAGES['INTERNAL_ERROR']);
    });
  });

  describe('guardas', () => {
    const state = (url: string) => ({ url }) as RouterStateSnapshot;

    it('authGuard sin sesión manda a /ingresar con volver', () => {
      const result = TestBed.runInInjectionContext(() =>
        authGuard({} as ActivatedRouteSnapshot, state('/mi-negocio')),
      ) as UrlTree;
      expect(TestBed.inject(Router).serializeUrl(result)).toBe('/ingresar?volver=%2Fmi-negocio');
    });

    it('roleGuard sin el rol pedido manda a /inicio', () => {
      store.session.set(session('RECEPTION'));
      const route = { data: { roles: ['OWNER'] } } as unknown as ActivatedRouteSnapshot;
      const result = TestBed.runInInjectionContext(() => roleGuard(route, state('/mi-negocio'))) as UrlTree;
      expect(TestBed.inject(Router).serializeUrl(result)).toBe('/inicio');
      store.session.set(session('OWNER'));
      expect(TestBed.runInInjectionContext(() => roleGuard(route, state('/mi-negocio')))).toBe(true);
    });
  });

  describe('authInterceptor', () => {
    it('ante 401 hace un refresh y reintenta con el token nuevo', async () => {
      store.session.set(session('OWNER'));
      const result = firstValueFrom(TestBed.inject(HttpClient).get<{ ok: boolean }>('/api/v1/business'));

      const first = http.expectOne('/api/v1/business');
      expect(first.request.headers.get('Authorization')).toBe('Bearer token-viejo');
      first.flush({ code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });

      await Promise.resolve();
      const refreshCall = http.expectOne('/api/v1/auth/refresh');
      expect(refreshCall.request.headers.has('Authorization')).toBe(false);
      refreshCall.flush({ accessToken: 'token-nuevo', business: { id: 'b1', name: 'Negocio 1', slug: 'negocio-1', role: 'OWNER' } });

      await vi.waitFor(() => http.expectOne('/api/v1/business'), { timeout: 1000 }).then((retry) => {
        expect(retry.request.headers.get('Authorization')).toBe('Bearer token-nuevo');
        retry.flush({ ok: true });
      });
      expect(await result).toEqual({ ok: true });
    });

    it('si el refresh falla limpia la sesión y navega a /ingresar', async () => {
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      store.session.set(session('OWNER'));
      const result = firstValueFrom(TestBed.inject(HttpClient).get('/api/v1/business')).catch((e) => e);

      http.expectOne('/api/v1/business').flush({}, { status: 401, statusText: 'Unauthorized' });
      await Promise.resolve();
      http.expectOne('/api/v1/auth/refresh').flush({ code: 'INVALID_REFRESH_TOKEN' }, { status: 401, statusText: 'Unauthorized' });

      const error = await result;
      expect(error).toBeInstanceOf(HttpErrorResponse);
      expect(store.session()).toBeNull();
      expect(navigate).toHaveBeenCalledWith(['/ingresar']);
    });
  });

  describe('AuthStore.switchBusiness', () => {
    it('reemplaza el negocio, reinicia las etiquetas y las vuelve a cargar', async () => {
      const labels = TestBed.inject(LabelsService);
      store.session.set(session('OWNER', 2));
      labels.labels.set({ ...DEFAULT_LABELS, resource: 'Profesional' });

      const done = store.switchBusiness('b2');
      const call = http.expectOne('/api/v1/auth/switch-business');
      expect(call.request.body).toEqual({ businessId: 'b2' });
      call.flush({ accessToken: 'token-b2', business: { id: 'b2', name: 'Negocio 2', slug: 'negocio-2', role: 'EMPLOYEE' } });

      await vi.waitFor(() => http.expectOne('/api/v1/business'), { timeout: 1000 }).then((load) => {
        expect(labels.labels()).toEqual(DEFAULT_LABELS);
        expect(load.request.headers.get('Authorization')).toBe('Bearer token-b2');
        load.flush({ id: 'b2', name: 'Negocio 2', labels: { resource: 'Estilista' } });
      });
      await done;
      expect(store.session()?.business).toEqual({ id: 'b2', name: 'Negocio 2', slug: 'negocio-2', role: 'EMPLOYEE' });
      expect(labels.labels()['resource']).toBe('Estilista');
    });
  });

  describe('Shell', () => {
    async function render(role: Session['business']['role'], businesses: number): Promise<HTMLElement> {
      store.session.set(session(role, businesses));
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();
      http.match(() => true).forEach((req) => req.flush({ id: 'b1', name: 'Negocio 1', labels: {} }));
      await fixture.whenStable();
      return fixture.nativeElement as HTMLElement;
    }

    function menuLabels(element: HTMLElement): string[] {
      return Array.from(element.querySelectorAll('mat-nav-list a')).map((a) => a.textContent?.trim() ?? '');
    }

    it('RECEPTION ve Inicio y no Mi negocio ni el selector', async () => {
      const element = await render('RECEPTION', 1);
      expect(menuLabels(element)).toEqual(['Inicio']);
      expect(element.querySelector('mat-select')).toBeNull();
    });

    it('OWNER con dos negocios ve ambos ítems y el selector', async () => {
      const element = await render('OWNER', 2);
      expect(menuLabels(element)).toEqual(['Inicio', 'Mi negocio']);
      expect(element.querySelector('mat-select')).not.toBeNull();
      expect(element.textContent).toContain('Saltar al contenido');
    });
  });
});
