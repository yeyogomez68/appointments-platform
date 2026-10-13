import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth';

export const routes: Routes = [
  { path: 'ingresar', loadComponent: () => import('./features/auth/auth-pages').then((m) => m.LoginPage) },
  { path: 'elegir-negocio', loadComponent: () => import('./features/auth/auth-pages').then((m) => m.SelectBusinessPage) },
  { path: 'registro', loadComponent: () => import('./features/auth/auth-pages').then((m) => m.SignupPage) },
  { path: 'verificar-correo', loadComponent: () => import('./features/auth/auth-pages').then((m) => m.VerifyEmailPage) },
  {
    path: 'recuperar-contrasena',
    loadComponent: () => import('./features/auth/auth-pages').then((m) => m.ForgotPasswordPage),
  },
  {
    path: 'restablecer-contrasena',
    loadComponent: () => import('./features/auth/auth-pages').then((m) => m.ResetPasswordPage),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      { path: 'inicio', loadComponent: () => import('./layout/shell').then((m) => m.HomePage) },
      {
        path: 'mi-negocio',
        loadComponent: () => import('./layout/shell').then((m) => m.BusinessPage),
        canActivate: [roleGuard],
        data: { roles: ['OWNER'] },
      },
      {
        path: 'crear-negocio',
        loadComponent: () => import('./features/auth/auth-pages').then((m) => m.CreateBusinessPage),
      },
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
    ],
  },
  { path: '**', redirectTo: 'inicio' },
];
