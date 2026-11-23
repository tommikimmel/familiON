import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-analytics-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Analítica</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsPage {}
