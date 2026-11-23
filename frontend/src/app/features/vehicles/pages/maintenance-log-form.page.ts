import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-maintenance-log-form-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Registrar mantenimiento</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MaintenanceLogFormPage {}
