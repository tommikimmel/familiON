import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CurrencyChipComponent } from './currency-chip.component';

@Component({
  standalone: true,
  imports: [CurrencyChipComponent],
  template: `
    <fo-currency-chip
      [currency]="currency()"
      [adjusted]="adjusted()"
      (pressed)="clicks.set(clicks() + 1)"
    />
  `,
})
class ChipHostComponent {
  readonly currency = signal('ARS');
  readonly adjusted = signal(false);
  readonly clicks = signal(0);
}

describe('CurrencyChipComponent', () => {
  it('renders SVG flag, 3-letter currency code, and emits pressed on click', () => {
    const fixture = TestBed.createComponent(ChipHostComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('svg')).not.toBeNull();
    expect(host.textContent).toContain('ARS');

    const button = host.querySelector('button') as HTMLButtonElement;
    button.click();
    expect(fixture.componentInstance.clicks()).toBe(1);

    fixture.componentInstance.currency.set('usd');
    fixture.componentInstance.adjusted.set(true);
    fixture.detectChanges();

    expect(host.textContent).toContain('USD');
    expect(button.getAttribute('aria-label')).toContain('a plata de hoy');
  });
});
