import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { guestGuard } from './core/auth/guest.guard';
import { AuthLayoutComponent } from './core/layout/auth-layout.component';
import { ShellComponent } from './core/layout/shell.component';

export const routes: Routes = [
  {
    path: 'auth',
    component: AuthLayoutComponent,
    canActivate: [guestGuard],
    loadChildren: () => import('./features/auth/auth.routes'),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'home' },
      {
        path: 'home',
        loadChildren: () => import('./features/home/home.routes'),
        data: { title: 'Inicio' },
      },
      {
        path: 'transactions',
        loadChildren: () => import('./features/transactions/transactions.routes'),
        data: { title: 'Movimientos' },
      },
      {
        path: 'analytics',
        loadChildren: () => import('./features/analytics/analytics.routes'),
        data: { title: 'Analítica' },
      },
      {
        path: 'accounts',
        loadChildren: () => import('./features/accounts/accounts.routes'),
        data: { title: 'Cuentas' },
      },
      {
        path: 'plan',
        loadChildren: () => import('./features/plan/plan.routes'),
        data: { title: 'Planificación' },
      },
      {
        path: 'vehicles',
        loadChildren: () => import('./features/vehicles/vehicles.routes'),
        data: { title: 'Vehículos' },
      },
      {
        path: 'profile',
        loadChildren: () => import('./features/profile/profile.routes'),
        data: { title: 'Familia y Perfil' },
      },
      {
        path: 'notifications',
        loadChildren: () => import('./features/notifications/notifications.routes'),
        data: { title: 'Notificaciones' },
      },
    ],
  },
  { path: '**', redirectTo: 'home' },
];
