import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  BottomSheetComponent,
  ButtonComponent,
  IconComponent,
  SkeletonComponent,
} from '../../shared/ui';
import { AppUpdateService } from './app-update.service';
import { MarkdownBlock, parseMarkdown } from './markdown-renderer';
import { SystemChangelogEntry } from './pwa.models';

interface ParsedChangelogRelease {
  entry: SystemChangelogEntry;
  blocks: readonly MarkdownBlock[];
  formattedDate: string;
}

function formatReleaseDate(iso: string): string {
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) {
    return '';
  }
  return new Intl.DateTimeFormat('es-AR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(parsed);
}

@Component({
  selector: 'fo-pwa-update-banner',
  standalone: true,
  imports: [BottomSheetComponent, ButtonComponent, IconComponent, SkeletonComponent],
  template: `
    @if (appUpdate.showBanner()) {
      <div
        role="status"
        aria-live="polite"
        data-testid="pwa-update-banner"
        class="border-b border-accent/25 bg-accent-soft px-4 py-2.5 text-text"
      >
        <div class="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2 text-xs sm:text-sm">
          <div class="flex min-w-0 items-center gap-2">
            <fo-icon name="sparkles" [size]="16" class="shrink-0 text-accent" />
            <span class="font-medium">
              @if (appUpdate.availableVersion(); as version) {
                FamiliON v{{ version }} disponible
              } @else {
                Nueva versión de FamiliON disponible
              }
            </span>
            <span class="hidden text-muted sm:inline" aria-hidden="true">—</span>
            <button
              type="button"
              data-testid="pwa-update-now-btn"
              [disabled]="appUpdate.updating()"
              (click)="appUpdate.applyUpdate()"
              class="fo-focus-ring cursor-pointer rounded-md font-semibold text-accent underline-offset-2 hover:underline disabled:opacity-60"
            >
              {{ appUpdate.updating() ? 'Actualizando…' : 'Actualizar ahora' }}
            </button>
          </div>

          <div class="flex items-center gap-2">
            <button
              type="button"
              data-testid="pwa-whats-new-btn"
              (click)="openChangelog()"
              class="fo-focus-ring cursor-pointer rounded-lg px-2 py-1 text-xs font-medium text-accent transition-colors hover:bg-accent/10"
            >
              Qué cambió
            </button>

            <button
              type="button"
              data-testid="pwa-update-dismiss-btn"
              aria-label="Cerrar aviso de actualización"
              (click)="appUpdate.dismissBanner()"
              class="fo-focus-ring inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface/60 hover:text-text"
            >
              <fo-icon name="x" [size]="15" />
            </button>
          </div>
        </div>
      </div>
    }

    <fo-bottom-sheet
      [(open)]="changelogOpen"
      title="Qué cambió"
      ariaLabel="Novedades de la versión"
    >
      <div data-testid="pwa-changelog-modal" class="space-y-4">
        @if (appUpdate.changelogLoading()) {
          <div class="space-y-3" aria-busy="true">
            <fo-skeleton width="45%" height="1rem" />
            <fo-skeleton height="3.5rem" rounded="md" />
            <fo-skeleton height="3.5rem" rounded="md" />
          </div>
        } @else if (appUpdate.changelogError(); as errorMessage) {
          <div role="alert" class="rounded-xl border border-danger/30 bg-danger/10 p-3 text-sm text-danger">
            {{ errorMessage }}
          </div>
        } @else if (parsedReleases().length === 0) {
          <p class="text-sm text-muted">
            Hay mejoras de rendimiento y estabilidad listas para aplicar.
          </p>
        } @else {
          <div class="space-y-5">
            @for (release of parsedReleases(); track release.entry.version) {
              <article class="space-y-2 border-b border-border/70 pb-4 last:border-b-0 last:pb-0">
                <header class="flex flex-wrap items-center justify-between gap-2">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="rounded-full bg-accent-soft px-2.5 py-0.5 text-xs font-semibold text-accent tabular-nums">
                      v{{ release.entry.version }}
                    </span>
                    <h3 class="text-sm font-semibold text-text">{{ release.entry.title }}</h3>
                    @if (release.entry.isBreaking) {
                      <span class="rounded-full bg-warning/15 px-2 py-0.5 text-[11px] font-semibold text-warning">
                        Cambio importante
                      </span>
                    }
                  </div>
                  @if (release.formattedDate) {
                    <span class="text-xs text-muted">{{ release.formattedDate }}</span>
                  }
                </header>

                <div class="space-y-2 text-sm leading-relaxed text-text/90">
                  @for (block of release.blocks; track $index) {
                    @switch (block.type) {
                      @case ('heading') {
                        <h4 class="pt-1 text-xs font-semibold tracking-wide text-muted uppercase">
                          @for (token of block.tokens; track $index) {
                            @switch (token.type) {
                              @case ('bold') {
                                <strong class="font-semibold text-text">{{ token.text }}</strong>
                              }
                              @case ('code') {
                                <code class="rounded bg-surface-2 px-1 py-0.5 font-mono text-xs">{{ token.text }}</code>
                              }
                              @default {
                                {{ token.text }}
                              }
                            }
                          }
                        </h4>
                      }
                      @case ('unordered-list') {
                        <ul class="list-disc space-y-1 pl-5">
                          @for (itemTokens of block.items; track $index) {
                            <li>
                              @for (token of itemTokens; track $index) {
                                @switch (token.type) {
                                  @case ('bold') {
                                    <strong class="font-semibold text-text">{{ token.text }}</strong>
                                  }
                                  @case ('code') {
                                    <code class="rounded bg-surface-2 px-1 py-0.5 font-mono text-xs">{{ token.text }}</code>
                                  }
                                  @default {
                                    {{ token.text }}
                                  }
                                }
                              }
                            </li>
                          }
                        </ul>
                      }
                      @case ('ordered-list') {
                        <ol class="list-decimal space-y-1 pl-5">
                          @for (itemTokens of block.items; track $index) {
                            <li>
                              @for (token of itemTokens; track $index) {
                                @switch (token.type) {
                                  @case ('bold') {
                                    <strong class="font-semibold text-text">{{ token.text }}</strong>
                                  }
                                  @case ('code') {
                                    <code class="rounded bg-surface-2 px-1 py-0.5 font-mono text-xs">{{ token.text }}</code>
                                  }
                                  @default {
                                    {{ token.text }}
                                  }
                                }
                              }
                            </li>
                          }
                        </ol>
                      }
                      @default {
                        <p>
                          @for (token of block.tokens; track $index) {
                            @switch (token.type) {
                              @case ('bold') {
                                <strong class="font-semibold text-text">{{ token.text }}</strong>
                              }
                              @case ('code') {
                                <code class="rounded bg-surface-2 px-1 py-0.5 font-mono text-xs">{{ token.text }}</code>
                              }
                              @default {
                                {{ token.text }}
                              }
                            }
                          }
                        </p>
                      }
                    }
                  }
                </div>
              </article>
            }
          </div>
        }

        <div class="flex items-center justify-end gap-2 border-t border-border pt-4">
          <fo-button variant="ghost" size="sm" (click)="changelogOpen.set(false)">
            Cerrar
          </fo-button>
          <fo-button
            size="sm"
            [loading]="appUpdate.updating()"
            (click)="appUpdate.applyUpdate()"
          >
            Actualizar ahora
          </fo-button>
        </div>
      </div>
    </fo-bottom-sheet>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PwaUpdateBannerComponent {
  protected readonly appUpdate = inject(AppUpdateService);
  protected readonly changelogOpen = signal<boolean>(false);

  protected readonly parsedReleases = computed<readonly ParsedChangelogRelease[]>(() =>
    this.appUpdate.changelog().map((entry) => ({
      entry,
      blocks: parseMarkdown(entry.changelogMarkdown),
      formattedDate: formatReleaseDate(entry.releasedAt),
    })),
  );

  protected openChangelog(): void {
    this.changelogOpen.set(true);
    void this.appUpdate.loadChangelog();
  }
}
