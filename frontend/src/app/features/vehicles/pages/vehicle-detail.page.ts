import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-vehicle-detail-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Detalle del vehículo</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleDetailPage {}
