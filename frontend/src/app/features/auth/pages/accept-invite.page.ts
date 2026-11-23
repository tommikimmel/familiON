import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-accept-invite-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Aceptar invitación</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AcceptInvitePage {}
