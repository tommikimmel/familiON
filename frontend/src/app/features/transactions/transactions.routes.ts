import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/transactions.page').then((m) => m.TransactionsPage),
  },
];

export default routes;
