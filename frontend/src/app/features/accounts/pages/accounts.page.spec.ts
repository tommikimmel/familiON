import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Me } from '../../../core/api/models/me.model';
import { Account, Category } from '../../../core/finance/finance.models';
import { SessionStore } from '../../../core/state/session.store';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { AccountsPage } from './accounts.page';

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 'user-1',
    email: 'ana@familion.test',
    fullName: 'Ana García',
    role: 'ADULTO',
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'LIGHT',
    accentColor: '#0F766E',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia García',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
    ...overrides,
  };
}

describe('AccountsPage', () => {
  let httpMock: HttpTestingController;
  let session: SessionStore;
  let viewContext: ViewContextStore;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    session = TestBed.inject(SessionStore);
    session.setMe(buildMe());
    viewContext = TestBed.inject(ViewContextStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('loads accounts for active view, reloads when view changes, and displays card details and currency totals', async () => {
    const fixture = TestBed.createComponent(AccountsPage);
    fixture.detectChanges();

    const accountsReq = httpMock.expectOne('/bff/api/accounts?view=PERSONAL');
    const membersReq = httpMock.expectOne('/bff/api/family/members');

    const accounts: Account[] = [
      {
        id: 'acc-cash',
        name: 'Efectivo',
        accountType: 'CASH',
        currencyCode: 'ARS',
        initialBalance: '10000.0000',
        currentBalance: '25000.0000',
        ownerUserId: null,
        creditLimit: null,
        closingDay: null,
        dueDay: null,
        isActive: true,
      },
      {
        id: 'acc-visa',
        name: 'Visa Galicia',
        accountType: 'CREDIT_CARD',
        currencyCode: 'ARS',
        initialBalance: '0.0000',
        currentBalance: '-15000.0000',
        ownerUserId: 'user-1',
        creditLimit: '500000.0000',
        closingDay: 20,
        dueDay: 5,
        isActive: true,
      },
    ];

    accountsReq.flush(accounts);
    membersReq.flush([]);

    await fixture.whenStable();
    fixture.detectChanges();

    const hostEl = fixture.nativeElement as HTMLElement;
    expect(hostEl.textContent).toContain('Efectivo');
    expect(hostEl.textContent).toContain('Compartida');
    expect(hostEl.textContent).toContain('Visa Galicia');
    expect(hostEl.textContent).toContain('Cierra el día 20 · Vence el día 5');
    expect(hostEl.textContent).toContain('Límite:');
    expect(hostEl.querySelector('.text-danger')).not.toBeNull();

    // Switching ViewContextStore view triggers reload with FAMILY.
    viewContext.setView('FAMILY');
    fixture.detectChanges();

    const familyReq = httpMock.expectOne('/bff/api/accounts?view=FAMILY');
    familyReq.flush(accounts);
    await fixture.whenStable();
    fixture.detectChanges();

    // Switching to Categories tab loads /bff/api/categories and displays the tree.
    const categoryTab = Array.from(hostEl.querySelectorAll('button[role="tab"]')).find((btn) =>
      btn.textContent?.includes('Categorías'),
    ) as HTMLButtonElement;
    categoryTab.click();
    fixture.detectChanges();

    const categories: Category[] = [
      {
        id: 'cat-1',
        parentCategoryId: null,
        name: 'Supermercado',
        kind: 'EXPENSE',
        icon: 'shopping-cart',
        colorHex: '#0F766E',
        isSystem: true,
        systemKey: 'GROCERIES',
      },
      {
        id: 'cat-2',
        parentCategoryId: null,
        name: 'Sueldo',
        kind: 'INCOME',
        icon: 'briefcase',
        colorHex: '#16A34A',
        isSystem: true,
        systemKey: 'SALARY',
      },
    ];
    httpMock.expectOne('/bff/api/categories').flush(categories);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(hostEl.textContent).toContain('Supermercado');
    expect(hostEl.textContent).toContain('Sueldo');
  });
});
