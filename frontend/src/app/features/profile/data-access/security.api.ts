import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../core/api/api-config';
import { SecuritySettings, UpdateSecuritySettingsRequest } from './security.models';

@Injectable({ providedIn: 'root' })
export class SecurityApi {
  private readonly http = inject(HttpClient);

  getSettings(): Observable<SecuritySettings> {
    return this.http.get<SecuritySettings>(`${API}/auth/me/security`);
  }

  updateSettings(payload: UpdateSecuritySettingsRequest): Observable<SecuritySettings> {
    return this.http.patch<SecuritySettings>(`${API}/auth/me/security`, payload);
  }

  revokeTrustedDevice(id: string): Observable<void> {
    return this.http.delete<void>(`${API}/auth/me/security/trusted-devices/${encodeURIComponent(id)}`);
  }
}
