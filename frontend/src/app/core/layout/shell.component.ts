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
import { PwaShellBannersComponent } from '../pwa/pwa-shell-banners.component';
import { PwaSplashComponent } from '../pwa/pwa-splash.component';
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
  { label: 'Plan', route: '/plan', icon: 'credit-card' },
];

const MOBILE_RIGHT_NAV: readonly NavItem[] = [
  { label: 'Autos', route: '/vehicles', icon: 'car' },
  { label: 'Perfil', route: '/profile', icon: 'users' },
];

const DESKTOP_NAV: readonly NavItem[] = [
  { label: 'Inicio', route: '/home', icon: 'home' },
  { label: 'Movimientos', route: '/transactions', icon: 'arrow-left-right' },
  { label: 'Analítica', route: '/analytics', icon: 'chart-pie' },
  { label: 'Planificación', route: '/plan', icon: 'credit-card' },
  { label: 'Vehículos', route: '/vehicles', icon: 'car' },
  { label: 'Cuentas', route: '/accounts', icon: 'wallet' },
  { label: 'Familia y Perfil', route: '/profile', icon: 'users' },
];

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter((part) => part.length > 0);
  if (parts.length === 0) {
    return '?';
  }
  const first = parts[0].charAt(0);
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
  return `${first}${last}`.toUpperCase();
}

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
    PwaShellBannersComponent,
    PwaSplashComponent,
    QuickAddHostComponent,
    SegmentedToggleComponent,
    ToastComponent,
  ],
  host: {
    '(document:keydown)': 'onGlobalKeydown($event)',
  },
  template: `
    @if (isDesktop()) {
      <div class="fo-app-shell">
        <aside data-testid="desktop-sidebar" aria-label="Barra lateral" class="fo-sidebar">
          <a routerLink="/home" aria-label="FamiliON inicio" class="fo-logo-wrap fo-focus-ring">
            <fo-logo [animate]="true" />
          </a>

          @if (viewContext.canSeeFamily()) {
            <fo-segmented-toggle
              [options]="viewOptions"
              [value]="viewContext.view()"
              (valueChange)="viewContext.setView($event)"
              ariaLabel="Vista personal o familiar"
              variant="scope"
            />
          }

          <button
            type="button"
            data-testid="desktop-quick-add-btn"
            (click)="quickAdd.open()"
            class="fo-new-btn fo-focus-ring"
          >
            <span class="inline-flex items-center gap-[7px]">
              <fo-icon name="plus" [size]="15" [strokeWidth]="2.6" />
              <span>Nuevo movimiento</span>
            </span>
            <kbd class="fo-kbd-hint" aria-hidden="true">N</kbd>
          </button>

          <nav aria-label="Navegación principal" class="fo-nav-list">
            @for (item of desktopNav; track item.route) {
              <a
                [routerLink]="item.route"
                routerLinkActive="is-active"
                #rla="routerLinkActive"
                [attr.aria-current]="rla.isActive ? 'page' : null"
                class="fo-nav-btn fo-focus-ring"
              >
                <fo-icon [name]="item.icon" [size]="16" />
                <span class="truncate">{{ item.label }}</span>
              </a>
            }
          </nav>

          <footer class="fo-sidebar-footer">
            <div class="fo-user-chip">
              <span class="fo-avatar" aria-hidden="true">{{ userInitials() }}</span>
              <div class="min-w-0">
                <strong class="fo-user-name">{{ userDisplayName() }}</strong>
                <span class="fo-user-sub">{{ userSubtitle() }}</span>
              </div>
            </div>
            <button
              type="button"
              [attr.aria-label]="theme.isDark() ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'"
              [attr.aria-pressed]="theme.isDark()"
              (click)="theme.toggleDark()"
              class="fo-icon-btn fo-focus-ring"
            >
              <fo-icon [name]="theme.isDark() ? 'sun' : 'moon'" [size]="14" />
            </button>
          </footer>
        </aside>

        <div class="fo-main-pane">
          <header data-testid="desktop-topbar" class="fo-main-topbar">
            <div class="fo-top-left">
              <h1 class="fo-page-title">{{ pageTitle() }}</h1>

              <fo-segmented-toggle
                class="inline"
                [options]="granularityOptions"
                [value]="viewContext.granularity()"
                (valueChange)="viewContext.setGranularity($event)"
                ariaLabel="Granularidad temporal"
                variant="tabs-boxed"
              />

              <div class="fo-period-nav">
                <button
                  type="button"
                  aria-label="Período anterior"
                  (click)="viewContext.shiftPeriod(-1)"
                  class="fo-period-step fo-focus-ring"
                >
                  <fo-icon name="chevron-left" [size]="14" />
                </button>
                <button
                  type="button"
                  title="Volver a hoy"
                  (click)="viewContext.resetToToday()"
                  class="fo-period-label fo-focus-ring"
                >
                  {{ viewContext.anchorDate() | periodLabel: viewContext.granularity() }}
                </button>
                <button
                  type="button"
                  aria-label="Período siguiente"
                  (click)="viewContext.shiftPeriod(1)"
                  class="fo-period-step fo-focus-ring"
                >
                  <fo-icon name="chevron-right" [size]="14" />
                </button>
              </div>
            </div>

            <div class="fo-top-right">
              <fo-currency-menu />

              <a
                routerLink="/notifications"
                [attr.aria-label]="notificationsLabel()"
                class="fo-icon-btn fo-focus-ring"
              >
                <fo-icon name="bell" [size]="15" />
                @if (notificationBadge.unread() > 0) {
                  <span data-testid="notification-badge" class="fo-notif-dot">
                    <span class="sr-only">{{ badgeLabel() }}</span>
                  </span>
                }
              </a>
            </div>
          </header>

          <fo-pwa-shell-banners />

          <main class="fo-content-body">
            <router-outlet />
          </main>
        </div>
      </div>
    } @else {
      <div class="fo-mobile-shell">
        <header data-testid="mobile-header" class="fo-app-header">
          <a routerLink="/home" aria-label="FamiliON inicio" class="fo-focus-ring inline-flex rounded-lg">
            <fo-logo [compact]="true" />
          </a>

          <fo-currency-menu />
        </header>

        <fo-pwa-shell-banners />

        <main class="fo-screen-scroll">
          @if (viewContext.canSeeFamily()) {
            <fo-segmented-toggle
              data-testid="mobile-scope-bar"
              [options]="viewOptions"
              [value]="viewContext.view()"
              (valueChange)="viewContext.setView($event)"
              ariaLabel="Vista personal o familiar"
              variant="scope"
            />
          }

          <router-outlet />
        </main>

        <nav data-testid="mobile-bottom-nav" aria-label="Navegación principal" class="fo-bottom-nav">
          @for (item of mobileLeftNav; track item.route) {
            <a
              [routerLink]="item.route"
              routerLinkActive="is-active"
              #rla="routerLinkActive"
              [attr.aria-current]="rla.isActive ? 'page' : null"
              class="fo-nav-item fo-focus-ring"
            >
              <fo-icon [name]="item.icon" [size]="18" />
              <span>{{ item.label }}</span>
            </a>
          }

          <div class="fo-fab-slot">
            <button
              type="button"
              data-testid="mobile-quick-add-btn"
              aria-label="Nuevo movimiento"
              title="Carga rápida"
              [attr.aria-expanded]="quickAdd.isOpen()"
              (click)="quickAdd.toggle()"
              class="fo-fab-btn fo-focus-ring"
            >
              <fo-icon
                name="plus"
                [size]="24"
                [strokeWidth]="2.6"
                class="fo-fab-icon"
                [class.fo-fab-icon--open]="quickAdd.isOpen()"
              />
            </button>
          </div>

          @for (item of mobileRightNav; track item.route) {
            <a
              [routerLink]="item.route"
              routerLinkActive="is-active"
              #rla="routerLinkActive"
              [attr.aria-current]="rla.isActive ? 'page' : null"
              class="fo-nav-item fo-focus-ring"
            >
              <fo-icon [name]="item.icon" [size]="18" />
              <span>{{ item.label }}</span>
            </a>
          }
        </nav>
      </div>
    }

    <fo-quick-add-host />
    <fo-toast />
    <fo-pwa-splash />
  `,
  styleUrl: './shell.component.css',
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

  protected readonly userInitials = computed(() => initialsOf(this.userDisplayName()));

  protected readonly userSubtitle = computed(() => {
    const me = this.session.me();
    if (!me) {
      return '';
    }
    const role = me.role === 'HIJO' ? 'Hijo/a' : 'Adulto';
    return me.twoFactorEnabled ? `${role} · 2FA activo` : `${role} · ${me.family.name}`;
  });

  protected readonly notificationsLabel = computed(() => {
    const count = this.notificationBadge.unread();
    return count > 0 ? `Notificaciones (${this.badgeLabel()} sin leer)` : 'Notificaciones';
  });

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
