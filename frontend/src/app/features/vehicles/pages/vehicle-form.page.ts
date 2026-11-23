import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-vehicle-form-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Vehículo</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleFormPage {}
