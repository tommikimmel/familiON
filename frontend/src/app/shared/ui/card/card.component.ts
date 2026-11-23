import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'fo-card',
  standalone: true,
  template: `
    <div
      class="rounded-[var(--fo-radius)] border border-border bg-surface shadow-2xs transition-colors duration-150"
      [class.p-0]="padding() === 'none'"
      [class.p-3]="padding() === 'sm'"
      [class.p-4]="padding() === 'md'"
      [class.p-6]="padding() === 'lg'"
      [class.hover:border-accent]="interactive()"
    >
      <ng-content />
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardComponent {
  readonly padding = input<'none' | 'sm' | 'md' | 'lg'>('md');
  readonly interactive = input<boolean>(false);
}
