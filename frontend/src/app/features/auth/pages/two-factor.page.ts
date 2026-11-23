import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-two-factor-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Verificación en dos pasos</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TwoFactorPage {}
