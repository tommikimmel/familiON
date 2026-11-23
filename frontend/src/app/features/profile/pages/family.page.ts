import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-family-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Familia</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyPage {}
