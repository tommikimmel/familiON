import { EnvironmentProviders, inject, makeEnvironmentProviders, provideEnvironmentInitializer } from '@angular/core';
import { AppUpdateService } from './app-update.service';
import { InstallPromptService } from './install-prompt.service';
import { NotificationBadgePollerService } from './notification-badge-poller.service';
import { PushNotificationsService } from './push-notifications.service';

export function providePwa(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideEnvironmentInitializer(() => {
      inject(NotificationBadgePollerService);
      inject(PushNotificationsService);
      inject(AppUpdateService);
      inject(InstallPromptService);
    }),
  ]);
}
