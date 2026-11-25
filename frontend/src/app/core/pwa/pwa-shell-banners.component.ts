import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PwaInstallBannerComponent } from './pwa-install-banner.component';
import { PwaUpdateBannerComponent } from './pwa-update-banner.component';

@Component({
  selector: 'fo-pwa-shell-banners',
  standalone: true,
  imports: [PwaInstallBannerComponent, PwaUpdateBannerComponent],
  template: `
    <fo-pwa-update-banner />
    <fo-pwa-install-banner />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PwaShellBannersComponent {}
