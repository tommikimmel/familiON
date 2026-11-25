import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { SessionStore } from '../../../core/state/session.store';
import {
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  IconComponent,
  SkeletonComponent,
} from '../../../shared/ui';
import { VehicleCardComponent } from '../components/vehicle-card.component';
import { VehiclesStore } from '../data-access/vehicles.store';

@Component({
  selector: 'fo-vehicles-list-page',
  standalone: true,
  imports: [
    ButtonComponent,
    CardComponent,
    EmptyStateComponent,
    IconComponent,
    SkeletonComponent,
    VehicleCardComponent,
  ],
  template: `
    <section class="w-full space-y-3 lg:space-y-4">
      <header class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 class="fo-page-title text-text lg:sr-only">Vehículos</h1>
          <p class="mt-0.5 text-[11px] text-muted">
            Combustible, services, vencimientos y costo real de cada auto.
          </p>
        </div>

        @if (isAdult()) {
          <fo-button
            variant="primary"
            size="md"
            data-testid="vehicles-add-btn"
            (click)="goToNew()"
          >
            <fo-icon name="plus" [size]="18" />
            <span>Agregar vehículo</span>
          </fo-button>
        }
      </header>

      @if (store.error(); as error) {
        <fo-card padding="md">
          <div class="flex flex-wrap items-center justify-between gap-3" role="alert">
            <div class="flex items-center gap-2.5 text-sm text-danger">
              <fo-icon name="alert-circle" [size]="18" />
              <span>{{ error }}</span>
            </div>
            <fo-button variant="ghost" size="sm" (click)="reload()">Reintentar</fo-button>
          </div>
        </fo-card>
      }

      @if (store.loading() && store.activeVehicles().length === 0) {
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2" aria-busy="true" aria-label="Cargando vehículos">
          @for (item of [1, 2]; track item) {
            <fo-card padding="md">
              <div class="space-y-3">
                <div class="flex items-center gap-3">
                  <fo-skeleton width="44px" height="44px" rounded="md" />
                  <div class="flex-1 space-y-2">
                    <fo-skeleton width="55%" height="18px" />
                    <fo-skeleton width="40%" height="14px" />
                  </div>
                </div>
                <fo-skeleton width="70%" height="14px" />
                <fo-skeleton width="100%" height="36px" />
              </div>
            </fo-card>
          }
        </div>
      } @else if (store.activeVehicles().length === 0 && !store.error()) {
        <fo-empty-state
          icon="car"
          title="Todavía no hay vehículos cargados"
          [description]="
            isAdult()
              ? 'Agregá tu primer auto o moto para registrar cargas de combustible, services y vencimientos.'
              : 'Cuando un adulto agregue un vehículo familiar vas a poder verlo y registrar cargas acá.'
          "
        >
          @if (isAdult()) {
            <fo-button
              variant="primary"
              data-testid="vehicles-empty-add-btn"
              (click)="goToNew()"
            >
              <fo-icon name="plus" [size]="18" />
              <span>Agregar vehículo</span>
            </fo-button>
          }
        </fo-empty-state>
      } @else if (store.activeVehicles().length > 0) {
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          @for (vehicle of store.activeVehicles(); track vehicle.id) {
            <fo-vehicle-card
              [vehicle]="vehicle"
              [isAdult]="isAdult()"
              (opened)="goToDetail($event)"
              (edited)="goToEdit($event)"
              (fuelClicked)="goToFuel($event)"
            />
          }
        </div>
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehiclesListPage implements OnInit {
  protected readonly store = inject(VehiclesStore);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);

  protected readonly isAdult = this.session.isAdult;

  ngOnInit(): void {
    void this.store.load();
  }

  protected reload(): void {
    void this.store.load();
  }

  protected goToNew(): void {
    void this.router.navigate(['/vehicles/new']);
  }

  protected goToDetail(id: string): void {
    void this.router.navigate(['/vehicles', id]);
  }

  protected goToEdit(id: string): void {
    void this.router.navigate(['/vehicles', id, 'edit']);
  }

  protected goToFuel(id: string): void {
    void this.router.navigate(['/vehicles', id, 'fuel', 'new']);
  }
}
