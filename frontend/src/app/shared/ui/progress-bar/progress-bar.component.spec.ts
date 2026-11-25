import { TestBed } from '@angular/core/testing';
import { ProgressBarComponent } from './progress-bar.component';

describe('ProgressBarComponent', () => {
  it.each([
    [42.4, 42],
    [-10, 0],
    [180, 100],
    [Number.NaN, 0],
  ])('clamps %s to %s and renders the fill width', (input, expected) => {
    const fixture = TestBed.createComponent(ProgressBarComponent);
    fixture.componentRef.setInput('value', input);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.percent()).toBe(expected);
    expect(host.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe(String(expected));
    expect((host.querySelector('.fo-progress-fill') as HTMLElement).style.width).toBe(`${expected}%`);
  });
});
