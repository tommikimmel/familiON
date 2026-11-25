import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconComponent } from '../../../shared/ui/icon/icon.component';

export type AuthBannerKind = 'error' | 'info' | 'success';

@Component({
  selector: 'fo-auth-banner',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div
      [attr.role]="kind() === 'error' ? 'alert' : 'status'"
      [attr.aria-live]="kind() === 'error' ? 'assertive' : 'polite'"
      class="flex items-start gap-2 rounded-[12px] border px-3 py-2.5 text-[12px] font-medium leading-snug"
      [class.border-danger/30]="kind() === 'error'"
      [class.bg-danger/10]="kind() === 'error'"
      [class.text-danger]="kind() === 'error'"
      [class.border-success/30]="kind() === 'success'"
      [class.bg-success/10]="kind() === 'success'"
      [class.text-success]="kind() === 'success'"
      [class.border-accent/30]="kind() === 'info'"
      [class.bg-accent-soft]="kind() === 'info'"
      [class.text-text]="kind() === 'info'"
    >
      <fo-icon
        [name]="kind() === 'error' ? 'alert-circle' : 'check-circle'"
        [size]="16"
        class="mt-px shrink-0"
      />
      <span>{{ message() }}</span>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthBannerComponent {
  readonly kind = input<AuthBannerKind>('error');
  readonly message = input.required<string>();
}
