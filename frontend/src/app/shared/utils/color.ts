const DEFAULT_ACCENT = '#0F766E';

type RgbTuple = [number, number, number];

function normalizeHex(hex: string): string | null {
  const cleaned = hex.trim().replace(/^#/, '');
  if (/^[0-9a-fA-F]{3}$/.test(cleaned)) {
    const expanded = cleaned
      .split('')
      .map((ch) => ch + ch)
      .join('');
    return `#${expanded.toUpperCase()}`;
  }
  if (/^[0-9a-fA-F]{6}$/.test(cleaned)) {
    return `#${cleaned.toUpperCase()}`;
  }
  return null;
}

function parseRgb(hex: string): RgbTuple {
  const normalized = normalizeHex(hex);
  if (!normalized) {
    return [0, 0, 0];
  }
  const value = Number.parseInt(normalized.slice(1), 16);
  return [(value >> 16) & 255, (value >> 8) & 255, value & 255];
}

function toHex([r, g, b]: RgbTuple): string {
  const clamp = (n: number): number => Math.max(0, Math.min(255, Math.round(n)));
  const hexByte = (n: number): string => clamp(n).toString(16).padStart(2, '0');
  return `#${hexByte(r)}${hexByte(g)}${hexByte(b)}`.toUpperCase();
}

function linearizeChannel(channel: number): number {
  const srgb = channel / 255;
  return srgb <= 0.04045 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4;
}

function mixRgb(a: RgbTuple, b: RgbTuple, weightA: number): RgbTuple {
  const w = Math.max(0, Math.min(1, weightA));
  return [
    a[0] * w + b[0] * (1 - w),
    a[1] * w + b[1] * (1 - w),
    a[2] * w + b[2] * (1 - w),
  ];
}

/**
 * Computes WCAG 2.1 relative luminance in the range [0, 1].
 */
export function relativeLuminance(hex: string): number {
  const [r, g, b] = parseRgb(hex);
  return 0.2126 * linearizeChannel(r) + 0.7152 * linearizeChannel(g) + 0.0722 * linearizeChannel(b);
}

/**
 * Computes WCAG 2.1 contrast ratio between two hex colors in the range [1, 21].
 */
export function contrastRatio(a: string, b: string): number {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  const lighter = Math.max(la, lb);
  const darker = Math.min(la, lb);
  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Returns the highest-contrast foreground color (#FFFFFF or #0A0A0A) on the given background.
 */
export function readableTextOn(hex: string): '#FFFFFF' | '#0A0A0A' {
  const whiteContrast = contrastRatio(hex, '#FFFFFF');
  const darkContrast = contrastRatio(hex, '#0A0A0A');
  return whiteContrast >= darkContrast ? '#FFFFFF' : '#0A0A0A';
}

/**
 * Generates a harmonious tonal scale of `count` distinct colors starting from `accentHex`
 * and blending progressively toward neutral grays.
 */
export function tonalScale(accentHex: string, count: number): string[] {
  const n = Math.floor(count);
  if (n <= 0) {
    return [];
  }

  const baseHex = normalizeHex(accentHex) ?? DEFAULT_ACCENT;
  if (n === 1) {
    return [baseHex];
  }

  const accentRgb = parseRgb(baseHex);
  const result: string[] = [baseHex];
  const seen = new Set<string>([baseHex]);

  for (let i = 1; i < n; i++) {
    const progress = i / (n - 1);
    const accentWeight = Math.max(0.08, 1 - progress * 0.88);
    // Sweep neutral target from mid-dark cool gray to light neutral gray
    const grayLevel = 92 + Math.round(progress * 116);
    const neutralRgb: RgbTuple = [grayLevel, grayLevel + 2, Math.min(255, grayLevel + 6)];

    let candidateRgb = mixRgb(accentRgb, neutralRgb, accentWeight);
    let candidateHex = toHex(candidateRgb);

    // Guarantee distinctness even if the accent itself is neutral gray or count is large
    let step = 1;
    while (seen.has(candidateHex)) {
      const delta = (step % 2 === 0 ? 1 : -1) * Math.ceil(step / 2) * 7;
      candidateRgb = [
        Math.max(0, Math.min(255, candidateRgb[0] + delta)),
        Math.max(0, Math.min(255, candidateRgb[1] + delta)),
        Math.max(0, Math.min(255, candidateRgb[2] + delta)),
      ];
      candidateHex = toHex(candidateRgb);
      step++;
    }

    seen.add(candidateHex);
    result.push(candidateHex);
  }

  return result;
}
