import { TestBed } from '@angular/core/testing';
import { LogoComponent } from './logo.component';

describe('LogoComponent', () => {
  it('renders SVG wordmark with accessible FamiliON label and switches OFF to ON when animated', () => {
    vi.useFakeTimers();
    try {
      const fixture = TestBed.createComponent(LogoComponent);
      fixture.componentRef.setInput('animate', true);
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      const svg = host.querySelector('svg[role="img"]');
      expect(svg?.getAttribute('aria-label')).toBe('FamiliON');

      const knob = host.querySelector('.fo-logo-knob');
      expect(knob?.classList.contains('fo-logo-knob--on')).toBe(false);

      vi.advanceTimersByTime(50);
      fixture.detectChanges();
      expect(knob?.classList.contains('fo-logo-knob--on')).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });
});
