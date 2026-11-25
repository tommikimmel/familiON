import { expect, Page, test } from '@playwright/test';
import { registerVerifyAndLogin } from '../helpers/auth';
import { buildUserSeed } from '../helpers/factory';

async function enterQuickAddAmount(page: Page, amount: string): Promise<void> {
  const desktopInput = page.getByTestId('quick-add-amount-input');
  if (await desktopInput.isVisible()) {
    await desktopInput.fill(amount);
    return;
  }

  for (const ch of amount) {
    await page.getByTestId(`keypad-key-${ch}`).click();
  }
}

async function selectQuickAddCategory(page: Page, categoryName: string): Promise<void> {
  const sheet = page.getByTestId('quick-add-sheet');
  const directChip = sheet
    .locator('[data-testid^="category-chip-"]')
    .filter({ hasText: categoryName })
    .first();

  if (await directChip.isVisible()) {
    await directChip.click();
    return;
  }

  await page.getByTestId('category-more-btn').click();
  const searchPanel = page.getByTestId('category-search-panel');
  await expect(searchPanel).toBeVisible();
  await page.locator('#fo-quick-add-cat-search').fill(categoryName);

  const match = searchPanel
    .locator('[data-testid^="category-search-item-"]')
    .filter({ hasText: categoryName })
    .first();
  await expect(match).toBeVisible();
  await match.click();
}

test.describe('Accounts and Quick Add transaction flow', () => {
  test('creates a Cash ARS account, records a 1500 Supermercado expense via quick-add, and verifies it on Home and Transactions', async ({
    page,
  }) => {
    const user = buildUserSeed('QuickAdd');
    await registerVerifyAndLogin(page, user);

    // 1. Create a Cash ARS account in /accounts
    await page.goto('/accounts');
    await expect(page.getByRole('heading', { name: 'Cuentas', level: 1 })).toBeVisible();

    await page.getByRole('button', { name: /Nueva cuenta/i }).first().click();

    await expect(page.locator('#fo-account-name')).toBeVisible();
    await page.locator('#fo-account-name').fill('Efectivo ARS');
    await page.getByTestId('account-type-CASH').click();
    await page.locator('#fo-account-currency').selectOption('ARS');

    await page.getByRole('button', { name: 'Crear cuenta' }).click();

    await expect(
      page.getByRole('status').filter({ hasText: 'Cuenta creada.' }),
    ).toBeVisible();
    await expect(page.getByText('Efectivo ARS').first()).toBeVisible();

    // 2. Open Quick Add (+) and record a 1500 ARS expense under "Supermercado"
    await page
      .locator('[data-testid="desktop-quick-add-btn"], [data-testid="mobile-quick-add-btn"]')
      .first()
      .click();

    await expect(page.getByTestId('quick-add-sheet')).toBeVisible();

    await enterQuickAddAmount(page, '1500');
    await selectQuickAddCategory(page, 'Supermercado');

    const saveBtn = page.getByTestId('quick-add-save-btn');
    await expect(saveBtn).toBeEnabled();
    await saveBtn.click();

    // 3. Toast confirmation
    await expect(
      page.getByRole('status').filter({ hasText: 'Gasto guardado' }),
    ).toBeVisible();
    await expect(page.getByTestId('quick-add-sheet')).toBeHidden();

    // 4. Transaction appears in Home (/home)
    await page.goto('/home');
    await expect(page).toHaveURL(/\/home$/);
    const homeMovements = page.locator('fo-movements-table, fo-recent-list');
    await expect(homeMovements).toBeVisible();
    await expect(homeMovements.getByText('Supermercado').first()).toBeVisible();
    await expect(homeMovements.getByText(/1\.?500/).first()).toBeVisible();

    // 5. Transaction appears in Transactions (/transactions)
    await page.goto('/transactions');
    await expect(page).toHaveURL(/\/transactions$/);
    const txContainer = page.locator(
      '[data-testid="transactions-desktop-table"], [data-testid="transactions-day-list"]',
    );
    await expect(txContainer).toBeVisible();
    await expect(txContainer.getByText('Supermercado').first()).toBeVisible();
    await expect(txContainer.getByText(/1\.?500/).first()).toBeVisible();
  });
});
