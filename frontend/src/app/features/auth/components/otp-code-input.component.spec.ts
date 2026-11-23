import { TestBed } from '@angular/core/testing';
import { OtpCodeInputComponent } from './otp-code-input.component';

describe('OtpCodeInputComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders 6 numeric inputs with one-time-code autocomplete on the first box', () => {
    const fixture = TestBed.createComponent(OtpCodeInputComponent);
    fixture.detectChanges();

    const inputs = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLInputElement>('input'),
    );
    expect(inputs.length).toBe(6);
    expect(inputs[0].getAttribute('autocomplete')).toBe('one-time-code');
    for (const input of inputs) {
      expect(input.getAttribute('inputmode')).toBe('numeric');
    }
  });

  it('auto-advances focus when typing digits and emits completed on the 6th digit', () => {
    const fixture = TestBed.createComponent(OtpCodeInputComponent);
    const changes: string[] = [];
    const completed: string[] = [];

    fixture.componentInstance.codeChange.subscribe((val) => changes.push(val));
    fixture.componentInstance.completed.subscribe((val) => completed.push(val));
    fixture.detectChanges();

    const inputs = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLInputElement>('input'),
    );
    const focusSpies = inputs.map((el) => vi.spyOn(el, 'focus'));

    const digits = ['4', '8', '1', '5', '1', '6'];
    digits.forEach((digit, idx) => {
      inputs[idx].value = digit;
      inputs[idx].dispatchEvent(new Event('input', { bubbles: true }));
      fixture.detectChanges();
    });

    expect(focusSpies[1]).toHaveBeenCalled();
    expect(focusSpies[2]).toHaveBeenCalled();
    expect(focusSpies[3]).toHaveBeenCalled();
    expect(focusSpies[4]).toHaveBeenCalled();
    expect(focusSpies[5]).toHaveBeenCalled();
    expect(changes[changes.length - 1]).toBe('481516');
    expect(completed).toEqual(['481516']);
  });

  it('fills all 6 boxes when pasting a 6-digit code (ignoring non-digits)', () => {
    const fixture = TestBed.createComponent(OtpCodeInputComponent);
    const changes: string[] = [];
    const completed: string[] = [];

    fixture.componentInstance.codeChange.subscribe((val) => changes.push(val));
    fixture.componentInstance.completed.subscribe((val) => completed.push(val));
    fixture.detectChanges();

    const inputs = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLInputElement>('input'),
    );

    const pasteEvent = new Event('paste', { bubbles: true, cancelable: true }) as ClipboardEvent;
    Object.defineProperty(pasteEvent, 'clipboardData', {
      value: {
        getData: () => '987-654',
      },
    });

    inputs[0].dispatchEvent(pasteEvent);
    fixture.detectChanges();

    expect(inputs.map((el) => el.value)).toEqual(['9', '8', '7', '6', '5', '4']);
    expect(changes).toContain('987654');
    expect(completed).toEqual(['987654']);
  });

  it('moves focus back and clears previous digit on Backspace when current box is empty', () => {
    const fixture = TestBed.createComponent(OtpCodeInputComponent);
    fixture.componentRef.setInput('value', '12');
    fixture.detectChanges();

    const inputs = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLInputElement>('input'),
    );
    const focusSpy = vi.spyOn(inputs[1], 'focus');

    inputs[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace', bubbles: true }));
    fixture.detectChanges();

    expect(focusSpy).toHaveBeenCalled();
    expect(inputs[1].value).toBe('');
  });
});
