import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FamilyVisibility } from '../../../core/api/models/me.model';
import { SessionStore } from '../../../core/state/session.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { FamilyCurrenciesSectionComponent } from '../components/family-currencies-section.component';
import { FamilyInvitationsSectionComponent } from '../components/family-invitations-section.component';
import { FamilyMembersListComponent } from '../components/family-members-list.component';
import { FamilySettingsFormComponent } from '../components/family-settings-form.component';
import {
  CreateInvitationRequest,
  EnableFamilyCurrencyRequest,
  Invitation,
  InvitationCreated,
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
    FamilyInvitationsSectionComponent,
    FamilyMembersListComponent,
    FamilySettingsFormComponent,
    IconComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="mx-auto max-w-3xl space-y-6">
      <header class="space-y-3">
        <a
          routerLink="/profile"
          class="fo-focus-ring inline-flex items-center gap-1 rounded-lg text-xs font-medium text-muted transition-colors hover:text-text"
        >
          <fo-icon name="chevron-left" [size]="14" />
          <span>Volver a Perfil</span>
        </a>
        <div class="space-y-1">
          <h1 class="text-xl font-semibold text-text">Familia</h1>
          <p class="text-sm text-muted">Quiénes forman tu familia y cómo usan FamiliON.</p>
        </div>
      </header>

      @if (store.loading()) {
        <div data-testid="family-loading" class="space-y-4">
          <fo-card padding="lg">
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
        <fo-family-members-list
          [members]="store.members()"
          [editable]="isAdult()"
          [currentUserId]="currentUserId()"
          [updatingUserId]="store.updatingVisibilityUserId()"
          (visibilityChange)="onVisibilityChange($event)"
        >
          @if (isAdult()) {
            <fo-family-invitations-section
              [invitations]="store.invitations()"
              [lastCreated]="store.lastCreatedInvitation()"
              [creating]="store.creatingInvitation()"
              [revokingId]="store.revokingInvitationId()"
              [resendingId]="store.resendingInvitationId()"
              [canShare]="canShare"
              (createInvitation)="onCreateInvitation($event)"
              (revokeInvitation)="onRevokeInvitation($event)"
              (resendInvitation)="onResendInvitation($event)"
              (copyLink)="onCopyInviteLink($event)"
              (shareLink)="onShareInviteLink($event)"
              (dismissCreated)="store.clearLastCreatedInvitation()"
            />
          }
        </fo-family-members-list>

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
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyPage implements OnInit {
  private readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);
  private readonly doc = inject(DOCUMENT);

  protected readonly store = inject(FamilyStore);

  protected readonly isAdult = computed(() => this.session.isAdult());
  protected readonly currentUserId = computed(() => this.session.me()?.id ?? null);
  protected readonly canShare =
    typeof this.doc.defaultView?.navigator?.share === 'function';

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

  protected onCreateInvitation(payload: CreateInvitationRequest): void {
    void this.store.createInvitation(payload);
  }

  protected onRevokeInvitation(id: string): void {
    void this.store.revokeInvitation(id);
  }

  protected onResendInvitation(invitation: Invitation): void {
    void this.store.resendInvitation(invitation);
  }

  protected async onCopyInviteLink(url: string): Promise<void> {
    const clipboard = this.doc.defaultView?.navigator?.clipboard;
    if (!clipboard || typeof clipboard.writeText !== 'function') {
      this.toast.error('No se pudo copiar automáticamente. Copia el enlace manualmente.');
      return;
    }

    try {
      await clipboard.writeText(url);
      this.toast.success('Enlace copiado al portapapeles.');
    } catch {
      this.toast.error('No se pudo copiar el enlace.');
    }
  }

  protected async onShareInviteLink(invitation: InvitationCreated): Promise<void> {
    const nav = this.doc.defaultView?.navigator;
    if (!nav || typeof nav.share !== 'function') {
      return;
    }

    try {
      await nav.share({
        title: 'Invitación a FamiliON',
        text: `Te invitaron a unirte a ${this.store.family()?.name ?? 'tu familia'} en FamiliON.`,
        url: invitation.inviteUrl,
      });
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        return;
      }
      this.toast.error('No se pudo compartir el enlace.');
    }
  }
}
