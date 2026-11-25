import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../core/api/api-config';
import { Me } from '../../../core/api/models/me.model';
import { UpdatePreferencesRequest } from './profile.models';

@Injectable({ providedIn: 'root' })
export class ProfileApi {
  private readonly http = inject(HttpClient);

  updatePreferences(payload: UpdatePreferencesRequest): Observable<Me> {
    return this.http.patch<Me>(`${API}/auth/me/preferences`, payload);
  }

  uploadAvatar(file: File): Observable<Me> {
    const body = new FormData();
    body.append('file', file);
    return this.http.put<Me>(`${API}/auth/me/avatar`, body);
  }

  removeAvatar(): Observable<Me> {
    return this.http.delete<Me>(`${API}/auth/me/avatar`);
  }
}
