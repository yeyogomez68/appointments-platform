import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localeEsCO from '@angular/common/locales/es-CO';
import {
  ApplicationConfig,
  DEFAULT_CURRENCY_CODE,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideApiConfiguration } from './api/api-configuration';
import { routes } from './app.routes';
import { AuthStore, authInterceptor, errorToastInterceptor } from './core/auth';

registerLocaleData(localeEsCO);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorToastInterceptor])),
    provideApiConfiguration(''),
    { provide: LOCALE_ID, useValue: 'es-CO' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'COP' },
    provideAppInitializer(() => inject(AuthStore).restore()),
  ],
};
