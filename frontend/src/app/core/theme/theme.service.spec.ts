import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

interface MockMediaQueryList {
  matches: boolean;
  media: string;
  addEventListener: ReturnType<typeof vi.fn>;
  removeEventListener: ReturnType<typeof vi.fn>;
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
    trigger(matches: boolean) {
      mock.matches = matches;
      listener?.({ matches, media: mock.media } as MediaQueryListEvent);
    },
  };
  return mock;
}

describe('ThemeService', () => {
  let doc: Document;
  let originalMatchMedia: typeof window.matchMedia | undefined;

  beforeEach(() => {
    TestBed.resetTestingModule();
    doc = TestBed.inject(DOCUMENT);
    doc.documentElement.classList.remove('dark');
    doc.documentElement.style.removeProperty('--fo-accent');
    doc.documentElement.style.removeProperty('--fo-accent-contrast');
    doc.querySelector('meta[name="theme-color"]')?.remove();
    localStorage.clear();
    originalMatchMedia = window.matchMedia;
  });

  afterEach(() => {
    if (originalMatchMedia) {
      Object.defineProperty(window, 'matchMedia', {
        writable: true,
        configurable: true,
        value: originalMatchMedia,
      });
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
    expect(doc.documentElement.style.getPropertyValue('--fo-accent')).toBe('#2563EB');
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
