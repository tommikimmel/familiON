import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../core/api/api-config';
import { Role } from '../../../core/api/models/me.model';

export interface QuickAddFamilyMember {
  id: string;
  fullName: string;
  role: Role;
  accentColor: string;
  isActive: boolean;
}

@Injectable({ providedIn: 'root' })
export class QuickAddMembersApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/family/members`;

  list(): Observable<QuickAddFamilyMember[]> {
    return this.http.get<QuickAddFamilyMember[]>(this.baseUrl);
  }
}
