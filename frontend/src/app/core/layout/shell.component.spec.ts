import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Me } from '../api/models/me.model';
import { SessionStore } from '../state/session.store';
import { ViewContextStore } from '../state/view-context.store';
import { NotificationBadgeService } from './notification-badge.service';
import { QuickAddLauncher } from './quick-add-launcher.service';
import { ShellComponent } from './shell.component';

@Component({ standalone: true, template: '<p>Home</p>' })
class DummyHomeComponent {}

function createMockBreakpointObserver(initialMatches: boolean) {
  const subject = new BehaviorSubject<BreakpointState>({
    matches: initialMatches,
    breakpoints: { '(min-width: 1024px)': initialMatches },
  });

  const observer: Partial<BreakpointObserver> = {
    observe: () => subject.asObservable(),
    isMatched: () => subject.value.matches,
  };

  return {
    observer,
    setDesktop(matches: boolean): void {
      subject.next({
        matches,
        breakpoints: { '(min-width: 1024px)': matches },
      });
    },
  };
}

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 'user-1',
    email: 'carlos@familion.test',
    fullName: 'Carlos López',
    role: 'ADULTO',
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'LIGHT',
    accentColor: '#0F766E',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia López',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
    ...overrides,
  };
}

describe('ShellComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('renders mobile header and bottom nav below 1024px and operates ViewContextStore & QuickAddLauncher', () => {
    const bp = createMockBreakpointObserver(false);

    TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([{ path: 'home', component: DummyHomeComponent, data: { title: 'Inicio' } }]),
        { provide: BreakpointObserver, useValue: bp.observer },
      ],
    });

    const fixture = TestBed.createComponent(ShellComponent);
    const viewContext = TestBed.inject(ViewContextStore);
    const quickAdd = TestBed.inject(QuickAddLauncher);
    const session = TestBed.inject(SessionStore);
    session.setMe(buildMe());
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="mobile-bottom-nav"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="desktop-sidebar"]')).toBeNull();

    const navText = host.querySelector('[data-testid="mobile-bottom-nav"]')?.textContent ?? '';
    expect(navText).toContain('Inicio');
    expect(navText).toContain('Plan');
    expect(navText).toContain('Autos');
    expect(navText).toContain('Perfil');

    // Toggle quick add FAB
    const fab = host.querySelector('[data-testid="mobile-quick-add-btn"]') as HTMLButtonElement;
    fab.click();
    fixture.detectChanges();
    expect(quickAdd.isOpen()).toBe(true);
    expect(fab.querySelector('.fo-fab-icon--open')).not.toBeNull();

    // Switch Personal -> Familia
    const tabs = host.querySelectorAll<HTMLButtonElement>('fo-segmented-toggle [role="tab"]');
    tabs[1].click();
    fixture.detectChanges();
    expect(viewContext.view()).toBe('FAMILY');

    // Open currency dropdown, toggle "A plata de hoy" and select USD reference currency
    const chipBtn = host.querySelector('fo-currency-chip button') as HTMLButtonElement;
    chipBtn.click();
    fixture.detectChanges();

    const switchBtn = host.querySelector('[role="switch"]') as HTMLButtonElement;
    switchBtn.click();
    fixture.detectChanges();
    expect(viewContext.lens()).toBe('CONSTANT_BASE');

    switchBtn.click();
    fixture.detectChanges();
    expect(viewContext.lens()).toBe('NOMINAL');

    const currencyItems = Array.from(
      host.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]'),
    );
    const usdBtn = currencyItems.find((btn) => btn.textContent?.includes('USD'));
    usdBtn?.click();
    fixture.detectChanges();
    expect(viewContext.lens()).toBe('REFERENCE_CURRENCY');

    // Hide Personal|Familia toggle when HIJO has OWN_ONLY visibility
    session.setMe(buildMe({ role: 'HIJO', familyVisibility: 'OWN_ONLY' }));
    fixture.detectChanges();
    expect(host.querySelector('fo-segmented-toggle')).toBeNull();
  });

  it('renders desktop sidebar and topbar at >=1024px, handles N shortcut, period shift, and badge', () => {
    const bp = createMockBreakpointObserver(true);

    TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        provideRouter([{ path: 'home', component: DummyHomeComponent, data: { title: 'Inicio' } }]),
        { provide: BreakpointObserver, useValue: bp.observer },
      ],
    });

    const fixture = TestBed.createComponent(ShellComponent);
    const session = TestBed.inject(SessionStore);
    const viewContext = TestBed.inject(ViewContextStore);
    const quickAdd = TestBed.inject(QuickAddLauncher);
    const badge = TestBed.inject(NotificationBadgeService);

    session.setMe(buildMe());
    badge.set(7);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const sidebar = host.querySelector('[data-testid="desktop-sidebar"]');
    expect(sidebar).not.toBeNull();
    expect(host.querySelector('[data-testid="mobile-bottom-nav"]')).toBeNull();

    const sidebarText = sidebar?.textContent ?? '';
    expect(sidebarText).toContain('Inicio');
    expect(sidebarText).toContain('Movimientos');
    expect(sidebarText).toContain('Analítica');
    expect(sidebarText).toContain('Planificación');
    expect(sidebarText).toContain('Vehículos');
    expect(sidebarText).toContain('Cuentas');
    expect(sidebarText).toContain('Familia y Perfil');
    expect(sidebarText).toContain('Carlos López');

    // Notification badge
    expect(host.querySelector('[data-testid="notification-badge"]')?.textContent?.trim()).toBe('7');

    // Keyboard shortcut N ignored inside input
    const input = document.createElement('input');
    document.body.appendChild(input);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }));
    expect(quickAdd.isOpen()).toBe(false);
    input.remove();

    // Keyboard shortcut N opens quick add when not in editable field
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }));
    expect(quickAdd.isOpen()).toBe(true);

    // Period shift button updates anchorDate
    const initialDate = viewContext.anchorDate();
    const prevBtn = host.querySelector('button[aria-label="Período anterior"]') as HTMLButtonElement;
    prevBtn.click();
    fixture.detectChanges();
    expect(viewContext.anchorDate()).not.toBe(initialDate);

    // Responsive switch from desktop to mobile updates layout live
    bp.setDesktop(false);
    fixture.detectChanges();
    expect(host.querySelector('[data-testid="mobile-bottom-nav"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="desktop-sidebar"]')).toBeNull();
  });
});
