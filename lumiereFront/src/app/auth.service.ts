import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private apiUrl = environment.authUrl;
  private demoApiUrl = environment.demoUrl;

  constructor(private http: HttpClient) { }

  login(email: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/authenticate`, { email, password });
  }

  register(userData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, userData);
  }

  approveClient(id: number, codeClient: string, idEdi: string): Observable<any> {
    const params = { codeClient, idEdi };
    return this.http.post(`${environment.adminUrl}/users/${id}/approve-client`, null, { 
      params,
      headers: this.getAuthHeaders() 
    });
  }


  profile(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/profile`, { headers: this.getAuthHeaders() });
  }
  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders(token ? { 'Authorization': `Bearer ${token}` } : {});
  }

  isAuthenticated(): boolean {
    // Implement your logic to check if the user is authenticated
    return !!localStorage.getItem('token');
  }

  getAllUsers(): Observable<any[]> {
    return this.http.get<any[]>(`${environment.adminUrl}/users`, { headers: this.getAuthHeaders() });
  }

  updateUserStatus(id: number, status: string): Observable<any> {
    return this.http.put<any>(`${environment.adminUrl}/users/${id}/status?status=${status}`, {}, { headers: this.getAuthHeaders() });
  }

  updateUser(id: number, userData: any): Observable<any> {
    return this.http.put<any>(`${environment.adminUrl}/users/${id}`, userData, { headers: this.getAuthHeaders() });
  }

  deleteUser(id: number): Observable<any> {
    return this.http.delete<any>(`${environment.adminUrl}/users/${id}`, { headers: this.getAuthHeaders() });
  }

  logout(): void {
    localStorage.removeItem('token');
  }

}



