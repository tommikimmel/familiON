import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Category, CategoryKind, CategoryNode } from '../../../core/finance/finance.models';
import { ButtonComponent, CardComponent, EmptyStateComponent, IconComponent } from '../../../shared/ui';

export interface CreateCategoryIntent {
  kind: CategoryKind;
  parentCategoryId: string | null;
}

@Component({
  selector: 'fo-category-tree',
  standalone: true,
  imports: [ButtonComponent, CardComponent, EmptyStateComponent, IconComponent],
  template: `
    <div class="grid grid-cols-1 gap-5 lg:grid-cols-2">
      @for (section of sections(); track section.kind) {
        <section [attr.aria-label]="section.title" class="space-y-3">
          <div class="flex items-center justify-between gap-2">
            <div class="flex items-center gap-2">
              <h2 class="text-sm font-semibold text-text">{{ section.title }}</h2>
              <span
                class="rounded-full bg-surface-2 px-2 py-0.5 text-xs font-medium text-muted tabular-nums"
              >
                {{ section.nodes.length }}
              </span>
            </div>

            @if (isAdult()) {
              <fo-button
                variant="ghost"
                size="sm"
                (click)="createCategory.emit({ kind: section.kind, parentCategoryId: null })"
              >
                <fo-icon name="plus" [size]="14" />
                <span>Nueva</span>
              </fo-button>
            }
          </div>

          @if (section.nodes.length === 0) {
            <fo-empty-state
              icon="circle-ellipsis"
              [title]="'Sin categorías de ' + section.title.toLowerCase()"
              description="Creá una categoría para organizar tus movimientos."
            />
          } @else {
            <div class="space-y-2.5">
              @for (node of section.nodes; track node.id) {
                <fo-card padding="sm">
                  <div class="flex items-center justify-between gap-2">
                    <div class="flex min-w-0 items-center gap-2.5">
                      <span
                        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl"
                        [style.background-color]="badgeBg(node.colorHex)"
                        [style.color]="resolvedColor(node.colorHex)"
                        aria-hidden="true"
                      >
                        <fo-icon [name]="node.icon || 'circle-ellipsis'" [size]="18" />
                      </span>

                      <div class="min-w-0">
                        <div class="flex items-center gap-1.5">
                          <span class="truncate text-sm font-medium text-text">
                            {{ node.name }}
                          </span>
                          @if (node.isSystem) {
                            <span
                              data-testid="system-category-lock"
                              title="Categoría del sistema"
                              aria-label="Categoría del sistema"
                              class="inline-flex text-muted"
                            >
                              <fo-icon name="lock" [size]="13" />
                            </span>
                          }
                        </div>
                        @if (node.children.length > 0) {
                          <p class="text-[11px] text-muted">
                            {{ node.children.length }}
                            {{ node.children.length === 1 ? 'subcategoría' : 'subcategorías' }}
                          </p>
                        }
                      </div>
                    </div>

                    @if (isAdult()) {
                      <div class="flex shrink-0 items-center gap-1">
                        <button
                          type="button"
                          [attr.aria-label]="'Agregar subcategoría en ' + node.name"
                          title="Agregar subcategoría"
                          (click)="
                            createCategory.emit({ kind: node.kind, parentCategoryId: node.id })
                          "
                          class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-text"
                        >
                          <fo-icon name="plus" [size]="15" />
                        </button>

                        <button
                          type="button"
                          [attr.aria-label]="'Editar categoría ' + node.name"
                          title="Editar categoría"
                          (click)="editCategory.emit(node)"
                          class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-text"
                        >
                          <fo-icon name="pencil" [size]="15" />
                        </button>

                        @if (!node.isSystem) {
                          <button
                            type="button"
                            [attr.aria-label]="'Desactivar categoría ' + node.name"
                            title="Desactivar categoría"
                            (click)="deactivateCategory.emit(node)"
                            class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-danger transition-colors hover:bg-danger/10"
                          >
                            <fo-icon name="trash-2" [size]="15" />
                          </button>
                        }
                      </div>
                    }
                  </div>

                  @if (node.children.length > 0) {
                    <ul
                      class="mt-2.5 ml-4 space-y-1.5 border-l border-border pl-3"
                      [attr.aria-label]="'Subcategorías de ' + node.name"
                    >
                      @for (child of node.children; track child.id) {
                        <li class="flex items-center justify-between gap-2 py-1">
                          <div class="flex min-w-0 items-center gap-2">
                            <span
                              class="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
                              [style.background-color]="badgeBg(child.colorHex || node.colorHex)"
                              [style.color]="resolvedColor(child.colorHex || node.colorHex)"
                              aria-hidden="true"
                            >
                              <fo-icon [name]="child.icon || 'circle-ellipsis'" [size]="15" />
                            </span>

                            <span class="truncate text-sm text-text">{{ child.name }}</span>

                            @if (child.isSystem) {
                              <span
                                data-testid="system-category-lock"
                                title="Categoría del sistema"
                                aria-label="Categoría del sistema"
                                class="inline-flex text-muted"
                              >
                                <fo-icon name="lock" [size]="12" />
                              </span>
                            }
                          </div>

                          @if (isAdult()) {
                            <div class="flex shrink-0 items-center gap-1">
                              <button
                                type="button"
                                [attr.aria-label]="'Editar categoría ' + child.name"
                                title="Editar categoría"
                                (click)="editCategory.emit(child)"
                                class="fo-focus-ring inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-text"
                              >
                                <fo-icon name="pencil" [size]="14" />
                              </button>

                              @if (!child.isSystem) {
                                <button
                                  type="button"
                                  [attr.aria-label]="'Desactivar categoría ' + child.name"
                                  title="Desactivar categoría"
                                  (click)="deactivateCategory.emit(child)"
                                  class="fo-focus-ring inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg text-danger transition-colors hover:bg-danger/10"
                                >
                                  <fo-icon name="trash-2" [size]="14" />
                                </button>
                              }
                            </div>
                          }
                        </li>
                      }
                    </ul>
                  }
                </fo-card>
              }
            </div>
          }
        </section>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoryTreeComponent {
  readonly expenseTree = input.required<readonly CategoryNode[]>();
  readonly incomeTree = input.required<readonly CategoryNode[]>();
  readonly isAdult = input<boolean>(false);

  readonly createCategory = output<CreateCategoryIntent>();
  readonly editCategory = output<Category>();
  readonly deactivateCategory = output<Category>();

  protected sections(): readonly { kind: CategoryKind; title: string; nodes: readonly CategoryNode[] }[] {
    return [
      { kind: 'EXPENSE', title: 'Gastos', nodes: this.expenseTree() },
      { kind: 'INCOME', title: 'Ingresos', nodes: this.incomeTree() },
    ];
  }

  protected resolvedColor(colorHex: string | null): string {
    return colorHex && colorHex.trim().length > 0 ? colorHex : 'var(--fo-accent)';
  }

  protected badgeBg(colorHex: string | null): string {
    const color = this.resolvedColor(colorHex);
    return `color-mix(in srgb, ${color} 14%, transparent)`;
  }
}
