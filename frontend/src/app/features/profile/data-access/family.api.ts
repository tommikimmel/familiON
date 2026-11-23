import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../core/api/api-config';
import {
  CreateInvitationRequest,
  EnableFamilyCurrencyRequest,
  Family,
  FamilyCurrency,
  Invitation,
  InvitationCreated,
  Member,
  UpdateFamilyCurrencyRequest,
  UpdateFamilySettingsRequest,
  UpdateMemberVisibilityRequest,
} from './family.models';

@Injectable({ providedIn: 'root' })
export class FamilyApi {
  private readonly http = inject(HttpClient);

  getFamily(): Observable<Family> {
    return this.http.get<Family>(`${API}/family`);
  }

  updateSettings(payload: UpdateFamilySettingsRequest): Observable<Family> {
    return this.http.patch<Family>(`${API}/family/settings`, payload);
  }

  enableCurrency(payload: EnableFamilyCurrencyRequest): Observable<FamilyCurrency> {
    return this.http.post<FamilyCurrency>(`${API}/family/currencies`, payload);
  }

  updateCurrency(
    currencyCode: string,
    payload: UpdateFamilyCurrencyRequest,
  ): Observable<FamilyCurrency> {
    return this.http.patch<FamilyCurrency>(
      `${API}/family/currencies/${encodeURIComponent(currencyCode)}`,
      payload,
    );
  }

  listMembers(): Observable<Member[]> {
    return this.http.get<Member[]>(`${API}/family/members`);
  }

  updateMemberVisibility(
    userId: string,
    payload: UpdateMemberVisibilityRequest,
  ): Observable<Member> {
    return this.http.patch<Member>(
      `${API}/family/members/${encodeURIComponent(userId)}/visibility`,
      payload,
    );
  }

  listInvitations(): Observable<Invitation[]> {
    return this.http.get<Invitation[]>(`${API}/family/invitations`);
  }

  createInvitation(payload: CreateInvitationRequest): Observable<InvitationCreated> {
    return this.http.post<InvitationCreated>(`${API}/family/invitations`, payload);
  }

  deleteInvitation(id: string): Observable<void> {
    return this.http.delete<void>(`${API}/family/invitations/${encodeURIComponent(id)}`);
  }
}
