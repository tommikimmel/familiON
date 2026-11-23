import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FoIconName, IconComponent } from '../icon/icon.component';

@Component({
  selector: 'fo-empty-state',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div
      class="flex flex-col items-center justify-center rounded-[var(--fo-radius)] border border-dashed border-border bg-surface/60 px-6 py-10 text-center"
    >
      @if (icon(); as iconName) {
        <div
          class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-accent-soft text-accent"
        >
          <fo-icon [name]="iconName" [size]="24" />
        </div>
      }
      <h3 class="text-base font-semibold text-text">{{ title() }}</h3>
      @if (description()) {
        <p class="mt-1 max-w-xs text-sm text-muted">{{ description() }}</p>
      }
      <div class="mt-4 empty:hidden">
        <ng-content />
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly icon = input<FoIconName | null>(null);
  readonly title = input.required<string>();
  readonly description = input<string>('');
}
