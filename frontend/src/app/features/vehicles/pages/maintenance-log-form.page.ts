import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AccountsStore } from '../../../core/finance/accounts.store';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { SessionStore } from '../../../core/state/session.store';
import { CardComponent, IconComponent, SkeletonComponent, ToastService } from '../../../shared/ui';
import { toNumber } from '../../../shared/utils/decimal';
import { MaintenanceLogFormComponent } from '../components/maintenance-log-form.component';
import { VehiclesApi } from '../data-access/vehicles.api';
import { MaintenanceLogCreate, Vehicle } from '../data-access/vehicles.models';
import { VehiclesStore } from '../data-access/vehicles.store';
import { parseProblemError } from '../utils/problem-errors';

@Component({
  selector: 'fo-maintenance-log-form-page',
  standalone: true,
  imports: [CardComponent, IconComponent, MaintenanceLogFormComponent, SkeletonComponent],
  template: `
    <section class="mx-auto w-full max-w-2xl space-y-3 lg:space-y-4">
      <header class="flex items-center gap-3">
        <button
          type="button"
          aria-label="Volver al vehículo"
          (click)="goBack()"
          class="fo-focus-ring flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-xl border border-border bg-surface text-text transition-colors hover:bg-surface-2"
        >
          <fo-icon name="chevron-left" [size]="20" />
        </button>

        <div class="min-w-0 flex-1">
          <h1 class="fo-page-title truncate text-text lg:sr-only">Registrar mantenimiento</h1>
          @if (vehicle(); as v) {
            <p class="truncate mt-0.5 text-[11px] text-muted">
              {{ v.nickname }} · {{ v.licensePlate }}
            </p>
          }
        </div>
      </header>

      @if (loadingVehicle()) {
        <fo-card padding="md">
          <div class="space-y-4" aria-busy="true">
            <fo-skeleton height="40px" />
            <fo-skeleton height="40px" />
            <fo-skeleton height="120px" />
          </div>
        </fo-card>
      } @else {
        <fo-card padding="md">
          <fo-maintenance-log-form
            [vehicle]="vehicle()"
            [accounts]="usableAccounts()"
            [defaultAccountId]="defaultAccountId()"
            [enabledCurrencies]="enabledCurrencies()"
            [defaultCurrency]="baseCurrency()"
            [saving]="saving()"
            [errorMessage]="submitError()"
            [fieldErrors]="fieldErrors()"
            (submitted)="handleSubmit($event)"
            (cancelled)="goBack()"
          />
        </fo-card>
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MaintenanceLogFormPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(VehiclesApi);
  private readonly vehiclesStore = inject(VehiclesStore);
  private readonly accountsStore = inject(AccountsStore);
  private readonly session = inject(SessionStore);
  private readonly financeEvents = inject(FinanceEvents);
  private readonly toast = inject(ToastService);

  private readonly vehicleId = signal<string>('');
  protected readonly vehicle = signal<Vehicle | null>(null);
  protected readonly loadingVehicle = signal<boolean>(false);
  protected readonly saving = signal<boolean>(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  protected readonly baseCurrency = computed(
    () => this.session.me()?.family?.baseCurrency ?? 'ARS',
  );
  protected readonly enabledCurrencies = computed(
    () => this.session.me()?.family?.enabledCurrencies ?? ['ARS', 'USD'],
  );
  protected readonly usableAccounts = this.accountsStore.usableFor();
  protected readonly defaultAccountId = computed(
    () => this.accountsStore.defaultAccountFor(this.baseCurrency())?.id ?? null,
  );

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.vehicleId.set(id);

    if (this.accountsStore.accounts().length === 0) {
      void this.accountsStore.load();
    }
    if (id) {
      void this.loadVehicle(id);
    }
  }

  protected async handleSubmit(payload: MaintenanceLogCreate): Promise<void> {
    const id = this.vehicleId();
    if (!id) {
      return;
    }

    this.saving.set(true);
    this.submitError.set(null);
    this.fieldErrors.set({});

    try {
      await firstValueFrom(this.api.createMaintenanceLog(id, payload));
      this.toast.success('Mantenimiento registrado');
      this.financeEvents.emit('vehicle-changed');
      if (toNumber(payload.costAmount) > 0) {
        this.financeEvents.emit('transaction-created');
      }
      await this.router.navigate(['/vehicles', id]);
    } catch (err: unknown) {
      const parsed = parseProblemError(
        err,
        'No pudimos registrar el mantenimiento. Revisá los datos e intentá nuevamente.',
      );
      this.submitError.set(parsed.message);
      this.fieldErrors.set(parsed.fieldErrors);
    } finally {
      this.saving.set(false);
    }
  }

  protected goBack(): void {
    const id = this.vehicleId();
    if (id) {
      void this.router.navigate(['/vehicles', id]);
    } else {
      void this.router.navigate(['/vehicles']);
    }
  }

  private async loadVehicle(id: string): Promise<void> {
    const cached = this.vehiclesStore.findCached(id);
    if (cached) {
      this.vehicle.set(cached);
    }
    this.loadingVehicle.set(!cached);
    try {
      const fresh = await this.vehiclesStore.loadOne(id);
      this.vehicle.set(fresh);
    } catch {
      // Allow filling the form even if vehicle metadata failed to load.
    } finally {
      this.loadingVehicle.set(false);
    }
  }
}
