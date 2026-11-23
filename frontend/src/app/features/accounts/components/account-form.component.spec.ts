import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Account, AccountCreate } from '../../../core/finance/finance.models';
import { FamilyMemberOption } from '../data-access/family-members.api';
import { AccountFormComponent } from './account-form.component';

@Component({
  standalone: true,
  imports: [AccountFormComponent],
  template: `
    <fo-account-form
      [account]="account()"
      [enabledCurrencies]="['ARS', 'USD']"
      [defaultCurrency]="'ARS'"
      [isAdult]="isAdult()"
      [currentUserId]="'user-1'"
      [members]="members"
      (submitted)="lastSubmitted.set($event)"
    />
  `,
})
class AccountFormHostComponent {
  readonly account = signal<Account | null>(null);
  readonly isAdult = signal<boolean>(true);
  readonly lastSubmitted = signal<AccountCreate | null>(null);
  readonly members: FamilyMemberOption[] = [
    {
      id: 'user-1',
      fullName: 'Ana García',
      email: 'ana@familion.test',
      role: 'ADULTO',
      isActive: true,
    },
    {
      id: 'user-2',
      fullName: 'Lucas García',
      email: 'lucas@familion.test',
      role: 'HIJO',
      isActive: true,
    },
  ];
}

describe('AccountFormComponent', () => {
  it('does not require credit card fields for non-credit-card account types', () => {
    const fixture = TestBed.createComponent(AccountFormHostComponent);
    fixture.detectChanges();

    const hostEl = fixture.nativeElement as HTMLElement;
    const formComp = fixture.debugElement.children[0].componentInstance as AccountFormComponent;

    formComp.form.controls.name.setValue('Efectivo casa');
    formComp.form.controls.initialBalance.setValue('12000');
    fixture.detectChanges();

    expect(hostEl.querySelector('#fo-account-credit-limit')).toBeNull();
    expect(formComp.form.valid).toBe(true);

    const formEl = hostEl.querySelector('form') as HTMLFormElement;
    formEl.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance.lastSubmitted()).toEqual({
      name: 'Efectivo casa',
      accountType: 'CASH',
      currencyCode: 'ARS',
      initialBalance: '12000',
      ownerUserId: 'user-1',
    });
  });

  it('requires creditLimit, closingDay (1-31), and dueDay (1-31) only when CREDIT_CARD is selected', () => {
    const fixture = TestBed.createComponent(AccountFormHostComponent);
    fixture.detectChanges();

    const hostEl = fixture.nativeElement as HTMLElement;
    const formComp = fixture.debugElement.children[0].componentInstance as AccountFormComponent;

    formComp.form.controls.name.setValue('Visa Galicia');

    const cardButton = hostEl.querySelector(
      '[data-testid="account-type-CREDIT_CARD"]',
    ) as HTMLButtonElement;
    cardButton.click();
    fixture.detectChanges();

    expect(hostEl.querySelector('#fo-account-credit-limit')).not.toBeNull();
    expect(formComp.form.invalid).toBe(true);
    expect(formComp.form.controls.creditLimit.hasError('required')).toBe(true);
    expect(formComp.form.controls.closingDay.hasError('required')).toBe(true);
    expect(formComp.form.controls.dueDay.hasError('required')).toBe(true);

    // Out-of-range days fail validation.
    formComp.form.controls.creditLimit.setValue('500000');
    formComp.form.controls.closingDay.setValue('32');
    formComp.form.controls.dueDay.setValue('0');
    fixture.detectChanges();
    expect(formComp.form.controls.closingDay.hasError('dayOfMonth')).toBe(true);
    expect(formComp.form.controls.dueDay.hasError('dayOfMonth')).toBe(true);

    // Valid card values submit cleanly.
    formComp.form.controls.closingDay.setValue('20');
    formComp.form.controls.dueDay.setValue('5');
    formComp.form.controls.ownerSelection.setValue('SHARED');
    fixture.detectChanges();

    expect(formComp.form.valid).toBe(true);
    (hostEl.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance.lastSubmitted()).toEqual({
      name: 'Visa Galicia',
      accountType: 'CREDIT_CARD',
      currencyCode: 'ARS',
      initialBalance: '0',
      ownerUserId: null,
      creditLimit: '500000',
      closingDay: 20,
      dueDay: 5,
    });

    // Switching back to BANK clears credit card validators immediately.
    const bankButton = hostEl.querySelector(
      '[data-testid="account-type-BANK"]',
    ) as HTMLButtonElement;
    bankButton.click();
    formComp.form.controls.creditLimit.setValue('');
    formComp.form.controls.closingDay.setValue('');
    formComp.form.controls.dueDay.setValue('');
    fixture.detectChanges();

    expect(formComp.form.valid).toBe(true);
  });

  it('hides owner selector for non-adult users', () => {
    const fixture = TestBed.createComponent(AccountFormHostComponent);
    fixture.componentInstance.isAdult.set(false);
    fixture.detectChanges();

    const hostEl = fixture.nativeElement as HTMLElement;
    expect(hostEl.querySelector('#fo-account-owner')).toBeNull();
  });
});
