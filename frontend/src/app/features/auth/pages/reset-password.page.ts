import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-reset-password-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Restablecer contraseña</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordPage {}
