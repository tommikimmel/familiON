import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SwitchComponent } from './switch.component';

@Component({
  standalone: true,
  imports: [SwitchComponent],
  template: `<fo-switch [(checked)]="on" ariaLabel="A plata de hoy" [disabled]="disabled()" />`,
})
class SwitchHostComponent {
  readonly on = signal(false);
  readonly disabled = signal(false);
}

describe('SwitchComponent', () => {
  it('toggles the two-way checked model and exposes aria-checked', () => {
    const fixture = TestBed.createComponent(SwitchHostComponent);
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector('[role="switch"]') as HTMLButtonElement;

    expect(button.getAttribute('aria-checked')).toBe('false');
    button.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.on()).toBe(true);
    expect(button.getAttribute('aria-checked')).toBe('true');
    expect(button.querySelector('.fo-mini-switch')?.classList.contains('is-on')).toBe(true);
  });

  it('ignores clicks while disabled', () => {
    const fixture = TestBed.createComponent(SwitchHostComponent);
    fixture.componentInstance.disabled.set(true);
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector('[role="switch"]') as HTMLButtonElement;

    button.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.on()).toBe(false);
  });
});
