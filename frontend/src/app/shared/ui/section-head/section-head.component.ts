import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/** Section header (mock `.sec-head` + `.sec-title` + optional `.sec-link`). */
@Component({
  selector: 'fo-section-head',
  standalone: true,
  template: `
    <div class="fo-sec-head">
      <h2 class="fo-sec-title">{{ title() }}</h2>
      @if (linkLabel()) {
        <button type="button" class="fo-sec-link fo-focus-ring" (click)="linkClick.emit()">
          {{ linkLabel() }}
        </button>
      } @else {
        <ng-content />
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SectionHeadComponent {
  readonly title = input.required<string>();
  readonly linkLabel = input<string | null>(null);
  readonly linkClick = output<void>();
}
