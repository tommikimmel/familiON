import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { ButtonComponent, IconComponent, SkeletonComponent } from '../../../../shared/ui';
import { toNumber } from '../../../../shared/utils/decimal';
import {
  CancelRemainingMode,
  CancelRemainingPayload,
  InstallmentPlanDetail,
  InstallmentStatus,
} from '../data-access/installments.models';
import {
  formatIsoDate,
  formatSurchargePct,
  formatTodayIso,
} from '../utils/installments-format';

@Component({
  selector: 'fo-installment-detail-sheet',
  standalone: true,
  imports: [ButtonComponent, IconComponent, MoneyPipe, SkeletonComponent],
  template: `
    @if (loading()) {
      <div class="space-y-4" aria-busy="true" aria-label="Cargando detalle del plan">
        <fo-skeleton height="5rem" rounded="lg" />
        <div class="space-y-2">
          <fo-skeleton height="2.75rem" rounded="md" />
          <fo-skeleton height="2.75rem" rounded="md" />
          <fo-skeleton height="2.75rem" rounded="md" />
        </div>
      </div>
    } @else if (error(); as errMsg) {
      <div class="space-y-4 py-4 text-center">
        <p class="text-sm text-danger">{{ errMsg }}</p>
        <fo-button variant="primary" size="sm" (click)="retry.emit()">Reintentar</fo-button>
      </div>
    } @else if (detail(); as plan) {
      <div class="space-y-5">
        <div class="rounded-xl border border-border bg-surface-2/60 p-3.5">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p class="text-xs text-muted">
                {{ plan.creditCardAccount.name }} · {{ plan.category.name }}
              </p>
              <p class="mt-0.5 text-xs text-muted">
                Compra del {{ formatDate(plan.purchaseDate) }} ·
                Cuota {{ plan.paidInstallments }} de {{ plan.totalInstallments }}
              </p>
            </div>
            <span
              class="rounded-full px-2.5 py-0.5 text-xs font-medium tabular-nums"
              [class.bg-accent-soft]="!plan.hasInterest"
              [class.text-accent]="!plan.hasInterest"
              [class.bg-warning/15]="plan.hasInterest"
              [class.text-warning]="plan.hasInterest"
            >
              @if (!plan.hasInterest) {
                Sin interés
              } @else if (plan.surchargePct !== null) {
                Recargo {{ formatPct(plan.surchargePct) }}
              } @else {
                Con interés
              }
            </span>
          </div>

          <div class="mt-3 grid grid-cols-2 gap-3 border-t border-border pt-3 text-sm">
            <div>
              <p class="text-xs text-muted">Total financiado</p>
              <p class="font-semibold text-text tabular-nums">
                {{ plan.financedTotal | money: plan.currencyCode }}
              </p>
              @if (plan.hasInterest && plan.cashPrice !== null) {
                <p class="text-xs text-muted tabular-nums">
                  Contado: {{ plan.cashPrice | money: plan.currencyCode }}
                  @if (plan.surchargeAmount !== null) {
                    (+{{ plan.surchargeAmount | money: plan.currencyCode }})
                  }
                </p>
              }
            </div>

            <div class="text-right">
              <p class="text-xs text-muted">Restante</p>
              <p class="font-semibold text-text tabular-nums">
                {{ plan.remainingAmount | money: plan.currencyCode }}
              </p>
              @if (showRealRemaining()) {
                <p class="text-xs font-medium text-accent tabular-nums">
                  ≈ {{ plan.realRemainingAmount | money: plan.currencyCode }} en plata de hoy
                </p>
              }
            </div>
          </div>

          @if (showRealRemaining()) {
            <p class="mt-2 text-[11px] text-muted">
              Las cuotas fijas se licúan con la inflación.
            </p>
          }
        </div>

        <section aria-label="Cronograma de cuotas" class="space-y-2">
          <h3 class="text-xs font-semibold tracking-wide text-muted uppercase">Cronograma</h3>
          <ul class="divide-y divide-border rounded-xl border border-border bg-surface">
            @for (item of plan.installments; track item.number) {
              <li
                data-testid="schedule-item"
                class="flex items-center justify-between gap-3 px-3.5 py-2.5 text-sm"
              >
                <div class="min-w-0">
                  <p class="font-medium text-text tabular-nums">
                    Cuota {{ item.number }} de {{ plan.totalInstallments }}
                  </p>
                  <p class="text-xs text-muted">{{ formatDate(item.date) }}</p>
                </div>

                <div class="flex items-center gap-2.5">
                  <span class="font-semibold text-text tabular-nums">
                    {{ item.amount | money: plan.currencyCode }}
                  </span>
                  <span
                    class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
                    [class.bg-success/15]="item.status === 'POSTED'"
                    [class.text-success]="item.status === 'POSTED'"
                    [class.bg-surface-2]="item.status === 'PROJECTED'"
                    [class.text-muted]="item.status === 'PROJECTED'"
                    [class.bg-danger/12]="item.status === 'CANCELLED'"
                    [class.text-danger]="item.status === 'CANCELLED'"
                  >
                    @if (item.status === 'POSTED') {
                      <fo-icon name="check" [size]="12" />
                    }
                    <span>{{ statusLabel(item.status) }}</span>
                  </span>
                </div>
              </li>
            }
          </ul>
        </section>

        @if (canCancelOrPrepay()) {
          <section class="space-y-3 border-t border-border pt-4">
            @if (mutationError(); as mutErr) {
              <p
                role="alert"
                class="rounded-xl border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger"
              >
                {{ mutErr }}
              </p>
            }

            @if (pendingMode() === null) {
              <div class="flex flex-wrap items-center justify-end gap-2">
                <fo-button
                  variant="ghost"
                  size="sm"
                  data-testid="start-cancel-remaining-btn"
                  (click)="startConfirmation('CANCEL')"
                >
                  Cancelar cuotas restantes
                </fo-button>
                <fo-button
                  variant="primary"
                  size="sm"
                  data-testid="start-prepay-btn"
                  (click)="startConfirmation('PREPAY')"
                >
                  Precancelar
                </fo-button>
              </div>
            } @else if (pendingMode() === 'CANCEL') {
              <div
                data-testid="confirm-cancel-panel"
                class="space-y-3 rounded-xl border border-danger/30 bg-danger/5 p-3.5"
              >
                <p class="text-sm font-semibold text-text">¿Cancelar las cuotas restantes?</p>
                <p class="text-xs text-muted">
                  Las {{ projectedCount() }} cuotas proyectadas se marcarán como canceladas y dejarán
                  de impactar en tus compromisos futuros. Las cuotas ya pagadas se mantienen.
                </p>
                <div class="flex items-center justify-end gap-2">
                  <fo-button
                    variant="ghost"
                    size="sm"
                    [disabled]="mutating()"
                    (click)="abortConfirmation()"
                  >
                    Volver
                  </fo-button>
                  <fo-button
                    variant="danger"
                    size="sm"
                    data-testid="confirm-cancel-remaining-btn"
                    [loading]="mutating()"
                    (click)="confirmAction()"
                  >
                    Confirmar cancelación
                  </fo-button>
                </div>
              </div>
            } @else if (pendingMode() === 'PREPAY') {
              <div
                data-testid="confirm-prepay-panel"
                class="space-y-3 rounded-xl border border-accent/30 bg-accent-soft/40 p-3.5"
              >
                <p class="text-sm font-semibold text-text">Precancelar cuotas restantes</p>
                <p class="text-xs text-muted">
                  Se consolidarán las {{ projectedCount() }} cuotas pendientes por
                  <strong class="font-semibold text-text tabular-nums">
                    {{ plan.remainingAmount | money: plan.currencyCode }}
                  </strong>
                  en un único pago en la fecha elegida.
                </p>

                <div>
                  <label
                    for="fo-prepay-date"
                    class="mb-1 block text-xs font-medium text-text"
                  >
                    Fecha de precancelación
                  </label>
                  <input
                    id="fo-prepay-date"
                    data-testid="prepay-date-input"
                    type="date"
                    [value]="prepayDate()"
                    (input)="onPrepayDateInput($event)"
                    class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
                  />
                </div>

                <div class="flex items-center justify-end gap-2">
                  <fo-button
                    variant="ghost"
                    size="sm"
                    [disabled]="mutating()"
                    (click)="abortConfirmation()"
                  >
                    Volver
                  </fo-button>
                  <fo-button
                    variant="primary"
                    size="sm"
                    data-testid="confirm-prepay-btn"
                    [disabled]="!prepayDate()"
                    [loading]="mutating()"
                    (click)="confirmAction()"
                  >
                    Confirmar precancelación
                  </fo-button>
                </div>
              </div>
            }
          </section>
        }
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstallmentDetailSheetComponent {
  readonly detail = input<InstallmentPlanDetail | null>(null);
  readonly loading = input<boolean>(false);
  readonly error = input<string | null>(null);
  readonly mutating = input<boolean>(false);
  readonly mutationError = input<string | null>(null);

  readonly cancelRemaining = output<CancelRemainingPayload>();
  readonly retry = output<void>();

  protected readonly pendingMode = signal<CancelRemainingMode | null>(null);
  protected readonly prepayDate = signal<string>(formatTodayIso());

  protected readonly projectedCount = computed<number>(
    () => this.detail()?.installments.filter((item) => item.status === 'PROJECTED').length ?? 0,
  );

  protected readonly canCancelOrPrepay = computed<boolean>(() => {
    const current = this.detail();
    return current !== null && current.status === 'ACTIVE' && this.projectedCount() > 0;
  });

  protected readonly showRealRemaining = computed<boolean>(() => {
    const current = this.detail();
    return (
      current !== null &&
      current.status === 'ACTIVE' &&
      current.realRemainingAmount !== null &&
      toNumber(current.remainingAmount) > 0
    );
  });

  constructor() {
    effect(() => {
      this.detail();
      this.pendingMode.set(null);
      this.prepayDate.set(formatTodayIso());
    });
  }

  protected startConfirmation(mode: CancelRemainingMode): void {
    this.pendingMode.set(mode);
    if (mode === 'PREPAY') {
      this.prepayDate.set(formatTodayIso());
    }
  }

  protected abortConfirmation(): void {
    this.pendingMode.set(null);
  }

  protected onPrepayDateInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.prepayDate.set(value);
  }

  protected confirmAction(): void {
    const mode = this.pendingMode();
    if (!mode) {
      return;
    }
    if (mode === 'PREPAY') {
      const date = this.prepayDate().trim();
      if (!date) {
        return;
      }
      this.cancelRemaining.emit({ mode: 'PREPAY', prepayDate: date });
      return;
    }
    this.cancelRemaining.emit({ mode: 'CANCEL' });
  }

  protected formatDate(isoDate: string): string {
    return formatIsoDate(isoDate);
  }

  protected formatPct(value: string): string {
    return formatSurchargePct(value);
  }

  protected statusLabel(status: InstallmentStatus): string {
    switch (status) {
      case 'POSTED':
        return 'Pagada';
      case 'PROJECTED':
        return 'Pendiente';
      case 'CANCELLED':
        return 'Cancelada';
    }
  }
}
