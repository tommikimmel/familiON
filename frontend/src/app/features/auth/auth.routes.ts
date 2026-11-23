import { Routes } from '@angular/router';

// No child guards here: guestGuard on the parent 'auth' route handles access rules (including /auth/invite) in T12.
const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login.page').then((m) => m.LoginPage),
    data: { title: 'Iniciar sesión' },
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/register.page').then((m) => m.RegisterPage),
    data: { title: 'Crear cuenta' },
  },
  {
    path: 'verify-email',
    loadComponent: () => import('./pages/verify-email.page').then((m) => m.VerifyEmailPage),
    data: { title: 'Verificar correo' },
  },
  {
    path: '2fa',
    loadComponent: () => import('./pages/two-factor.page').then((m) => m.TwoFactorPage),
    data: { title: 'Verificación en dos pasos' },
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./pages/forgot-password.page').then((m) => m.ForgotPasswordPage),
    data: { title: 'Recuperar contraseña' },
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./pages/reset-password.page').then((m) => m.ResetPasswordPage),
    data: { title: 'Restablecer contraseña' },
  },
  {
    path: 'invite',
    loadComponent: () => import('./pages/accept-invite.page').then((m) => m.AcceptInvitePage),
    data: { title: 'Aceptar invitación' },
  },
];

export default routes;
