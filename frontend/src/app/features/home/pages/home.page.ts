import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-home-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Inicio</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {}
