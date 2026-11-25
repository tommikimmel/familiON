import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonComponent } from '../../../../shared/ui';

export type SectionBadgeTone = 'neutral' | 'danger' | 'success' | 'accent';

/**
 * Card shell for every Plan overview section (mock `.fn-card` with a title row).
 * With `flatOnMobile` the card chrome is dropped below the desktop breakpoint, so the section
 * reads as the mock's `sec-head` + `tx-list` pair on phones.
 */
@Component({
  selector: 'fo-plan-section-card',
  standalone: true,
  imports: [ButtonComponent, RouterLink],
  template: `
    <div
      class="fo-card plan-section"
      [class.fo-hero-glow]="glow()"
      [class.plan-section--flat]="flatOnMobile()"
      [attr.data-testid]="testId()"
    >
      <header class="pl-head">
        <div class="min-w-0">
          <h2 class="pl-title truncate" [class.plan-flat-title]="flatOnMobile()">
            {{ title() }}
          </h2>
          @if (description(); as desc) {
            <p class="pl-sub mt-0.5" data-testid="section-description">{{ desc }}</p>
          }
        </div>
        <div class="flex shrink-0 items-center gap-2">
          @if (badgeText(); as badge) {
            <span
              class="fo-pill-tag fo-num"
              [class.pl-pill-danger]="badgeTone() === 'danger'"
              [class.fo-pill-accent]="badgeTone() === 'accent' || badgeTone() === 'success'"
            >
              {{ badge }}
            </span>
          }
          <a
            [routerLink]="linkRoute()"
            [attr.aria-label]="'Ver todo en ' + title()"
            class="fo-sec-link fo-focus-ring rounded-md"
          >
            Ver todo
          </a>
        </div>
      </header>

      @if (degraded()) {
        <div data-testid="section-degraded-state" class="pl-empty mt-2.5">
          <p class="fo-tx-title pl-danger">No se pudo cargar</p>
          <p class="pl-sub">Hubo un problema al obtener esta sección.</p>
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
      } @else {
        <div class="plan-section-body">
          <ng-content />
        </div>
      }
    </div>
  `,
  styleUrl: '../../plan-ui.css',
  styles: `
    :host {
      height: 100%;
    }
    .plan-section {
      display: flex;
      flex-direction: column;
      height: 100%;
    }
    .plan-section-body {
      display: flex;
      flex: 1;
      flex-direction: column;
      margin-top: 10px;
    }
    @media (max-width: 1023.98px) {
      .plan-section--flat {
        overflow: visible;
        padding: 0;
        border: none;
        background: transparent;
        box-shadow: none;
      }
      .plan-section--flat .plan-section-body {
        margin-top: 8px;
      }
      .plan-flat-title {
        font-size: 12px;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanSectionCardComponent {
  readonly title = input.required<string>();
  readonly linkRoute = input.required<string>();
  /** One-line explanation of what the section is for, shown under the title. */
  readonly description = input<string | null>(null);
  readonly degraded = input<boolean>(false);
  readonly retrying = input<boolean>(false);
  readonly badgeText = input<string | null>(null);
  readonly badgeTone = input<SectionBadgeTone>('neutral');
  readonly testId = input<string | null>(null);
  readonly glow = input<boolean>(false);
  readonly flatOnMobile = input<boolean>(false);

  readonly retry = output<void>();
}
