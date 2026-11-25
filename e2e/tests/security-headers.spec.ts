import { expect, test } from '@playwright/test';

test.describe('Edge proxy security headers and core isolation', () => {
  test('serves hardening headers on / and never calls port 8081 from the browser', async ({
    page,
  }) => {
    const requestedUrls: string[] = [];
    page.on('request', (request) => {
      requestedUrls.push(request.url());
    });

    const response = await page.goto('/', { waitUntil: 'networkidle' });
    expect(response, 'Root document response should be present').not.toBeNull();
    expect(response!.status()).toBe(200);

    const headers = response!.headers();

    expect(headers['content-security-policy'], 'Content-Security-Policy header').toBeTruthy();
    expect(headers['content-security-policy']).toContain("default-src 'self'");
    expect(headers['x-frame-options']).toBe('DENY');
    expect(headers['x-content-type-options']).toBe('nosniff');

    expect(requestedUrls.length).toBeGreaterThan(0);

    const coreRequests = requestedUrls.filter((rawUrl) => {
      try {
        const parsed = new URL(rawUrl);
        return parsed.port === '8081';
      } catch {
        return rawUrl.includes(':8081');
      }
    });
    expect(coreRequests, 'Browser must never call the internal Core API on :8081').toEqual([]);

    const bffRequests = requestedUrls.filter((rawUrl) => rawUrl.includes('/bff/'));
    expect(
      bffRequests.length,
      'App bootstrap should route API traffic through /bff/',
    ).toBeGreaterThan(0);
  });
});
