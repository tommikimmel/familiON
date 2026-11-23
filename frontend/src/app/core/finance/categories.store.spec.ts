import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CategoriesStore } from './categories.store';
import { Category } from './finance.models';

function buildCategory(overrides: Partial<Category> = {}): Category {
  return {
    id: 'cat-1',
    parentCategoryId: null,
    name: 'Supermercado',
    kind: 'EXPENSE',
    icon: 'shopping-cart',
    colorHex: '#0F766E',
    isSystem: true,
    systemKey: 'FOOD_GROCERIES',
    ...overrides,
  };
}

describe('CategoriesStore', () => {
  let store: CategoriesStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    store = TestBed.inject(CategoriesStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('caches categories in memory on load and reloads when force is true', async () => {
    const firstBatch = [buildCategory({ id: 'cat-1', name: 'Supermercado' })];

    const firstLoad = store.load();
    httpMock.expectOne('/bff/api/categories').flush(firstBatch);
    await firstLoad;

    // Second call without force does not hit HTTP.
    await store.load();
    expect(store.categories()).toEqual(firstBatch);

    const secondBatch = [
      ...firstBatch,
      buildCategory({ id: 'cat-2', name: 'Sueldo', kind: 'INCOME', systemKey: 'SALARY' }),
    ];
    const forcedLoad = store.load(true);
    httpMock.expectOne('/bff/api/categories').flush(secondBatch);
    await forcedLoad;

    expect(store.categories()).toEqual(secondBatch);
  });

  it('builds a parent/children tree per kind', async () => {
    const loadPromise = store.load();
    httpMock.expectOne('/bff/api/categories').flush([
      buildCategory({ id: 'exp-auto', name: 'Auto', kind: 'EXPENSE', systemKey: 'AUTO' }),
      buildCategory({
        id: 'exp-fuel',
        parentCategoryId: 'exp-auto',
        name: 'Combustible',
        kind: 'EXPENSE',
        icon: 'fuel',
        systemKey: 'AUTO_FUEL',
      }),
      buildCategory({
        id: 'exp-service',
        parentCategoryId: 'exp-auto',
        name: 'Service',
        kind: 'EXPENSE',
        icon: 'wrench',
        systemKey: 'AUTO_MAINTENANCE',
      }),
      buildCategory({ id: 'exp-home', name: 'Hogar', kind: 'EXPENSE', systemKey: 'HOME' }),
      buildCategory({ id: 'inc-salary', name: 'Sueldo', kind: 'INCOME', systemKey: 'SALARY' }),
    ]);
    await loadPromise;

    const expenseTree = store.tree('EXPENSE')();
    expect(expenseTree).toHaveLength(2);
    expect(expenseTree[0].id).toBe('exp-auto');
    expect(expenseTree[0].children.map((c) => c.id)).toEqual(['exp-fuel', 'exp-service']);
    expect(expenseTree[1].id).toBe('exp-home');
    expect(expenseTree[1].children).toEqual([]);

    const incomeTree = store.tree('INCOME')();
    expect(incomeTree.map((c) => c.id)).toEqual(['inc-salary']);
  });

  it('ranks frequent categories using fo.categoryUsage in localStorage with system order fallback', async () => {
    localStorage.setItem(
      'fo.categoryUsage',
      JSON.stringify({
        'cat-3': 5,
        'cat-2': 2,
      }),
    );

    const loadPromise = store.load();
    httpMock.expectOne('/bff/api/categories').flush([
      buildCategory({ id: 'cat-1', name: 'Primero' }),
      buildCategory({ id: 'cat-2', name: 'Segundo' }),
      buildCategory({ id: 'cat-3', name: 'Tercero' }),
      buildCategory({ id: 'cat-4', name: 'Cuarto' }),
    ]);
    await loadPromise;

    const top3 = store.frequent('EXPENSE', 3);
    expect(top3().map((c) => c.id)).toEqual(['cat-3', 'cat-2', 'cat-1']);

    // Recording usage updates localStorage and reactively re-ranks.
    store.recordUsage('cat-4');
    store.recordUsage('cat-4');
    store.recordUsage('cat-4');

    expect(top3().map((c) => c.id)).toEqual(['cat-3', 'cat-4', 'cat-2']);
    const persisted = JSON.parse(localStorage.getItem('fo.categoryUsage') ?? '{}') as Record<
      string,
      number
    >;
    expect(persisted['cat-4']).toBe(3);
  });

  it('looks up categories by systemKey and supports create and update', async () => {
    const loadPromise = store.load();
    httpMock.expectOne('/bff/api/categories').flush([
      buildCategory({ id: 'cat-fuel', name: 'Combustible', systemKey: 'VEHICLE_FUEL' }),
    ]);
    await loadPromise;

    expect(store.bySystemKey('VEHICLE_FUEL')?.id).toBe('cat-fuel');
    expect(store.bySystemKey('NON_EXISTENT')).toBeUndefined();

    const created = buildCategory({
      id: 'cat-custom',
      name: 'Gimnasio',
      icon: 'heart-pulse',
      colorHex: '#2563EB',
      isSystem: false,
      systemKey: null,
    });
    const createPromise = store.create({
      name: 'Gimnasio',
      kind: 'EXPENSE',
      icon: 'heart-pulse',
      colorHex: '#2563EB',
    });
    const createReq = httpMock.expectOne('/bff/api/categories');
    expect(createReq.request.method).toBe('POST');
    createReq.flush(created);
    await createPromise;

    expect(store.byKind('EXPENSE')().map((c) => c.id)).toEqual(['cat-fuel', 'cat-custom']);

    const deactivatePromise = store.update('cat-custom', { isActive: false });
    const patchReq = httpMock.expectOne('/bff/api/categories/cat-custom');
    expect(patchReq.request.method).toBe('PATCH');
    expect(patchReq.request.body).toEqual({ isActive: false });
    patchReq.flush({ ...created, isActive: false });
    await deactivatePromise;

    expect(store.byKind('EXPENSE')().map((c) => c.id)).toEqual(['cat-fuel']);
  });
});
