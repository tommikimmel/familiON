import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

interface MockMediaQueryList {
  matches: boolean;
  media: string;
  addEventListener: ReturnType<typeof vi.fn>;
  removeEventListener: ReturnType<typeof vi.fn>;
  addListener: ReturnType<typeof vi.fn>;
  removeListener: ReturnType<typeof vi.fn>;
  trigger(matches: boolean): void;
}

function createMockMediaQuery(initialMatches: boolean): MockMediaQueryList {
  let listener: ((event: MediaQueryListEvent) => void) | null = null;
  const mock: MockMediaQueryList = {
    matches: initialMatches,
    media: '(prefers-color-scheme: dark)',
    addEventListener: vi.fn((_type: string, cb: (event: MediaQueryListEvent) => void) => {
      listener = cb;
    }),
    removeEventListener: vi.fn(() => {
      listener = null;
    }),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    trigger(matches: boolean) {
      mock.matches = matches;
      listener?.({ matches, media: mock.media } as MediaQueryListEvent);
    },
  };
  return mock;
}

describe('ThemeService', () => {
  let doc: Document;
  let originalMatchMediaDescriptor: PropertyDescriptor | undefined;

  beforeEach(() => {
    TestBed.resetTestingModule();
    doc = TestBed.inject(DOCUMENT);
    doc.documentElement.classList.remove('dark');
    doc.documentElement.style.removeProperty('--fo-accent');
    doc.documentElement.style.removeProperty('--fo-accent-contrast');
    doc.querySelector('meta[name="theme-color"]')?.remove();
    localStorage.clear();
    originalMatchMediaDescriptor = Object.getOwnPropertyDescriptor(window, 'matchMedia');
  });

  afterEach(() => {
    if (originalMatchMediaDescriptor) {
      Object.defineProperty(window, 'matchMedia', originalMatchMediaDescriptor);
    } else {
      Reflect.deleteProperty(window, 'matchMedia');
    }
    localStorage.clear();
  });

  it('applies dark class, CSS variables, theme-color meta, and persists to localStorage', () => {
    const service = TestBed.inject(ThemeService);

    service.apply({ themePreference: 'DARK', accentColor: '#FDE047' });

    expect(service.theme()).toBe('DARK');
    expect(service.accent()).toBe('#FDE047');
    expect(service.isDark()).toBe(true);
    expect(doc.documentElement.classList.contains('dark')).toBe(true);
    expect(doc.documentElement.style.getPropertyValue('--fo-accent')).toBe('#FDE047');
    expect(doc.documentElement.style.getPropertyValue('--fo-accent-contrast')).toBe('#0A0A0A');
    expect(doc.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('#FDE047');
    expect(localStorage.getItem('fo.theme')).toBe('DARK');
    expect(localStorage.getItem('fo.accent')).toBe('#FDE047');
  });

  it('removes dark class when switching to LIGHT and sets readable white contrast on #0F766E', () => {
    const service = TestBed.inject(ThemeService);

    service.apply({ themePreference: 'DARK', accentColor: '#0F766E' });
    expect(doc.documentElement.classList.contains('dark')).toBe(true);

    service.apply({ themePreference: 'LIGHT', accentColor: '#0F766E' });
    expect(doc.documentElement.classList.contains('dark')).toBe(false);
    expect(doc.documentElement.style.getPropertyValue('--fo-accent-contrast')).toBe('#FFFFFF');
  });

  it('restores theme and accent from localStorage on initialization', () => {
    localStorage.setItem('fo.theme', 'DARK');
    localStorage.setItem('fo.accent', '#2563EB');

    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('DARK');
    expect(service.accent()).toBe('#2563EB');
    expect(doc.documentElement.classList.contains('dark')).toBe(true);
    // Preset accents switch to their mock dark variant in dark theme.
    expect(doc.documentElement.style.getPropertyValue('--fo-accent')).toBe('#60A5FA');
  });

  it('applies mock preset colors, foreground and accent variants per theme', () => {
    const service = TestBed.inject(ThemeService);
    const style = doc.documentElement.style;

    service.apply({ themePreference: 'LIGHT', accentColor: '#0F766E' });
    expect(style.getPropertyValue('--fo-accent')).toBe('#0F766E');
    expect(style.getPropertyValue('--fo-accent-contrast')).toBe('#FFFFFF');
    expect(style.getPropertyValue('--fo-accent-soft')).toBe('rgba(15, 118, 110, 0.11)');
    expect(style.getPropertyValue('--fo-accent-glow-sm')).toBe('rgba(15, 118, 110, 0.2)');
    expect(style.getPropertyValue('--fo-accent-glow-lg')).toBe('rgba(15, 118, 110, 0.18)');
    expect(style.getPropertyValue('--fo-accent-mid')).toBe('rgba(15, 118, 110, 0.56)');
    expect(style.getPropertyValue('--fo-accent-light')).toBe('rgba(15, 118, 110, 0.28)');

    service.apply({ themePreference: 'DARK', accentColor: '#0F766E' });
    expect(style.getPropertyValue('--fo-accent')).toBe('#2DD4BF');
    expect(style.getPropertyValue('--fo-accent-contrast')).toBe('#042F2E');
    expect(style.getPropertyValue('--fo-accent-soft')).toBe('rgba(45, 212, 191, 0.16)');
    expect(style.getPropertyValue('--fo-accent-glow-sm')).toBe('rgba(45, 212, 191, 0.28)');
    expect(style.getPropertyValue('--fo-accent-glow-lg')).toBe('rgba(45, 212, 191, 0.26)');
    // The stored preference keeps the light preset color.
    expect(service.accent()).toBe('#0F766E');
  });

  it('follows prefers-color-scheme changes when theme is SYSTEM', () => {
    const mockMql = createMockMediaQuery(false);
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: vi.fn().mockReturnValue(mockMql),
    });

    const service = TestBed.inject(ThemeService);
    service.apply({ themePreference: 'SYSTEM', accentColor: '#0F766E' });
    expect(doc.documentElement.classList.contains('dark')).toBe(false);

    mockMql.trigger(true);
    expect(doc.documentElement.classList.contains('dark')).toBe(true);

    mockMql.trigger(false);
    expect(doc.documentElement.classList.contains('dark')).toBe(false);
  });
});
