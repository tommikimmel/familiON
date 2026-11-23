import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'fo-notifications-page',
  template: `<section class="p-4"><h1 class="text-xl font-semibold">Notificaciones</h1></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationsPage {}
