import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CategoryBreakdown, PersonBreakdown } from '../data-access/analytics.models';
import { AnalyticsBreakdownsComponent } from './analytics-breakdowns.component';

const MOCK_CATEGORY: CategoryBreakdown = {
  period: {
    granularity: 'MONTH',
    from: '2026-11-01',
    to: '2026-11-30',
    label: 'Noviembre 2026',
  },
  lens: 'NOMINAL',
  unit: 'ARS',
  total: '350000',
  slices: [
    {
      categoryId: 'cat-1',
      name: 'Supermercado',
      colorHex: '#0f766e',
      icon: 'shopping-cart',
      amount: '200000',
      pct: '57.1',
      variationPct: '-5.5',
    },
    {
      categoryId: 'cat-2',
      name: 'Servicios',
      colorHex: '#d97706',
      icon: 'plug-zap',
      amount: '150000',
      pct: '42.9',
      variationPct: '12.0',
    },
  ],
};

const MOCK_PERSON: PersonBreakdown = {
  period: MOCK_CATEGORY.period,
  lens: 'NOMINAL',
  unit: 'ARS',
  total: '350000',
  slices: [
    {
      userId: 'u-1',
      fullName: 'Ana Pérez',
      accentColor: '#0f766e',
      amount: '210000',
      pct: '60',
    },
    {
      userId: 'u-2',
      fullName: 'Juan Pérez',
      accentColor: '#2563eb',
      amount: '140000',
      pct: '40',
    },
  ],
};

describe('AnalyticsBreakdownsComponent', () => {
  let fixture: ComponentFixture<AnalyticsBreakdownsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalyticsBreakdownsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AnalyticsBreakdownsComponent);
  });

  it('renders category donut with real variations and hides person card when showPerson is false', () => {
    fixture.componentRef.setInput('byCategory', MOCK_CATEGORY);
    fixture.componentRef.setInput('byPerson', MOCK_PERSON);
    fixture.componentRef.setInput('showPerson', false);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="category-breakdown-card"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="person-breakdown-card"]')).toBeNull();

    const variations = el.querySelector('[data-testid="category-variation-list"]');
    expect(variations?.textContent).toContain('Supermercado');
    expect(variations?.textContent).toContain('\u22125,5% real');
    expect(variations?.textContent).toContain('+12% real');
  });

  it('renders both category and person donuts when showPerson is true', () => {
    fixture.componentRef.setInput('byCategory', MOCK_CATEGORY);
    fixture.componentRef.setInput('byPerson', MOCK_PERSON);
    fixture.componentRef.setInput('showPerson', true);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const personCard = el.querySelector('[data-testid="person-breakdown-card"]');
    expect(personCard).not.toBeNull();
    expect(personCard?.textContent).toContain('Ana Pérez');
    expect(personCard?.textContent).toContain('Juan Pérez');
  });
});
