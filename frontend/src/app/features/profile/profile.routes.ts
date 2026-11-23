import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/profile.page').then((m) => m.ProfilePage),
    data: { title: 'Familia y Perfil' },
  },
  {
    path: 'family',
    loadComponent: () => import('./pages/family.page').then((m) => m.FamilyPage),
    data: { title: 'Familia' },
  },
  {
    path: 'security',
    loadComponent: () => import('./pages/security.page').then((m) => m.SecurityPage),
    data: { title: 'Seguridad' },
  },
];

export default routes;
