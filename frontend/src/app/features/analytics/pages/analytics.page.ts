import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { SessionStore } from '../../../core/state/session.store';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { PeriodLabelPipe } from '../../../shared/pipes/period-label.pipe';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { AnalyticsHeaderComponent } from '../components/analytics-header.component';
import { AnalyticsStore } from '../data-access/analytics.store';

@Component({
  selector: 'fo-analytics-page',
  standalone: true,
  providers: [AnalyticsStore],
  imports: [
    AnalyticsHeaderComponent,
    ButtonComponent,
    CardComponent,
    IconComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="mx-auto max-w-7xl space-y-5">
      <fo-analytics-header
        [activeTab]="store.activeTab()"
        [granularity]="viewContext.granularity()"
        [periodLabel]="periodLabel()"
        [lens]="viewContext.lens()"
        [referenceCurrency]="referenceCurrency()"
        (tabChange)="store.setActiveTab($event)"
        (granularityChange)="viewContext.setGranularity($event)"
        (shiftPeriod)="viewContext.shiftPeriod($event)"
        (resetToday)="viewContext.resetToToday()"
        (lensChange)="viewContext.setLens($event)"
      />

      @if (store.activeTab() === 'OVERVIEW') {
        @if (store.loading()) {
          <div class="space-y-4" aria-busy="true">
            <fo-card padding="md">
              <fo-skeleton width="40%" height="1rem" />
              <div class="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
                @for (item of [1, 2, 3, 4]; track item) {
                  <fo-skeleton height="5rem" rounded="lg" />
                }
              </div>
            </fo-card>
          </div>
        } @else if (store.error(); as errorMessage) {
          <fo-card padding="lg" data-testid="analytics-error-state">
            <div class="flex flex-col items-center py-6 text-center">
              <div
                class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-danger/12 text-danger"
              >
                <fo-icon name="alert-circle" [size]="24" />
              </div>
              <h2 class="text-base font-semibold text-text">No pudimos cargar la analítica</h2>
              <p class="mt-1 max-w-sm text-sm text-muted">{{ errorMessage }}</p>
              <div class="mt-4">
                <fo-button data-testid="analytics-retry-btn" (click)="store.reload()">
                  Reintentar
                </fo-button>
              </div>
            </div>
          </fo-card>
        }
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsPage {
  protected readonly store = inject(AnalyticsStore);
  protected readonly viewContext = inject(ViewContextStore);
  private readonly session = inject(SessionStore);
  private readonly periodPipe = new PeriodLabelPipe();

  protected readonly referenceCurrency = computed(
    () => this.session.me()?.family.referenceCurrency ?? 'USD',
  );

  protected readonly periodLabel = computed(() => {
    const serverLabel = this.store.summary()?.period.label;
    if (serverLabel && serverLabel.trim().length > 0) {
      return serverLabel;
    }
    return this.periodPipe.transform(
      this.viewContext.anchorDate(),
      this.viewContext.granularity(),
    );
  });
}
