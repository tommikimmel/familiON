import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Me } from '../../../core/api/models/me.model';
import { AuthStore } from '../../../core/auth/auth.store';
import { SessionStore } from '../../../core/state/session.store';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { Family, Invitation, InvitationCreated, Member } from './family.models';
import { FamilyStore } from './family.store';

function buildFamily(overrides: Partial<Family> = {}): Family {
  return {
    id: 'fam-1',
    name: 'Familia García',
    baseCurrency: 'ARS',
    referenceCurrency: 'USD',
    referenceRateSource: 'USD_MEP',
    timezone: 'America/Argentina/Buenos_Aires',
    enabledCurrencies: [
      { currencyCode: 'ARS', preferredRateSource: null, isActive: true },
      { currencyCode: 'USD', preferredRateSource: 'USD_MEP', isActive: true },
    ],
    ...overrides,
  };
}

function buildMembers(): Member[] {
  return [
    {
      id: 'user-adult',
      fullName: 'Ana García',
      email: 'ana@familion.test',
      role: 'ADULTO',
      familyVisibility: 'FAMILY_FULL',
      accentColor: '#0F766E',
      isActive: true,
    },
    {
      id: 'user-child',
      fullName: 'Lucas García',
      email: 'lucas@familion.test',
      role: 'HIJO',
      familyVisibility: 'OWN_ONLY',
      accentColor: '#2563EB',
      isActive: true,
    },
  ];
}

function buildInvitation(overrides: Partial<Invitation> = {}): Invitation {
  return {
    id: 'inv-1',
    email: 'sofia@familion.test',
    role: 'HIJO',
    familyVisibility: 'FAMILY_SUMMARY',
    expiresAt: '2026-12-01T12:00:00Z',
    createdAt: '2026-11-24T12:00:00Z',
    invitedBy: { id: 'user-adult', fullName: 'Ana García' },
    ...overrides,
  };
}

function buildMe(role: 'ADULTO' | 'HIJO' = 'ADULTO'): Me {
  return {
    id: role === 'ADULTO' ? 'user-adult' : 'user-child',
    email: role === 'ADULTO' ? 'ana@familion.test' : 'lucas@familion.test',
    fullName: role === 'ADULTO' ? 'Ana García' : 'Lucas García',
    role,
    familyVisibility: role === 'ADULTO' ? 'FAMILY_FULL' : 'OWN_ONLY',
    themePreference: 'SYSTEM',
    accentColor: '#0F766E',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia García',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'USD_MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
  };
}

describe('FamilyStore', () => {
  let store: FamilyStore;
  let httpMock: HttpTestingController;
  let session: SessionStore;
  let authStore: AuthStore;
  let toast: ToastService;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    store = TestBed.inject(FamilyStore);
    httpMock = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
    authStore = TestBed.inject(AuthStore);
    toast = TestBed.inject(ToastService);

    session.setMe(buildMe('ADULTO'));
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads family, members and invitations for ADULTO', async () => {
    const family = buildFamily();
    const members = buildMembers();
    const invitations = [buildInvitation()];

    const promise = store.load(true);
    expect(store.loading()).toBe(true);

    httpMock.expectOne('/bff/api/family').flush(family);
    httpMock.expectOne('/bff/api/family/members').flush(members);
    httpMock.expectOne('/bff/api/family/invitations').flush(invitations);

    await promise;

    expect(store.loading()).toBe(false);
    expect(store.family()).toEqual(family);
    expect(store.members()).toEqual(members);
    expect(store.invitations()).toEqual(invitations);
  });

  it('skips invitations request when loading as HIJO', async () => {
    session.setMe(buildMe('HIJO'));
    const promise = store.load(false);

    httpMock.expectOne('/bff/api/family').flush(buildFamily());
    httpMock.expectOne('/bff/api/family/members').flush(buildMembers());
    httpMock.expectNone('/bff/api/family/invitations');

    await promise;

    expect(store.invitations()).toEqual([]);
  });

  it('creates an invitation, prepends it to the list, and stores lastCreatedInvitation', async () => {
    const existing = buildInvitation({ id: 'inv-old', email: 'old@familion.test' });
    const loadPromise = store.load(true);
    httpMock.expectOne('/bff/api/family').flush(buildFamily());
    httpMock.expectOne('/bff/api/family/members').flush(buildMembers());
    httpMock.expectOne('/bff/api/family/invitations').flush([existing]);
    await loadPromise;

    const created: InvitationCreated = {
      ...buildInvitation({ id: 'inv-new', email: 'nuevo@familion.test' }),
      inviteUrl: 'https://familion.app/auth/invite?token=abc123',
    };

    const createPromise = store.createInvitation({
      email: 'nuevo@familion.test',
      role: 'HIJO',
      familyVisibility: 'FAMILY_SUMMARY',
    });

    const req = httpMock.expectOne('/bff/api/family/invitations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'nuevo@familion.test',
      role: 'HIJO',
      familyVisibility: 'FAMILY_SUMMARY',
    });
    req.flush(created, { status: 201, statusText: 'Created' });

    const result = await createPromise;

    expect(result).toEqual(created);
    expect(store.lastCreatedInvitation()).toEqual(created);
    expect(store.invitations().map((i) => i.id)).toEqual(['inv-new', 'inv-old']);
  });

  it('revokes an invitation and removes it from the list', async () => {
    const inv1 = buildInvitation({ id: 'inv-1' });
    const inv2 = buildInvitation({ id: 'inv-2', email: 'other@familion.test' });
    const loadPromise = store.load(true);
    httpMock.expectOne('/bff/api/family').flush(buildFamily());
    httpMock.expectOne('/bff/api/family/members').flush(buildMembers());
    httpMock.expectOne('/bff/api/family/invitations').flush([inv1, inv2]);
    await loadPromise;

    const revokePromise = store.revokeInvitation('inv-1');
    const req = httpMock.expectOne('/bff/api/family/invitations/inv-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    const ok = await revokePromise;

    expect(ok).toBe(true);
    expect(store.invitations().map((i) => i.id)).toEqual(['inv-2']);
  });

  it('applies optimistic visibility update and rolls back on error with toast', async () => {
    const toastSpy = vi.spyOn(toast, 'error');
    const loadPromise = store.load(false);
    httpMock.expectOne('/bff/api/family').flush(buildFamily());
    httpMock.expectOne('/bff/api/family/members').flush(buildMembers());
    await loadPromise;

    const updatePromise = store.updateMemberVisibility('user-child', 'FAMILY_FULL');

    // Optimistic state is reflected before HTTP completes
    expect(store.members().find((m) => m.id === 'user-child')?.familyVisibility).toBe(
      'FAMILY_FULL',
    );

    const req = httpMock.expectOne('/bff/api/family/members/user-child/visibility');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ familyVisibility: 'FAMILY_FULL' });
    req.flush(
      { status: 500, detail: 'No se pudo cambiar la visibilidad' },
      { status: 500, statusText: 'Internal Server Error' },
    );

    const ok = await updatePromise;

    expect(ok).toBe(false);
    expect(store.members().find((m) => m.id === 'user-child')?.familyVisibility).toBe('OWN_ONLY');
    expect(toastSpy).toHaveBeenCalledWith('No se pudo cambiar la visibilidad');
  });

  it('calls AuthStore.reloadMe after updating settings and maps 409 baseCurrency error', async () => {
    const reloadSpy = vi.spyOn(authStore, 'reloadMe').mockResolvedValue(buildMe('ADULTO'));
    const toastErrorSpy = vi.spyOn(toast, 'error');

    const loadPromise = store.load(false);
    httpMock.expectOne('/bff/api/family').flush(buildFamily());
    httpMock.expectOne('/bff/api/family/members').flush(buildMembers());
    await loadPromise;

    const successPromise = store.updateSettings({ name: 'Los García' });
    const okReq = httpMock.expectOne('/bff/api/family/settings');
    expect(okReq.request.method).toBe('PATCH');
    okReq.flush(buildFamily({ name: 'Los García' }));

    expect(await successPromise).toBe(true);
    expect(store.family()?.name).toBe('Los García');
    expect(reloadSpy).toHaveBeenCalledTimes(1);

    const conflictPromise = store.updateSettings({ baseCurrency: 'USD' });
    const conflictReq = httpMock.expectOne('/bff/api/family/settings');
    conflictReq.flush(
      { status: 409, detail: 'Conflict' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(await conflictPromise).toBe(false);
    const expectedMsg =
      'No se puede cambiar la divisa principal porque ya hay movimientos cargados.';
    expect(store.settingsError()).toBe(expectedMsg);
    expect(toastErrorSpy).toHaveBeenCalledWith(expectedMsg);
  });
});
