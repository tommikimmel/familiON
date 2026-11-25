import { Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/vehicles-list.page').then((m) => m.VehiclesListPage),
    data: { title: 'Vehículos' },
  },
  {
    path: 'new',
    loadComponent: () => import('./pages/vehicle-form.page').then((m) => m.VehicleFormPage),
    data: { title: 'Nuevo vehículo' },
  },
  {
    path: ':id',
    loadComponent: () => import('./pages/vehicle-detail.page').then((m) => m.VehicleDetailPage),
    data: { title: 'Vehículos' },
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./pages/vehicle-form.page').then((m) => m.VehicleFormPage),
    data: { title: 'Editar vehículo' },
  },
  {
    path: ':id/fuel/new',
    loadComponent: () => import('./pages/fuel-log-form.page').then((m) => m.FuelLogFormPage),
    data: { title: 'Cargar combustible' },
  },
  {
    path: ':id/maintenance/new',
    loadComponent: () => import('./pages/maintenance-log-form.page').then((m) => m.MaintenanceLogFormPage),
    data: { title: 'Registrar mantenimiento' },
  },
];

export default routes;
