import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-register-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Crear cuenta</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {}
