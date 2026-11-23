import { Injectable, Signal, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class NotificationBadgeService {
  private readonly _unread = signal<number>(0);

  readonly unread: Signal<number> = this._unread.asReadonly();

  set(n: number): void {
    const safeCount = Number.isFinite(n) ? Math.max(0, Math.floor(n)) : 0;
    this._unread.set(safeCount);
  }
}
