import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Small status pill (mock `.pill-tag`; `accent` = `.pill-accent`). */
@Component({
  selector: 'fo-pill-tag',
  standalone: true,
  host: {
    class: 'fo-pill-tag',
    '[class.fo-pill-accent]': 'accent()',
  },
  template: `<ng-content />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PillTagComponent {
  readonly accent = input<boolean>(false);
}
