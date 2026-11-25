import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ChipComponent } from './chip.component';

@Component({
  standalone: true,
  imports: [ChipComponent],
  template: `<fo-chip variant="category" [active]="active()" (pressed)="presses.set(presses() + 1)">Súper</fo-chip>`,
})
class ChipHostComponent {
  readonly active = signal(false);
  readonly presses = signal(0);
}

describe('ChipComponent', () => {
  it('renders the category pill, reflects the active state and emits pressed', () => {
    const fixture = TestBed.createComponent(ChipHostComponent);
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button') as HTMLButtonElement;

    expect(button.classList.contains('fo-cat-pill')).toBe(true);
    expect(button.getAttribute('aria-pressed')).toBe('false');

    button.click();
    fixture.componentInstance.active.set(true);
    fixture.detectChanges();

    expect(fixture.componentInstance.presses()).toBe(1);
    expect(button.classList.contains('is-active')).toBe(true);
    expect(button.getAttribute('aria-pressed')).toBe('true');
  });
});
