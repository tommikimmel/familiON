import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output, signal } from '@angular/core';
import type { ExportFormat } from '../../../core/finance/exports.api';
import { IconComponent } from '../../../shared/ui';

interface ExportOption {
  readonly format: ExportFormat;
  readonly label: string;
}

const OPTIONS: readonly ExportOption[] = [
  { format: 'CSV', label: 'CSV' },
  { format: 'XLSX', label: 'Excel' },
];

@Component({
  selector: 'fo-transactions-export-menu',
  standalone: true,
  imports: [IconComponent],
  host: {
    class: 'relative inline-block',
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'open.set(false)',
  },
  template: `
    <button
      type="button"
      data-testid="transactions-export-btn"
      aria-haspopup="menu"
      [attr.aria-expanded]="open()"
      [attr.aria-busy]="loading() ? 'true' : null"
      [disabled]="loading()"
      (click)="toggle()"
      class="fo-focus-ring inline-flex cursor-pointer items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1.5 text-xs font-semibold text-text transition-colors hover:border-accent disabled:cursor-wait disabled:opacity-60"
    >
      @if (loading()) {
        <span
          class="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent motion-reduce:animate-none"
          aria-hidden="true"
        ></span>
        <span>Exportando…</span>
      } @else {
        <fo-icon name="file-text" [size]="14" />
        <span>Exportar</span>
        <fo-icon name="chevron-down" [size]="12" />
      }
    </button>

    @if (open()) {
      <div
        role="menu"
        aria-label="Formato de exportación"
        data-testid="transactions-export-menu"
        class="absolute right-0 z-20 mt-1.5 min-w-32 rounded-xl border border-border bg-surface p-1 shadow-lg"
      >
        @for (option of options; track option.format) {
          <button
            type="button"
            role="menuitem"
            [attr.data-testid]="'transactions-export-' + option.format"
            (click)="select(option.format)"
            class="fo-focus-ring flex w-full cursor-pointer items-center rounded-lg px-2.5 py-1.5 text-left text-xs font-semibold text-text transition-colors hover:bg-surface-2"
          >
            {{ option.label }}
          </button>
        }
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionsExportMenuComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly loading = input<boolean>(false);
  readonly formatSelected = output<ExportFormat>();

  protected readonly options = OPTIONS;
  protected readonly open = signal<boolean>(false);

  protected toggle(): void {
    this.open.update((value) => !value);
  }

  protected select(format: ExportFormat): void {
    this.open.set(false);
    this.formatSelected.emit(format);
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node | null)) {
      this.open.set(false);
    }
  }
}
