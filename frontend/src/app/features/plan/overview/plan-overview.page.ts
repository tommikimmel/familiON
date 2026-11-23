import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-plan-overview-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Planificación</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanOverviewPage {}
