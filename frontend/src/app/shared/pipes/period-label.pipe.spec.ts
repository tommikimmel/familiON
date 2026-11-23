import { PeriodLabelPipe } from './period-label.pipe';

describe('PeriodLabelPipe', () => {
  const pipe = new PeriodLabelPipe();

  it('formats YEAR granularity as four-digit year', () => {
    expect(pipe.transform('2026-03-15', 'YEAR')).toBe('2026');
  });

  it('formats MONTH granularity with capitalized Spanish month and year', () => {
    const label = pipe.transform('2026-03-15', 'MONTH');
    expect(label.toLowerCase()).toContain('marzo');
    expect(label).toContain('2026');
    expect(label[0]).toBe(label[0].toUpperCase());
  });

  it('formats DAY and WEEK granularities non-emptily', () => {
    expect(pipe.transform('2026-03-15', 'DAY')).toContain('2026');
    const week = pipe.transform('2026-03-15', 'WEEK');
    expect(week).toContain('–');
    expect(week).toContain('2026');
  });

  it('returns empty string for nullish input', () => {
    expect(pipe.transform(null, 'MONTH')).toBe('');
  });
});
