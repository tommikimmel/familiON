import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FoIconName, IconComponent } from '../../../shared/ui/icon/icon.component';

export type KpiBadgeTone = 'neutral' | 'accent' | 'success' | 'danger' | 'warning';

@Component({
  selector: 'fo-kpi-card',
  standalone: true,
  imports: [IconComponent],
  template: `
    <section
      class="relative flex h-full flex-col justify-between overflow-hidden rounded-[var(--fo-radius)] border border-border bg-surface p-5 shadow-2xs"
      [class.fo-kpi-glow]="glow()"
      [attr.aria-label]="title()"
    >
      <header class="flex items-start justify-between gap-3">
        <div class="flex min-w-0 items-center gap-2.5">
          @if (icon(); as iconName) {
            <span
              aria-hidden="true"
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-muted"
            >
              <fo-icon [name]="iconName" [size]="16" />
            </span>
          }
          <div class="min-w-0">
            <h2 class="truncate text-sm font-semibold text-text">{{ title() }}</h2>
            @if (subtitle(); as sub) {
              <p class="truncate text-xs text-muted">{{ sub }}</p>
            }
          </div>
        </div>

        @if (badge(); as badgeText) {
          <span
            class="inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-semibold tabular-nums"
            [class.bg-surface-2]="badgeTone() === 'neutral'"
            [class.text-muted]="badgeTone() === 'neutral'"
            [class.bg-accent-soft]="badgeTone() === 'accent'"
            [class.text-accent]="badgeTone() === 'accent'"
            [class.bg-success/12]="badgeTone() === 'success'"
            [class.text-success]="badgeTone() === 'success'"
            [class.bg-danger/12]="badgeTone() === 'danger'"
            [class.text-danger]="badgeTone() === 'danger'"
            [class.bg-warning/12]="badgeTone() === 'warning'"
            [class.text-warning]="badgeTone() === 'warning'"
          >
            {{ badgeText }}
          </span>
        }
      </header>

      <div class="mt-3 flex flex-1 flex-col justify-between">
        <ng-content />
      </div>
    </section>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }

    .fo-kpi-glow {
      background-image: radial-gradient(
        circle at 88% -10%,
        var(--fo-accent-soft) 0%,
        transparent 64%
      );
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KpiCardComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
  readonly badge = input<string | null>(null);
  readonly badgeTone = input<KpiBadgeTone>('neutral');
  readonly icon = input<FoIconName | null>(null);
  readonly glow = input<boolean>(false);
}
