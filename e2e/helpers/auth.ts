import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { expect, Page } from '@playwright/test';
import { E2eUserSeed } from './factory';
import { waitForSixDigitCode } from './mailpit';

const AUTH_RATE_STATE_FILE = path.join(os.tmpdir(), 'familion-e2e-auth-rate-limit.json');
// Matches edge/nginx.conf: limit_req_zone ... zone=auth:10m rate=5r/m; burst=3 nodelay;
const LEAK_INTERVAL_MS = 12_500;
const SAFE_BURST_CEILING = 2.0;

interface RateLimitState {
  excess: number;
  updatedAt: number;
}

function readRateLimitState(): RateLimitState {
  try {
    const raw = fs.readFileSync(AUTH_RATE_STATE_FILE, 'utf8');
    const parsed = JSON.parse(raw) as Partial<RateLimitState>;
    if (
      typeof parsed.excess === 'number' &&
      Number.isFinite(parsed.excess) &&
      typeof parsed.updatedAt === 'number' &&
      Number.isFinite(parsed.updatedAt)
    ) {
      return { excess: parsed.excess, updatedAt: parsed.updatedAt };
    }
  } catch {
    // No prior state file yet.
  }
  return { excess: -1, updatedAt: Date.now() };
}

function writeRateLimitState(state: RateLimitState): void {
  try {
    fs.writeFileSync(AUTH_RATE_STATE_FILE, JSON.stringify(state), 'utf8');
  } catch {
    // Non-fatal if temp directory is read-only.
  }
}

/**
 * Coordinates pacing for sensitive `/bff/auth/*` endpoints across Playwright
 * test files and projects so the edge proxy's 5 r/m (burst 3) rate limit is respected.
 */
export async function reserveAuthSlot(): Promise<void> {
  const now = Date.now();
  const state = readRateLimitState();
  const elapsed = Math.max(0, now - state.updatedAt);
  const decayed = state.excess < 0 ? -1 : Math.max(-1, state.excess - elapsed / LEAK_INTERVAL_MS);
  const nextExcess = decayed < 0 ? 0 : decayed + 1;

  if (nextExcess > SAFE_BURST_CEILING) {
    const waitMs = Math.ceil((nextExcess - SAFE_BURST_CEILING) * LEAK_INTERVAL_MS);
    await new Promise((resolve) => setTimeout(resolve, waitMs));
    const afterWait = Date.now();
    const afterDecayed = Math.max(
      -1,
      state.excess - Math.max(0, afterWait - state.updatedAt) / LEAK_INTERVAL_MS,
    );
    writeRateLimitState({
      excess: afterDecayed < 0 ? 0 : afterDecayed + 1,
      updatedAt: afterWait,
    });
    return;
  }

  writeRateLimitState({
    excess: nextExcess,
    updatedAt: now,
  });
}

export function recordAuthRateLimitHit(): void {
  // Nginx rejected excess > 3.0 without incrementing it, so actual excess is <= 3.0.
  writeRateLimitState({
    excess: 3.0,
    updatedAt: Date.now(),
  });
}

/**
 * Clicks a submit action that calls a rate-limited `/bff/auth/*` endpoint.
 * Paces proactively and retries if a 429 response is still returned.
 */
export async function submitRateLimitedAuth(
  page: Page,
  endpointSubstring: string,
  triggerSubmit: () => Promise<void>,
): Promise<void> {
  for (let attempt = 0; attempt < 4; attempt++) {
    await reserveAuthSlot();

    const responsePromise = page.waitForResponse(
      (res) =>
        res.url().includes(endpointSubstring) && res.request().method() === 'POST',
      { timeout: 25_000 },
    );

    await triggerSubmit();
    const response = await responsePromise;

    if (response.status() !== 429) {
      return;
    }

    recordAuthRateLimitHit();
  }
}

export async function fillOtpCode(page: Page, code: string): Promise<void> {
  expect(code).toMatch(/^\d{6}$/);
  for (let index = 0; index < 6; index++) {
    const digitInput = page.getByLabel(`Dígito ${index + 1} de 6`);
    await digitInput.fill(code[index]);
  }
}

export async function registerVerifyAndLogin(
  page: Page,
  user: E2eUserSeed,
  options: { trustDevice?: boolean } = {},
): Promise<void> {
  const trustDevice = options.trustDevice ?? true;

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

  const verification = await waitForSixDigitCode(user.email, {
    subjectIncludes: 'Verifica tu correo',
  });
  await fillOtpCode(page, verification.code);

  await submitRateLimitedAuth(page, '/bff/auth/verify-email', async () => {
    await page.getByRole('button', { name: 'Verificar' }).click();
  });

  await expect(page).toHaveURL(/\/auth\/login/);

  await page.locator('#login-email').fill(user.email);
  await page.locator('#login-password').fill(user.password);

  await submitRateLimitedAuth(page, '/bff/auth/login', async () => {
    await page.getByRole('button', { name: 'Ingresar' }).click();
  });

  await expect(page).toHaveURL(/\/auth\/2fa/);

  const twoFactor = await waitForSixDigitCode(user.email, {
    subjectIncludes: 'código de acceso',
    excludeIds: new Set([verification.messageId]),
  });

  const trustCheckbox = page.locator('#two-factor-trust-device');
  if (trustDevice) {
    if (!(await trustCheckbox.isChecked())) {
      await trustCheckbox.check();
    }
  } else if (await trustCheckbox.isChecked()) {
    await trustCheckbox.uncheck();
  }

  await fillOtpCode(page, twoFactor.code);

  await submitRateLimitedAuth(page, '/bff/auth/2fa/verify', async () => {
    await page.getByRole('button', { name: 'Verificar' }).click();
  });

  await expect(page).toHaveURL(/\/home$/);
}
