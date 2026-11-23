import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-exchanges-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Cambio de moneda</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExchangesPage {}
