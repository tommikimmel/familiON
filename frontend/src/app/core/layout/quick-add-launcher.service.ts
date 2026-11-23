import { Injectable, Signal, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class QuickAddLauncher {
  private readonly _isOpen = signal<boolean>(false);

  readonly isOpen: Signal<boolean> = this._isOpen.asReadonly();

  open(): void {
    this._isOpen.set(true);
  }

  close(): void {
    this._isOpen.set(false);
  }

  toggle(): void {
    this._isOpen.update((open) => !open);
  }
}
