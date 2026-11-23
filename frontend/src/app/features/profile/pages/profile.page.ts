import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-profile-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Familia y Perfil</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {}
