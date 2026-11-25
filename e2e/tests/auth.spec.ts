import { expect, test } from '@playwright/test';
import {
  fillOtpCode,
  submitRateLimitedAuth,
} from '../helpers/auth';
import { buildUserSeed } from '../helpers/factory';
import { waitForSixDigitCode } from '../helpers/mailpit';

test.describe('Authentication and trusted device flow', () => {
  test('registers, verifies email via Mailpit, completes 2FA with trusted device, logs out, and logs in without 2FA', async ({
    page,
  }) => {
    const user = buildUserSeed('Auth');

    // 1. Register a new adult account + family
    await page.goto('/auth/register');
    await expect(page.getByRole('heading', { name: 'Crear cuenta' })).toBeVisible();

    await page.locator('#register-full-name').fill(user.fullName);
    await page.locator('#register-family-name').fill(user.familyName);
    await page.locator('#register-email').fill(user.email);
    await page.locator('#register-password').fill(user.password);
    await page.locator('#register-confirm-password').fill(user.password);

    await submitRateLimitedAuth(page, '/bff/auth/register', async () => {
      await page.getByRole('button', { name: 'Crear cuenta familiar' }).click();
    });

    await expect(page).toHaveURL(/\/auth\/verify-email/);

    // 2. Verify email using the 6-digit code captured by Mailpit
    const verification = await waitForSixDigitCode(user.email, {
      subjectIncludes: 'Verifica tu correo',
    });
    await fillOtpCode(page, verification.code);

    await submitRateLimitedAuth(page, '/bff/auth/verify-email', async () => {
      await page.getByRole('button', { name: 'Verificar' }).click();
    });

    await expect(page).toHaveURL(/\/auth\/login/);

    // 3. First login triggers 2FA
    await page.locator('#login-email').fill(user.email);
    await page.locator('#login-password').fill(user.password);

    await submitRateLimitedAuth(page, '/bff/auth/login', async () => {
      await page.getByRole('button', { name: 'Ingresar' }).click();
    });

    await expect(page).toHaveURL(/\/auth\/2fa/);

    // 4. Complete 2FA via Mailpit and trust this device
    const twoFactor = await waitForSixDigitCode(user.email, {
      subjectIncludes: 'código de acceso',
      excludeIds: new Set([verification.messageId]),
    });

    const trustCheckbox = page.locator('#two-factor-trust-device');
    await expect(trustCheckbox).toBeVisible();
    if (!(await trustCheckbox.isChecked())) {
      await trustCheckbox.check();
    }
    await expect(trustCheckbox).toBeChecked();

    await fillOtpCode(page, twoFactor.code);

    await submitRateLimitedAuth(page, '/bff/auth/2fa/verify', async () => {
      await page.getByRole('button', { name: 'Verificar' }).click();
    });

    await expect(page).toHaveURL(/\/home$/);
    await expect(
      page.locator('[data-testid="desktop-sidebar"], [data-testid="mobile-header"]'),
    ).toBeVisible();

    // 5. Log out from Profile
    await page.locator('a[href="/profile"]').first().click();
    await expect(page).toHaveURL(/\/profile$/);
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await expect(page).toHaveURL(/\/auth\/login/);

    // 6. Log in again in the same context -> trusted device skips 2FA
    await page.locator('#login-email').fill(user.email);
    await page.locator('#login-password').fill(user.password);

    await submitRateLimitedAuth(page, '/bff/auth/login', async () => {
      await page.getByRole('button', { name: 'Ingresar' }).click();
    });

    await expect(page).toHaveURL(/\/home$/);
    await expect(page).not.toHaveURL(/\/auth\/2fa/);
  });
});
