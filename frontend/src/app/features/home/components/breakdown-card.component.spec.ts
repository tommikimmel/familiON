import { TestBed } from '@angular/core/testing';
import { buildDemoHomePayload } from '../data-access/home.fixtures';
import { BreakdownCardComponent } from './breakdown-card.component';

describe('BreakdownCardComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('hides the Personas segmented toggle in PERSONAL view', () => {
    const familyPayload = buildDemoHomePayload({
      view: 'FAMILY',
      lens: 'NOMINAL',
      granularity: 'MONTH',
      date: '2026-11-23',
    });

    const fixture = TestBed.createComponent(BreakdownCardComponent);
    fixture.componentRef.setInput('byCategory', familyPayload.byCategory);
    fixture.componentRef.setInput('byPerson', familyPayload.byPerson);
    fixture.componentRef.setInput('view', 'PERSONAL');
    fixture.componentRef.setInput('lens', 'NOMINAL');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="breakdown-mode-toggle"]')).toBeNull();
    expect(host.textContent).not.toContain('Personas');
    expect(host.textContent).toContain('Supermercado');
  });

  it('shows Categorías | Personas toggle in FAMILY view when byPerson is present and switches slices', () => {
    const familyPayload = buildDemoHomePayload({
      view: 'FAMILY',
      lens: 'NOMINAL',
      granularity: 'MONTH',
      date: '2026-11-23',
    });

    const fixture = TestBed.createComponent(BreakdownCardComponent);
    fixture.componentRef.setInput('byCategory', familyPayload.byCategory);
    fixture.componentRef.setInput('byPerson', familyPayload.byPerson);
    fixture.componentRef.setInput('view', 'FAMILY');
    fixture.componentRef.setInput('lens', 'NOMINAL');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const toggle = host.querySelector('[data-testid="breakdown-mode-toggle"]');
    expect(toggle).not.toBeNull();

    // Real variation chips visible in category mode with red/green styling
    const variations = host.querySelector('[data-testid="category-variations"]');
    expect(variations?.textContent).toContain('+8,2% real');
    expect(variations?.textContent).toContain('-3,4% real');
    expect(variations?.querySelector('.text-danger')).not.toBeNull();
    expect(variations?.querySelector('.text-success')).not.toBeNull();

    // Switch to Personas
    const tabs = host.querySelectorAll<HTMLButtonElement>(
      '[data-testid="breakdown-mode-toggle"] button[role="tab"]',
    );
    expect(tabs).toHaveLength(2);
    tabs[1].click();
    fixture.detectChanges();

    expect(host.textContent).toContain('Ana García');
    expect(host.textContent).toContain('Carlos García');
    expect(host.querySelector('[data-testid="category-variations"]')).toBeNull();
  });

  it('hides Personas toggle in FAMILY view when byPerson is null', () => {
    const familyPayload = buildDemoHomePayload({
      view: 'FAMILY',
      lens: 'NOMINAL',
      granularity: 'MONTH',
      date: '2026-11-23',
    });

    const fixture = TestBed.createComponent(BreakdownCardComponent);
    fixture.componentRef.setInput('byCategory', familyPayload.byCategory);
    fixture.componentRef.setInput('byPerson', null);
    fixture.componentRef.setInput('view', 'FAMILY');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="breakdown-mode-toggle"]')).toBeNull();
  });
});
