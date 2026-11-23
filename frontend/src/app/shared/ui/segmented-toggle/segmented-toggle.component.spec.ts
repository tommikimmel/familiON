import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SegmentedOption, SegmentedToggleComponent } from './segmented-toggle.component';

@Component({
  standalone: true,
  imports: [SegmentedToggleComponent],
  template: `
    <fo-segmented-toggle
      [options]="options"
      [(value)]="selected"
      ariaLabel="Vista"
    />
  `,
})
class ToggleHostComponent {
  readonly options: SegmentedOption<'PERSONAL' | 'FAMILY'>[] = [
    { value: 'PERSONAL', label: 'Personal' },
    { value: 'FAMILY', label: 'Familia' },
  ];
  readonly selected = signal<'PERSONAL' | 'FAMILY'>('PERSONAL');
}

describe('SegmentedToggleComponent', () => {
  it('renders a tablist with aria-selected matching the current value', () => {
    const fixture = TestBed.createComponent(ToggleHostComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const tablist = host.querySelector('[role="tablist"]');
    const tabs = host.querySelectorAll<HTMLButtonElement>('[role="tab"]');

    expect(tablist).not.toBeNull();
    expect(tabs).toHaveLength(2);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs[1].getAttribute('aria-selected')).toBe('false');
  });

  it('updates model value on click and keyboard arrow navigation', () => {
    const fixture = TestBed.createComponent(ToggleHostComponent);
    fixture.detectChanges();

    const tabs = fixture.nativeElement.querySelectorAll('[role="tab"]') as NodeListOf<HTMLButtonElement>;
    tabs[1].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selected()).toBe('FAMILY');
    expect(tabs[1].getAttribute('aria-selected')).toBe('true');

    tabs[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.selected()).toBe('PERSONAL');
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
  });
});
