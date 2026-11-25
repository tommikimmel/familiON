import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./overview/plan-overview.page').then((m) => m.PlanOverviewPage),
    data: { title: 'Planificación · Cuotas, Fijos, Divisas y Metas' },
  },
  {
    path: 'installments',
    loadComponent: () => import('./installments/installments.page').then((m) => m.InstallmentsPage),
    data: { title: 'Cuotas' },
  },
  {
    path: 'recurring',
    loadComponent: () => import('./recurring/recurring.page').then((m) => m.RecurringPage),
    data: { title: 'Recurrentes' },
  },
  {
    path: 'goals',
    loadComponent: () => import('./goals/goals.page').then((m) => m.GoalsPage),
    data: { title: 'Metas' },
  },
  {
    path: 'exchanges',
    loadComponent: () => import('./exchanges/exchanges.page').then((m) => m.ExchangesPage),
    data: { title: 'Cambio de moneda' },
  },
  {
    path: 'allowances',
    loadComponent: () => import('./allowances/allowances.page').then((m) => m.AllowancesPage),
    data: { title: 'Mensualidades' },
  },
];

export default routes;
