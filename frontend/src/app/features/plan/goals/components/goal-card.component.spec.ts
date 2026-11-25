import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { SavingsGoal } from '../data-access/goals.models';
import { GoalCardComponent } from './goal-card.component';

const SAMPLE_GOAL: SavingsGoal = {
  id: 'goal-1',
  name: 'Viaje a Brasil',
  scope: 'PERSONAL',
  owner: { id: 'u-1', fullName: 'Ana García' },
  targetAmount: '1000',
  targetUnit: 'USD',
  targetDate: '2026-12-31',
  status: 'ACTIVE',
  accumulatedInUnit: '250',
  progressPct: '25.0',
  remainingInUnit: '750',
  monthsLeft: 10,
  suggestedMonthlyInUnit: '75',
  contributionsCount: 3,
  lastContributionDate: '2026-02-15',
  accumulatedInBaseToday: '300000',
};

const COMPLETED_GOAL: SavingsGoal = {
  ...SAMPLE_GOAL,
  id: 'goal-2',
  name: 'Computadora',
  status: 'COMPLETED',
  accumulatedInUnit: '1000',
  progressPct: '100.0',
  remainingInUnit: '0',
  monthsLeft: 0,
};

describe('GoalCardComponent', () => {
  let fixture: ComponentFixture<GoalCardComponent>;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [GoalCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(GoalCardComponent);
  });

  it('renders goal details, progress, and suggested savings', () => {
    fixture.componentRef.setInput('goal', SAMPLE_GOAL);
    fixture.componentRef.setInput('baseCurrency', 'ARS');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Viaje a Brasil');
    expect(host.textContent).toContain('Personal');
    expect(host.textContent).toContain('Faltan 10 meses');
    expect(host.textContent).toContain('3 aportes');
  });

  it('renders celebratory state when goal is completed', () => {
    fixture.componentRef.setInput('goal', COMPLETED_GOAL);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('¡Meta cumplida!');
  });
});
