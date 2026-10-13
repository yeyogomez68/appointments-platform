import { ChangeDetectionStrategy, Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { forgotPassword } from '../../api/fn/auth/forgot-password';
import { resetPassword } from '../../api/fn/auth/reset-password';
import { createBusiness } from '../../api/fn/signup/create-business';
import { publicConfig } from '../../api/fn/signup/public-config';
import { signup } from '../../api/fn/signup/signup';
import { verifySignup } from '../../api/fn/signup/verify-signup';
import { AuthStore } from '../../core/auth';
import { messageFor } from '../../core/ui-text';

/** Token de prueba de Turnstile: lo acepta el backend cuando no hay clave de sitio configurada. */
export const DUMMY_CAPTCHA_TOKEN = 'XXXX.DUMMY.TOKEN.XXXX';
const TURNSTILE_SCRIPT = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';

export const VERTICALS = [{ value: 'PELUQUERIA', label: 'Peluquería' }];
export const TIMEZONES = [
  { value: 'America/Bogota', label: 'Bogotá (GMT-5)' },
  { value: 'America/Mexico_City', label: 'Ciudad de México (GMT-6)' },
  { value: 'America/Lima', label: 'Lima (GMT-5)' },
  { value: 'America/Guayaquil', label: 'Guayaquil (GMT-5)' },
  { value: 'America/Caracas', label: 'Caracas (GMT-4)' },
  { value: 'America/Santiago', label: 'Santiago de Chile' },
  { value: 'America/Argentina/Buenos_Aires', label: 'Buenos Aires (GMT-3)' },
  { value: 'Europe/Madrid', label: 'Madrid' },
];

interface TurnstileApi {
  render(container: HTMLElement, options: { sitekey: string; callback: (token: string) => void }): void;
}

const FORM_STYLES = `
  :host { display: flex; justify-content: center; padding: var(--space-8) var(--space-4); }
  .card { width: 100%; max-width: 440px; }
  form { display: flex; flex-direction: column; gap: var(--space-2); }
  mat-form-field { width: 100%; }
  .actions { display: flex; flex-direction: column; gap: var(--space-3); margin-top: var(--space-2); }
  .links { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--space-2); font-size: var(--fs-14); }
  .notice { color: var(--c-success-text); }
  .business-list { display: flex; flex-direction: column; gap: var(--space-3); }
  .captcha { min-height: 0; }
`;

const COMMON_IMPORTS = [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule];

@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: COMMON_IMPORTS,
  styles: FORM_STYLES,
  template: `
    <section class="card">
      <h1>Ingresar</h1>
      @if (notice()) {
        <p class="notice">{{ notice() }}</p>
      }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <mat-form-field>
          <mat-label>Correo electrónico</mat-label>
          <input matInput type="email" formControlName="email" autocomplete="email" />
          @if (form.controls.email.invalid && form.controls.email.touched) {
            <mat-error>Escribe un correo válido.</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Contraseña</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="current-password" />
          @if (form.controls.password.invalid && form.controls.password.touched) {
            <mat-error>Escribe tu contraseña.</mat-error>
          }
        </mat-form-field>
        @if (error()) {
          <p role="alert" class="error-text">{{ error() }}</p>
        }
        <div class="actions">
          <button mat-flat-button type="submit" [disabled]="busy()">Ingresar</button>
          <div class="links">
            <a routerLink="/recuperar-contrasena">¿Olvidaste tu contraseña?</a>
            <a routerLink="/registro">Crear una cuenta</a>
          </div>
        </div>
      </form>
    </section>
  `,
})
export class LoginPage {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly notice = signal(
    this.route.snapshot.queryParamMap.get('aviso') === 'contrasena-actualizada'
      ? 'Tu contraseña se actualizó. Ingresa con la nueva.'
      : null,
  );

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const { email, password } = this.form.getRawValue();
      const status = await this.store.login(email, password);
      if (status === 'AUTHENTICATED') {
        await this.router.navigateByUrl(this.route.snapshot.queryParamMap.get('volver') || '/inicio');
      } else {
        await this.router.navigate(['/elegir-negocio']);
      }
    } catch (e) {
      this.error.set(messageFor(e));
    } finally {
      this.busy.set(false);
    }
  }
}

@Component({
  selector: 'app-select-business-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule],
  styles: FORM_STYLES,
  template: `
    <section class="card">
      <h1>Elige un negocio</h1>
      <div class="business-list">
        @for (b of businesses(); track b.id) {
          <button mat-stroked-button type="button" [disabled]="busy()" (click)="choose(b.id)">{{ b.name }}</button>
        }
      </div>
      @if (error()) {
        <p role="alert" class="error-text">{{ error() }}</p>
      }
    </section>
  `,
})
export class SelectBusinessPage implements OnInit {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  protected readonly businesses = signal(this.store.pendingPreAuth()?.businesses ?? []);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  ngOnInit(): void {
    if (!this.store.pendingPreAuth()) {
      void this.router.navigate(['/ingresar']);
    }
  }

  protected async choose(businessId: string): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.store.selectBusiness(businessId);
      await this.router.navigate(['/inicio']);
    } catch (e) {
      this.error.set(messageFor(e));
    } finally {
      this.busy.set(false);
    }
  }
}

@Component({
  selector: 'app-signup-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [...COMMON_IMPORTS, MatSelectModule, MatCheckboxModule],
  styles: FORM_STYLES,
  template: `
    <section class="card">
      @if (sent()) {
        <h1>Revisa tu correo</h1>
        <p>Te enviamos un enlace para confirmar tu correo y activar tu negocio. Vence en 24 horas.</p>
      } @else {
        <h1>Crea tu negocio</h1>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <mat-form-field>
            <mat-label>Nombre del negocio</mat-label>
            <input matInput formControlName="businessName" autocomplete="organization" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Tipo de negocio</mat-label>
            <mat-select formControlName="vertical">
              @for (v of verticals; track v.value) {
                <mat-option [value]="v.value">{{ v.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Zona horaria</mat-label>
            <mat-select formControlName="timezone">
              @for (z of timezones; track z.value) {
                <mat-option [value]="z.value">{{ z.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Tu nombre</mat-label>
            <input matInput formControlName="ownerName" autocomplete="name" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Correo electrónico</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" />
            @if (form.controls.email.invalid && form.controls.email.touched) {
              <mat-error>Escribe un correo válido.</mat-error>
            }
          </mat-form-field>
          <mat-form-field>
            <mat-label>Contraseña</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="new-password" />
            <mat-hint>Mínimo 10 caracteres.</mat-hint>
            @if (form.controls.password.invalid && form.controls.password.touched) {
              <mat-error>La contraseña debe tener entre 10 y 128 caracteres.</mat-error>
            }
          </mat-form-field>
          <mat-checkbox formControlName="acceptTerms">Acepto los términos de servicio</mat-checkbox>
          <mat-checkbox formControlName="acceptDpa">Acepto el acuerdo de encargo de datos</mat-checkbox>
          <div class="captcha" #captcha></div>
          @if (error()) {
            <p role="alert" class="error-text">{{ error() }}</p>
          }
          <div class="actions">
            <button mat-flat-button type="submit" [disabled]="form.invalid || busy()">Crear mi negocio</button>
            <div class="links"><a routerLink="/ingresar">Ya tengo cuenta</a></div>
          </div>
        </form>
      }
    </section>
  `,
})
export class SignupPage implements OnInit {
  private readonly api = inject(Api);
  private readonly captchaHost = viewChild<ElementRef<HTMLElement>>('captcha');
  protected readonly verticals = VERTICALS;
  protected readonly timezones = TIMEZONES;
  protected readonly form = inject(NonNullableFormBuilder).group({
    businessName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    vertical: ['PELUQUERIA', Validators.required],
    timezone: ['America/Bogota', Validators.required],
    ownerName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(128)]],
    acceptTerms: [false, Validators.requiredTrue],
    acceptDpa: [false, Validators.requiredTrue],
  });
  protected readonly sent = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private captchaToken: string | null = null;

  async ngOnInit(): Promise<void> {
    try {
      const config = await this.api.invoke(publicConfig);
      const siteKey = config.captchaSiteKey ?? '';
      if (siteKey) {
        await this.renderTurnstile(siteKey);
      } else {
        this.captchaToken = DUMMY_CAPTCHA_TOKEN;
      }
    } catch (e) {
      this.error.set(messageFor(e));
    }
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.captchaToken) {
      this.error.set('Completa la verificación de seguridad.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.api.invoke(signup, { body: { ...this.form.getRawValue(), captchaToken: this.captchaToken } });
      this.sent.set(true);
    } catch (e) {
      this.error.set(messageFor(e));
    } finally {
      this.busy.set(false);
    }
  }

  private async renderTurnstile(siteKey: string): Promise<void> {
    const host = this.captchaHost()?.nativeElement;
    if (!host) {
      return;
    }
    const turnstile = await loadTurnstile();
    turnstile.render(host, { sitekey: siteKey, callback: (token) => (this.captchaToken = token) });
  }
}

function loadTurnstile(): Promise<TurnstileApi> {
  const existing = (window as unknown as { turnstile?: TurnstileApi }).turnstile;
  if (existing) {
    return Promise.resolve(existing);
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = TURNSTILE_SCRIPT;
    script.async = true;
    script.onload = () => resolve((window as unknown as { turnstile: TurnstileApi }).turnstile);
    script.onerror = () => reject(new Error('No se pudo cargar la verificación de seguridad.'));
    document.head.appendChild(script);
  });
}

@Component({
  selector: 'app-verify-email-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  styles: FORM_STYLES,
  template: `
    <section class="card">
      <h1>Verificando tu correo</h1>
      @if (error()) {
        <p role="alert" class="error-text">{{ error() }}</p>
        <p><a routerLink="/ingresar">Ir a ingresar</a></p>
      } @else {
        <p class="muted">Un momento, estamos activando tu negocio…</p>
      }
    </section>
  `,
})
export class VerifyEmailPage implements OnInit {
  private readonly api = inject(Api);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const token = this.route.snapshot.queryParamMap.get('token') ?? '';
    try {
      const session = await this.api.invoke(verifySignup, { body: { token } });
      await this.store.acceptSession(session);
      await this.router.navigate(['/inicio']);
    } catch (e) {
      this.error.set(messageFor(e));
    }
  }
}

@Component({
  selector: 'app-forgot-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: COMMON_IMPORTS,
  styles: FORM_STYLES,
  template: `
    <section class="card">
      <h1>Recuperar contraseña</h1>
      @if (sent()) {
        <p role="status">Si el correo está registrado, te enviamos un enlace.</p>
        <p><a routerLink="/ingresar">Volver a ingresar</a></p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <mat-form-field>
            <mat-label>Correo electrónico</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" />
            @if (form.controls.email.invalid && form.controls.email.touched) {
              <mat-error>Escribe un correo válido.</mat-error>
            }
          </mat-form-field>
          @if (error()) {
            <p role="alert" class="error-text">{{ error() }}</p>
          }
          <div class="actions">
            <button mat-flat-button type="submit" [disabled]="busy()">Enviar enlace</button>
          </div>
        </form>
      }
    </section>
  `,
})
export class ForgotPasswordPage {
  private readonly api = inject(Api);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
  });
  protected readonly sent = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.api.invoke(forgotPassword, { body: this.form.getRawValue() });
      this.sent.set(true);
    } catch (e) {
      this.error.set(messageFor(e));
    } finally {
      this.busy.set(false);
    }
  }
}

@Component({
  selector: 'app-reset-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: COMMON_IMPORTS,
  styles: FORM_STYLES,
  template: `
    <section class="card">
      <h1>Nueva contraseña</h1>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <mat-form-field>
          <mat-label>Nueva contraseña</mat-label>
          <input matInput type="password" formControlName="newPassword" autocomplete="new-password" />
          <mat-hint>Mínimo 10 caracteres.</mat-hint>
          @if (form.controls.newPassword.invalid && form.controls.newPassword.touched) {
            <mat-error>La contraseña debe tener entre 10 y 128 caracteres.</mat-error>
          }
        </mat-form-field>
        @if (error()) {
          <p role="alert" class="error-text">{{ error() }}</p>
        }
        <div class="actions">
          <button mat-flat-button type="submit" [disabled]="busy()">Guardar contraseña</button>
        </div>
      </form>
    </section>
  `,
})
export class ResetPasswordPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly form = inject(NonNullableFormBuilder).group({
    newPassword: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(128)]],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const token = this.route.snapshot.queryParamMap.get('token') ?? '';
      await this.api.invoke(resetPassword, { body: { token, newPassword: this.form.getRawValue().newPassword } });
      await this.router.navigate(['/ingresar'], { queryParams: { aviso: 'contrasena-actualizada' } });
    } catch (e) {
      this.error.set(messageFor(e));
    } finally {
      this.busy.set(false);
    }
  }
}

@Component({
  selector: 'app-create-business-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [...COMMON_IMPORTS, MatSelectModule, MatCheckboxModule],
  styles: FORM_STYLES,
  template: `
    <section class="card">
      <h1>Crear otro negocio</h1>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <mat-form-field>
          <mat-label>Nombre del negocio</mat-label>
          <input matInput formControlName="businessName" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Tipo de negocio</mat-label>
          <mat-select formControlName="vertical">
            @for (v of verticals; track v.value) {
              <mat-option [value]="v.value">{{ v.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Zona horaria</mat-label>
          <mat-select formControlName="timezone">
            @for (z of timezones; track z.value) {
              <mat-option [value]="z.value">{{ z.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-checkbox formControlName="acceptTerms">Acepto los términos de servicio</mat-checkbox>
        <mat-checkbox formControlName="acceptDpa">Acepto el acuerdo de encargo de datos</mat-checkbox>
        @if (error()) {
          <p role="alert" class="error-text">{{ error() }}</p>
        }
        <div class="actions">
          <button mat-flat-button type="submit" [disabled]="form.invalid || busy()">Crear negocio</button>
        </div>
      </form>
    </section>
  `,
})
export class CreateBusinessPage {
  private readonly api = inject(Api);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  protected readonly verticals = VERTICALS;
  protected readonly timezones = TIMEZONES;
  protected readonly form = inject(NonNullableFormBuilder).group({
    businessName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    vertical: ['PELUQUERIA', Validators.required],
    timezone: ['America/Bogota', Validators.required],
    acceptTerms: [false, Validators.requiredTrue],
    acceptDpa: [false, Validators.requiredTrue],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const created = await this.api.invoke(createBusiness, { body: this.form.getRawValue() });
      await this.store.switchBusiness(created.id ?? '');
      await this.router.navigate(['/inicio']);
    } catch (e) {
      this.error.set(messageFor(e));
    } finally {
      this.busy.set(false);
    }
  }
}
