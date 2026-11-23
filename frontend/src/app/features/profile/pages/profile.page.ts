import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, firstValueFrom } from 'rxjs';
import { ThemePreference } from '../../../core/api/models/me.model';
import { AuthStore } from '../../../core/auth/auth.store';
import { SessionStore } from '../../../core/state/session.store';
import { ThemeService } from '../../../core/theme/theme.service';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import {
  SegmentedOption,
  SegmentedToggleComponent,
} from '../../../shared/ui/segmented-toggle/segmented-toggle.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AccentColorPickerComponent } from '../components/accent-color-picker.component';
import { ProfileHeaderComponent } from '../components/profile-header.component';
import { ProfileApi } from '../data-access/profile.api';
import {
  UpdatePreferencesRequest,
  extractApiErrorMessage,
  normalizeHexColor,
} from '../data-access/profile.models';

interface ThemePrefsSnapshot {
  themePreference: ThemePreference;
  accentColor: string;
}

const THEME_OPTIONS: readonly SegmentedOption<ThemePreference>[] = [
  { value: 'LIGHT', label: 'Claro' },
  { value: 'DARK', label: 'Oscuro' },
  { value: 'SYSTEM', label: 'Sistema' },
];

const PREFERENCES_DEBOUNCE_MS = 400;

@Component({
  selector: 'fo-profile-page',
  standalone: true,
  imports: [
    RouterLink,
    AccentColorPickerComponent,
    ButtonComponent,
    CardComponent,
    IconComponent,
    ProfileHeaderComponent,
    SegmentedToggleComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="mx-auto max-w-2xl space-y-5">
      <header class="space-y-0.5">
        <h1 class="text-xl font-semibold text-text">Familia y Perfil</h1>
        <p class="text-sm text-muted">
          Personaliza tu apariencia y administra la configuración de tu familia y cuenta.
        </p>
      </header>

      <fo-card padding="md">
        @if (me(); as user) {
          <fo-profile-header
            [fullName]="user.fullName"
            [email]="user.email"
            [role]="user.role"
            [familyName]="user.family.name"
            [accentColor]="theme.accent()"
          />
        } @else {
          <div class="flex items-center gap-4">
            <fo-skeleton width="3.5rem" height="3.5rem" rounded="full" />
            <div class="flex-1 space-y-2">
              <fo-skeleton width="45%" height="1.25rem" />
              <fo-skeleton width="65%" height="0.875rem" />
              <fo-skeleton width="35%" height="0.75rem" />
            </div>
          </div>
        }
      </fo-card>

      <fo-card padding="md">
        <div class="space-y-5">
          <div class="space-y-2">
            <h2 class="text-base font-semibold text-text">Tema</h2>
            <fo-segmented-toggle
              [options]="themeOptions"
              [value]="theme.theme()"
              (valueChange)="onThemeChange($event)"
              ariaLabel="Preferencia de tema"
            />
          </div>

          <div class="space-y-3 border-t border-border pt-5">
            <div class="flex items-center justify-between gap-2">
              <div>
                <h2 class="text-base font-semibold text-text">Color de perfil</h2>
                <p class="text-xs text-muted">
                  Personaliza el acento de FamiliON en toda la aplicación.
                </p>
              </div>
              @if (savingPreferences()) {
                <span class="text-xs text-muted" role="status" aria-live="polite">
                  Guardando…
                </span>
              }
            </div>

            <fo-accent-color-picker
              [selectedColor]="theme.accent()"
              (colorSelected)="onAccentChange($event)"
            />
          </div>
        </div>
      </fo-card>

      <fo-card padding="none">
        <nav aria-label="Configuración de cuenta" class="divide-y divide-border">
          <a
            routerLink="/profile/family"
            class="fo-focus-ring flex items-center justify-between gap-3 rounded-t-[var(--fo-radius)] px-4 py-3.5 transition-colors hover:bg-surface-2"
          >
            <div class="flex min-w-0 items-center gap-3">
              <span
                class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent"
              >
                <fo-icon name="users" [size]="18" />
              </span>
              <div class="min-w-0">
                <p class="text-sm font-semibold text-text">Familia</p>
                <p class="truncate text-xs text-muted">
                  Divisas, configuración económica, miembros e invitaciones
                </p>
              </div>
            </div>
            <fo-icon name="chevron-right" [size]="18" class="text-muted" />
          </a>

          <a
            routerLink="/profile/security"
            class="fo-focus-ring flex items-center justify-between gap-3 rounded-b-[var(--fo-radius)] px-4 py-3.5 transition-colors hover:bg-surface-2"
          >
            <div class="flex min-w-0 items-center gap-3">
              <span
                class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent"
              >
                <fo-icon name="eye" [size]="18" />
              </span>
              <div class="min-w-0">
                <p class="text-sm font-semibold text-text">Seguridad</p>
                <p class="truncate text-xs text-muted">
                  Verificación en dos pasos y dispositivos confiables
                </p>
              </div>
            </div>
            <fo-icon name="chevron-right" [size]="18" class="text-muted" />
          </a>
        </nav>
      </fo-card>

      <div class="pt-1">
        <fo-button
          variant="danger"
          [fullWidth]="true"
          [loading]="loggingOut()"
          [disabled]="loggingOut()"
          (click)="onLogout()"
        >
          Cerrar sesión
        </fo-button>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {
  private readonly session = inject(SessionStore);
  private readonly authStore = inject(AuthStore);
  private readonly profileApi = inject(ProfileApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly theme = inject(ThemeService);
  protected readonly me = computed(() => this.session.me());
  protected readonly themeOptions = THEME_OPTIONS;
  protected readonly savingPreferences = signal(false);
  protected readonly loggingOut = signal(false);

  private readonly pendingPreferences$ = new Subject<UpdatePreferencesRequest>();
  private latestRequestSeq = 0;

  constructor() {
    this.pendingPreferences$
      .pipe(debounceTime(PREFERENCES_DEBOUNCE_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe((payload) => {
        void this.persistPreferences(payload);
      });
  }

  protected onThemeChange(nextTheme: ThemePreference): void {
    const currentAccent = this.theme.accent();
    this.theme.apply({ themePreference: nextTheme, accentColor: currentAccent });
    this.pendingPreferences$.next({
      themePreference: nextTheme,
      accentColor: currentAccent,
    });
  }

  protected onAccentChange(nextAccent: string): void {
    const normalized = normalizeHexColor(nextAccent);
    const currentTheme = this.theme.theme();
    this.theme.apply({ themePreference: currentTheme, accentColor: normalized });
    this.pendingPreferences$.next({
      themePreference: currentTheme,
      accentColor: normalized,
    });
  }

  protected async onLogout(): Promise<void> {
    if (this.loggingOut()) {
      return;
    }
    this.loggingOut.set(true);
    try {
      await this.authStore.logout();
    } finally {
      this.loggingOut.set(false);
    }
  }

  private getPersistedSnapshot(): ThemePrefsSnapshot {
    const currentMe = this.session.me();
    return {
      themePreference: currentMe?.themePreference ?? 'SYSTEM',
      accentColor: normalizeHexColor(currentMe?.accentColor),
    };
  }

  private async persistPreferences(payload: UpdatePreferencesRequest): Promise<void> {
    const seq = ++this.latestRequestSeq;
    const rollback = this.getPersistedSnapshot();
    this.savingPreferences.set(true);

    try {
      const updated = await firstValueFrom(this.profileApi.updatePreferences(payload));
      if (seq !== this.latestRequestSeq) {
        return;
      }
      this.session.setMe(updated);
      this.theme.apply({
        themePreference: updated.themePreference,
        accentColor: updated.accentColor,
      });
    } catch (err: unknown) {
      if (seq !== this.latestRequestSeq) {
        return;
      }
      this.theme.apply(rollback);
      this.toast.error(
        extractApiErrorMessage(err, 'No se pudieron guardar tus preferencias. Intenta de nuevo.'),
      );
    } finally {
      if (seq === this.latestRequestSeq) {
        this.savingPreferences.set(false);
      }
    }
  }
}
