import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-fuel-log-form-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Cargar combustible</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FuelLogFormPage {}
