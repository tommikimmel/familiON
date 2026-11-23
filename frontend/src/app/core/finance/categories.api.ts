import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../api/api-config';
import { Category, CategoryCreate, CategoryUpdate } from './finance.models';

@Injectable({ providedIn: 'root' })
export class CategoriesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/categories`;

  list(): Observable<Category[]> {
    return this.http.get<Category[]>(this.baseUrl);
  }

  create(payload: CategoryCreate): Observable<Category> {
    return this.http.post<Category>(this.baseUrl, payload);
  }

  update(id: string, patch: CategoryUpdate): Observable<Category> {
    return this.http.patch<Category>(`${this.baseUrl}/${encodeURIComponent(id)}`, patch);
  }
}
