import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { PillTagComponent } from '../../../shared/ui/pill-tag/pill-tag.component';

/** Desktop KPI card (mock `.kpi-grid > .fn-card`: `kpi-label` + pill, projected `kpi-val` / `kpi-sub`). */
@Component({
  selector: 'fo-kpi-card',
  standalone: true,
  imports: [CardComponent, PillTagComponent],
  template: `
    <fo-card class="h-full" [glow]="glow()">
      <section [attr.aria-label]="title()">
        <header class="kpi-label">
          <h2 class="truncate">{{ title() }}</h2>
          @if (badge(); as badgeText) {
            <fo-pill-tag class="shrink-0 tabular-nums" [accent]="badgeAccent()">
              {{ badgeText }}
            </fo-pill-tag>
          }
        </header>
        <ng-content />
      </section>
    </fo-card>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }
    .kpi-label {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      font-size: 11.5px;
      font-weight: 500;
      color: var(--fo-text-muted);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KpiCardComponent {
  readonly title = input.required<string>();
  readonly badge = input<string | null>(null);
  readonly badgeAccent = input<boolean>(false);
  readonly glow = input<boolean>(false);
}
