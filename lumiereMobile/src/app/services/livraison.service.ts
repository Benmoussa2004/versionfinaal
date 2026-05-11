// src/app/services/livraison.service.ts
// Adapté pour fonctionner avec le backend Otflow réel
// Les livraisons correspondent aux ordres avec statut EN_COURS_DE_LIVRAISON ou LIVRE

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface LivraisonSimple {
  id: number;
  orderNumber?: string;
  client: string;
  nomclient: string;
  chargementDate: string | Date;
  chargementVille?: string;
  chargementNom?: string;
  livraisonVille: string;
  livraisonDate: string | Date;
  chauffeur?: string;
  telchauffeur?: string;
  camion?: string;
  matricule?: string;
  statut: string;
  voycle?: string;
  commentaires?: string[];
  poids?: number;
  nombrePalettes?: number;
  volume?: number;
  siteclient?: string;
  codeArticle?: string;
  currentLat?: number;
  currentLon?: number;
  speed?: number;
}

@Injectable({
  providedIn: 'root'
})
export class LivraisonService {
  // Les livraisons sont des ordres avec certains statuts dans le backend
  private readonly API_URL = `${environment.apiUrl}/v1/ordres`;

  constructor(private http: HttpClient) { }

  private authHeaders() {
    return {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('token')}`
      }
    };
  }

  /**
   * Obtenir tous les ordres de livraison
   */
  getLivraisons(): Observable<LivraisonSimple[]> {
    return this.http.get<LivraisonSimple[]>(this.API_URL, this.authHeaders());
  }

  /**
   * Obtenir les livraisons en cours (statut EN_COURS_DE_LIVRAISON, PLANIFIE, CHARGE, etc.)
   */
  getLivraisonsEnCours(): Observable<LivraisonSimple[]> {
    return this.http.get<any>(this.API_URL, this.authHeaders()).pipe(
      map(res => {
        const ordres = Array.isArray(res) ? res : (res.content || []);
        const activeStatuses = ['NON_PLANIFIE', 'PLANIFIE', 'EN_COURS_DE_CHARGEMENT', 'CHARGE', 'EN_COURS_DE_LIVRAISON', 'EN_LIVRAISON'];
        return ordres.filter((o: any) => activeStatuses.includes(o.statut));
      })
    );
  }

  /**
   * Obtenir les livraisons terminées (statut LIVRE ou FIN)
   */
  getLivraisonsTerminees(): Observable<LivraisonSimple[]> {
    return this.http.get<any>(this.API_URL, this.authHeaders()).pipe(
      map(res => {
        const ordres = Array.isArray(res) ? res : (res.content || []);
        return ordres.filter((o: any) => o.statut === 'LIVRE' || o.statut === 'FIN');
      })
    );
  }

  /**
   * Obtenir une livraison par ID
   */
  getLivraisonById(id: number): Observable<LivraisonSimple> {
    return this.http.get<LivraisonSimple>(`${this.API_URL}/${id}`, this.authHeaders());
  }

  /**
   * Obtenir l'historique (commentaires) d'une livraison
   */
  getHistorique(id: number): Observable<string[]> {
    return this.http.get<string[]>(`${this.API_URL}/${id}/historique`, this.authHeaders());
  }

  /**
   * Confirmer la réception de la livraison
   */
  confirmerReception(id: number): Observable<LivraisonSimple> {
    return this.http.put<LivraisonSimple>(`${this.API_URL}/${id}/confirmer`, {}, this.authHeaders());
  }

  /**
   * Obtenir les statistiques de livraison
   */
  getStatistiques(): Observable<any> {
    return this.http.get(`${this.API_URL}/statistiques`, this.authHeaders());
  }
}

