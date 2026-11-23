import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ACCOUNT_TYPE_ICON, ACCOUNT_TYPE_LABEL } from '../../../core/finance/account-labels';
import { Account } from '../../../core/finance/finance.models';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { CardComponent, IconComponent } from '../../../shared/ui';
import { toNumber } from '../../../shared/utils/decimal';

@Component({
  selector: 'fo-account-card',
  standalone: true,
  imports: [CardComponent, IconComponent, MoneyPipe],
  template: `
    <fo-card padding="md">
      <div class="flex flex-col gap-3">
        <div class="flex items-start justify-between gap-3">
          <div class="flex min-w-0 items-start gap-3">
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent"
              aria-hidden="true"
            >
              <fo-icon [name]="typeIcon()" [size]="20" />
            </div>

            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-1.5">
                <h3 class="truncate text-sm font-semibold text-text">{{ account().name }}</h3>
                <span
                  class="rounded-full border border-border bg-surface-2 px-2 py-0.5 text-[11px] font-semibold tracking-wide text-muted"
                >
                  {{ account().currencyCode }}
                </span>
                @if (account().ownerUserId === null) {
                  <span
                    class="rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-medium text-accent"
                  >
                    Compartida
                  </span>
                } @else if (ownerLabel(); as owner) {
                  <span
                    class="rounded-full border border-border bg-surface-2 px-2 py-0.5 text-[11px] font-medium text-muted"
                  >
                    {{ owner }}
                  </span>
                }
                @if (isDefault()) {
                  <span
                    class="inline-flex items-center gap-1 rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-medium text-accent"
                  >
                    <fo-icon name="star" [size]="12" />
                    <span>Habitual</span>
                  </span>
                }
              </div>

              <p class="mt-0.5 text-xs text-muted">{{ typeLabel() }}</p>

              @if (account().accountType === 'CREDIT_CARD') {
                <div class="mt-1.5 space-y-0.5 text-xs text-muted">
                  @if (account().closingDay !== null && account().dueDay !== null) {
                    <p>
                      Cierra el día {{ account().closingDay }} · Vence el día
                      {{ account().dueDay }}
                    </p>
                  }
                  @if (account().creditLimit !== null) {
                    <p class="tabular-nums">
                      Límite: {{ account().creditLimit | money: account().currencyCode }}
                    </p>
                  }
                </div>
              }
            </div>
          </div>

          <div class="shrink-0 text-right">
            <p
              class="text-base font-semibold tabular-nums"
              [class.text-danger]="isNegative()"
              [class.text-text]="!isNegative()"
            >
              {{ account().currentBalance | money: account().currencyCode }}
            </p>
            <p class="text-[11px] text-muted">Saldo actual</p>
          </div>
        </div>

        <div
          class="flex flex-wrap items-center justify-end gap-1.5 border-t border-border pt-2.5 empty:hidden"
        >
          @if (!isDefault() && account().isActive) {
            <button
              type="button"
              (click)="setDefault.emit(account())"
              class="fo-focus-ring inline-flex cursor-pointer items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium text-muted transition-colors hover:bg-surface-2 hover:text-text"
            >
              <fo-icon name="star" [size]="14" />
              <span>Usar como cuenta habitual</span>
            </button>
          }

          @if (canManage()) {
            <button
              type="button"
              [attr.aria-label]="'Editar cuenta ' + account().name"
              (click)="edit.emit(account())"
              class="fo-focus-ring inline-flex cursor-pointer items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium text-muted transition-colors hover:bg-surface-2 hover:text-text"
            >
              <fo-icon name="pencil" [size]="14" />
              <span>Editar</span>
            </button>

            <button
              type="button"
              [attr.aria-label]="'Archivar cuenta ' + account().name"
              (click)="archive.emit(account())"
              class="fo-focus-ring inline-flex cursor-pointer items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium text-danger transition-colors hover:bg-danger/10"
            >
              <fo-icon name="trash-2" [size]="14" />
              <span>Archivar</span>
            </button>
          }
        </div>
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountCardComponent {
  readonly account = input.required<Account>();
  readonly isDefault = input<boolean>(false);
  readonly ownerLabel = input<string | null>(null);
  readonly canManage = input<boolean>(true);

  readonly edit = output<Account>();
  readonly archive = output<Account>();
  readonly setDefault = output<Account>();

  protected readonly typeIcon = computed(() => ACCOUNT_TYPE_ICON[this.account().accountType]);
  protected readonly typeLabel = computed(() => ACCOUNT_TYPE_LABEL[this.account().accountType]);
  protected readonly isNegative = computed(() => toNumber(this.account().currentBalance) < 0);
}
