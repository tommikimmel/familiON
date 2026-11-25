import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DonutChartComponent, DonutSlice } from './donut-chart.component';

@Component({
  standalone: true,
  imports: [DonutChartComponent],
  template: `
    <fo-donut-chart
      [slices]="slices()"
      centerLabel="Gastos"
      [formatValue]="formatValue"
      (selected)="onSelected($event)"
    />
  `,
})
class DonutHostComponent {
  readonly slices = signal<DonutSlice[]>([
    { id: 'food', label: 'Supermercado', value: 750 },
    { id: 'car', label: 'Combustible', value: 250 },
  ]);
  readonly lastSelected = signal<DonutSlice | null | undefined>(undefined);
  readonly formatValue = (n: number): string => `$${n}`;

  onSelected(slice: DonutSlice | null): void {
    this.lastSelected.set(slice);
  }
}

describe('DonutChartComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });

  it('computes arc percentages and renders total in the center by default', () => {
    const fixture = TestBed.createComponent(DonutHostComponent);
    fixture.detectChanges();

    const donutCmp = fixture.debugElement.children[0].componentInstance as DonutChartComponent;
    const segments = donutCmp.segments();

    expect(segments).toHaveLength(2);
    expect(segments[0].percentage).toBeCloseTo(75, 5);
    expect(segments[1].percentage).toBeCloseTo(25, 5);
    expect(segments[0].dashOffset).toBe(0);
    expect(segments[1].dashOffset).toBeCloseTo(-segments[0].arcLength, 5);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Gastos');
    expect(text).toContain('$1000');
  });

  it('pops out a slice, updates center details, and emits selected on interaction', () => {
    const fixture = TestBed.createComponent(DonutHostComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const legendButtons = host.querySelectorAll<HTMLButtonElement>('ul button');
    expect(legendButtons).toHaveLength(2);

    legendButtons[0].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.lastSelected()?.id).toBe('food');
    const centerText = host.textContent ?? '';
    expect(centerText).toContain('Supermercado');
    expect(centerText).toContain('$750');
    expect(centerText).toContain('75%');

    const arcs = host.querySelectorAll('circle.fo-donut-arc');
    expect(arcs[0].classList.contains('is-focused')).toBe(true);
    expect(arcs[0].getAttribute('stroke-width')).toBe('19');
    expect(arcs[1].classList.contains('is-dim')).toBe(true);

    // Clicking the same item again deselects it
    legendButtons[0].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.lastSelected()).toBeNull();
  });

  it('colors slices with the mock palette tokens so they follow the active accent', () => {
    const fixture = TestBed.createComponent(DonutHostComponent);
    fixture.detectChanges();

    const donutCmp = fixture.debugElement.children[0].componentInstance as DonutChartComponent;
    expect(donutCmp.segments().map((segment) => segment.color)).toEqual([
      'var(--fo-accent)',
      'var(--fo-accent-mid)',
    ]);
    const firstArc = (fixture.nativeElement as HTMLElement).querySelector('circle.fo-donut-arc');
    expect(firstArc?.getAttribute('stroke')).toBe('var(--fo-accent)');
  });
});
