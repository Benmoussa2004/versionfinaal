import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';
import Swal from 'sweetalert2';

export interface SystemNotification {
  id: number;
  type: string;
  message: string;
  timestamp: string;
  isRead: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {

  private apiUrl = `${environment.apiUrl}/notifications`;

  constructor(private http: HttpClient) { }

  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders(token ? { 'Authorization': `Bearer ${token}` } : {});
  }

  /**
   * Success toast notification (top-right, disappears quickly)
   */
  showSuccess(message: string) {
    const Toast = Swal.mixin({
      toast: true,
      position: 'top-end',
      showConfirmButton: false,
      timer: 3000,
      timerProgressBar: true,
      didOpen: (toast) => {
        toast.addEventListener('mouseenter', Swal.stopTimer)
        toast.addEventListener('mouseleave', Swal.resumeTimer)
      }
    });

    Toast.fire({
      icon: 'success',
      title: message
    });
  }

  /**
   * Error toast notification
   */
  showError(message: string) {
    const Toast = Swal.mixin({
      toast: true,
      position: 'top-end',
      showConfirmButton: false,
      timer: 4000,
      timerProgressBar: true,
    });

    Toast.fire({
      icon: 'error',
      title: message
    });
  }

  /**
   * Warning toast notification
   */
  showWarning(message: string) {
    const Toast = Swal.mixin({
      toast: true,
      position: 'top-end',
      showConfirmButton: false,
      timer: 3000,
      timerProgressBar: true,
    });

    Toast.fire({
      icon: 'warning',
      title: message
    });
  }

  /**
   * Confirmation dialog (centered, requires action)
   */
  async confirm(title: string, text: string, confirmButtonText: string = 'Oui, supprimer'): Promise<boolean> {
    const result = await Swal.fire({
      title: title,
      text: text,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ff8c00', // Matches your orange theme
      cancelButtonColor: '#d33',
      confirmButtonText: confirmButtonText,
      cancelButtonText: 'Annuler',
      background: '#fff',
      color: '#333'
    });

    return result.isConfirmed;
  }

  /**
   * Standard alert dialog
   */
  alert(title: string, message: string, icon: 'success' | 'error' | 'warning' | 'info' = 'info') {
    Swal.fire({
      title: title,
      text: message,
      icon: icon,
      confirmButtonColor: '#ff8c00'
    });
  }
  /**
   * Generic notification
   */
  notify(message: string, type: 'success' | 'error' | 'warning' | 'info' = 'success') {
    switch (type) {
      case 'success': this.showSuccess(message); break;
      case 'error': this.showError(message); break;
      case 'warning': this.showWarning(message); break;
      case 'info': this.alert('Information', message, 'info'); break;
    }
  }

  // Compatibility for old code if needed
  notification = { type: '', message: '' };
  ajouternotification(notif: any) {
    this.notify(notif.message, 'info');
  }

  // --- Database-backed system notifications ---
  
  afficher(): Observable<SystemNotification[]> {
    return this.http.get<SystemNotification[]>(this.apiUrl, { headers: this.getAuthHeaders() });
  }

  markAllAsRead(): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/mark-all-read`, {}, { headers: this.getAuthHeaders() });
  }

  markAsRead(id: number): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/${id}/mark-read`, {}, { headers: this.getAuthHeaders() });
  }

  deleteNotification(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`, { headers: this.getAuthHeaders() });
  }
}
