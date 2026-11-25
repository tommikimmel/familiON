import { expect, test } from '@playwright/test';
import {
  registerVerifyAndLogin,
  submitRateLimitedAuth,
} from '../helpers/auth';
import { buildChildEmail, buildUserSeed } from '../helpers/factory';

test.describe('Family invitation and child visibility', () => {
  test('adult invites a child with OWN_ONLY visibility and child does not see the Family toggle', async ({
    page,
    browser,
  }, testInfo) => {
    const adult = buildUserSeed('Adult');
    const childEmail = buildChildEmail();
    const childPassword = `Child${String(Date.now()).slice(-6)}A1`;

    // 1. Register and sign in as an adult
    await registerVerifyAndLogin(page, adult);

    // Positive control: the adult sees the "Personal | Familia" toggle
    const adultToggle = page.getByRole('tablist', { name: 'Vista personal o familiar' });
    await expect(adultToggle).toBeVisible();
    await expect(adultToggle.getByRole('tab', { name: 'Familia' })).toBeVisible();

    // 2. Navigate to /profile/family and invite a child with OWN_ONLY visibility
    await page.goto('/profile/family');
    await expect(page.getByTestId('invitation-form')).toBeVisible();

    await page.locator('#invite-email').fill(childEmail);
    await page.locator('#invite-role').selectOption('HIJO');

    await expect(page.getByTestId('invite-visibility-field')).toBeVisible();
    await page.locator('#invite-visibility').selectOption('OWN_ONLY');

    await page.getByRole('button', { name: /Crear invitación/i }).click();

    await expect(page.getByTestId('created-invitation-banner')).toBeVisible();
    const inviteUrl = await page.getByTestId('created-invitation-url').inputValue();
    expect(inviteUrl).toContain('/auth/invite?token=');

    // 3. Open inviteUrl in an isolated browser context matching the current device profile
    const childContext = await browser.newContext({
      baseURL: testInfo.project.use.baseURL,
      viewport: testInfo.project.use.viewport,
      userAgent: testInfo.project.use.userAgent,
      deviceScaleFactor: testInfo.project.use.deviceScaleFactor,
      isMobile: testInfo.project.use.isMobile,
      hasTouch: testInfo.project.use.hasTouch,
    });

    try {
      const childPage = await childContext.newPage();
      await childPage.goto(inviteUrl);

      await expect(
        childPage.getByRole('heading', { name: 'Aceptar invitación' }),
      ).toBeVisible();
      await expect(childPage.getByText(/Hijo\/a/i)).toBeVisible();

      await childPage.locator('#invite-full-name').fill('Lucas Hijo');
      await childPage.locator('#invite-password').fill(childPassword);
      await childPage.locator('#invite-confirm-password').fill(childPassword);

      await submitRateLimitedAuth(childPage, '/bff/auth/register-invite', async () => {
        await childPage.getByRole('button', { name: 'Unirme a la familia' }).click();
      });

      // 4. Child arrives at /home and does NOT see the "Familia" view toggle
      await expect(childPage).toHaveURL(/\/home$/);
      await expect(
        childPage.locator('[data-testid="desktop-sidebar"], [data-testid="mobile-header"]'),
      ).toBeVisible();

      const childViewToggle = childPage.getByRole('tablist', {
        name: 'Vista personal o familiar',
      });
      await expect(childViewToggle).toHaveCount(0);
      await expect(childPage.getByRole('tab', { name: 'Familia' })).toHaveCount(0);
    } finally {
      await childContext.close();
    }
  });
});
