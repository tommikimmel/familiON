import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-recurring-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Recurrentes</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecurringPage {}
