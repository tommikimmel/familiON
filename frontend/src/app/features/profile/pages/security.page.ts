import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-security-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Seguridad</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SecurityPage {}
