import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Card surface (mock `.fn-card`; `glow` adds the hero ambient glow). */
@Component({
  selector: 'fo-card',
  standalone: true,
  template: `
    <div
      class="fo-card"
      [class.fo-card--none]="padding() === 'none'"
      [class.fo-card--sm]="padding() === 'sm'"
      [class.fo-card--lg]="padding() === 'lg'"
      [class.fo-card--interactive]="interactive()"
      [class.fo-hero-glow]="glow()"
    >
      <ng-content />
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    /* Lets a stretched grid item (class="h-full") fill its cell. */
    :host(.h-full) .fo-card {
      height: 100%;
    }

    .fo-card.fo-card--none {
      padding: 0;
    }

    .fo-card.fo-card--sm {
      padding: 10px 12px;
    }

    .fo-card.fo-card--lg {
      padding: 20px;
    }

    .fo-card--interactive {
      cursor: pointer;
    }

    .fo-card--interactive:hover {
      border-color: var(--fo-accent);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardComponent {
  readonly padding = input<'none' | 'sm' | 'md' | 'lg'>('md');
  readonly interactive = input<boolean>(false);
  readonly glow = input<boolean>(false);
}
