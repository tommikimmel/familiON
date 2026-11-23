import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-accounts-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Cuentas</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountsPage {}
