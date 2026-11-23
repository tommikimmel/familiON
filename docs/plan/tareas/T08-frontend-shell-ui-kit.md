# T08 — feat(frontend): shell de navegación, tema/acento, contexto de vista y UI kit

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 1 | 1.1 | `feat/frontend-shell-ui-kit` | Ola 0.2 (y T02) | T07, T09 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `frontend/src/styles.css` (tokens `--fo-*` de T02) y `frontend/src/app/app.routes.ts`.
- `docs/brand/familion-logo-adaptive.svg`.

## Objetivo
Construir la "carcasa" visual de la app (mobile-first y desktop), los servicios transversales de tema y contexto (Personal/Familia, lente, divisa, período) y el kit de componentes compartidos que usarán todas las features.

## Archivos permitidos
```
frontend/src/app/core/layout/**
frontend/src/app/core/theme/**
frontend/src/app/core/state/**
frontend/src/app/core/api/models/**
frontend/src/app/shared/**
frontend/src/styles.css            (solo agregar keyframes/utilidades; no cambiar tokens existentes)
frontend/package.json, frontend/package-lock.json   (solo para agregar @angular/cdk y @lucide/angular)
```

## Contrato (lo usan otras tareas — no cambiar nombres)

```ts
// core/api/models/me.model.ts
export type Role = 'ADULTO' | 'HIJO';
export type FamilyVisibility = 'OWN_ONLY' | 'FAMILY_SUMMARY' | 'FAMILY_FULL';
export type ThemePreference = 'LIGHT' | 'DARK' | 'SYSTEM';
export interface Me { id: string; email: string; fullName: string; role: Role; familyVisibility: FamilyVisibility;
  themePreference: ThemePreference; accentColor: string; twoFactorEnabled: boolean; emailVerified: boolean;
  family: { id: string; name: string; baseCurrency: string; referenceCurrency: string; referenceRateSource: string;
            timezone: string; enabledCurrencies: string[] } }

// core/state/session.store.ts  (T12 lo alimenta tras login)
export class SessionStore { readonly me: Signal<Me|null>; readonly isAdult: Signal<boolean>;
  setMe(me: Me|null): void; }

// core/state/view-context.store.ts
export type View = 'PERSONAL' | 'FAMILY';
export type Lens = 'NOMINAL' | 'CONSTANT_BASE' | 'REFERENCE_CURRENCY' | 'INCOME_PCT';
export type Granularity = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';
export class ViewContextStore {
  readonly view: Signal<View>; readonly lens: Signal<Lens>; readonly granularity: Signal<Granularity>;
  readonly anchorDate: Signal<string>;            // yyyy-MM-dd
  readonly canSeeFamily: Signal<boolean>;         // ADULTO o HIJO con visibility != OWN_ONLY (desde SessionStore)
  readonly queryParams: Signal<{ view: View; lens: Lens; granularity: Granularity; date: string }>;
  setView(v: View): void; setLens(l: Lens): void; setGranularity(g: Granularity): void;
  shiftPeriod(direction: -1 | 1): void; resetToToday(): void;
}

// core/theme/theme.service.ts
export class ThemeService { readonly theme: Signal<ThemePreference>; readonly accent: Signal<string>;
  apply(prefs: { themePreference: ThemePreference; accentColor: string }): void; }

// core/layout/quick-add-launcher.service.ts   (T17 renderiza el sheet)
export class QuickAddLauncher { readonly isOpen: Signal<boolean>; open(): void; close(): void; }

// core/layout/notification-badge.service.ts    (T37 lo alimenta)
export class NotificationBadgeService { readonly unread: Signal<number>; set(n: number): void; }

// shared/utils/color.ts
export function relativeLuminance(hex: string): number;
export function contrastRatio(a: string, b: string): number;
export function readableTextOn(hex: string): '#FFFFFF' | '#0A0A0A';
export function tonalScale(accentHex: string, count: number): string[];   // acento + mezclas hacia grises neutros

// shared/utils/decimal.ts
export function toNumber(decimal: string | null | undefined): number;      // "36000.0000" → 36000
```

## Especificación

1. **ThemeService:** aplica clase `dark` en `<html>` según preferencia (`SYSTEM` escucha `prefers-color-scheme`); setea `--fo-accent`, `--fo-accent-contrast` (`readableTextOn`) en `document.documentElement`; actualiza `<meta name="theme-color">`. Persiste en `localStorage` (`fo.theme`, `fo.accent`) para el arranque antes de tener `/me`.
2. **ViewContextStore:** defaults `PERSONAL`, `NOMINAL`, `MONTH`, hoy. Persistir `view`, `lens`, `granularity` en `localStorage` (`fo.viewContext`). Si `canSeeFamily` pasa a `false`, forzar `PERSONAL`. `shiftPeriod` mueve `anchorDate` según granularidad.
3. **Shell mobile (< 1024px):**
   - Header: logo compacto a la izquierda; a la derecha `fo-currency-chip` (bandera SVG + 3 letras de la divisa activa). Al tocarlo, dropdown liviano: lista de `enabledCurrencies` y switch **«A plata de hoy»** (on → lens `CONSTANT_BASE`, off → `NOMINAL`). Elegir la divisa de referencia de la familia → lens `REFERENCE_CURRENCY`.
   - Debajo: `fo-segmented-toggle` **«Personal | Familia»** (oculto si `!canSeeFamily`).
   - Contenido: `<router-outlet>` con padding inferior para la barra.
   - Bottom nav: `Inicio` (/home), `Plan` (/plan), botón **`+` central de 56px elevado** con corte visual sobre la barra (abre `QuickAddLauncher`; rota 45° cuando está abierto), `Autos` (/vehicles), `Perfil` (/profile). Ítem activo con color de acento.
4. **Shell desktop (≥ 1024px):** sidebar fija de **226px** (logo interactivo, toggle Personal|Familia, CTA **«+ Nuevo movimiento»** con atajo de teclado `N` —ignorar si el foco está en un input—, nav: Inicio, Movimientos, Analítica, Planificación, Vehículos, Cuentas, Familia y Perfil; footer con nombre del usuario y toggle de tema). Top bar: título (de `route.data.title`), selector temporal `Día | Semana | Mes | Año` + flechas `‹ ›` (shiftPeriod) + label del período, chip de divisa con switch «A plata de hoy», campana con badge (`NotificationBadgeService`) que navega a `/notifications`.
5. **Shell:** debe dejar un punto de montaje para el sheet de carga rápida: `<ng-container #quickAddHost />` comentado como `<!-- T17 mounts fo-quick-add-host here -->`.
6. **UI kit** en `shared/ui/` (standalone, OnPush, prefijo `fo-`):
   - `fo-logo` (`compact`, `animate` inputs; toggle `OFF → ON` con spring `cubic-bezier(0.34,1.35,0.64,1)` 360ms; usa `currentColor` y `--fo-accent`).
   - `fo-segmented-toggle<T>` (`options: {value:T,label:string}[]`, `value` model; cápsula deslizante 320ms spring; `role="tablist"`/`aria-selected`).
   - `fo-bottom-sheet` (mobile: desliza desde abajo 360ms; desktop: modal centrado; backdrop `blur(5px)`; `Esc` cierra; focus trap con `@angular/cdk/a11y`; `open` model + `closed` output).
   - `fo-donut-chart` (SVG; input `slices: {id:string; label:string; value:number; color?:string; sublabel?:string}[]`, `centerLabel`, `formatValue: (n)=>string`; colores por defecto `tonalScale(accent, n)`; transición `stroke-dasharray/dashoffset`; al tocar porción o leyenda: pop-out + centro muestra nombre, monto y %; output `selected`).
   - `fo-currency-chip`, `fo-card`, `fo-button` (`variant: 'primary'|'ghost'|'danger'`, `loading`), `fo-empty-state`, `fo-skeleton`, `fo-icon` (wrapper de `@lucide/angular`).
   - `fo-toast` + `ToastService.success(msg)/error(msg)` (píldora superior con checkmark SVG animado, 3s).
   - Directiva `foCountUp` (interpola números 280ms ease-out; respeta reduced-motion).
   - Pipe `money` (`shared/pipes/money.pipe.ts`): `value | money:'ARS'` → `Intl.NumberFormat('es-AR', {style:'currency', currency, maximumFractionDigits: 2})`; acepta string decimal o number; para unidades no ISO (`UVA`, `%`) formatea número + sufijo.
   - Pipe `periodLabel` opcional.
7. **Animaciones** cortas (140–360ms) y desactivadas con `prefers-reduced-motion`.
8. Íconos: `@lucide/angular` (home, calendar-range, plus, car, user, bell, camera, chevron-left/right, check, x, wallet, chart-pie, arrow-left-right).

## Tests
- `color.spec.ts`: luminancia/contraste (blanco vs negro 21:1), `readableTextOn('#0F766E') === '#FFFFFF'`, `readableTextOn('#FDE047') === '#0A0A0A'`, `tonalScale` devuelve N colores distintos.
- `view-context.store.spec.ts`: defaults, persistencia, `shiftPeriod` por granularidad, fuerza PERSONAL si no puede ver familia.
- `theme.service.spec.ts`: aplica `dark`, setea CSS vars.
- `segmented-toggle`, `donut-chart` (cálculo de arcos/porcentajes, selección), `money.pipe`.
- Shell: renderiza bottom nav en mobile, sidebar en desktop (simular ancho con `matchMedia` mock o `BreakpointObserver`).

## Fuera de alcance
Auth real (T12), carga rápida (T17), datos reales en las pantallas.

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] La app con `ng serve` muestra el shell mobile y desktop, el toggle y el chip de divisa operan sobre `ViewContextStore`.
- [ ] Cambiar acento en runtime (`ThemeService.apply`) recolorea nav activo, `+` y donut.
- [ ] Accesible por teclado (tab order lógico, foco visible, `N` abre el launcher).

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(frontend): add theme service and color contrast utilities`
2. `feat(frontend): add session and view context stores`
3. `feat(frontend): add shared ui kit components and money pipe`
4. `feat(frontend): add responsive app shell with bottom nav and sidebar`
