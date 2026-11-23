import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ButtonComponent } from './button.component';

@Component({
  standalone: true,
  imports: [ButtonComponent],
  template: `
    <fo-button [variant]="variant()" [loading]="loading()">Guardar</fo-button>
  `,
})
class ButtonHostComponent {
  readonly variant = signal<'primary' | 'ghost' | 'danger'>('primary');
  readonly loading = signal(false);
}

describe('ButtonComponent', () => {
  it('renders projected label and disables button with aria-busy while loading', () => {
    const fixture = TestBed.createComponent(ButtonHostComponent);
    fixture.detectChanges();

    const btn = (fixture.nativeElement as HTMLElement).querySelector('button') as HTMLButtonElement;
    expect(btn.textContent?.trim()).toBe('Guardar');
    expect(btn.disabled).toBe(false);

    fixture.componentInstance.loading.set(true);
    fixture.detectChanges();

    expect(btn.disabled).toBe(true);
    expect(btn.getAttribute('aria-busy')).toBe('true');
  });
});
