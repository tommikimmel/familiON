import { CanActivateFn } from '@angular/router';

// Placeholder guard; real guest/authenticated redirect logic is implemented in T12.
export const guestGuard: CanActivateFn = () => true;
