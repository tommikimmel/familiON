import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-login-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Iniciar sesión</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {}
