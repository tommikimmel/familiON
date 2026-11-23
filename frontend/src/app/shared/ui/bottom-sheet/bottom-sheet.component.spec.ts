import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BottomSheetComponent } from './bottom-sheet.component';

@Component({
  standalone: true,
  imports: [BottomSheetComponent],
  template: `
    <fo-bottom-sheet [(open)]="isOpen" title="Detalle" (closed)="closedCount.set(closedCount() + 1)">
      <p>Contenido del panel</p>
      <button type="button">Guardar</button>
    </fo-bottom-sheet>
  `,
})
class SheetHostComponent {
  readonly isOpen = signal(false);
  readonly closedCount = signal(0);
}

describe('BottomSheetComponent', () => {
  it('opens, renders dialog content, and closes on Escape key', () => {
    const fixture = TestBed.createComponent(SheetHostComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="dialog"]')).toBeNull();

    fixture.componentInstance.isOpen.set(true);
    fixture.detectChanges();

    const dialog = host.querySelector('[role="dialog"]');
    expect(dialog).not.toBeNull();
    expect(dialog?.textContent).toContain('Contenido del panel');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.isOpen()).toBe(false);
    expect(fixture.componentInstance.closedCount()).toBe(1);
    expect(host.querySelector('[role="dialog"]')).toBeNull();
  });

  it('closes when clicking the backdrop', () => {
    const fixture = TestBed.createComponent(SheetHostComponent);
    fixture.componentInstance.isOpen.set(true);
    fixture.detectChanges();

    const backdrop = (fixture.nativeElement as HTMLElement).querySelector(
      'button[aria-label="Cerrar panel"]',
    ) as HTMLButtonElement;
    backdrop.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.isOpen()).toBe(false);
    expect(fixture.componentInstance.closedCount()).toBe(1);
  });
});
