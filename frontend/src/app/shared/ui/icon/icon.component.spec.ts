import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { IconComponent, isKnownFoIcon } from './icon.component';

@Component({
  standalone: true,
  imports: [IconComponent],
  template: `<fo-icon [name]="iconName()" [size]="20" />`,
})
class IconHostComponent {
  readonly iconName = signal<string>('wallet');
}

describe('IconComponent', () => {
  it('renders known account, lock, and system category icons', () => {
    const fixture = TestBed.createComponent(IconHostComponent);
    const icons = [
      'wallet',
      'banknote',
      'landmark',
      'credit-card',
      'piggy-bank',
      'smartphone',
      'lock',
      'shopping-cart',
      'house',
      'plug-zap',
      'heart-pulse',
      'graduation-cap',
      'car',
      'fuel',
      'wrench',
      'shield',
      'file-text',
      'ticket',
      'siren',
      'party-popper',
      'bus',
      'shirt',
      'sofa',
      'paw-print',
      'baby',
      'laptop',
      'repeat',
      'gift',
      'percent',
      'circle-ellipsis',
      'briefcase',
      'sparkles',
      'key-round',
      'trending-up',
    ];

    for (const icon of icons) {
      expect(isKnownFoIcon(icon)).toBe(true);
      fixture.componentInstance.iconName.set(icon);
      fixture.detectChanges();
      const svg = (fixture.nativeElement as HTMLElement).querySelector('svg');
      expect(svg).not.toBeNull();
    }
  });

  it('falls back to circle-ellipsis without throwing for unknown icon keys', () => {
    const fallbackFixture = TestBed.createComponent(IconHostComponent);
    fallbackFixture.componentInstance.iconName.set('circle-ellipsis');
    fallbackFixture.detectChanges();
    const expectedMarkup = (fallbackFixture.nativeElement as HTMLElement).querySelector('svg')?.innerHTML;
    expect(expectedMarkup).toBeTruthy();

    const unknownFixture = TestBed.createComponent(IconHostComponent);
    unknownFixture.componentInstance.iconName.set('unknown-custom-icon-xyz');
    expect(() => unknownFixture.detectChanges()).not.toThrow();

    const unknownSvg = (unknownFixture.nativeElement as HTMLElement).querySelector('svg');
    expect(unknownSvg).not.toBeNull();
    expect(unknownSvg?.innerHTML).toBe(expectedMarkup);

    unknownFixture.componentInstance.iconName.set('constructor');
    expect(() => unknownFixture.detectChanges()).not.toThrow();
    expect((unknownFixture.nativeElement as HTMLElement).querySelector('svg')?.innerHTML).toBe(
      expectedMarkup,
    );
  });
});
