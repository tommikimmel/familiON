import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-verify-email-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Verificar correo</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyEmailPage {}
