import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-allowances-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Mensualidades</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AllowancesPage {}
