import { DOCUMENT } from '@angular/common';
import { InjectionToken, inject } from '@angular/core';

export const PWA_RELOAD_PAGE = new InjectionToken<() => void>('PWA_RELOAD_PAGE', {
  providedIn: 'root',
  factory: () => {
    const doc = inject(DOCUMENT);
    return () => {
      doc.defaultView?.location?.reload();
    };
  },
});

export const PWA_NOW = new InjectionToken<() => number>('PWA_NOW', {
  providedIn: 'root',
  factory: () => () => Date.now(),
});
