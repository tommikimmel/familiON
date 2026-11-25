import { ChangeDetectionStrategy, Component, ViewEncapsulation } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LogoComponent } from '../../shared/ui/logo/logo.component';
import { ToastComponent } from '../../shared/ui/toast/toast.component';

/**
 * Access screens shell (mock splash `.splash-overlay` + `.logo-anim` + `.modal-box`).
 *
 * Styles are unencapsulated on purpose: the `.fo-auth-*` classes below are the visual
 * language shared by every page under `features/auth` (mock `.d-input`, labels, links,
 * `.mini-switch`, OTP boxes). Every selector is namespaced with `fo-auth` to avoid leaks.
 */
@Component({
  selector: 'fo-auth-layout',
  imports: [RouterOutlet, LogoComponent, ToastComponent],
  encapsulation: ViewEncapsulation.None,
  template: `
    <main class="fo-auth">
      <div class="fo-auth-brand">
        <fo-logo [animate]="true" class="fo-auth-logo" />
        <p class="fo-auth-sub">Las finanzas de tu familia, sin inflación</p>
      </div>
      <div class="fo-auth-card">
        <router-outlet />
      </div>
    </main>
    <fo-toast />
  `,
  styles: `
    .fo-auth {
      min-height: 100dvh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 22px;
      padding: 32px 16px;
      background: var(--fo-bg);
      color: var(--fo-text);
    }
    .fo-auth-brand {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
    }
    .fo-auth-logo svg {
      width: 210px;
      height: 62px;
    }
    .fo-auth-sub {
      font-size: 12px;
      font-weight: 500;
      color: var(--fo-text-muted);
      animation: fo-fade-slide-up 0.35s var(--fo-ease-smooth) 0.25s backwards;
    }
    .fo-auth-card {
      width: 100%;
      max-width: 400px;
      background: var(--fo-surface);
      border: 1px solid var(--fo-border);
      border-radius: 20px;
      padding: 20px;
      box-shadow: var(--fo-shadow);
      animation: fo-modal-enter 0.34s var(--fo-ease-spring) 0.12s backwards;
    }
    @media (min-width: 1024px) {
      .fo-auth-card {
        padding: 22px 24px;
        box-shadow: 0 24px 60px rgba(15, 23, 42, 0.08);
      }
      .dark .fo-auth-card {
        box-shadow: 0 24px 60px rgba(0, 0, 0, 0.36);
      }
    }
    .fo-auth fo-password-input,
    .fo-auth fo-otp-code-input,
    .fo-auth fo-auth-banner,
    .fo-auth fo-button[type='submit'] {
      display: block;
    }
    .fo-auth fo-button[type='submit'] .fo-btn {
      width: 100%;
    }
    .fo-auth-title {
      font-size: 18px;
      font-weight: 700;
      letter-spacing: -0.02em;
      color: var(--fo-text);
    }
    .fo-auth-lead {
      font-size: 12.5px;
      line-height: 1.5;
      color: var(--fo-text-2);
    }
    .fo-auth-label {
      display: block;
      font-size: 11px;
      font-weight: 600;
      color: var(--fo-text-muted);
    }
    .fo-auth-input {
      display: block;
      width: 100%;
      border: 1px solid var(--fo-border);
      background: var(--fo-bg);
      color: var(--fo-text);
      font: inherit;
      font-size: 13px;
      padding: 10px 12px;
      border-radius: 10px;
      outline: none;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }
    .fo-auth-input::placeholder {
      color: var(--fo-text-muted);
      opacity: 0.75;
    }
    .fo-auth-input:focus {
      border-color: var(--fo-accent);
      box-shadow: 0 0 0 3px var(--fo-accent-soft);
    }
    .fo-auth-input[aria-invalid='true'] {
      border-color: var(--fo-danger);
    }
    .fo-auth-input:disabled {
      opacity: 0.6;
    }
    .fo-auth-input--icon {
      padding-right: 44px;
    }
    .fo-auth-otp {
      height: 48px;
      padding: 0;
      border-radius: 12px;
      text-align: center;
      font-size: 19px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .fo-auth-error {
      font-size: 11px;
      font-weight: 500;
      color: var(--fo-danger);
    }
    .fo-auth-hint {
      font-size: 11px;
      color: var(--fo-text-muted);
    }
    .fo-auth-link {
      border-radius: 6px;
      font-weight: 600;
      color: var(--fo-accent);
      cursor: pointer;
    }
    .fo-auth-link:hover {
      text-decoration: underline;
    }
    .fo-auth-link:disabled {
      color: var(--fo-text-muted);
      pointer-events: none;
    }
    .fo-auth-foot {
      text-align: center;
      font-size: 12px;
      color: var(--fo-text-muted);
    }
    .fo-auth-switch {
      appearance: none;
      position: relative;
      flex-shrink: 0;
      width: 28px;
      height: 16px;
      border-radius: 999px;
      background: var(--fo-border);
      cursor: pointer;
      transition: background 0.2s ease;
    }
    .fo-auth-switch::after {
      content: '';
      position: absolute;
      top: 2px;
      left: 2px;
      width: 12px;
      height: 12px;
      border-radius: 999px;
      background: #fff;
      transition: transform 0.24s var(--fo-ease-spring);
    }
    .fo-auth-switch:checked {
      background: var(--fo-accent);
    }
    .fo-auth-switch:checked::after {
      transform: translateX(12px);
    }
    .fo-auth-switch:disabled {
      opacity: 0.45;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthLayoutComponent {}
