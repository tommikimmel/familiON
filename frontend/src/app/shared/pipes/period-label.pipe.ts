import { Pipe, PipeTransform } from '@angular/core';
import { Granularity } from '../../core/state/view-context.store';

const LOCALE = 'es-AR';

const dayFormatter = new Intl.DateTimeFormat(LOCALE, {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
});

const shortDayFormatter = new Intl.DateTimeFormat(LOCALE, {
  day: 'numeric',
  month: 'short',
  timeZone: 'UTC',
});

const monthFormatter = new Intl.DateTimeFormat(LOCALE, {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

function parseIsoUtc(isoDate: string): Date | null {
  const parts = isoDate.split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
    return null;
  }
  return new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
}

function capitalize(value: string): string {
  if (!value) {
    return value;
  }
  return value.charAt(0).toUpperCase() + value.slice(1);
}

@Pipe({
  name: 'periodLabel',
  standalone: true,
})
export class PeriodLabelPipe implements PipeTransform {
  transform(anchorDate: string | null | undefined, granularity: Granularity): string {
    if (!anchorDate) {
      return '';
    }
    const date = parseIsoUtc(anchorDate);
    if (!date) {
      return anchorDate;
    }

    switch (granularity) {
      case 'DAY':
        return dayFormatter.format(date);
      case 'WEEK': {
        const dayOfWeek = date.getUTCDay(); // 0=Sun..6=Sat
        const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
        const start = new Date(date.getTime());
        start.setUTCDate(start.getUTCDate() + mondayOffset);
        const end = new Date(start.getTime());
        end.setUTCDate(end.getUTCDate() + 6);
        return `${shortDayFormatter.format(start)} – ${dayFormatter.format(end)}`;
      }
      case 'MONTH':
        return capitalize(monthFormatter.format(date));
      case 'YEAR':
        return String(date.getUTCFullYear());
    }
  }
}
