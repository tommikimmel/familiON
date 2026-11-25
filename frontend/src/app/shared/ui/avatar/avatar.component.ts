import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Initials avatar (mock `.avatar` 30px / `.avatar-circle` 36px). */
@Component({
  selector: 'fo-avatar',
  standalone: true,
  template: `
    <span
      class="fo-avatar"
      [style.width.px]="size()"
      [style.height.px]="size()"
      [style.font-size.px]="size() >= 36 ? 12 : 11"
      [style.background]="color()"
      [attr.aria-label]="name()"
      role="img"
    >
      {{ initials() }}
    </span>
  `,
  styles: `
    :host {
      display: inline-flex;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvatarComponent {
  readonly name = input.required<string>();
  readonly size = input<number>(30);
  readonly color = input<string | null>(null);

  readonly initials = computed(() => {
    const parts = this.name()
      .trim()
      .split(/\s+/)
      .filter((part) => part.length > 0);
    if (parts.length === 0) {
      return '?';
    }
    const first = parts[0].charAt(0);
    const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
    return `${first}${last}`.toUpperCase();
  });
}
