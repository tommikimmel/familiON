import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SessionStore } from '../../../core/state/session.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ProfileApi } from '../data-access/profile.api';

export const AVATAR_MAX_BYTES = 2 * 1024 * 1024;
const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const TOO_LARGE_MSG = 'La foto supera el máximo de 2 MB. Elige una imagen más liviana.';
const BAD_TYPE_MSG = 'Formato no admitido. Usa una imagen JPG, PNG o WebP.';

/** Pick, preview, save or remove the current user's profile photo. */
@Component({
  selector: 'fo-profile-photo-controls',
  standalone: true,
  imports: [ButtonComponent],
  template: `
    <div class="space-y-1.5">
      <input
        #fileInput
        type="file"
        class="sr-only"
        accept="image/jpeg,image/png,image/webp"
        aria-label="Elegir foto de perfil"
        aria-describedby="profile-photo-hint"
        data-testid="profile-photo-input"
        (change)="onFileSelected(fileInput)"
      />
      <div class="flex flex-wrap items-center gap-2">
        @if (pendingFile()) {
          <fo-button size="sm" [loading]="busy()" [disabled]="busy()" (click)="save()">
            Guardar foto
          </fo-button>
          <fo-button size="sm" variant="ghost" [disabled]="busy()" (click)="clearPending()">
            Cancelar
          </fo-button>
        } @else {
          <fo-button size="sm" variant="soft" [disabled]="busy()" (click)="fileInput.click()">
            {{ hasPhoto() ? 'Cambiar foto' : 'Agregar foto' }}
          </fo-button>
          @if (hasPhoto()) {
            <fo-button
              size="sm"
              variant="ghost"
              [loading]="busy()"
              [disabled]="busy()"
              (click)="remove()"
            >
              Quitar foto
            </fo-button>
          }
        }
      </div>
      <p id="profile-photo-hint" class="text-xs text-muted">
        @if (pendingFile()) {
          Vista previa. Guarda para aplicar la nueva foto.
        } @else {
          JPG, PNG o WebP · máximo 2 MB
        }
      </p>
      @if (error(); as message) {
        <p role="alert" data-testid="profile-photo-error" class="text-xs font-medium text-danger">
          {{ message }}
        </p>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePhotoControlsComponent {
  private readonly api = inject(ProfileApi);
  private readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);

  readonly hasPhoto = input.required<boolean>();
  /** Object URL of the selected-but-unsaved file, or null. */
  readonly previewChange = output<string | null>();

  protected readonly pendingFile = signal<File | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private previewUrl: string | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.revokePreview());
  }

  protected onFileSelected(inputEl: HTMLInputElement): void {
    const file = inputEl.files?.[0] ?? null;
    inputEl.value = ''; // allow re-selecting the same file after cancel
    if (!file) {
      return;
    }
    if (!AVATAR_TYPES.includes(file.type)) {
      this.error.set(BAD_TYPE_MSG);
      return;
    }
    if (file.size > AVATAR_MAX_BYTES) {
      this.error.set(TOO_LARGE_MSG);
      return;
    }
    this.error.set(null);
    this.revokePreview();
    this.previewUrl = URL.createObjectURL(file);
    this.pendingFile.set(file);
    this.previewChange.emit(this.previewUrl);
  }

  protected clearPending(): void {
    this.pendingFile.set(null);
    this.revokePreview();
    this.previewChange.emit(null);
  }

  protected async save(): Promise<void> {
    const file = this.pendingFile();
    if (!file) {
      return;
    }
    await this.run(this.api.uploadAvatar(file), 'Foto de perfil actualizada.');
  }

  protected async remove(): Promise<void> {
    await this.run(this.api.removeAvatar(), 'Foto de perfil eliminada.');
  }

  private async run(
    request: ReturnType<ProfileApi['removeAvatar']>,
    successMsg: string,
  ): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      this.session.setMe(await firstValueFrom(request));
      this.clearPending();
      this.toast.success(successMsg);
    } catch (err: unknown) {
      this.error.set(toMessage(err));
    } finally {
      this.busy.set(false);
    }
  }

  private revokePreview(): void {
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
      this.previewUrl = null;
    }
  }
}

function toMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 413) return TOO_LARGE_MSG;
    if (err.status === 415) return BAD_TYPE_MSG;
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor. Verifica tu conexión e intenta de nuevo.';
    }
  }
  return 'No se pudo actualizar la foto. Intenta de nuevo.';
}
