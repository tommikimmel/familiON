import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ACCENT_PRESETS } from '../data-access/profile.models';
import { AccentColorPickerComponent } from './accent-color-picker.component';

@Component({
  standalone: true,
  imports: [AccentColorPickerComponent],
  template: `
    <fo-accent-color-picker
      [selectedColor]="selected()"
      (colorSelected)="onSelected($event)"
    />
  `,
})
class TestHostComponent {
  readonly selected = signal('#0F766E');
  readonly emitted: string[] = [];

  onSelected(color: string): void {
    this.emitted.push(color);
  }
}

describe('AccentColorPickerComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders all 8 presets with aria-pressed on the selected preset and emits on click', () => {
    const fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();

    const presetButtons = Array.from(
      fixture.nativeElement.querySelectorAll('[data-testid="accent-preset-btn"]'),
    ) as HTMLButtonElement[];

    expect(presetButtons.length).toBe(ACCENT_PRESETS.length);
    expect(presetButtons[0].getAttribute('aria-pressed')).toBe('true');
    expect(presetButtons[1].getAttribute('aria-pressed')).toBe('false');

    presetButtons[1].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.emitted).toEqual(['#2563EB']);
  });

  it('triggers the native color input from the + button and emits custom colors', () => {
    const fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();

    const customBtn = fixture.nativeElement.querySelector(
      '[data-testid="accent-custom-btn"]',
    ) as HTMLButtonElement;
    const nativeInput = fixture.nativeElement.querySelector(
      '[data-testid="accent-native-input"]',
    ) as HTMLInputElement;

    const clickSpy = vi.spyOn(nativeInput, 'click');
    customBtn.click();
    expect(clickSpy).toHaveBeenCalledTimes(1);

    nativeInput.value = '#12ab34';
    nativeInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.componentInstance.emitted).toEqual(['#12AB34']);
  });

  it('marks the custom + button as pressed when a non-preset color is active and shows logo preview', () => {
    const fixture = TestBed.createComponent(TestHostComponent);
    fixture.componentInstance.selected.set('#123456');
    fixture.detectChanges();

    const customBtn = fixture.nativeElement.querySelector(
      '[data-testid="accent-custom-btn"]',
    ) as HTMLButtonElement;
    const presetButtons = Array.from(
      fixture.nativeElement.querySelectorAll('[data-testid="accent-preset-btn"]'),
    ) as HTMLButtonElement[];

    expect(customBtn.getAttribute('aria-pressed')).toBe('true');
    expect(presetButtons.every((btn) => btn.getAttribute('aria-pressed') === 'false')).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="accent-logo-preview"]')).toBeTruthy();
  });
});
