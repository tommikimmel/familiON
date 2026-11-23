import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-transactions-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Movimientos</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionsPage {}
