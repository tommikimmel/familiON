import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../core/api/api-config';
import {
  NotificationItem,
  NotificationPageResponse,
  PushConfigResponse,
  PushSubscriptionPayload,
  PushUnsubscribePayload,
  UnreadCountResponse,
} from './notifications.models';

export interface NotificationListQuery {
  unreadOnly: boolean;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/notifications`;

  list(query: NotificationListQuery): Observable<NotificationPageResponse> {
    let params = new HttpParams()
      .set('page', String(query.page))
      .set('size', String(query.size));

    if (query.unreadOnly) {
      params = params.set('unreadOnly', 'true');
    }

    return this.http.get<NotificationPageResponse>(this.baseUrl, { params });
  }

  getUnreadCount(): Observable<UnreadCountResponse> {
    return this.http.get<UnreadCountResponse>(`${this.baseUrl}/unread-count`);
  }

  markAsRead(id: string): Observable<NotificationItem> {
    return this.http.patch<NotificationItem>(
      `${this.baseUrl}/${encodeURIComponent(id)}/read`,
      {},
    );
  }

  markAllAsRead(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/read-all`, {});
  }

  getPushConfig(): Observable<PushConfigResponse> {
    return this.http.get<PushConfigResponse>(`${this.baseUrl}/push-config`);
  }

  registerPushSubscription(payload: PushSubscriptionPayload): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/push-subscriptions`, payload);
  }

  unregisterPushSubscription(payload: PushUnsubscribePayload): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/push-subscriptions`, {
      body: payload,
    });
  }
}
