import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { Api } from '../api/api';
import { getBusiness } from '../api/fn/business/get-business';
import { BusinessResponse } from '../api/models/business-response';
import { AuthStore, Role } from '../core/auth';
import { LabelsService, messageFor } from '../core/ui-text';

export interface MenuItem {
  label: string;
  path: string;
  roles: Role[];
}

/** Menú lateral; cada bundle agrega sus ítems con los roles que los ven. */
export const MENU: MenuItem[] = [
  { label: 'Inicio', path: '/inicio', roles: ['OWNER', 'RECEPTION', 'EMPLOYEE'] },
  { label: 'Mi negocio', path: '/mi-negocio', roles: ['OWNER'] },
];

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatSelectModule,
    MatMenuModule,
    MatButtonModule,
  ],
  template: `
    <a class="skip-link" href="#contenido">Saltar al contenido</a>
    <mat-toolbar class="topbar">
      <button mat-button class="menu-toggle" type="button" (click)="nav.toggle()" aria-label="Abrir menú">Menú</button>
      <span class="business-name">{{ session()?.business?.name }}</span>
      <span class="spacer"></span>
      @if (businesses().length > 1) {
        <mat-form-field class="business-select" subscriptSizing="dynamic">
          <mat-label>Negocio</mat-label>
          <mat-select [value]="session()?.business?.id" (selectionChange)="switchTo($event.value)">
            @for (b of businesses(); track b.id) {
              <mat-option [value]="b.id">{{ b.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      }
      <button mat-button type="button" [matMenuTriggerFor]="account">Cuenta</button>
      <mat-menu #account="matMenu">
        <a mat-menu-item routerLink="/crear-negocio">Crear otro negocio</a>
        <button mat-menu-item type="button" (click)="logout()">Cerrar sesión</button>
      </mat-menu>
    </mat-toolbar>
    <mat-sidenav-container class="container">
      <mat-sidenav #nav [mode]="isMobile() ? 'over' : 'side'" [opened]="!isMobile()">
        <mat-nav-list aria-label="Navegación principal">
          @for (item of menu(); track item.path) {
            <a mat-list-item [routerLink]="item.path" routerLinkActive="active">{{ item.label }}</a>
          }
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content>
        <main id="contenido" tabindex="-1">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 100vh; }
    .topbar { gap: var(--space-2); background: var(--c-surface); color: var(--c-text); border-bottom: 1px solid var(--c-border); }
    .business-name { font-weight: 600; font-size: var(--fs-16); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .spacer { flex: 1; }
    .business-select { width: 200px; font-size: var(--fs-14); }
    .container { flex: 1; background: var(--c-bg); }
    mat-sidenav { width: 240px; background: var(--c-surface); border-right: 1px solid var(--c-border); }
    .active { color: var(--c-primary); font-weight: 600; }
    main { padding: var(--space-6) var(--space-4); max-width: var(--content-max); margin: 0 auto; outline: none; }
    @media (min-width: 768px) { .menu-toggle { display: none; } main { padding: var(--space-8); } }
    @media (max-width: 599px) { .business-select { width: 140px; } }
  `,
})
export class Shell implements OnInit {
  private readonly store = inject(AuthStore);
  private readonly labels = inject(LabelsService);
  private readonly router = inject(Router);

  protected readonly session = this.store.session;
  protected readonly businesses = computed(() => this.session()?.businesses ?? []);
  protected readonly menu = computed(() => {
    const role = this.session()?.business.role;
    return MENU.filter((item) => role !== undefined && item.roles.includes(role));
  });
  protected readonly isMobile = toSignal(
    inject(BreakpointObserver).observe('(max-width: 767px)').pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  ngOnInit(): void {
    if (!this.labels.business()) {
      this.labels.load().catch(() => undefined);
    }
  }

  protected async switchTo(businessId: string): Promise<void> {
    await this.store.switchBusiness(businessId);
    await this.router.navigate(['/inicio']);
  }

  protected async logout(): Promise<void> {
    await this.store.logout();
    await this.router.navigate(['/ingresar']);
  }
}

@Component({
  selector: 'app-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, LowerCasePipe],
  template: `
    <h1>{{ labels.business()?.name ?? session()?.business?.name }}</h1>
    @if (labels.business()?.trialEndsAt; as trialEndsAt) {
      <p class="card">Tu prueba gratuita vence el {{ trialEndsAt | date: 'longDate' }}</p>
    }
    <p class="muted">
      Pronto podrás organizar la agenda de tus {{ labels.labels()['resourcePlural'] | lowercase }}, tus
      {{ labels.labels()['servicePlural'] | lowercase }} y tus {{ labels.labels()['customerPlural'] | lowercase }}.
    </p>
  `,
})
export class HomePage {
  protected readonly labels = inject(LabelsService);
  protected readonly session = inject(AuthStore).session;
}

@Component({
  selector: 'app-business-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
  template: `
    <h1>Mi negocio</h1>
    @if (error()) {
      <p role="alert" class="error-text">{{ error() }}</p>
    }
    @if (business(); as b) {
      <dl class="card details">
        <dt>Nombre</dt>
        <dd>{{ b.name }}</dd>
        <dt>Zona horaria</dt>
        <dd>{{ b.timezone }}</dd>
        <dt>Moneda</dt>
        <dd>{{ b.currency }}</dd>
        <dt>Fin de la prueba</dt>
        <dd>{{ b.trialEndsAt | date: 'longDate' }}</dd>
      </dl>
    }
  `,
  styles: `
    .details { display: grid; grid-template-columns: max-content 1fr; gap: var(--space-2) var(--space-6); margin: 0; }
    dt { color: var(--c-text-muted); }
    dd { margin: 0; }
  `,
})
export class BusinessPage implements OnInit {
  private readonly api = inject(Api);
  protected readonly business = signal<BusinessResponse | null>(null);
  protected readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.business.set(await this.api.invoke(getBusiness));
    } catch (e) {
      this.error.set(messageFor(e));
    }
  }
}
