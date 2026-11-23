import { TestBed } from '@angular/core/testing';
import { FinanceEvents } from './finance-events';

describe('FinanceEvents', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('starts with last set to null and updates last on emit', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-04-10T12:00:00.000Z'));

    const events = TestBed.inject(FinanceEvents);
    expect(events.last()).toBeNull();

    events.emit('transaction-created');
    expect(events.last()).toEqual({
      type: 'transaction-created',
      at: Date.parse('2026-04-10T12:00:00.000Z'),
    });

    vi.setSystemTime(new Date('2026-04-10T12:00:05.000Z'));
    events.emit('plan-changed');
    expect(events.last()).toEqual({
      type: 'plan-changed',
      at: Date.parse('2026-04-10T12:00:05.000Z'),
    });
  });

  it('emits a new object reference for consecutive events of the same type', () => {
    const events = TestBed.inject(FinanceEvents);

    events.emit('vehicle-changed');
    const first = events.last();

    events.emit('vehicle-changed');
    const second = events.last();

    expect(first).not.toBeNull();
    expect(second).not.toBeNull();
    expect(second).not.toBe(first);
    expect(second?.type).toBe('vehicle-changed');
  });
});
