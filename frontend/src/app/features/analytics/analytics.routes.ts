import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/analytics.page').then((m) => m.AnalyticsPage),
  },
];

export default routes;
