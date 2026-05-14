import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class OrdreService {

  private apiUrl = `${environment.apiUrl}/ordres`;
  private baseUrl = `${environment.apiUrl}/clients`;

  constructor(private http: HttpClient) { }

  afficher(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl, { headers: this.getAuthHeaders() });
  }
  afficherByClient(clientCode: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/client/${clientCode}`, { headers: this.getAuthHeaders() });
  }
  ajouter(depence: any): Observable<any[]> {
    return this.http.post<any[]>(this.apiUrl, depence, { headers: this.getAuthHeaders() });
  }



  sendEmail(emailRequest: any): Observable<string> {
    return this.http.post<string>(`${environment.apiUrl}/email/send`, emailRequest, { headers: this.getAuthHeaders() });
  }



  supprimer(id: number): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${id}`, { headers: this.getAuthHeaders() });
  }


  confirmer(id: number): Observable<void> {
    return this.http.put<any>(`${this.apiUrl}/confirmer/${id}`, {}, { headers: this.getAuthHeaders() });
  }

  confirmerMultiple(ids: number[]): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/confirmer-multiple`, ids, { headers: this.getAuthHeaders() });
  }


  afficheremail(id: any): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/email/${id}`, { headers: this.getAuthHeaders() });

  }



  getEmail(clientId: number): Observable<string> {
    const url = `${this.baseUrl}/email/${clientId}`;
    const headers = this.getAuthHeaders();
    return this.http.get(url, { headers, responseType: 'text' });
  }

  gettelephone(clientId: number): Observable<string> {
    const url = `${this.baseUrl}/telephone/${clientId}`;
    const headers = this.getAuthHeaders();
    return this.http.get(url, { headers, responseType: 'text' });
  }

  private smsUrl = 'http://172.18.3.65:28500/SMS_SEND';
  sendSms(mobile: string, message: string): Observable<any> {
    const url = `${this.apiUrl}/sms/send?mobile=${mobile}&message=${encodeURIComponent(message)}`;
    return this.http.post(url, {});
  }


  detail: any;

  dupliquerMultiple(id: number, count: number): Observable<any[]> {
    return this.http.post<any[]>(`${this.apiUrl}/${id}/dupliquer-multiple?count=${count}`, {}, { headers: this.getAuthHeaders() });
  }

  generatePlaFiles(ids: number[]): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/generate-pla-file`, ids, { headers: this.getAuthHeaders() });
  }

  search(params: any): Observable<any[]> {
    let httpParams = new HttpParams();
    if (params.client) httpParams = httpParams.set('client', params.client);
    if (params.statut) httpParams = httpParams.set('statut', params.statut);
    if (params.startDate) httpParams = httpParams.set('startDate', params.startDate);
    if (params.endDate) httpParams = httpParams.set('endDate', params.endDate);
    if (params.chauffeur) httpParams = httpParams.set('chauffeur', params.chauffeur);
    if (params.site) httpParams = httpParams.set('site', params.site);
    if (params.destination) httpParams = httpParams.set('destination', params.destination);

    return this.http.get<any[]>(`${this.apiUrl}/search`, { params: httpParams, headers: this.getAuthHeaders() });
  }

  private ordersUrl = 'assets/mesvoyes.json';
  getOrders(): Observable<any[]> {
    return this.http.get<any[]>(this.ordersUrl);
  }


  exportToCsv(data: any[], filename: string, headers: string[]) {
    if (!data || !data.length) return;

    const separator = ';';
    const csvContent = [
      headers.join(separator),
      ...data.map(row =>
        headers.map(header => {
          const cell = row[header] === null || row[header] === undefined ? '' : row[header];
          return `"${String(cell).replace(/"/g, '""')}"`;
        }).join(separator)
      )
    ].join('\n');

    const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    if (link.download !== undefined) {
      const url = URL.createObjectURL(blob);
      link.setAttribute('href', url);
      link.setAttribute('download', filename);
      link.style.visibility = 'hidden';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    }
  }

  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders(token ? { 'Authorization': `Bearer ${token}` } : {});
  }

}



