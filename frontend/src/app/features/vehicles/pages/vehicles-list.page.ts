import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-vehicles-list-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Vehículos</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehiclesListPage {}
