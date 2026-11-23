import { DOCUMENT } from '@angular/common';
import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CategoriesApi } from './categories.api';
import {
  Category,
  CategoryCreate,
  CategoryKind,
  CategoryNode,
  CategoryUpdate,
} from './finance.models';

const CATEGORY_USAGE_STORAGE_KEY = 'fo.categoryUsage';

@Injectable({ providedIn: 'root' })
export class CategoriesStore {
  private readonly api = inject(CategoriesApi);
  private readonly doc = inject(DOCUMENT);

  private readonly _categories = signal<Category[]>([]);
  private readonly _loading = signal<boolean>(false);
  private readonly _usageVersion = signal<number>(0);

  private loaded = false;
  private inflightLoad: Promise<void> | null = null;

  readonly categories: Signal<Category[]> = this._categories.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();

  async load(force = false): Promise<void> {
    if (this.loaded && !force) {
      return;
    }
    if (this.inflightLoad && !force) {
      return this.inflightLoad;
    }

    const request = this.fetchCategories();
    this.inflightLoad = request;
    try {
      await request;
    } finally {
      if (this.inflightLoad === request) {
        this.inflightLoad = null;
      }
    }
  }

  byKind(kind: CategoryKind): Signal<Category[]> {
    return computed(() =>
      this._categories().filter((category) => category.kind === kind && category.isActive !== false),
    );
  }

  tree(kind: CategoryKind): Signal<CategoryNode[]> {
    const byKindSignal = this.byKind(kind);
    return computed(() => {
      const items = byKindSignal();
      const idSet = new Set(items.map((item) => item.id));
      const childrenByParent = new Map<string, Category[]>();

      for (const item of items) {
        if (item.parentCategoryId && idSet.has(item.parentCategoryId)) {
          const siblings = childrenByParent.get(item.parentCategoryId) ?? [];
          siblings.push(item);
          childrenByParent.set(item.parentCategoryId, siblings);
        }
      }

      return items
        .filter((item) => !item.parentCategoryId || !idSet.has(item.parentCategoryId))
        .map((root) => ({
          ...root,
          children: childrenByParent.get(root.id) ?? [],
        }));
    });
  }

  frequent(kind: CategoryKind, limit = 8): Signal<Category[]> {
    const byKindSignal = this.byKind(kind);
    return computed(() => {
      this._usageVersion();
      const usage = this.readUsage();
      const items = byKindSignal();

      return [...items]
        .sort((a, b) => (usage[b.id] ?? 0) - (usage[a.id] ?? 0))
        .slice(0, Math.max(0, limit));
    });
  }

  recordUsage(categoryId: string): void {
    if (!categoryId) {
      return;
    }
    const usage = this.readUsage();
    usage[categoryId] = (usage[categoryId] ?? 0) + 1;
    this.writeUsage(usage);
    this._usageVersion.update((version) => version + 1);
  }

  bySystemKey(key: string): Category | undefined {
    return this._categories().find((category) => category.systemKey === key);
  }

  async create(payload: CategoryCreate): Promise<Category> {
    const created = await firstValueFrom(this.api.create(payload));
    this._categories.update((current) => [
      ...current.filter((item) => item.id !== created.id),
      created,
    ]);
    return created;
  }

  async update(id: string, patch: CategoryUpdate): Promise<Category> {
    const updated = await firstValueFrom(this.api.update(id, patch));
    const normalized: Category =
      patch.isActive !== undefined && updated.isActive === undefined
        ? { ...updated, isActive: patch.isActive }
        : updated;

    this._categories.update((current) => {
      const exists = current.some((item) => item.id === normalized.id);
      if (!exists) {
        return [...current, normalized];
      }
      return current.map((item) => (item.id === normalized.id ? normalized : item));
    });
    return normalized;
  }

  private async fetchCategories(): Promise<void> {
    this._loading.set(true);
    try {
      const list = await firstValueFrom(this.api.list());
      this._categories.set(list);
      this.loaded = true;
    } finally {
      this._loading.set(false);
    }
  }

  private readUsage(): Record<string, number> {
    try {
      const raw = this.doc.defaultView?.localStorage?.getItem(CATEGORY_USAGE_STORAGE_KEY);
      if (!raw) {
        return {};
      }
      const parsed = JSON.parse(raw) as unknown;
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        return {};
      }
      const result: Record<string, number> = {};
      for (const [id, count] of Object.entries(parsed as Record<string, unknown>)) {
        if (typeof count === 'number' && Number.isFinite(count) && count > 0) {
          result[id] = count;
        }
      }
      return result;
    } catch {
      return {};
    }
  }

  private writeUsage(usage: Record<string, number>): void {
    try {
      this.doc.defaultView?.localStorage?.setItem(
        CATEGORY_USAGE_STORAGE_KEY,
        JSON.stringify(usage),
      );
    } catch {
      // Ignore storage errors (private mode / quota).
    }
  }
}
