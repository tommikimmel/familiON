import { defineConfig } from '@playwright/test';

/**
 * Visual-fidelity tooling (not part of the smoke suite).
 * - `seed`: creates a ready-to-use adult with realistic sample data against the local stack.
 * - `capture`: renders mock vs app side by side for every pair in `pairs.ts`.
 */
export default defineConfig({
  testDir: '.',
  outputDir: './out/test-results',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 15 * 60_000,
  expect: { timeout: 20_000 },
  reporter: [['list']],
  projects: [
    {
      name: 'seed',
      testMatch: /seed\.ts$/,
      use: {
        baseURL: process.env.SEED_BASE_URL ?? process.env.E2E_BASE_URL ?? 'http://localhost:8088',
        viewport: { width: 1440, height: 900 },
      },
    },
    {
      name: 'capture',
      testMatch: /capture\.ts$/,
    },
  ],
});
