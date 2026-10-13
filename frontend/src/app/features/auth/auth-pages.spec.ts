import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { provideApiConfiguration } from '../../api/api-configuration';
import { AuthStore } from '../../core/auth';
import { DUMMY_CAPTCHA_TOKEN, LoginPage, SelectBusinessPage, SignupPage, VerifyEmailPage } from './auth-pages';

describe('pantallas de acceso', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  function setup(queryParams: Record<string, string> = {}): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideApiConfiguration(''),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  }

  async function render<T>(component: Type<T>): Promise<ComponentFixture<T>> {
    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  async function next(url: string): Promise<TestRequest> {
    return vi.waitFor(() => http.expectOne(url), { timeout: 1000 });
  }

  function type(fixture: ComponentFixture<unknown>, control: string, value: string): void {
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(`input[formControlName="${control}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  function submit(fixture: ComponentFixture<unknown>): void {
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  async function settle(fixture: ComponentFixture<unknown>): Promise<HTMLElement> {
    await vi.waitFor(async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    });
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('el login muestra el error de credenciales en role="alert"', async () => {
    setup();
    const fixture = await render(LoginPage);
    type(fixture, 'email', 'ana@example.com');
    type(fixture, 'password', 'equivocada-123');
    submit(fixture);
    (await next('/api/v1/auth/login')).flush(
      { code: 'INVALID_CREDENTIALS', detail: 'Correo o contraseña incorrectos.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await vi.waitFor(async () => {
      const element = await settle(fixture);
      expect(element.querySelector('[role="alert"]')?.textContent?.trim()).toBe('Correo o contraseña incorrectos.');
    });
  });

  it('con varios negocios navega a /elegir-negocio, que lista un botón por negocio', async () => {
    setup();
    const fixture = await render(LoginPage);
    type(fixture, 'email', 'ana@example.com');
    type(fixture, 'password', 'clave-correcta-1');
    submit(fixture);
    (await next('/api/v1/auth/login')).flush({
      status: 'BUSINESS_SELECTION_REQUIRED',
      code: 'BUSINESS_SELECTION_REQUIRED',
      preAuthToken: 'preauth',
      businesses: [
        { id: 'a', name: 'Salón Norte', slug: 'salon-norte', role: 'OWNER' },
        { id: 'b', name: 'Salón Sur', slug: 'salon-sur', role: 'EMPLOYEE' },
      ],
    });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(['/elegir-negocio']));

    const select = await render(SelectBusinessPage);
    const names = Array.from((select.nativeElement as HTMLElement).querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(names).toEqual(['Salón Norte', 'Salón Sur']);
  });

  it('el registro exige formulario válido y ambas casillas, y usa el token de prueba sin clave de sitio', async () => {
    setup();
    const fixture = await render(SignupPage);
    (await next('/api/v1/public/config')).flush({ captchaSiteKey: '', termsVersion: 'v', dpaVersion: 'v' });
    const element = await settle(fixture);
    const button = () =>
      Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Crear mi negocio')!;
    expect(button().disabled).toBe(true);

    type(fixture, 'businessName', 'Salón Ana');
    type(fixture, 'ownerName', 'Ana Pérez');
    type(fixture, 'email', 'ana@example.com');
    type(fixture, 'password', 'clave-segura-2026');
    await settle(fixture);
    expect(button().disabled).toBe(true);

    const boxes = element.querySelectorAll<HTMLInputElement>('mat-checkbox input[type="checkbox"]');
    expect(element.textContent).toContain('Acepto los términos de servicio');
    expect(element.textContent).toContain('Acepto el acuerdo de encargo de datos');
    boxes[0].click();
    await settle(fixture);
    expect(button().disabled).toBe(true);
    boxes[1].click();
    await settle(fixture);
    expect(button().disabled).toBe(false);

    submit(fixture);
    const request = await next('/api/v1/signup');
    expect(request.request.body).toMatchObject({
      businessName: 'Salón Ana',
      vertical: 'PELUQUERIA',
      timezone: 'America/Bogota',
      acceptTerms: true,
      acceptDpa: true,
      captchaToken: DUMMY_CAPTCHA_TOKEN,
    });
    request.flush({ status: 'VERIFICATION_SENT' }, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(async () => expect((await settle(fixture)).querySelector('h1')?.textContent).toBe('Revisa tu correo'));
  });

  it('verificar correo guarda la sesión y navega a /inicio', async () => {
    setup({ token: 'token-del-correo' });
    await render(VerifyEmailPage);
    const verify = await next('/api/v1/signup/verify');
    expect(verify.request.body).toEqual({ token: 'token-del-correo' });
    verify.flush({ accessToken: 'acceso', business: { id: 'n', name: 'Nuevo', slug: 'nuevo', role: 'OWNER' } });
    (await next('/api/v1/auth/me')).flush({
      user: { id: 'u', email: 'ana@example.com', name: 'Ana' },
      business: { id: 'n', name: 'Nuevo', slug: 'nuevo', role: 'OWNER' },
      businesses: [{ id: 'n', name: 'Nuevo', slug: 'nuevo', role: 'OWNER' }],
    });
    (await next('/api/v1/business')).flush({ id: 'n', name: 'Nuevo', labels: {} });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(['/inicio']));
    expect(TestBed.inject(AuthStore).session()?.business.id).toBe('n');
  });

  it('verificar correo con token inválido muestra el error en role="alert"', async () => {
    setup({ token: 'usado' });
    const fixture = await render(VerifyEmailPage);
    (await next('/api/v1/signup/verify')).flush(
      { code: 'INVALID_TOKEN' },
      { status: 400, statusText: 'Bad Request' },
    );
    await vi.waitFor(async () => {
      const element = await settle(fixture);
      expect(element.querySelector('[role="alert"]')?.textContent?.trim()).toBe('El enlace no es válido o ya venció.');
    });
  });
});
