import { TestBed } from '@angular/core/testing';
import { AvatarComponent } from './avatar.component';

describe('AvatarComponent', () => {
  it.each([
    ['Tomás Kimmel', 'TK'],
    ['  laura  ', 'L'],
    ['Ana María López', 'AL'],
    ['', '?'],
  ])('derives initials for "%s"', (name, expected) => {
    const fixture = TestBed.createComponent(AvatarComponent);
    fixture.componentRef.setInput('name', name);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe(expected);
  });

  it('shows the photo when src is set and falls back to initials if it fails to load', () => {
    const fixture = TestBed.createComponent(AvatarComponent);
    fixture.componentRef.setInput('name', 'Tomás Kimmel');
    fixture.componentRef.setInput('src', '/photo.png');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const img = host.querySelector('img') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe('/photo.png');

    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(host.querySelector('img')).toBeNull();
    expect(host.textContent?.trim()).toBe('TK');
  });
});
