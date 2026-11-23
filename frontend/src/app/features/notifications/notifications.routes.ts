import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/notifications.page').then((m) => m.NotificationsPage),
  },
];

export default routes;
