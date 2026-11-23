import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-forgot-password-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Recuperar contraseña</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgotPasswordPage {}
