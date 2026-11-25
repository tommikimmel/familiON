/**
 * Editable table of (mock, mock tab) ↔ (app route) pairs captured by `capture.ts`.
 * Each pair declares which mock file represents each viewport. T43–T45 append their own pairs.
 */
export type ViewportId = 'mobile' | 'desktop';
export type ThemeId = 'light' | 'dark';
export type MockTab = 'home' | 'plan' | 'vehicles' | 'profile';

export interface MockTarget {
  /** File name inside docs/design/mocks/. */
  file: string;
  /** Value of the mock's `[data-tab]` attribute to activate, if any. */
  tab?: MockTab;
}

export interface VisualPair {
  /** Used in output file names: `<id>-<viewport>-<theme>.png`. */
  id: string;
  /** App route (relative to APP_BASE_URL). */
  route: string;
  mocks: Partial<Record<ViewportId, MockTarget>>;
}

export const VIEWPORTS: Record<ViewportId, { width: number; height: number }> = {
  mobile: { width: 390, height: 844 },
  desktop: { width: 1440, height: 900 },
};

export const THEMES: readonly ThemeId[] = ['light', 'dark'];

export const PAIRS: readonly VisualPair[] = [
  {
    id: 'shell-home',
    route: '/home',
    mocks: {
      mobile: { file: 'mobile.html', tab: 'home' },
      desktop: { file: 'desktop.html', tab: 'home' },
    },
  },
  {
    id: 'home',
    route: '/home',
    mocks: {
      mobile: { file: 'mobile.html', tab: 'home' },
      desktop: { file: 'desktop.html', tab: 'home' },
    },
  },
  // quick-add (sheet/modal open) needs a click hook in capture.ts; added once T46 lands.
];
