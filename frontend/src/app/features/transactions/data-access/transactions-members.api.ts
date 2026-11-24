import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../core/api/api-config';
import { Role } from '../../../core/api/models/me.model';

export interface TransactionsFamilyMember {
  id: string;
  fullName: string;
  role: Role;
  accentColor: string;
  isActive: boolean;
}

@Injectable({ providedIn: 'root' })
export class TransactionsMembersApi {
  private readonly http = inject(HttpClient);
  private readonly membersUrl = `${API}/family/members`;

  list(): Observable<TransactionsFamilyMember[]> {
    return this.http.get<TransactionsFamilyMember[]>(this.membersUrl);
  }
}
