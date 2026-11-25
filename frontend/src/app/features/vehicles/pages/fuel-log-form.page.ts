import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AccountsStore } from '../../../core/finance/accounts.store';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { SessionStore } from '../../../core/state/session.store';
import { CardComponent, IconComponent, SkeletonComponent, ToastService } from '../../../shared/ui';
import { FuelLogFormComponent } from '../components/fuel-log-form.component';
import { VehiclesApi } from '../data-access/vehicles.api';
import { FuelLogCreate, Vehicle } from '../data-access/vehicles.models';
import { VehiclesStore } from '../data-access/vehicles.store';
import { parseProblemError } from '../utils/problem-errors';

@Component({
  selector: 'fo-fuel-log-form-page',
  standalone: true,
  imports: [CardComponent, FuelLogFormComponent, IconComponent, SkeletonComponent],
  template: `
    <section class="mx-auto max-w-lg space-y-4 p-4 sm:p-6">
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
          <h1 class="truncate text-xl font-bold tracking-tight text-text">Cargar combustible</h1>
          @if (vehicle(); as v) {
            <p class="truncate text-xs text-muted">
              {{ v.nickname }} · {{ v.licensePlate }}
            </p>
          }
        </div>
      </header>

      @if (loadingVehicle()) {
        <fo-card padding="md">
          <div class="space-y-4" aria-busy="true">
            <fo-skeleton height="48px" />
            <fo-skeleton height="40px" />
            <fo-skeleton height="140px" />
          </div>
        </fo-card>
      } @else {
        <fo-card padding="md">
          <fo-fuel-log-form
            [vehicle]="vehicle()"
            [accounts]="usableAccounts()"
            [defaultAccountId]="defaultAccountId()"
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
export class FuelLogFormPage implements OnInit {
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

  protected async handleSubmit(payload: FuelLogCreate): Promise<void> {
    const id = this.vehicleId();
    if (!id) {
      return;
    }

    this.saving.set(true);
    this.submitError.set(null);
    this.fieldErrors.set({});

    try {
      await firstValueFrom(this.api.createFuelLog(id, payload));
      this.accountsStore.setDefaultAccount(payload.currencyCode, payload.accountId);
      this.toast.success('Carga de combustible registrada');
      this.financeEvents.emit('vehicle-changed');
      this.financeEvents.emit('transaction-created');
      await this.router.navigate(['/vehicles', id]);
    } catch (err: unknown) {
      const parsed = parseProblemError(
        err,
        'No pudimos registrar la carga. Revisá los datos e intentá nuevamente.',
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
