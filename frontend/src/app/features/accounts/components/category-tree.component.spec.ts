import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Category, CategoryNode } from '../../../core/finance/finance.models';
import { CATEGORY_ICON_OPTIONS } from '../utils/category-picker-options';
import { CategoryFormComponent, CategoryFormSubmit } from './category-form.component';
import { CategoryTreeComponent } from './category-tree.component';

@Component({
  standalone: true,
  imports: [CategoryTreeComponent, CategoryFormComponent],
  template: `
    <fo-category-tree
      [expenseTree]="expenseTree"
      [incomeTree]="incomeTree"
      [isAdult]="isAdult()"
      (deactivateCategory)="deactivated.set($event)"
    />
    <fo-category-form
      [expenseRoots]="expenseTree"
      [incomeRoots]="incomeTree"
      (submitted)="lastSubmitted.set($event)"
    />
  `,
})
class CategoryHostComponent {
  readonly isAdult = signal(true);
  readonly deactivated = signal<Category | null>(null);
  readonly lastSubmitted = signal<CategoryFormSubmit | null>(null);

  readonly expenseTree: CategoryNode[] = [
    {
      id: 'cat-sys',
      parentCategoryId: null,
      name: 'Auto',
      kind: 'EXPENSE',
      icon: 'car',
      colorHex: '#2563EB',
      isSystem: true,
      systemKey: 'AUTO',
      children: [
        {
          id: 'cat-custom-child',
          parentCategoryId: 'cat-sys',
          name: 'Lavado',
          kind: 'EXPENSE',
          icon: 'sparkles',
          colorHex: '#0891B2',
          isSystem: false,
          systemKey: null,
        },
      ],
    },
  ];

  readonly incomeTree: CategoryNode[] = [
    {
      id: 'cat-inc',
      parentCategoryId: null,
      name: 'Sueldo',
      kind: 'INCOME',
      icon: 'briefcase',
      colorHex: '#16A34A',
      isSystem: true,
      systemKey: 'SALARY',
      children: [],
    },
  ];
}

describe('CategoryTreeComponent & CategoryFormComponent', () => {
  it('renders lock badges on system categories and allows deactivating only custom categories for adults', () => {
    const fixture = TestBed.createComponent(CategoryHostComponent);
    fixture.detectChanges();

    const hostEl = fixture.nativeElement as HTMLElement;
    const locks = hostEl.querySelectorAll('[data-testid="system-category-lock"]');
    expect(locks.length).toBe(2);

    // System category does not have a deactivate button; custom subcategory does.
    expect(hostEl.querySelector('button[aria-label="Desactivar categoría Auto"]')).toBeNull();
    const deactivateBtn = hostEl.querySelector(
      'button[aria-label="Desactivar categoría Lavado"]',
    ) as HTMLButtonElement | null;
    expect(deactivateBtn).not.toBeNull();

    deactivateBtn?.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.deactivated()?.id).toBe('cat-custom-child');

    // Non-adult users do not see management actions.
    fixture.componentInstance.isAdult.set(false);
    fixture.detectChanges();
    expect(hostEl.querySelector('button[aria-label="Desactivar categoría Lavado"]')).toBeNull();
  });

  it('offers exactly 20 Lucide icons in the category icon picker and emits create payload', () => {
    expect(CATEGORY_ICON_OPTIONS).toHaveLength(20);

    const fixture = TestBed.createComponent(CategoryHostComponent);
    fixture.detectChanges();

    const hostEl = fixture.nativeElement as HTMLElement;
    const pickerButtons = hostEl.querySelectorAll('[data-testid="category-icon-picker"] button');
    expect(pickerButtons.length).toBe(20);

    const nameInput = hostEl.querySelector('#fo-category-name') as HTMLInputElement;
    nameInput.value = 'Mascotas';
    nameInput.dispatchEvent(new Event('input'));

    const pawButton = hostEl.querySelector('button[aria-label="Ícono Mascotas"]') as HTMLButtonElement;
    pawButton.click();
    fixture.detectChanges();

    const formEl = hostEl.querySelector('form') as HTMLFormElement;
    formEl.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance.lastSubmitted()).toEqual({
      mode: 'create',
      payload: {
        name: 'Mascotas',
        kind: 'EXPENSE',
        parentCategoryId: null,
        icon: 'paw-print',
        colorHex: '#0F766E',
      },
    });
  });
});
