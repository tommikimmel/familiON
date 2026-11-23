import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

// Placeholder shell; full responsive navigation layout is implemented in T08.
@Component({
  selector: 'fo-shell',
  imports: [RouterOutlet],
  template: `<router-outlet />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {}
