import * as fs from 'node:fs';
import * as path from 'node:path';
import { Browser, BrowserContext, expect, Page, test } from '@playwright/test';
import { fillOtpCode, submitRateLimitedAuth } from '../helpers/auth';
import { waitForSixDigitCode } from '../helpers/mailpit';
import { MockTarget, PAIRS, ThemeId, THEMES, VIEWPORTS, ViewportId, VisualPair } from './pairs';
import { OUT_DIR, readSession, VisualSession } from './session';

/**
 * Captures every (mock ↔ app) pair from pairs.ts in 390×844 and 1440×900, light and dark,
 * and writes `out/<pair>-<viewport>-<theme>.png` with the mock (left) and the app (right).
 * Public pairs without a mock for a viewport are written as app-only screenshots.
 * Raw screenshots are kept under `out/raw/`.
 */

const APP_BASE_URL = (process.env.APP_BASE_URL ?? 'http://localhost:4200').replace(/\/$/, '');
const MOCKS_DIR = path.resolve(__dirname, '../../docs/design/mocks');
const RAW_DIR = path.join(OUT_DIR, 'raw');
const SETTLE_MS = Number(process.env.VISUAL_SETTLE_MS ?? 900);

/** Hides the mocks' presentation frame so only the app surface (`.fn-surface`) is captured. */
const MOCK_FRAME_CSS = `
  html, body { margin: 0 !important; padding: 0 !important; background: transparent !important; }
  .stage { max-width: none !important; margin: 0 !important; gap: 0 !important; display: block !important; }
  .outer-bar, .outer-controls, .accent-bar, #topAccentBar, .motion-col,
  .status-bar, .notch, .browser-chrome, .url-pill, .win-dots, .splash-overlay {
    display: none !important;
  }
  .main-grid { display: block !important; }
  .phone-bezel {
    width: 100vw !important; max-width: none !important; height: 100vh !important;
    padding: 0 !important; border-radius: 0 !important; background: none !important; box-shadow: none !important;
  }
  .phone-screen { height: 100vh !important; border-radius: 0 !important; }
  .browser-frame { border: 0 !important; border-radius: 0 !important; box-shadow: none !important; }
  .fn-surface { min-height: 100vh !important; }
  .app-shell { min-height: 100vh !important; }
`;

async function newViewportContext(
  browser: Browser,
  viewport: ViewportId,
  theme: ThemeId,
  storageState?: VisualSession['storageState'],
): Promise<BrowserContext> {
  return browser.newContext({
    viewport: VIEWPORTS[viewport],
    deviceScaleFactor: 1,
    colorScheme: theme,
    reducedMotion: 'reduce',
    storageState,
  });
}

async function captureMock(browser: Browser, target: MockTarget, viewport: ViewportId, theme: ThemeId): Promise<Buffer> {
  const context = await newViewportContext(browser, viewport, theme);
  try {
    const page = await context.newPage();
    await page.goto(`file://${path.join(MOCKS_DIR, target.file)}`);
    await page.addStyleTag({ content: MOCK_FRAME_CSS });
    // Let the splash timers finish so they cannot interfere with the capture.
    await page.waitForTimeout(1_700);

    await page.evaluate((t) => {
      const w = window as unknown as { setTheme?: (value: string) => void };
      if (typeof w.setTheme === 'function') {
        w.setTheme(t);
      } else {
        document.querySelectorAll('.fn-surface').forEach((el) => el.setAttribute('data-theme', t));
      }
    }, theme);

    if (target.tab) {
      const tab = page.locator(`[data-tab="${target.tab}"]`).first();
      if (await tab.isVisible()) {
        await tab.click();
      }
    }

    await page.mouse.move(0, 0);
    await page.waitForTimeout(SETTLE_MS);
    return await page.screenshot();
  } finally {
    await context.close();
  }
}

async function ensureLoggedIn(page: Page, session: VisualSession, route: string): Promise<void> {
  await page.goto(`${APP_BASE_URL}${route}`);
  await page.waitForLoadState('networkidle');
  if (!/\/auth\//.test(page.url())) {
    return;
  }

  await page.goto(`${APP_BASE_URL}/auth/login`);
  await page.locator('#login-email').fill(session.email);
  await page.locator('#login-password').fill(session.password);
  await submitRateLimitedAuth(page, '/bff/auth/login', async () => {
    await page.getByRole('button', { name: 'Ingresar' }).click();
  });
  await page.waitForURL(/\/(home|auth\/2fa)/);

  if (/\/auth\/2fa/.test(page.url())) {
    const twoFactor = await waitForSixDigitCode(session.email, { subjectIncludes: 'código de acceso' });
    const trust = page.locator('#two-factor-trust-device');
    if (!(await trust.isChecked())) {
      await trust.check();
    }
    await fillOtpCode(page, twoFactor.code);
    await submitRateLimitedAuth(page, '/bff/auth/2fa/verify', async () => {
      await page.getByRole('button', { name: 'Verificar' }).click();
    });
  }

  await expect(page).toHaveURL(/\/home$/);
  session.storageState = await page.context().storageState();
  await page.goto(`${APP_BASE_URL}${route}`);
  await page.waitForLoadState('networkidle');
}

async function captureApp(
  browser: Browser,
  session: VisualSession,
  pair: VisualPair,
  viewport: ViewportId,
  theme: ThemeId,
): Promise<Buffer> {
  const context = await newViewportContext(
    browser,
    viewport,
    theme,
    pair.public ? undefined : session.storageState,
  );
  try {
    await context.addInitScript((t) => {
      try {
        window.localStorage.setItem('fo.theme', t === 'dark' ? 'DARK' : 'LIGHT');
      } catch {
        // Storage can be unavailable; prefers-color-scheme still drives SYSTEM.
      }
    }, theme);
    const page = await context.newPage();
    if (pair.public) {
      await page.goto(`${APP_BASE_URL}${pair.route}`);
      await page.waitForLoadState('networkidle');
      await page.locator('main').first().waitFor({ state: 'visible' });
    } else {
      await ensureLoggedIn(page, session, pair.route);
      await page
        .locator('[data-testid="mobile-header"], [data-testid="desktop-sidebar"]')
        .first()
        .waitFor({ state: 'visible' });
    }
    await page.mouse.move(0, 0);
    await page.waitForTimeout(SETTLE_MS);
    return await page.screenshot();
  } finally {
    await context.close();
  }
}

async function composeSideBySide(
  browser: Browser,
  viewport: ViewportId,
  label: string,
  mock: Buffer,
  app: Buffer,
): Promise<Buffer> {
  const { width, height } = VIEWPORTS[viewport];
  const gap = 24;
  const header = 36;
  const context = await browser.newContext({
    viewport: { width: width * 2 + gap * 3, height: height + header + gap },
    deviceScaleFactor: 1,
  });
  try {
    const page = await context.newPage();
    const img = (buffer: Buffer): string => `data:image/png;base64,${buffer.toString('base64')}`;
    await page.setContent(`
      <style>
        body { margin: 0; background: #3f3f46; font: 600 13px system-ui, sans-serif; color: #fafafa; }
        .row { display: flex; gap: ${gap}px; padding: 0 ${gap}px ${gap}px; }
        .col { display: flex; flex-direction: column; }
        .lbl { height: ${header}px; display: flex; align-items: center; }
        img { display: block; width: ${width}px; height: ${height}px; outline: 1px solid #71717a; }
      </style>
      <div class="row">
        <div class="col"><div class="lbl">MOCK · ${label}</div><img src="${img(mock)}" /></div>
        <div class="col"><div class="lbl">APP · ${label}</div><img src="${img(app)}" /></div>
      </div>
    `);
    return await page.screenshot({ fullPage: true });
  } finally {
    await context.close();
  }
}

test('capture mock vs app side by side', async ({ browser }) => {
  const session = readSession();
  fs.mkdirSync(RAW_DIR, { recursive: true });
  const only = process.env.VISUAL_PAIRS?.split(',').map((id) => id.trim());
  const written: string[] = [];

  for (const pair of PAIRS) {
    if (only && !only.includes(pair.id)) {
      continue;
    }
    for (const viewport of Object.keys(VIEWPORTS) as ViewportId[]) {
      const mockTarget = pair.mocks[viewport];
      if (!mockTarget && !pair.public) {
        continue;
      }
      for (const theme of THEMES) {
        const name = `${pair.id}-${viewport}-${theme}`;
        await test.step(name, async () => {
          if (!mockTarget) {
            const appOnly = await captureApp(browser, session, pair, viewport, theme);
            const file = path.join(OUT_DIR, `${name}.png`);
            fs.writeFileSync(file, appOnly);
            written.push(path.relative(process.cwd(), file));
            return;
          }
          const mock = await captureMock(browser, mockTarget, viewport, theme);
          const app = await captureApp(browser, session, pair, viewport, theme);
          fs.writeFileSync(path.join(RAW_DIR, `${name}-mock.png`), mock);
          fs.writeFileSync(path.join(RAW_DIR, `${name}-app.png`), app);
          const label = `${pair.id} · ${VIEWPORTS[viewport].width}×${VIEWPORTS[viewport].height} · ${theme}`;
          const combined = await composeSideBySide(browser, viewport, label, mock, app);
          const file = path.join(OUT_DIR, `${name}.png`);
          fs.writeFileSync(file, combined);
          written.push(path.relative(process.cwd(), file));
        });
      }
    }
  }

  expect(written.length, 'at least one side-by-side image was generated').toBeGreaterThan(0);
  console.log(`Side-by-side captures:\n${written.map((file) => `  ${file}`).join('\n')}`);
});
