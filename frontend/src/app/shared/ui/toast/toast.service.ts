import { DestroyRef, Injectable, Signal, inject, signal } from '@angular/core';

export type ToastKind = 'success' | 'error';

export interface ToastMessage {
  id: number;
  kind: ToastKind;
  message: string;
}

const DEFAULT_DURATION_MS = 3000;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly destroyRef = inject(DestroyRef);
  private readonly _current = signal<ToastMessage | null>(null);
  private nextId = 1;
  private timerId: ReturnType<typeof setTimeout> | null = null;

  readonly current: Signal<ToastMessage | null> = this._current.asReadonly();

  constructor() {
    this.destroyRef.onDestroy(() => this.clearTimer());
  }

  success(message: string, durationMs = DEFAULT_DURATION_MS): void {
    this.show('success', message, durationMs);
  }

  error(message: string, durationMs = DEFAULT_DURATION_MS): void {
    this.show('error', message, durationMs);
  }

  dismiss(): void {
    this.clearTimer();
    this._current.set(null);
  }

  private show(kind: ToastKind, message: string, durationMs: number): void {
    this.clearTimer();
    const id = this.nextId++;
    this._current.set({ id, kind, message });

    if (durationMs > 0) {
      this.timerId = setTimeout(() => {
        if (this._current()?.id === id) {
          this._current.set(null);
        }
        this.timerId = null;
      }, durationMs);
    }
  }

  private clearTimer(): void {
    if (this.timerId !== null) {
      clearTimeout(this.timerId);
      this.timerId = null;
    }
  }
}
