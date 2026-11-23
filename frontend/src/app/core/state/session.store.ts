import { Injectable, Signal, computed, signal } from '@angular/core';
import { Me } from '../api/models/me.model';

@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly _me = signal<Me | null>(null);

  readonly me: Signal<Me | null> = this._me.asReadonly();
  readonly isAdult: Signal<boolean> = computed(() => this._me()?.role === 'ADULTO');

  setMe(me: Me | null): void {
    this._me.set(me);
  }
}
