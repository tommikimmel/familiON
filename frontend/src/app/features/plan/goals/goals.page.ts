import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-goals-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Metas</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsPage {}
