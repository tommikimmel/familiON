import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SessionStore } from '../../../core/state/session.store';
import {
  ButtonComponent,
  CardComponent,
  IconComponent,
  SkeletonComponent,
  ToastService,
} from '../../../shared/ui';
import { VehicleFormComponent } from '../components/vehicle-form.component';
import { Vehicle, VehicleCreate } from '../data-access/vehicles.models';
import { VehiclesStore } from '../data-access/vehicles.store';
import { parseProblemError } from '../utils/problem-errors';

@Component({
  selector: 'fo-vehicle-form-page',
  standalone: true,
  imports: [
    ButtonComponent,
    CardComponent,
    IconComponent,
    SkeletonComponent,
    VehicleFormComponent,
  ],
  template: `
    <section class="mx-auto max-w-2xl space-y-5 p-4 sm:p-6">
      <header class="flex items-center gap-3">
        <button
          type="button"
          aria-label="Volver"
          (click)="goBack()"
          class="fo-focus-ring flex h-10 w-10 cursor-pointer items-center justify-center rounded-xl border border-border bg-surface text-text transition-colors hover:bg-surface-2"
        >
          <fo-icon name="chevron-left" [size]="20" />
        </button>

        <div class="min-w-0 flex-1">
          <h1 class="truncate text-xl font-bold tracking-tight text-text">Vehículo</h1>
          <p class="text-xs text-muted">
            {{
              isEditMode()
                ? 'Editar vehículo · Actualizá los datos, vencimientos u odómetro.'
                : 'Nuevo vehículo · Completá los datos del auto para seguir consumo y mantenimiento.'
            }}
          </p>
        </div>
      </header>

      @if (!isAdult()) {
        <fo-card padding="md">
          <div class="space-y-3">
            <div class="flex items-center gap-2 text-sm font-medium text-text">
              <fo-icon name="lock" [size]="18" />
              <span>Solo los adultos pueden agregar o editar vehículos.</span>
            </div>
            <fo-button variant="ghost" size="sm" (click)="goBack()">Volver a vehículos</fo-button>
          </div>
        </fo-card>
      } @else if (loadingVehicle()) {
        <fo-card padding="md">
          <div class="space-y-4" aria-busy="true">
            <fo-skeleton height="40px" />
            <div class="grid grid-cols-2 gap-3">
              <fo-skeleton height="40px" />
              <fo-skeleton height="40px" />
            </div>
            <fo-skeleton height="40px" />
          </div>
        </fo-card>
      } @else if (loadError(); as err) {
        <fo-card padding="md">
          <div class="space-y-3" role="alert">
            <p class="text-sm text-danger">{{ err }}</p>
            <div class="flex gap-2">
              <fo-button variant="ghost" size="sm" (click)="goBack()">Volver</fo-button>
              <fo-button variant="primary" size="sm" (click)="retryLoad()">Reintentar</fo-button>
            </div>
          </div>
        </fo-card>
      } @else {
        <fo-card padding="md">
          <fo-vehicle-form
            [vehicle]="vehicle()"
            [saving]="saving()"
            [errorMessage]="submitError()"
            [fieldErrors]="fieldErrors()"
            (submitted)="handleSubmit($event)"
            (cancelled)="goBack()"
          />
        </fo-card>

        @if (isEditMode() && vehicle()) {
          <fo-card padding="md">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 class="text-sm font-semibold text-text">Eliminar vehículo</h2>
                <p class="text-xs text-muted">
                  Se desactivará el vehículo de la lista familiar.
                </p>
              </div>

              @if (!confirmingDelete()) {
                <fo-button
                  variant="danger"
                  size="sm"
                  data-testid="vehicle-delete-trigger"
                  [disabled]="saving() || deleting()"
                  (click)="confirmingDelete.set(true)"
                >
                  <fo-icon name="trash-2" [size]="15" />
                  <span>Eliminar</span>
                </fo-button>
              } @else {
                <div class="flex items-center gap-2">
                  <fo-button
                    variant="ghost"
                    size="sm"
                    [disabled]="deleting()"
                    (click)="confirmingDelete.set(false)"
                  >
                    Cancelar
                  </fo-button>
                  <fo-button
                    variant="danger"
                    size="sm"
                    data-testid="vehicle-delete-confirm"
                    [loading]="deleting()"
                    (click)="confirmDelete()"
                  >
                    Confirmar eliminación
                  </fo-button>
                </div>
              }
            </div>
          </fo-card>
        }
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleFormPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly store = inject(VehiclesStore);
  private readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);

  protected readonly isAdult = this.session.isAdult;

  private readonly vehicleId = signal<string | null>(null);
  protected readonly isEditMode = computed(() => this.vehicleId() !== null);

  protected readonly vehicle = signal<Vehicle | null>(null);
  protected readonly loadingVehicle = signal<boolean>(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal<boolean>(false);
  protected readonly deleting = signal<boolean>(false);
  protected readonly confirmingDelete = signal<boolean>(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.vehicleId.set(id);
    if (id) {
      void this.loadVehicle(id);
    }
  }

  protected retryLoad(): void {
    const id = this.vehicleId();
    if (id) {
      void this.loadVehicle(id);
    }
  }

  protected async handleSubmit(payload: VehicleCreate): Promise<void> {
    if (!this.isAdult()) {
      return;
    }
    this.saving.set(true);
    this.submitError.set(null);
    this.fieldErrors.set({});

    try {
      const id = this.vehicleId();
      if (id) {
        const updated = await this.store.update(id, payload);
        this.toast.success('Vehículo actualizado');
        await this.router.navigate(['/vehicles', updated.id]);
      } else {
        const created = await this.store.create(payload);
        this.toast.success('Vehículo agregado');
        await this.router.navigate(['/vehicles', created.id]);
      }
    } catch (err: unknown) {
      const parsed = parseProblemError(
        err,
        'No pudimos guardar el vehículo. Revisá los datos e intentá nuevamente.',
      );
      this.submitError.set(parsed.message);
      this.fieldErrors.set(parsed.fieldErrors);
    } finally {
      this.saving.set(false);
    }
  }

  protected async confirmDelete(): Promise<void> {
    const id = this.vehicleId();
    if (!id || !this.isAdult()) {
      return;
    }
    this.deleting.set(true);
    try {
      await this.store.remove(id);
      this.toast.success('Vehículo eliminado');
      await this.router.navigate(['/vehicles']);
    } catch (err: unknown) {
      const parsed = parseProblemError(err, 'No pudimos eliminar el vehículo.');
      this.toast.error(parsed.message);
      this.confirmingDelete.set(false);
    } finally {
      this.deleting.set(false);
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
    const cached = this.store.findCached(id);
    if (cached) {
      this.vehicle.set(cached);
    }
    this.loadingVehicle.set(!cached);
    this.loadError.set(null);
    try {
      const fresh = await this.store.loadOne(id);
      this.vehicle.set(fresh);
    } catch (err: unknown) {
      if (!cached) {
        this.loadError.set(
          parseProblemError(err, 'No pudimos cargar los datos del vehículo.').message,
        );
      }
    } finally {
      this.loadingVehicle.set(false);
    }
  }
}
