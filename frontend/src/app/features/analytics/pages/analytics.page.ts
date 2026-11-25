import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  ExportFormat,
  ExportsApi,
  saveExportFile,
} from '../../../core/finance/exports.api';
import { SessionStore } from '../../../core/state/session.store';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { PeriodLabelPipe } from '../../../shared/pipes/period-label.pipe';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AnalyticsBreakdownsComponent } from '../components/analytics-breakdowns.component';
import { AnalyticsCalendarComponent } from '../components/analytics-calendar.component';
import { AnalyticsExportMenuComponent } from '../components/analytics-export-menu.component';
import { AnalyticsEvolutionChartComponent } from '../components/analytics-evolution-chart.component';
import { AnalyticsHeaderComponent } from '../components/analytics-header.component';
import { AnalyticsSummaryComponent } from '../components/analytics-summary.component';
import { AnalyticsStore } from '../data-access/analytics.store';

@Component({
  selector: 'fo-analytics-page',
  standalone: true,
  providers: [AnalyticsStore],
  imports: [
    AnalyticsBreakdownsComponent,
    AnalyticsCalendarComponent,
    AnalyticsEvolutionChartComponent,
    AnalyticsExportMenuComponent,
    AnalyticsHeaderComponent,
    AnalyticsSummaryComponent,
    ButtonComponent,
    CardComponent,
    IconComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="mx-auto max-w-7xl space-y-5">
      <div class="flex items-center justify-end gap-2">
        <span class="text-xs text-muted">Resumen mensual {{ exportYear() }}</span>
        <fo-analytics-export-menu
          [loading]="exporting()"
          (formatSelected)="onExport($event)"
        />
      </div>

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
          <div data-testid="analytics-loading-skeleton" class="space-y-5" aria-busy="true">
            <div class="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
              @for (item of [1, 2, 3, 4]; track item) {
                <fo-card padding="md">
                  <fo-skeleton width="45%" height="0.75rem" />
                  <div class="mt-3">
                    <fo-skeleton width="70%" height="1.75rem" />
                  </div>
                  <div class="mt-3">
                    <fo-skeleton width="80%" height="0.75rem" />
                  </div>
                </fo-card>
              }
            </div>

            <fo-card padding="md">
              <fo-skeleton width="35%" height="1rem" />
              <div class="mt-4">
                <fo-skeleton height="230px" rounded="lg" />
              </div>
            </fo-card>

            <div class="grid grid-cols-1 gap-5 lg:grid-cols-2">
              @for (card of [1, 2]; track card) {
                <fo-card padding="md">
                  <fo-skeleton width="40%" height="1rem" />
                  <div class="mt-4 flex justify-center">
                    <fo-skeleton width="180px" height="180px" rounded="full" />
                  </div>
                </fo-card>
              }
            </div>
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
        } @else {
          <div data-testid="analytics-overview-content" class="space-y-5">
            <fo-analytics-summary [summary]="store.summary()" [view]="viewContext.view()" />

            <fo-analytics-evolution-chart
              [series]="store.series()"
              [granularity]="viewContext.granularity()"
            />

            <fo-analytics-breakdowns
              [byCategory]="store.byCategory()"
              [byPerson]="store.byPerson()"
              [showPerson]="store.showPersonSection()"
              [lens]="viewContext.lens()"
            />
          </div>
        }
      } @else {
        <fo-analytics-calendar
          [yearMonth]="store.calendarMonth()"
          [calendar]="store.calendar()"
          [loading]="store.calendarLoading()"
          [error]="store.calendarError()"
          [baseCurrency]="baseCurrency()"
          (shiftMonth)="store.shiftCalendarMonth($event)"
          (resetToday)="store.resetCalendarMonth()"
          (retry)="store.reloadCalendar()"
        />
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsPage {
  protected readonly store = inject(AnalyticsStore);
  protected readonly viewContext = inject(ViewContextStore);
  private readonly session = inject(SessionStore);
  private readonly exportsApi = inject(ExportsApi);
  private readonly toast = inject(ToastService);
  private readonly periodPipe = new PeriodLabelPipe();

  protected readonly exporting = signal<boolean>(false);

  protected readonly exportYear = computed(() => {
    const year = Number(this.viewContext.anchorDate().slice(0, 4));
    return Number.isFinite(year) && year > 0 ? year : new Date().getFullYear();
  });

  protected readonly baseCurrency = computed(
    () => this.session.me()?.family.baseCurrency ?? 'ARS',
  );

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

  protected async onExport(format: ExportFormat): Promise<void> {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    try {
      const file = await firstValueFrom(
        this.exportsApi.monthlySummary({
          format,
          year: this.exportYear(),
          view: this.viewContext.view(),
          lens: this.viewContext.lens(),
        }),
      );
      saveExportFile(file);
    } catch {
      this.toast.error('No se pudo exportar el resumen mensual. Intenta de nuevo.');
    } finally {
      this.exporting.set(false);
    }
  }
}
