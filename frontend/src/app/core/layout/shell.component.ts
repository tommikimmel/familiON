import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { SessionStore } from '../state/session.store';
import { Granularity, View, ViewContextStore } from '../state/view-context.store';
import { ThemeService } from '../theme/theme.service';
import { PeriodLabelPipe } from '../../shared/pipes/period-label.pipe';
import { FoIconName, IconComponent } from '../../shared/ui/icon/icon.component';
import { LogoComponent } from '../../shared/ui/logo/logo.component';
import {
  SegmentedOption,
  SegmentedToggleComponent,
} from '../../shared/ui/segmented-toggle/segmented-toggle.component';
import { ToastComponent } from '../../shared/ui/toast/toast.component';
import { QuickAddHostComponent } from '../../features/quick-add/quick-add-host.component';
import { CurrencyMenuComponent } from './currency-menu.component';
import { NotificationBadgeService } from './notification-badge.service';
import { QuickAddLauncher } from './quick-add-launcher.service';

interface NavItem {
  label: string;
  route: string;
  icon: FoIconName;
}

const DESKTOP_BREAKPOINT = '(min-width: 1024px)';

const VIEW_OPTIONS: readonly SegmentedOption<View>[] = [
  { value: 'PERSONAL', label: 'Personal' },
  { value: 'FAMILY', label: 'Familia' },
];

const GRANULARITY_OPTIONS: readonly SegmentedOption<Granularity>[] = [
  { value: 'DAY', label: 'Día' },
  { value: 'WEEK', label: 'Semana' },
  { value: 'MONTH', label: 'Mes' },
  { value: 'YEAR', label: 'Año' },
];

const MOBILE_LEFT_NAV: readonly NavItem[] = [
  { label: 'Inicio', route: '/home', icon: 'home' },
  { label: 'Plan', route: '/plan', icon: 'calendar-range' },
];

const MOBILE_RIGHT_NAV: readonly NavItem[] = [
  { label: 'Autos', route: '/vehicles', icon: 'car' },
  { label: 'Perfil', route: '/profile', icon: 'user' },
];

const DESKTOP_NAV: readonly NavItem[] = [
  { label: 'Inicio', route: '/home', icon: 'home' },
  { label: 'Movimientos', route: '/transactions', icon: 'arrow-left-right' },
  { label: 'Analítica', route: '/analytics', icon: 'chart-pie' },
  { label: 'Planificación', route: '/plan', icon: 'calendar-range' },
  { label: 'Vehículos', route: '/vehicles', icon: 'car' },
  { label: 'Cuentas', route: '/accounts', icon: 'wallet' },
  { label: 'Familia y Perfil', route: '/profile', icon: 'users' },
];

function isEditableElement(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tag = target.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
    return true;
  }
  return target.isContentEditable || target.closest('[contenteditable="true"]') !== null;
}

@Component({
  selector: 'fo-shell',
  standalone: true,
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    CurrencyMenuComponent,
    IconComponent,
    LogoComponent,
    PeriodLabelPipe,
    QuickAddHostComponent,
    SegmentedToggleComponent,
    ToastComponent,
  ],
  host: {
    '(document:keydown)': 'onGlobalKeydown($event)',
  },
  template: `
    @if (isDesktop()) {
      <div class="flex min-h-dvh bg-bg text-text">
        <aside
          data-testid="desktop-sidebar"
          aria-label="Barra lateral"
          class="fixed inset-y-0 left-0 z-30 flex w-[226px] flex-col border-r border-border bg-surface px-3.5 py-4"
        >
          <a
            routerLink="/home"
            aria-label="FamiliON inicio"
            class="fo-focus-ring mb-4 inline-flex items-center rounded-xl px-1 py-0.5"
          >
            <fo-logo [animate]="true" />
          </a>

          @if (viewContext.canSeeFamily()) {
            <div class="mb-3">
              <fo-segmented-toggle
                [options]="viewOptions"
                [value]="viewContext.view()"
                (valueChange)="viewContext.setView($event)"
                ariaLabel="Vista personal o familiar"
                size="sm"
              />
            </div>
          }

          <button
            type="button"
            data-testid="desktop-quick-add-btn"
            (click)="quickAdd.open()"
            class="fo-focus-ring mb-4 flex w-full cursor-pointer items-center justify-between gap-2 rounded-xl bg-accent px-3.5 py-2.5 text-sm font-semibold text-accent-contrast shadow-xs transition-opacity duration-150 hover:opacity-92"
          >
            <span class="flex items-center gap-2">
              <fo-icon name="plus" [size]="18" />
              <span>Nuevo movimiento</span>
            </span>
            <kbd
              class="rounded-md bg-black/15 px-1.5 py-0.5 font-sans text-[11px] font-medium"
              aria-hidden="true"
            >
              N
            </kbd>
          </button>

          <nav aria-label="Navegación principal" class="flex flex-1 flex-col gap-1 overflow-y-auto">
            @for (item of desktopNav; track item.route) {
              <a
                [routerLink]="item.route"
                routerLinkActive="bg-accent-soft text-accent font-semibold"
                #rla="routerLinkActive"
                [attr.aria-current]="rla.isActive ? 'page' : null"
                class="fo-focus-ring flex items-center gap-3 rounded-xl px-3 py-2 text-sm text-muted transition-colors duration-150 hover:bg-surface-2 hover:text-text"
              >
                <fo-icon [name]="item.icon" [size]="18" />
                <span class="truncate">{{ item.label }}</span>
              </a>
            }
          </nav>

          <footer class="mt-4 flex items-center justify-between gap-2 border-t border-border pt-3">
            <div class="min-w-0">
              <p class="truncate text-sm font-medium text-text">{{ userDisplayName() }}</p>
              @if (familyName(); as fam) {
                <p class="truncate text-xs text-muted">{{ fam }}</p>
              }
            </div>
            <button
              type="button"
              [attr.aria-label]="theme.isDark() ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'"
              [attr.aria-pressed]="theme.isDark()"
              (click)="theme.toggleDark()"
              class="fo-focus-ring inline-flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-xl border border-border bg-surface-2 text-text transition-colors hover:border-accent"
            >
              <fo-icon [name]="theme.isDark() ? 'sun' : 'moon'" [size]="18" />
            </button>
          </footer>
        </aside>

        <div class="flex min-h-dvh flex-1 flex-col pl-[226px]">
          <header
            data-testid="desktop-topbar"
            class="sticky top-0 z-20 flex flex-wrap items-center justify-between gap-3 border-b border-border bg-surface/90 px-6 py-3 backdrop-blur-md"
          >
            <h2 class="text-lg font-semibold text-text">{{ pageTitle() }}</h2>

            <div class="flex items-center gap-3">
              <div class="w-72">
                <fo-segmented-toggle
                  [options]="granularityOptions"
                  [value]="viewContext.granularity()"
                  (valueChange)="viewContext.setGranularity($event)"
                  ariaLabel="Granularidad temporal"
                  size="sm"
                />
              </div>

              <div class="inline-flex items-center gap-1 rounded-full border border-border bg-surface-2 p-1">
                <button
                  type="button"
                  aria-label="Período anterior"
                  (click)="viewContext.shiftPeriod(-1)"
                  class="fo-focus-ring inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface hover:text-text"
                >
                  <fo-icon name="chevron-left" [size]="16" />
                </button>
                <button
                  type="button"
                  title="Volver a hoy"
                  (click)="viewContext.resetToToday()"
                  class="fo-focus-ring cursor-pointer rounded-full px-2.5 py-0.5 text-xs font-medium text-text tabular-nums transition-colors hover:bg-surface"
                >
                  {{ viewContext.anchorDate() | periodLabel: viewContext.granularity() }}
                </button>
                <button
                  type="button"
                  aria-label="Período siguiente"
                  (click)="viewContext.shiftPeriod(1)"
                  class="fo-focus-ring inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface hover:text-text"
                >
                  <fo-icon name="chevron-right" [size]="16" />
                </button>
              </div>
            </div>

            <div class="flex items-center gap-2.5">
              <fo-currency-menu />

              <a
                routerLink="/notifications"
                aria-label="Notificaciones"
                class="fo-focus-ring relative inline-flex h-9 w-9 items-center justify-center rounded-full border border-border bg-surface text-text transition-colors hover:bg-surface-2"
              >
                <fo-icon name="bell" [size]="18" />
                @if (notificationBadge.unread() > 0) {
                  <span
                    data-testid="notification-badge"
                    class="absolute -top-1 -right-1 flex min-w-5 items-center justify-center rounded-full bg-accent px-1 text-[10px] font-bold text-accent-contrast tabular-nums"
                  >
                    {{ badgeLabel() }}
                  </span>
                }
              </a>
            </div>
          </header>

          <main class="flex-1 p-6">
            <router-outlet />
          </main>
        </div>
      </div>
    } @else {
      <div class="flex min-h-dvh flex-col bg-bg text-text">
        <header
          data-testid="mobile-header"
          class="sticky top-0 z-20 border-b border-border bg-surface/92 px-4 pt-3 pb-2.5 backdrop-blur-md"
        >
          <div class="flex items-center justify-between gap-3">
            <a
              routerLink="/home"
              aria-label="FamiliON inicio"
              class="fo-focus-ring inline-flex items-center rounded-lg"
            >
              <fo-logo [compact]="true" />
            </a>

            <fo-currency-menu />
          </div>

          @if (viewContext.canSeeFamily()) {
            <div class="mt-2.5">
              <fo-segmented-toggle
                [options]="viewOptions"
                [value]="viewContext.view()"
                (valueChange)="viewContext.setView($event)"
                ariaLabel="Vista personal o familiar"
                size="sm"
              />
            </div>
          }
        </header>

        <main class="flex-1 px-4 pt-4 pb-24">
          <router-outlet />
        </main>

        <nav
          data-testid="mobile-bottom-nav"
          aria-label="Navegación principal"
          class="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface/96 px-2 pt-1.5 pb-[calc(0.5rem+env(safe-area-inset-bottom))] backdrop-blur-md"
        >
          <div class="mx-auto grid max-w-md grid-cols-5 items-end">
            @for (item of mobileLeftNav; track item.route) {
              <a
                [routerLink]="item.route"
                routerLinkActive="text-accent font-semibold"
                #rla="routerLinkActive"
                [attr.aria-current]="rla.isActive ? 'page' : null"
                class="fo-focus-ring flex flex-col items-center justify-center gap-0.5 rounded-xl py-1 text-[11px] text-muted transition-colors"
              >
                <fo-icon [name]="item.icon" [size]="20" />
                <span>{{ item.label }}</span>
              </a>
            }

            <div class="flex justify-center">
              <button
                type="button"
                data-testid="mobile-quick-add-btn"
                aria-label="Nuevo movimiento"
                [attr.aria-expanded]="quickAdd.isOpen()"
                (click)="quickAdd.toggle()"
                class="fo-focus-ring fo-fab -mt-7 flex h-14 w-14 cursor-pointer items-center justify-center rounded-full bg-accent text-accent-contrast shadow-lg ring-[6px] ring-bg transition-transform duration-200 active:scale-95"
              >
                <fo-icon
                  name="plus"
                  [size]="26"
                  class="fo-fab-icon"
                  [class.fo-fab-icon--open]="quickAdd.isOpen()"
                />
              </button>
            </div>

            @for (item of mobileRightNav; track item.route) {
              <a
                [routerLink]="item.route"
                routerLinkActive="text-accent font-semibold"
                #rla="routerLinkActive"
                [attr.aria-current]="rla.isActive ? 'page' : null"
                class="fo-focus-ring flex flex-col items-center justify-center gap-0.5 rounded-xl py-1 text-[11px] text-muted transition-colors"
              >
                <fo-icon [name]="item.icon" [size]="20" />
                <span>{{ item.label }}</span>
              </a>
            }
          </div>
        </nav>
      </div>
    }

    <fo-quick-add-host />
    <fo-toast />
  `,
  styles: `
    .fo-fab-icon {
      transition: transform 260ms var(--fo-ease-spring, cubic-bezier(0.34, 1.35, 0.64, 1));
    }

    .fo-fab-icon--open {
      transform: rotate(45deg);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly session = inject(SessionStore);

  protected readonly viewContext = inject(ViewContextStore);
  protected readonly theme = inject(ThemeService);
  protected readonly quickAdd = inject(QuickAddLauncher);
  protected readonly notificationBadge = inject(NotificationBadgeService);

  protected readonly viewOptions = VIEW_OPTIONS;
  protected readonly granularityOptions = GRANULARITY_OPTIONS;
  protected readonly mobileLeftNav = MOBILE_LEFT_NAV;
  protected readonly mobileRightNav = MOBILE_RIGHT_NAV;
  protected readonly desktopNav = DESKTOP_NAV;

  protected readonly isDesktop = toSignal(
    this.breakpointObserver
      .observe(DESKTOP_BREAKPOINT)
      .pipe(map((state) => state.matches)),
    { initialValue: this.breakpointObserver.isMatched(DESKTOP_BREAKPOINT) },
  );

  protected readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      startWith(null),
      map(() => this.resolveRouteTitle()),
    ),
    { initialValue: this.resolveRouteTitle() },
  );

  protected readonly userDisplayName = computed(
    () => this.session.me()?.fullName ?? 'Mi cuenta',
  );

  protected readonly familyName = computed(
    () => this.session.me()?.family.name ?? null,
  );

  protected readonly badgeLabel = computed(() => {
    const count = this.notificationBadge.unread();
    return count > 99 ? '99+' : String(count);
  });

  protected onGlobalKeydown(event: KeyboardEvent): void {
    if (event.ctrlKey || event.metaKey || event.altKey || event.repeat) {
      return;
    }
    if (event.key.toLowerCase() !== 'n') {
      return;
    }
    if (isEditableElement(event.target)) {
      return;
    }
    event.preventDefault();
    this.quickAdd.open();
  }

  private resolveRouteTitle(): string {
    let route: ActivatedRoute | null = this.activatedRoute;
    let title = 'Inicio';
    while (route) {
      const candidate = route.snapshot?.data?.['title'];
      if (typeof candidate === 'string' && candidate.trim().length > 0) {
        title = candidate;
      }
      route = route.firstChild;
    }
    return title;
  }
}
