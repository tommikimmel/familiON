/**
 * Profile accent presets. Copied verbatim from the `ACCENTS` list of
 * docs/design/mocks/mobile.html (ids, light/dark colors and dark foreground).
 */
export interface AccentPreset {
  readonly id: string;
  readonly name: string;
  readonly light: string;
  readonly dark: string;
  readonly darkFg: string;
}

export const ACCENT_PRESETS: readonly AccentPreset[] = [
  { id: 'teal', name: 'Esmeralda', light: '#0f766e', dark: '#2dd4bf', darkFg: '#042f2e' },
  { id: 'blue', name: 'Azul', light: '#2563eb', dark: '#60a5fa', darkFg: '#0b1930' },
  { id: 'indigo', name: 'Índigo', light: '#4f46e5', dark: '#818cf8', darkFg: '#131135' },
  { id: 'violet', name: 'Violeta', light: '#7c3aed', dark: '#a78bfa', darkFg: '#1e1038' },
  { id: 'amber', name: 'Ámbar', light: '#b45309', dark: '#fbbf24', darkFg: '#271704' },
  { id: 'slate', name: 'Grafito', light: '#334155', dark: '#cbd5e1', darkFg: '#0f172a' },
];

/** Foreground used on top of the accent in light theme (mock: always white). */
export const LIGHT_ACCENT_FG = '#ffffff';

/** Accent variant opacities, exactly as computed by the mocks' `applyAccent()`. */
export const ACCENT_ALPHA = {
  soft: { light: 0.11, dark: 0.16 },
  glowMobile: { light: 0.2, dark: 0.28 },
  glowDesktop: { light: 0.18, dark: 0.26 },
  mid: 0.56,
  light: 0.28,
} as const;

/** Finds the preset whose light or dark color matches the given hex (case-insensitive). */
export function findAccentPreset(hex: string): AccentPreset | null {
  const needle = hex.trim().toLowerCase();
  return (
    ACCENT_PRESETS.find(
      (preset) => preset.light === needle || preset.dark === needle,
    ) ?? null
  );
}

/** Mock `hexToRgba`: converts a 6-digit hex color to an `rgba()` string. */
export function hexToRgba(hex: string, alpha: number): string {
  const h = hex.replace('#', '');
  const r = Number.parseInt(h.slice(0, 2), 16);
  const g = Number.parseInt(h.slice(2, 4), 16);
  const b = Number.parseInt(h.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
