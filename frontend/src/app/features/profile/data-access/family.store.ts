import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, Signal, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { FamilyVisibility } from '../../../core/api/models/me.model';
import { AuthStore } from '../../../core/auth/auth.store';
import { SessionStore } from '../../../core/state/session.store';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { FamilyApi } from './family.api';
import {
  CreateInvitationRequest,
  EnableFamilyCurrencyRequest,
  Family,
  Invitation,
  InvitationCreated,
  Member,
  UpdateFamilyCurrencyRequest,
  UpdateFamilySettingsRequest,
} from './family.models';
import { extractApiErrorMessage } from './profile.models';

const BASE_CURRENCY_CONFLICT_MESSAGE =
  'No se puede cambiar la divisa principal porque ya hay movimientos cargados.';

@Injectable({ providedIn: 'root' })
export class FamilyStore {
  private readonly api = inject(FamilyApi);
  private readonly authStore = inject(AuthStore);
  private readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);

  private readonly _family = signal<Family | null>(null);
  private readonly _members = signal<Member[]>([]);
  private readonly _invitations = signal<Invitation[]>([]);
  private readonly _loading = signal<boolean>(false);
  private readonly _loadError = signal<string | null>(null);
  private readonly _savingSettings = signal<boolean>(false);
  private readonly _settingsError = signal<string | null>(null);
  private readonly _addingCurrency = signal<boolean>(false);
  private readonly _updatingCurrencyCode = signal<string | null>(null);
  private readonly _updatingVisibilityUserId = signal<string | null>(null);
  private readonly _creatingInvitation = signal<boolean>(false);
  private readonly _revokingInvitationId = signal<string | null>(null);
  private readonly _lastCreatedInvitation = signal<InvitationCreated | null>(null);

  readonly family: Signal<Family | null> = this._family.asReadonly();
  readonly members: Signal<Member[]> = this._members.asReadonly();
  readonly invitations: Signal<Invitation[]> = this._invitations.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly loadError: Signal<string | null> = this._loadError.asReadonly();
  readonly savingSettings: Signal<boolean> = this._savingSettings.asReadonly();
  readonly settingsError: Signal<string | null> = this._settingsError.asReadonly();
  readonly addingCurrency: Signal<boolean> = this._addingCurrency.asReadonly();
  readonly updatingCurrencyCode: Signal<string | null> = this._updatingCurrencyCode.asReadonly();
  readonly updatingVisibilityUserId: Signal<string | null> =
    this._updatingVisibilityUserId.asReadonly();
  readonly creatingInvitation: Signal<boolean> = this._creatingInvitation.asReadonly();
  readonly revokingInvitationId: Signal<string | null> = this._revokingInvitationId.asReadonly();
  readonly lastCreatedInvitation: Signal<InvitationCreated | null> =
    this._lastCreatedInvitation.asReadonly();

  async load(includeInvitations = this.session.isAdult()): Promise<void> {
    this._loading.set(true);
    this._loadError.set(null);
    this._settingsError.set(null);

    try {
      const [family, members, invitations] = await Promise.all([
        firstValueFrom(this.api.getFamily()),
        firstValueFrom(this.api.listMembers()),
        includeInvitations ? firstValueFrom(this.api.listInvitations()) : Promise.resolve([]),
      ]);

      this._family.set(family);
      this._members.set(members);
      this._invitations.set(invitations);
    } catch (err: unknown) {
      const message = extractApiErrorMessage(
        err,
        'No se pudo cargar la información de tu familia.',
      );
      this._loadError.set(message);
      this.toast.error(message);
    } finally {
      this._loading.set(false);
    }
  }

  async updateSettings(payload: UpdateFamilySettingsRequest): Promise<boolean> {
    this._savingSettings.set(true);
    this._settingsError.set(null);

    try {
      const updated = await firstValueFrom(this.api.updateSettings(payload));
      this._family.set(updated);
      await this.safeReloadMe();
      this.toast.success('Configuración de la familia guardada.');
      return true;
    } catch (err: unknown) {
      const message =
        err instanceof HttpErrorResponse && err.status === 409
          ? BASE_CURRENCY_CONFLICT_MESSAGE
          : extractApiErrorMessage(err, 'No se pudo guardar la configuración de la familia.');
      this._settingsError.set(message);
      this.toast.error(message);
      return false;
    } finally {
      this._savingSettings.set(false);
    }
  }

  async enableCurrency(payload: EnableFamilyCurrencyRequest): Promise<boolean> {
    this._addingCurrency.set(true);

    try {
      const created = await firstValueFrom(this.api.enableCurrency(payload));
      this._family.update((current) => {
        if (!current) {
          return current;
        }
        const exists = current.enabledCurrencies.some(
          (c) => c.currencyCode === created.currencyCode,
        );
        const nextCurrencies = exists
          ? current.enabledCurrencies.map((c) =>
              c.currencyCode === created.currencyCode ? created : c,
            )
          : [...current.enabledCurrencies, created];
        return { ...current, enabledCurrencies: nextCurrencies };
      });
      await this.safeReloadMe();
      this.toast.success(`Divisa ${created.currencyCode} agregada.`);
      return true;
    } catch (err: unknown) {
      this.toast.error(extractApiErrorMessage(err, 'No se pudo agregar la divisa.'));
      return false;
    } finally {
      this._addingCurrency.set(false);
    }
  }

  async updateCurrency(
    currencyCode: string,
    payload: UpdateFamilyCurrencyRequest,
  ): Promise<boolean> {
    const currentFamily = this._family();
    if (payload.isActive === false && currentFamily?.baseCurrency === currencyCode) {
      return false;
    }

    this._updatingCurrencyCode.set(currencyCode);

    try {
      const updated = await firstValueFrom(this.api.updateCurrency(currencyCode, payload));
      this._family.update((current) => {
        if (!current) {
          return current;
        }
        return {
          ...current,
          enabledCurrencies: current.enabledCurrencies.map((c) =>
            c.currencyCode === updated.currencyCode ? updated : c,
          ),
        };
      });
      await this.safeReloadMe();
      this.toast.success(
        updated.isActive
          ? `Divisa ${updated.currencyCode} activada.`
          : `Divisa ${updated.currencyCode} desactivada.`,
      );
      return true;
    } catch (err: unknown) {
      this.toast.error(extractApiErrorMessage(err, 'No se pudo actualizar la divisa.'));
      return false;
    } finally {
      this._updatingCurrencyCode.set(null);
    }
  }

  async updateMemberVisibility(
    userId: string,
    familyVisibility: FamilyVisibility,
  ): Promise<boolean> {
    const previousMembers = this._members();
    this._members.update((list) =>
      list.map((member) => (member.id === userId ? { ...member, familyVisibility } : member)),
    );
    this._updatingVisibilityUserId.set(userId);

    try {
      const updated = await firstValueFrom(
        this.api.updateMemberVisibility(userId, { familyVisibility }),
      );
      this._members.update((list) =>
        list.map((member) => (member.id === userId ? updated : member)),
      );
      this.toast.success('Visibilidad actualizada.');
      return true;
    } catch (err: unknown) {
      this._members.set(previousMembers);
      this.toast.error(
        extractApiErrorMessage(err, 'No se pudo actualizar la visibilidad del miembro.'),
      );
      return false;
    } finally {
      this._updatingVisibilityUserId.set(null);
    }
  }

  async createInvitation(payload: CreateInvitationRequest): Promise<InvitationCreated | null> {
    this._creatingInvitation.set(true);

    try {
      const created = await firstValueFrom(this.api.createInvitation(payload));
      const { inviteUrl: _inviteUrl, ...invitation } = created;
      void _inviteUrl;
      this._invitations.update((list) => [invitation, ...list]);
      this._lastCreatedInvitation.set(created);
      this.toast.success('Invitación creada.');
      return created;
    } catch (err: unknown) {
      this.toast.error(extractApiErrorMessage(err, 'No se pudo crear la invitación.'));
      return null;
    } finally {
      this._creatingInvitation.set(false);
    }
  }

  async revokeInvitation(id: string): Promise<boolean> {
    this._revokingInvitationId.set(id);

    try {
      await firstValueFrom(this.api.deleteInvitation(id));
      this._invitations.update((list) => list.filter((item) => item.id !== id));
      if (this._lastCreatedInvitation()?.id === id) {
        this._lastCreatedInvitation.set(null);
      }
      this.toast.success('Invitación revocada.');
      return true;
    } catch (err: unknown) {
      this.toast.error(extractApiErrorMessage(err, 'No se pudo revocar la invitación.'));
      return false;
    } finally {
      this._revokingInvitationId.set(null);
    }
  }

  clearLastCreatedInvitation(): void {
    this._lastCreatedInvitation.set(null);
  }

  private async safeReloadMe(): Promise<void> {
    try {
      await this.authStore.reloadMe();
    } catch {
      // Mutation succeeded; keep local state updated if background /me reload fails.
    }
  }
}
