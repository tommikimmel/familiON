import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonComponent, CardComponent, FoIconName, IconComponent } from '../../../../shared/ui';

export type SectionBadgeTone = 'neutral' | 'danger' | 'success' | 'accent';

@Component({
  selector: 'fo-plan-section-card',
  standalone: true,
  imports: [ButtonComponent, CardComponent, IconComponent, RouterLink],
  template: `
    <fo-card padding="md" class="h-full" [attr.data-testid]="testId()">
      <div class="flex h-full flex-col gap-4">
        <header class="flex items-center justify-between gap-2">
          <div class="flex min-w-0 items-center gap-2.5">
            <span
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent"
              aria-hidden="true"
            >
              <fo-icon [name]="icon()" [size]="17" />
            </span>
            <h2 class="truncate text-base font-semibold text-text">{{ title() }}</h2>
            @if (badgeText(); as badge) {
              <span
                class="inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-[11px] font-semibold tabular-nums"
                [class.bg-danger/12]="badgeTone() === 'danger'"
                [class.text-danger]="badgeTone() === 'danger'"
                [class.bg-success/12]="badgeTone() === 'success'"
                [class.text-success]="badgeTone() === 'success'"
                [class.bg-accent-soft]="badgeTone() === 'accent'"
                [class.text-accent]="badgeTone() === 'accent'"
                [class.bg-surface-2]="badgeTone() === 'neutral'"
                [class.text-muted]="badgeTone() === 'neutral'"
              >
                {{ badge }}
              </span>
            }
          </div>

          <a
            [routerLink]="linkRoute()"
            [attr.aria-label]="'Ver todo en ' + title()"
            class="fo-focus-ring inline-flex shrink-0 items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold text-accent transition-colors duration-150 hover:bg-accent-soft"
          >
            <span>Ver todo</span>
            <span aria-hidden="true">→</span>
          </a>
        </header>

        @if (degraded()) {
          <div
            data-testid="section-degraded-state"
            class="flex flex-1 flex-col items-center justify-center rounded-xl border border-dashed border-border bg-surface-2/40 px-4 py-6 text-center"
          >
            <div
              class="mb-2 flex h-9 w-9 items-center justify-center rounded-full bg-danger/12 text-danger"
              aria-hidden="true"
            >
              <fo-icon name="alert-circle" [size]="18" />
            </div>
            <p class="text-sm font-medium text-text">No se pudo cargar</p>
            <p class="mt-0.5 text-xs text-muted">
              Hubo un problema al obtener esta sección.
            </p>
            <div class="mt-3">
              <fo-button
                variant="ghost"
                size="sm"
                data-testid="section-retry-btn"
                [loading]="retrying()"
                (click)="retry.emit()"
              >
                Reintentar
              </fo-button>
            </div>
          </div>
        } @else {
          <div class="flex flex-1 flex-col">
            <ng-content />
          </div>
        }
      </div>
    </fo-card>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanSectionCardComponent {
  readonly title = input.required<string>();
  readonly icon = input.required<FoIconName>();
  readonly linkRoute = input.required<string>();
  readonly degraded = input<boolean>(false);
  readonly retrying = input<boolean>(false);
  readonly badgeText = input<string | null>(null);
  readonly badgeTone = input<SectionBadgeTone>('neutral');
  readonly testId = input<string | null>(null);

  readonly retry = output<void>();
}
