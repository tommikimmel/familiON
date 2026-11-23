import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'fo-auth-layout',
  imports: [RouterOutlet],
  template: `
    <main class="min-h-dvh flex items-center justify-center bg-bg px-4 py-8">
      <div class="w-full max-w-sm rounded-[var(--fo-radius)] border border-border bg-surface p-6 shadow-xs">
        <div class="mb-6 flex justify-center">
          <img src="brand/familion-logo-light.svg" alt="FamiliON" class="h-10 w-auto block dark:hidden" />
          <img src="brand/familion-logo-dark.svg" alt="FamiliON" class="h-10 w-auto hidden dark:block" />
        </div>
        <router-outlet />
      </div>
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthLayoutComponent {}
