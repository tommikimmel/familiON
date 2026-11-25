import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, Signal, inject, signal } from '@angular/core';
import { ThemePreference } from '../api/models/me.model';
import { readableTextOn } from '../../shared/utils/color';
import {
  ACCENT_ALPHA,
  LIGHT_ACCENT_FG,
  findAccentPreset,
  hexToRgba,
} from './accent-presets';

const THEME_STORAGE_KEY = 'fo.theme';
const ACCENT_STORAGE_KEY = 'fo.accent';
const DEFAULT_THEME: ThemePreference = 'SYSTEM';
const DEFAULT_ACCENT = '#0F766E';
const HEX_COLOR_PATTERN = /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;

function isThemePreference(value: string | null): value is ThemePreference {
  return value === 'LIGHT' || value === 'DARK' || value === 'SYSTEM';
}

function normalizeAccent(value: string | null | undefined): string {
  if (!value) {
    return DEFAULT_ACCENT;
  }
  const trimmed = value.trim();
  if (!HEX_COLOR_PATTERN.test(trimmed)) {
    return DEFAULT_ACCENT;
  }
  if (trimmed.length === 4) {
    const [r, g, b] = trimmed.slice(1).split('');
    return `#${r}${r}${g}${g}${b}${b}`.toUpperCase();
  }
  return trimmed.toUpperCase();
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly doc = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _theme = signal<ThemePreference>(this.readStoredTheme());
  private readonly _accent = signal<string>(this.readStoredAccent());
  private readonly _isDark = signal<boolean>(false);

  private mediaQuery: MediaQueryList | null = null;
  private readonly mediaListener = (event: MediaQueryListEvent): void => {
    if (this._theme() === 'SYSTEM') {
      this.applyDarkClass(event.matches);
      this.applyAccentVariables(this._accent(), event.matches);
    }
  };

  readonly theme: Signal<ThemePreference> = this._theme.asReadonly();
  readonly accent: Signal<string> = this._accent.asReadonly();
  readonly isDark: Signal<boolean> = this._isDark.asReadonly();

  constructor() {
    this.syncToDom(this._theme(), this._accent());
    this.destroyRef.onDestroy(() => this.detachSystemListener());
  }

  apply(prefs: { themePreference: ThemePreference; accentColor: string }): void {
    const nextTheme = isThemePreference(prefs.themePreference)
      ? prefs.themePreference
      : DEFAULT_THEME;
    const nextAccent = normalizeAccent(prefs.accentColor);

    this._theme.set(nextTheme);
    this._accent.set(nextAccent);

    this.writeStorage(THEME_STORAGE_KEY, nextTheme);
    this.writeStorage(ACCENT_STORAGE_KEY, nextAccent);

    this.syncToDom(nextTheme, nextAccent);
  }

  toggleDark(): void {
    const nextTheme: ThemePreference = this._isDark() ? 'LIGHT' : 'DARK';
    this.apply({ themePreference: nextTheme, accentColor: this._accent() });
  }

  private syncToDom(theme: ThemePreference, accent: string): void {
    this.detachSystemListener();

    if (theme === 'DARK') {
      this.applyDarkClass(true);
    } else if (theme === 'LIGHT') {
      this.applyDarkClass(false);
    } else {
      const mql = this.getColorSchemeMediaQuery();
      this.applyDarkClass(Boolean(mql?.matches));
      if (mql) {
        this.mediaQuery = mql;
        mql.addEventListener('change', this.mediaListener);
      }
    }

    this.applyAccentVariables(accent, this._isDark());
  }

  /**
   * Resolves the effective accent for the current theme and writes the accent tokens.
   * Preset accents (mock `ACCENTS`) switch to their dark color + `darkFg` in dark theme;
   * custom colors keep their hue and get a WCAG-readable foreground.
   */
  private applyAccentVariables(accent: string, isDark: boolean): void {
    const preset = findAccentPreset(accent);
    let main = accent;
    let foreground: string = readableTextOn(accent);
    if (preset) {
      main = (isDark ? preset.dark : preset.light).toUpperCase();
      foreground = (isDark ? preset.darkFg : LIGHT_ACCENT_FG).toUpperCase();
    }

    const mode = isDark ? 'dark' : 'light';
    const root = this.doc.documentElement;
    root.style.setProperty('--fo-accent', main);
    root.style.setProperty('--fo-accent-contrast', foreground);
    root.style.setProperty('--fo-accent-soft', hexToRgba(main, ACCENT_ALPHA.soft[mode]));
    root.style.setProperty('--fo-accent-glow-sm', hexToRgba(main, ACCENT_ALPHA.glowMobile[mode]));
    root.style.setProperty('--fo-accent-glow-lg', hexToRgba(main, ACCENT_ALPHA.glowDesktop[mode]));
    root.style.setProperty('--fo-accent-mid', hexToRgba(main, ACCENT_ALPHA.mid));
    root.style.setProperty('--fo-accent-light', hexToRgba(main, ACCENT_ALPHA.light));

    this.updateThemeColorMeta(main);
  }

  private applyDarkClass(isDark: boolean): void {
    this._isDark.set(isDark);
    this.doc.documentElement.classList.toggle('dark', isDark);
  }

  private updateThemeColorMeta(color: string): void {
    let meta = this.doc.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    if (!meta && this.doc.head) {
      meta = this.doc.createElement('meta');
      meta.setAttribute('name', 'theme-color');
      this.doc.head.appendChild(meta);
    }
    meta?.setAttribute('content', color);
  }

  private getColorSchemeMediaQuery(): MediaQueryList | null {
    const win = this.doc.defaultView;
    if (!win || typeof win.matchMedia !== 'function') {
      return null;
    }
    return win.matchMedia('(prefers-color-scheme: dark)');
  }

  private detachSystemListener(): void {
    if (this.mediaQuery) {
      this.mediaQuery.removeEventListener('change', this.mediaListener);
      this.mediaQuery = null;
    }
  }

  private readStoredTheme(): ThemePreference {
    const stored = this.readStorage(THEME_STORAGE_KEY);
    return isThemePreference(stored) ? stored : DEFAULT_THEME;
  }

  private readStoredAccent(): string {
    return normalizeAccent(this.readStorage(ACCENT_STORAGE_KEY));
  }

  private readStorage(key: string): string | null {
    try {
      return this.doc.defaultView?.localStorage?.getItem(key) ?? null;
    } catch {
      return null;
    }
  }

  private writeStorage(key: string, value: string): void {
    try {
      this.doc.defaultView?.localStorage?.setItem(key, value);
    } catch {
      // Ignore storage errors (private mode / quota)
    }
  }
}
