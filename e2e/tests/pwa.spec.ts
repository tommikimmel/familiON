import { expect, test } from '@playwright/test';

interface WebManifestIcon {
  src: string;
  sizes?: string;
  type?: string;
  purpose?: string;
}

interface WebManifest {
  name?: string;
  short_name?: string;
  display?: string;
  start_url?: string;
  icons?: WebManifestIcon[];
}

test.describe('PWA manifest and production service worker', () => {
  test('serves a valid manifest.webmanifest with 192 and 512 icons and registers ngsw-worker.js', async ({
    page,
  }) => {
    // 1. Validate manifest.webmanifest
    const manifestResponse = await page.request.get('/manifest.webmanifest');
    expect(manifestResponse.status()).toBe(200);

    const manifest = (await manifestResponse.json()) as WebManifest;
    expect(manifest.name).toBeTruthy();
    expect(Array.isArray(manifest.icons)).toBe(true);

    const icons = manifest.icons ?? [];
    const icon192 = icons.find((icon) => icon.sizes?.split(/\s+/).includes('192x192'));
    const icon512 = icons.find((icon) => icon.sizes?.split(/\s+/).includes('512x512'));

    expect(icon192, 'manifest.webmanifest must declare a 192x192 icon').toBeDefined();
    expect(icon512, 'manifest.webmanifest must declare a 512x512 icon').toBeDefined();

    for (const icon of [icon192!, icon512!]) {
      const iconPath = icon.src.startsWith('/') ? icon.src : `/${icon.src}`;
      const iconRes = await page.request.get(iconPath);
      expect(iconRes.status(), `Manifest icon ${iconPath} should be reachable`).toBe(200);
    }

    // 2. Verify production service worker registers on the app
    await page.goto('/', { waitUntil: 'load' });

    const swSupported = await page.evaluate(() => 'serviceWorker' in navigator);
    expect(swSupported, 'navigator.serviceWorker should be supported').toBe(true);

    await expect
      .poll(
        async () =>
          page.evaluate(async () => {
            const registrations = await navigator.serviceWorker.getRegistrations();
            return registrations.map(
              (reg) =>
                reg.active?.scriptURL ??
                reg.waiting?.scriptURL ??
                reg.installing?.scriptURL ??
                '',
            );
          }),
        {
          timeout: 35_000,
          intervals: [500, 1000, 2000],
          message: 'Expected ngsw-worker.js service worker to be registered',
        },
      )
      .toContainEqual(expect.stringContaining('ngsw-worker.js'));

    const activeScriptUrl = await page.evaluate(async () => {
      const ready = await navigator.serviceWorker.ready;
      return ready.active?.scriptURL ?? '';
    });
    expect(activeScriptUrl).toContain('ngsw-worker.js');
  });
});
