import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { Me } from '../../../core/api/models/me.model';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { SessionStore } from '../../../core/state/session.store';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AllowancesPage } from './allowances.page';
import { Allowance, DisburseResult, FamilyMemberSummary } from './data-access/allowances.models';

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 'user-adult-1',
    email: 'adulto@example.com',
    fullName: 'Papá Adulto',
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

const SAMPLE_ALLOWANCE: Allowance = {
  id: 'al-1',
  child: { id: 'child-1', fullName: 'Tomás García', accentColor: '#2563EB' },
  assignedBy: { id: 'user-adult-1', fullName: 'Papá Adulto' },
  sourceAccount: { id: 'acc-1', name: 'Banco Galicia ARS' },
  targetAccount: { id: 'acc-2', name: 'Mercado Pago Tomás' },
  amount: '15000.0000',
  currencyCode: 'ARS',
  frequency: 'MONTHLY',
  dayOfPeriod: 1,
  adjustmentRule: 'IPC',
  isActive: true,
  currentAmount: '16500.0000',
  nextDisbursementDate: '2026-12-01',
  lastDisbursementDate: '2026-11-01',
  childBalance: '8200.0000',
};

const SAMPLE_MEMBERS: FamilyMemberSummary[] = [
  { id: 'user-adult-1', fullName: 'Papá Adulto', role: 'ADULTO', accentColor: '#0F766E' },
  { id: 'child-1', fullName: 'Tomás García', role: 'HIJO', accentColor: '#2563EB' },
];

async function settle(fixture: ComponentFixture<AllowancesPage>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('AllowancesPage', () => {
  let fixture: ComponentFixture<AllowancesPage>;
  let httpMock: HttpTestingController;
  let session: SessionStore;
  let financeEvents: FinanceEvents;
  let toast: ToastService;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [AllowancesPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });

    session = TestBed.inject(SessionStore);
    financeEvents = TestBed.inject(FinanceEvents);
    toast = TestBed.inject(ToastService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  describe('ADULTO view', () => {
    beforeEach(() => {
      session.setMe(buildMe({ role: 'ADULTO' }));
    });

    it('renders heading "Mensualidades" and child allowances card list', async () => {
      fixture = TestBed.createComponent(AllowancesPage);
      fixture.detectChanges();

      httpMock.expectOne('/bff/api/family/allowances').flush([SAMPLE_ALLOWANCE]);
      httpMock.expectOne('/bff/api/family/members').flush(SAMPLE_MEMBERS);
      httpMock.expectOne('/bff/api/accounts?view=FAMILY').flush([]);

      await settle(fixture);

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('h1')?.textContent?.trim()).toBe('Mensualidades');
      expect(el.textContent).toContain('Tomás García');
      expect(el.textContent).toContain('Acreditar ahora');
      expect(el.textContent).toContain('Nueva mensualidad');
    });

    it('opens disburse sheet and confirms disbursement', async () => {
      fixture = TestBed.createComponent(AllowancesPage);
      fixture.detectChanges();

      httpMock.expectOne('/bff/api/family/allowances').flush([SAMPLE_ALLOWANCE]);
      httpMock.expectOne('/bff/api/family/members').flush(SAMPLE_MEMBERS);
      httpMock.expectOne('/bff/api/accounts?view=FAMILY').flush([]);

      await settle(fixture);

      const host = fixture.nativeElement as HTMLElement;
      const disburseBtn = host.querySelector('fo-allowance-card fo-button[variant="primary"] button') as HTMLButtonElement;
      disburseBtn.click();
      fixture.detectChanges();

      // Disburse sheet is now open
      const confirmBtn = host.querySelector('fo-allowance-disburse-sheet fo-button[variant="primary"] button') as HTMLButtonElement;
      confirmBtn.click();
      fixture.detectChanges();

      const disburseReq = httpMock.expectOne('/bff/api/family/allowances/al-1/disburse');
      expect(disburseReq.request.method).toBe('POST');

      const disburseResult: DisburseResult = {
        allowance: {
          ...SAMPLE_ALLOWANCE,
          childBalance: '24700.0000',
        },
        outTransactionId: 'tx-1',
        inTransactionId: 'tx-2',
      };
      disburseReq.flush(disburseResult);

      await settle(fixture);

      expect(financeEvents.last()?.type).toBe('transaction-created');
      expect(toast.current()?.message).toBe('Mensualidad acreditada correctamente.');
    });
  });

  describe('HIJO view', () => {
    beforeEach(() => {
      session.setMe(buildMe({ id: 'child-1', role: 'HIJO', fullName: 'Tomás García' }));
    });

    it('renders "Tu mensualidad" with balance and link to transactions', async () => {
      fixture = TestBed.createComponent(AllowancesPage);
      fixture.detectChanges();

      httpMock.expectOne('/bff/api/family/allowances').flush([SAMPLE_ALLOWANCE]);
      httpMock.expectOne('/bff/api/family/members').flush(SAMPLE_MEMBERS);
      httpMock.expectOne('/bff/api/accounts?view=FAMILY').flush([]);

      await settle(fixture);

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('h1')?.textContent?.trim()).toBe('Mensualidades');
      expect(el.textContent).toContain('Tu mensualidad');
      expect(el.textContent).toContain('Saldo disponible');
      expect(el.textContent).toContain('Ver mis movimientos');
      // Should not show adult controls
      expect(el.textContent).not.toContain('Nueva mensualidad');
      expect(el.textContent).not.toContain('Acreditar ahora');
    });

    it('shows empty state when no allowance is assigned to child', async () => {
      fixture = TestBed.createComponent(AllowancesPage);
      fixture.detectChanges();

      httpMock.expectOne('/bff/api/family/allowances').flush([]);
      httpMock.expectOne('/bff/api/family/members').flush(SAMPLE_MEMBERS);
      httpMock.expectOne('/bff/api/accounts?view=FAMILY').flush([]);

      await settle(fixture);

      const el = fixture.nativeElement as HTMLElement;
      expect(el.textContent).toContain('Sin mensualidad asignada');
      expect(el.textContent).toContain('Ver mis movimientos');
    });
  });
});
