import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

/** Accessible on/off switch (mock `.mini-switch`). */
@Component({
  selector: 'fo-switch',
  standalone: true,
  template: `
    <button
      type="button"
      role="switch"
      [attr.aria-checked]="checked()"
      [attr.aria-label]="ariaLabel()"
      [disabled]="disabled()"
      (click)="toggle()"
      class="fo-switch-btn fo-focus-ring"
    >
      <span class="fo-mini-switch" [class.is-on]="checked()" aria-hidden="true"></span>
    </button>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .fo-switch-btn {
      display: inline-flex;
      padding: 2px;
      border: none;
      border-radius: 999px;
      background: transparent;
      cursor: pointer;
    }

    .fo-switch-btn:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SwitchComponent {
  readonly checked = model<boolean>(false);
  readonly ariaLabel = input.required<string>();
  readonly disabled = input<boolean>(false);

  protected toggle(): void {
    if (!this.disabled()) {
      this.checked.update((value) => !value);
    }
  }
}
