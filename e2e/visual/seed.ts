import { APIResponse, expect, Page, test } from '@playwright/test';
import { registerVerifyAndLogin, submitRateLimitedAuth } from '../helpers/auth';
import { buildChildEmail, buildUserSeed } from '../helpers/factory';
import { writeSession } from './session';

/**
 * Seeds a ready-to-use adult with realistic sample data through the BFF:
 * 2 ARS accounts (+ a credit card for the installment plan), ~12 current-month movements in
 * 5 categories with one income, one expense attributed to an invited child, a 6-installment
 * purchase, 2 fixed expenses, 1 USD goal with a contribution, and 1 vehicle with 2 fuel logs
 * and 1 service. Credentials and the browser storage state are written to out/session.json.
 */

interface Category {
  id: string;
  parentCategoryId: string | null;
  name: string;
  kind: 'EXPENSE' | 'INCOME';
  isActive?: boolean;
}

interface Member {
  id: string;
  fullName: string;
  role: 'ADULTO' | 'HIJO';
}

interface Identified {
  id: string;
}

const API = '/bff/api';

const PREFERRED_EXPENSE_CATEGORIES = [
  ['supermercado', 'súper', 'super', 'almacén'],
  ['comida', 'restaurant', 'delivery', 'salidas'],
  ['transporte', 'combustible', 'auto'],
  ['hogar', 'casa', 'servicios', 'vivienda'],
  ['salud', 'farmacia', 'educación', 'ocio', 'entretenimiento'],
];

function isoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Day of the current month, clamped so no movement lands in the future. */
function dayOfMonth(day: number): string {
  const today = new Date();
  return isoDate(new Date(today.getFullYear(), today.getMonth(), Math.min(day, today.getDate())));
}

class BffClient {
  constructor(private readonly page: Page) {}

  private async xsrfHeader(): Promise<Record<string, string>> {
    let cookies = await this.page.context().cookies();
    let token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
    if (!token) {
      await this.page.request.get('/bff/csrf');
      cookies = await this.page.context().cookies();
      token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
    }
    return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {};
  }

  private async parse<T>(label: string, response: APIResponse): Promise<T> {
    if (!response.ok()) {
      throw new Error(`${label} failed: HTTP ${response.status()} ${await response.text()}`);
    }
    const text = await response.text();
    return (text ? JSON.parse(text) : null) as T;
  }

  async get<T>(path: string): Promise<T> {
    return this.parse<T>(`GET ${path}`, await this.page.request.get(`${API}${path}`));
  }

  async post<T>(path: string, data: unknown): Promise<T> {
    const response = await this.page.request.post(`${API}${path}`, {
      data,
      headers: await this.xsrfHeader(),
    });
    return this.parse<T>(`POST ${path}`, response);
  }
}

function pickExpenseCategories(categories: readonly Category[]): Category[] {
  const pool = categories.filter((c) => c.kind === 'EXPENSE' && c.isActive !== false);
  const picked: Category[] = [];
  for (const aliases of PREFERRED_EXPENSE_CATEGORIES) {
    const match = pool.find(
      (c) => !picked.includes(c) && aliases.some((alias) => c.name.toLowerCase().includes(alias)),
    );
    if (match) {
      picked.push(match);
    }
  }
  for (const candidate of pool) {
    if (picked.length >= 5) {
      break;
    }
    if (!picked.includes(candidate)) {
      picked.push(candidate);
    }
  }
  expect(picked.length, 'at least 5 expense categories are available').toBeGreaterThanOrEqual(5);
  return picked.slice(0, 5);
}

test('seed a visual-review family with realistic sample data', async ({ page, browser, baseURL }) => {
  const seed = buildUserSeed('Visual');
  const adult = { ...seed, fullName: 'Laura Gómez', familyName: 'Familia Gómez' };
  const child = {
    fullName: 'Sofía Gómez',
    email: buildChildEmail(),
    password: `Child${String(Date.now()).slice(-6)}A1!`,
  };

  await test.step('register, verify and sign in the adult', async () => {
    await registerVerifyAndLogin(page, adult);
  });

  const api = new BffClient(page);

  await test.step('invite and register a child', async () => {
    const invitation = await api.post<{ inviteUrl: string }>('/family/invitations', {
      email: child.email,
      role: 'HIJO',
      familyVisibility: 'OWN_ONLY',
    });
    const childContext = await browser.newContext({ baseURL });
    try {
      const childPage = await childContext.newPage();
      await childPage.goto(invitation.inviteUrl);
      await childPage.locator('#invite-full-name').fill(child.fullName);
      await childPage.locator('#invite-password').fill(child.password);
      await childPage.locator('#invite-confirm-password').fill(child.password);
      await submitRateLimitedAuth(childPage, '/bff/auth/register-invite', async () => {
        await childPage.getByRole('button', { name: 'Unirme a la familia' }).click();
      });
      await expect(childPage).toHaveURL(/\/home$/);
    } finally {
      await childContext.close();
    }
  });

  const members = await api.get<Member[]>('/family/members');
  const childMember = members.find((member) => member.role === 'HIJO');
  expect(childMember, 'child member is listed').toBeTruthy();

  const categories = await api.get<Category[]>('/categories');
  const [market, food, transport, home, leisure] = pickExpenseCategories(categories);
  const income = categories.find((c) => c.kind === 'INCOME' && c.isActive !== false);
  expect(income, 'an income category is available').toBeTruthy();

  const cash = await api.post<Identified>('/accounts', {
    name: 'Efectivo ARS',
    accountType: 'CASH',
    currencyCode: 'ARS',
    initialBalance: '250000',
  });
  const wallet = await api.post<Identified>('/accounts', {
    name: 'Mercado Pago',
    accountType: 'WALLET',
    currencyCode: 'ARS',
    initialBalance: '900000',
  });
  const creditCard = await api.post<Identified>('/accounts', {
    name: 'Visa Galicia',
    accountType: 'CREDIT_CARD',
    currencyCode: 'ARS',
    creditLimit: '2500000',
    closingDay: 25,
    dueDay: 5,
  });

  await test.step('record current-month movements', async () => {
    const movements: Array<{
      type: 'EXPENSE' | 'INCOME';
      amount: string;
      day: number;
      account: Identified;
      category: Category;
      description: string;
      attributedToUserId?: string;
    }> = [
      { type: 'INCOME', amount: '1850000', day: 1, account: wallet, category: income as Category, description: 'Sueldo' },
      { type: 'EXPENSE', amount: '310000', day: 3, account: wallet, category: market, description: 'Supermercado Coto' },
      { type: 'EXPENSE', amount: '285000', day: 12, account: wallet, category: market, description: 'Supermercado mensual' },
      { type: 'EXPENSE', amount: '18500', day: 20, account: cash, category: market, description: 'Verdulería' },
      { type: 'EXPENSE', amount: '68400', day: 8, account: wallet, category: food, description: 'Almuerzo y cafés' },
      { type: 'EXPENSE', amount: '42000', day: 18, account: cash, category: food, description: 'Pizzería' },
      { type: 'EXPENSE', amount: '45600', day: 22, account: wallet, category: transport, description: 'YPF Nafta Súper' },
      { type: 'EXPENSE', amount: '12000', day: 14, account: cash, category: transport, description: 'SUBE' },
      { type: 'EXPENSE', amount: '68500', day: 9, account: wallet, category: home, description: 'Luz + Internet' },
      { type: 'EXPENSE', amount: '35000', day: 16, account: wallet, category: home, description: 'Ferretería' },
      { type: 'EXPENSE', amount: '30000', day: 16, account: cash, category: leisure, description: 'Librería / Salida' },
      {
        type: 'EXPENSE',
        amount: '20000',
        day: 21,
        account: cash,
        category: leisure,
        description: 'Salida con amigas',
        attributedToUserId: childMember?.id,
      },
    ];

    for (const movement of movements) {
      await api.post('/transactions', {
        type: movement.type,
        amount: movement.amount,
        currencyCode: 'ARS',
        transactionDate: dayOfMonth(movement.day),
        accountId: movement.account.id,
        categoryId: movement.category.id,
        description: movement.description,
        ...(movement.attributedToUserId ? { attributedToUserId: movement.attributedToUserId } : {}),
      });
    }
  });

  await test.step('create a 6-installment purchase', async () => {
    await api.post('/installment-plans', {
      description: 'Heladera Samsung',
      categoryId: home.id,
      creditCardAccountId: creditCard.id,
      currencyCode: 'ARS',
      totalInstallments: 6,
      amountMode: 'TOTAL',
      amount: '720000',
      hasInterest: false,
      purchaseDate: dayOfMonth(6),
    });
  });

  await test.step('create two fixed expenses', async () => {
    const firstOfMonth = dayOfMonth(1);
    await api.post('/recurring-expenses', {
      name: 'Alquiler / Expensas',
      categoryId: home.id,
      accountId: wallet.id,
      scope: 'FAMILY',
      baseAmount: '520000',
      currencyCode: 'ARS',
      dueDay: 10,
      adjustmentRule: 'IPC',
      startDate: firstOfMonth,
    });
    await api.post('/recurring-expenses', {
      name: 'Prepaga familiar',
      categoryId: leisure.id,
      accountId: wallet.id,
      scope: 'FAMILY',
      baseAmount: '245000',
      currencyCode: 'ARS',
      dueDay: 15,
      adjustmentRule: 'MANUAL',
      startDate: firstOfMonth,
    });
  });

  await test.step('create a USD savings goal with a contribution', async () => {
    const target = new Date();
    target.setMonth(target.getMonth() + 8);
    const goal = await api.post<Identified>('/savings-goals', {
      name: 'Vacaciones en Brasil',
      scope: 'FAMILY',
      targetAmount: '3000',
      targetUnit: 'USD',
      targetDate: isoDate(target),
    });
    await api.post(`/savings-goals/${goal.id}/contributions`, {
      amount: '450',
      currencyCode: 'USD',
      contributionDate: dayOfMonth(5),
      notes: 'Primer aporte',
    });
  });

  await test.step('create a vehicle with fuel logs and a service', async () => {
    const vehicle = await api.post<Identified>('/vehicles', {
      nickname: 'Auto familiar',
      brand: 'Toyota',
      model: 'Etios',
      modelYear: 2019,
      licensePlate: 'AE123CD',
      primaryFuelType: 'NAFTA_SUPER',
      currentOdometerKm: 58000,
    });
    await api.post(`/vehicles/${vehicle.id}/maintenance-logs`, {
      maintenanceType: 'SERVICE',
      maintenanceDate: dayOfMonth(2),
      odometerKm: 58000,
      description: 'Service 60.000 km',
      workshopName: 'Taller Norte',
      costAmount: '180000',
      currencyCode: 'ARS',
      accountId: wallet.id,
      nextDueKm: 68000,
    });
    for (const [index, odometerKm] of [58420, 58860].entries()) {
      await api.post(`/vehicles/${vehicle.id}/fuel-logs`, {
        fuelDate: dayOfMonth(index === 0 ? 7 : 19),
        odometerKm,
        fuelType: 'NAFTA_SUPER',
        liters: '38',
        pricePerLiter: '1200',
        currencyCode: 'ARS',
        fullTank: true,
        stationName: 'YPF',
        accountId: wallet.id,
      });
    }
  });

  await page.goto('/home');
  await expect(page).toHaveURL(/\/home$/);

  writeSession({
    createdAt: new Date().toISOString(),
    seedBaseUrl: baseURL ?? '',
    fullName: adult.fullName,
    familyName: adult.familyName,
    email: adult.email,
    password: adult.password,
    child,
    storageState: await page.context().storageState(),
  });
});
