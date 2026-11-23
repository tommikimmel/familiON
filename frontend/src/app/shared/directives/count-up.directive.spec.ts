import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CountUpDirective } from './count-up.directive';

@Component({
  standalone: true,
  imports: [CountUpDirective],
  template: `<span [foCountUp]="amount()" [foCountUpDuration]="duration()"></span>`,
})
class CountUpHostComponent {
  readonly amount = signal(150);
  readonly duration = signal(0);
}

describe('CountUpDirective', () => {
  it('renders the target value immediately when duration is 0 or reduced motion is enabled', () => {
    const fixture = TestBed.createComponent(CountUpHostComponent);
    fixture.detectChanges();

    const span = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(span.textContent?.trim()).toBe('150');

    fixture.componentInstance.amount.set(420);
    fixture.detectChanges();
    expect(span.textContent?.trim()).toBe('420');
  });

  it('respects prefers-reduced-motion media query', () => {
    const originalDescriptor = Object.getOwnPropertyDescriptor(window, 'matchMedia');
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: vi.fn().mockReturnValue({
        matches: true,
        media: '(prefers-reduced-motion: reduce)',
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
      }),
    });

    try {
      const fixture = TestBed.createComponent(CountUpHostComponent);
      fixture.componentInstance.duration.set(280);
      fixture.componentInstance.amount.set(999);
      fixture.detectChanges();

      const span = fixture.nativeElement.querySelector('span') as HTMLElement;
      expect(span.textContent?.trim()).toBe('999');
    } finally {
      if (originalDescriptor) {
        Object.defineProperty(window, 'matchMedia', originalDescriptor);
      } else {
        Reflect.deleteProperty(window, 'matchMedia');
      }
    }
  });
});
