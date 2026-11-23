import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../core/api/api-config';
import { Role } from '../../../core/api/models/me.model';

export interface FamilyMemberOption {
  id: string;
  fullName: string;
  email: string;
  role: Role;
  isActive: boolean;
}

@Injectable({ providedIn: 'root' })
export class FamilyMembersApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/family/members`;

  list(): Observable<FamilyMemberOption[]> {
    return this.http.get<FamilyMemberOption[]>(this.baseUrl);
  }
}
