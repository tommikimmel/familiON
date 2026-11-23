import { Injectable, Signal, signal } from '@angular/core';

export interface FinanceEvent {
  type:
    | 'transaction-created'
    | 'transaction-updated'
    | 'transaction-deleted'
    | 'plan-changed'
    | 'vehicle-changed';
  at: number;
}

@Injectable({ providedIn: 'root' })
export class FinanceEvents {
  private readonly _last = signal<FinanceEvent | null>(null);

  readonly last: Signal<FinanceEvent | null> = this._last.asReadonly();

  emit(type: FinanceEvent['type']): void {
    this._last.set({ type, at: Date.now() });
  }
}
