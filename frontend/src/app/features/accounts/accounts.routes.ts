import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/accounts.page').then((m) => m.AccountsPage),
  },
];

export default routes;
