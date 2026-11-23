import { TestBed } from '@angular/core/testing';
import { ToastComponent } from './toast.component';
import { ToastService } from './toast.service';

describe('ToastService & ToastComponent', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders success toast with animated checkmark and auto-dismisses after 3s', () => {
    vi.useFakeTimers();
    const service = TestBed.inject(ToastService);
    const fixture = TestBed.createComponent(ToastComponent);
    fixture.detectChanges();

    service.success('Movimiento guardado');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const status = host.querySelector('[role="status"]');
    expect(status).not.toBeNull();
    expect(status?.textContent).toContain('Movimiento guardado');
    expect(host.querySelector('.fo-animate-check')).not.toBeNull();

    vi.advanceTimersByTime(3000);
    fixture.detectChanges();

    expect(service.current()).toBeNull();
    expect(host.querySelector('[role="status"]')).toBeNull();
  });

  it('renders error toast with alert role and supports manual dismiss', () => {
    const service = TestBed.inject(ToastService);
    const fixture = TestBed.createComponent(ToastComponent);

    service.error('No se pudo guardar');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const alert = host.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('No se pudo guardar');

    const closeBtn = host.querySelector('button[aria-label="Cerrar notificación"]') as HTMLButtonElement;
    closeBtn.click();
    fixture.detectChanges();

    expect(service.current()).toBeNull();
  });
});
