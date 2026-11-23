import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FamilyVisibility } from '../../../core/api/models/me.model';
import { SessionStore } from '../../../core/state/session.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { FamilyCurrenciesSectionComponent } from '../components/family-currencies-section.component';
import { FamilyMembersListComponent } from '../components/family-members-list.component';
import { FamilySettingsFormComponent } from '../components/family-settings-form.component';
import {
  EnableFamilyCurrencyRequest,
  UpdateFamilySettingsRequest,
} from '../data-access/family.models';
import { FamilyStore } from '../data-access/family.store';

@Component({
  selector: 'fo-family-page',
  standalone: true,
  imports: [
    RouterLink,
    ButtonComponent,
    CardComponent,
    EmptyStateComponent,
    FamilyCurrenciesSectionComponent,
    FamilyMembersListComponent,
    FamilySettingsFormComponent,
    IconComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="mx-auto max-w-3xl space-y-5">
      <header class="space-y-2">
        <a
          routerLink="/profile"
          class="fo-focus-ring inline-flex items-center gap-1 rounded-lg text-xs font-medium text-muted transition-colors hover:text-text"
        >
          <fo-icon name="chevron-left" [size]="14" />
          <span>Volver a Perfil</span>
        </a>
        <div class="space-y-0.5">
          <h1 class="text-xl font-semibold text-text">Familia</h1>
          <p class="text-sm text-muted">
            Administra la configuración económica, las divisas habilitadas y los miembros del hogar.
          </p>
        </div>
      </header>

      @if (store.loading()) {
        <div data-testid="family-loading" class="space-y-4">
          <fo-card padding="md">
            <div class="space-y-3">
              <fo-skeleton width="40%" height="1.25rem" />
              <fo-skeleton width="100%" height="2.5rem" />
              <div class="grid gap-3 sm:grid-cols-2">
                <fo-skeleton width="100%" height="2.5rem" />
                <fo-skeleton width="100%" height="2.5rem" />
              </div>
            </div>
          </fo-card>
          <fo-card padding="md">
            <div class="space-y-3">
              <fo-skeleton width="35%" height="1.25rem" />
              <div class="grid gap-2.5 sm:grid-cols-2">
                <fo-skeleton width="100%" height="3.25rem" />
                <fo-skeleton width="100%" height="3.25rem" />
              </div>
            </div>
          </fo-card>
        </div>
      } @else if (store.loadError(); as error) {
        <fo-empty-state
          icon="alert-circle"
          title="No pudimos cargar tu familia"
          [description]="error"
        >
          <fo-button size="sm" (click)="reload()">Reintentar</fo-button>
        </fo-empty-state>
      } @else if (store.family(); as family) {
        <fo-family-settings-form
          [family]="family"
          [editable]="isAdult()"
          [saving]="store.savingSettings()"
          [errorMessage]="store.settingsError()"
          (saveSettings)="onSaveSettings($event)"
        />

        <fo-family-currencies-section
          [currencies]="family.enabledCurrencies"
          [baseCurrency]="family.baseCurrency"
          [editable]="isAdult()"
          [adding]="store.addingCurrency()"
          [updatingCode]="store.updatingCurrencyCode()"
          (addCurrency)="onAddCurrency($event)"
          (toggleCurrency)="onToggleCurrency($event)"
        />

        <fo-family-members-list
          [members]="store.members()"
          [editable]="isAdult()"
          [currentUserId]="currentUserId()"
          [updatingUserId]="store.updatingVisibilityUserId()"
          (visibilityChange)="onVisibilityChange($event)"
        />
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyPage implements OnInit {
  private readonly session = inject(SessionStore);
  protected readonly store = inject(FamilyStore);

  protected readonly isAdult = computed(() => this.session.isAdult());
  protected readonly currentUserId = computed(() => this.session.me()?.id ?? null);

  ngOnInit(): void {
    void this.store.load(this.isAdult());
  }

  protected reload(): void {
    void this.store.load(this.isAdult());
  }

  protected onSaveSettings(payload: UpdateFamilySettingsRequest): void {
    void this.store.updateSettings(payload);
  }

  protected onAddCurrency(payload: EnableFamilyCurrencyRequest): void {
    void this.store.enableCurrency(payload);
  }

  protected onToggleCurrency(event: { currencyCode: string; isActive: boolean }): void {
    void this.store.updateCurrency(event.currencyCode, { isActive: event.isActive });
  }

  protected onVisibilityChange(event: {
    userId: string;
    familyVisibility: FamilyVisibility;
  }): void {
    void this.store.updateMemberVisibility(event.userId, event.familyVisibility);
  }
}
