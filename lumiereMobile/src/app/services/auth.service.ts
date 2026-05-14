
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginRequest, RegisterRequest, AuthResponse } from '../models/auth.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  // Auth: /api/v1/auth
  private apiUrl = `${environment.apiUrl}/v1/auth`;

  // Admin: /api/v1/admin
  private adminUrl = `${environment.apiUrl}/v1/admin`;

  constructor(private http: HttpClient) { }

  // 🔐 LOGIN
  login(data: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/authenticate`, data, {
        headers: new HttpHeaders({ 'X-App-Platform': 'mobile' })
      })
      .pipe(
        tap(res => {
          localStorage.setItem('token', res.token);
        })
      );
  }

  // 📝 REGISTER — account starts as PENDING, no token is returned
  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, data);
  }

  // 👤 GET PROFILE (JWT required)
  getProfile(): Observable<any> {
    return this.http.get(`${this.apiUrl}/profile`, {
      headers: this.getAuthHeaders()
    });
  }

  // ✏️ UPDATE PROFILE
  updateProfile(data: RegisterRequest): Observable<any> {
    return this.http.put(`${this.apiUrl}/update`, data, {
      headers: this.getAuthHeaders()
    });
  }

  // 📦 GET ALL USERS
  getAllUsers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/profileALL`, {
      headers: this.getAuthHeaders()
    });
  }

  // 🚪 LOGOUT
  logout(): void {
    localStorage.removeItem('token');
  }

  // 🔍 CHECK ACCOUNT STATUS (public — no token needed)
  checkAccountStatus(email: string): Observable<{ status: string; email: string }> {
    return this.http.get<{ status: string; email: string }>(
      `${this.adminUrl}/status?email=${encodeURIComponent(email)}`
    );
  }

  // 🔑 TOKEN
  getToken(): string | null {
    return localStorage.getItem('token');
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  // 🔐 Headers
  private getAuthHeaders(): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${this.getToken()}`
    });
  }
}

