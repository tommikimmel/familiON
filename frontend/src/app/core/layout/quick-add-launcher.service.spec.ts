import { TestBed } from '@angular/core/testing';
import { QuickAddLauncher } from './quick-add-launcher.service';

describe('QuickAddLauncher', () => {
  it('opens, closes, and toggles state', () => {
    const launcher = TestBed.inject(QuickAddLauncher);
    expect(launcher.isOpen()).toBe(false);

    launcher.open();
    expect(launcher.isOpen()).toBe(true);

    launcher.close();
    expect(launcher.isOpen()).toBe(false);

    launcher.toggle();
    expect(launcher.isOpen()).toBe(true);
  });
});
