import { contrastRatio, readableTextOn, relativeLuminance, tonalScale } from './color';

describe('color utilities', () => {
  describe('relativeLuminance', () => {
    it('returns 1 for white and 0 for black', () => {
      expect(relativeLuminance('#FFFFFF')).toBeCloseTo(1, 5);
      expect(relativeLuminance('#000000')).toBeCloseTo(0, 5);
    });

    it('supports shorthand 3-digit hex values', () => {
      expect(relativeLuminance('#fff')).toBeCloseTo(1, 5);
      expect(relativeLuminance('#000')).toBeCloseTo(0, 5);
    });
  });

  describe('contrastRatio', () => {
    it('returns 21:1 for white against black', () => {
      expect(contrastRatio('#FFFFFF', '#000000')).toBeCloseTo(21, 5);
      expect(contrastRatio('#000000', '#FFFFFF')).toBeCloseTo(21, 5);
    });

    it('returns 1:1 for identical colors', () => {
      expect(contrastRatio('#0F766E', '#0F766E')).toBeCloseTo(1, 5);
    });
  });

  describe('readableTextOn', () => {
    it('returns white text on dark teal #0F766E', () => {
      expect(readableTextOn('#0F766E')).toBe('#FFFFFF');
    });

    it('returns dark text on bright yellow #FDE047', () => {
      expect(readableTextOn('#FDE047')).toBe('#0A0A0A');
    });
  });

  describe('tonalScale', () => {
    it('returns empty array when count is 0 or negative', () => {
      expect(tonalScale('#0F766E', 0)).toEqual([]);
      expect(tonalScale('#0F766E', -3)).toEqual([]);
    });

    it('returns N distinct hex colors starting with the accent color', () => {
      const colors = tonalScale('#0F766E', 6);
      expect(colors).toHaveLength(6);
      expect(colors[0]).toBe('#0F766E');
      expect(new Set(colors).size).toBe(6);
      for (const color of colors) {
        expect(color).toMatch(/^#[0-9A-F]{6}$/);
      }
    });

    it('returns distinct colors even when accent is a neutral gray', () => {
      const colors = tonalScale('#71717A', 5);
      expect(colors).toHaveLength(5);
      expect(new Set(colors).size).toBe(5);
    });
  });
});
