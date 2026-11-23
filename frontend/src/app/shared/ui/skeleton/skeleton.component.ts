import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'fo-skeleton',
  standalone: true,
  template: `
    <span
      aria-hidden="true"
      class="fo-skeleton-shimmer block"
      [style.width]="width()"
      [style.height]="height()"
      [class.rounded-sm]="rounded() === 'sm'"
      [class.rounded-lg]="rounded() === 'md'"
      [class.rounded-[var(--fo-radius)]]="rounded() === 'lg'"
      [class.rounded-full]="rounded() === 'full'"
    ></span>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkeletonComponent {
  readonly width = input<string>('100%');
  readonly height = input<string>('1rem');
  readonly rounded = input<'sm' | 'md' | 'lg' | 'full'>('md');
}
