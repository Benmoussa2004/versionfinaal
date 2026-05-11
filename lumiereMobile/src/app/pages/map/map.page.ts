import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonContent, IonHeader, IonIcon, IonSpinner, ToastController } from '@ionic/angular/standalone';
import { NavController, ViewDidEnter } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { arrowBackOutline, notificationsOutline, logOutOutline, busOutline, analyticsOutline, locationOutline, businessOutline } from 'ionicons/icons';
import { ActivatedRoute } from '@angular/router';
import { LivraisonService, LivraisonSimple } from '../../services/livraison.service';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import * as L from 'leaflet';

@Component({
  selector: 'app-map',
  templateUrl: './map.page.html',
  styleUrls: ['./map.page.scss'],
  standalone: true,
  imports: [IonContent, IonHeader, IonIcon, IonSpinner, CommonModule, FormsModule, HttpClientModule]
})
export class MapPage implements OnInit, OnDestroy, ViewDidEnter {
  selectedLivraison: LivraisonSimple | null = null;
  map: L.Map | null = null;
  isLoading = false;
  isExpanded = false; 
  private timeouts: any[] = [];
  private truckMarker: L.Marker | null = null;
  private markerSource: L.Marker | null = null;
  private markerDest: L.Marker | null = null;
  private routeLayer: L.Polyline | null = null;
  private routeGlow: L.Polyline | null = null;
  private refreshInterval: any = null;
  private refCoords: any = null;
  private coordsCache: Map<string, {lat: number, lon: number}> = new Map();

  constructor(
    public navCtrl: NavController,
    private route: ActivatedRoute,
    private livraisonService: LivraisonService,
    private http: HttpClient,
    private toastCtrl: ToastController
  ) {
    addIcons({ arrowBackOutline, notificationsOutline, logOutOutline, busOutline, analyticsOutline, locationOutline, businessOutline });
  }

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      const id = params['livraisonId'];
      if (id) {
        this.loadLivraison(+id);
        this.startLiveTracking(+id);
      }
    });
  }

  ngOnDestroy() {
    this.cleanup();
  }

  private cleanup() {
    this.timeouts.forEach(t => clearTimeout(t));
    this.timeouts = [];
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
      this.refreshInterval = null;
    }
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
    this.truckMarker = null;
    this.markerSource = null;
    this.markerDest = null;
    this.routeLayer = null;
    this.routeGlow = null;
  }

  ionViewDidEnter() {
    // Only init map if not already done, otherwise just refresh size
    if (!this.map && this.selectedLivraison) {
      this.initMap(this.selectedLivraison);
    } else if (this.map) {
      setTimeout(() => this.map?.invalidateSize(), 300);
    }
  }

  loadLivraison(id: number) {
    this.isLoading = true;
    this.livraisonService.getLivraisonById(id).subscribe({
      next: (livraison: LivraisonSimple) => {
        this.selectedLivraison = livraison;
        if (!this.map) {
          this.initMap(livraison);
        } else {
          this.geocodeAndPlot(livraison);
        }
        this.isLoading = false;
      },
      error: (err: any) => {
        console.error('Erreur chargement livraison:', err);
        this.isLoading = false;
      }
    });
  }

  startLiveTracking(id: number) {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
    this.refreshInterval = setInterval(() => {
      this.livraisonService.getLivraisonById(id).subscribe(updated => {
        if (this.selectedLivraison) {
          this.selectedLivraison.currentLat = updated.currentLat;
          this.selectedLivraison.currentLon = updated.currentLon;
          this.selectedLivraison.speed = updated.speed;
          this.selectedLivraison.statut = updated.statut;
          this.selectedLivraison.camion = updated.camion;
          
          if (this.map && this.refCoords) {
             // Efficient update without re-calculating geocoding
             this.plotTruck(this.selectedLivraison, this.refCoords.lat1, this.refCoords.lon1, this.refCoords.lat2, this.refCoords.lon2);
          }
        }
      });
    }, 15000); // 15s to reduce mobile load
  }

  initMap(livraison: LivraisonSimple) {
    if (this.map) {
      this.map.remove();
      this.map = null;
    }

    // Exact view from frontend
    this.map = L.map('osm-map', {
      zoomControl: false,
      attributionControl: false
    }).setView([33.8869, 9.5375], 6);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);

    // Initial resize to fix tiling
    setTimeout(() => {
      if (this.map) this.map.invalidateSize();
    }, 100);

    this.geocodeAndPlot(livraison);
  }

  async geocodeAndPlot(livraison: LivraisonSimple) {
    const sourceCity = livraison.chargementVille || '';
    const destCity = livraison.livraisonVille || '';
    if (!this.map || !sourceCity || !destCity) return;

    const getCoords = async (city: string) => {
      const cacheKey = `${city}, Tunisia`;
      if (this.coordsCache.has(cacheKey)) return this.coordsCache.get(cacheKey)!;
      
      try {
        const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(cacheKey)}`;
        const res = await this.http.get<any[]>(url).toPromise();
        if (res && res.length > 0) {
          const coords = { lat: parseFloat(res[0].lat), lon: parseFloat(res[0].lon) };
          this.coordsCache.set(cacheKey, coords);
          return coords;
        }
      } catch (e) { console.error('Geocoding error', e); }
      return null;
    };

    const c1 = await getCoords(sourceCity) || { lat: 36.8065, lon: 10.1815 };
    const c2 = await getCoords(destCity) || { lat: 34.7398, lon: 10.7600 };

    if (!this.map) return;

    // Marker Source
    if (this.markerSource) this.markerSource.remove();
    this.markerSource = L.marker([c1.lat, c1.lon], {
        icon: L.divIcon({
          className: 'custom-div-icon',
          html: `<div style='background-color:#10b981; color:white; border-radius:50%; width:30px; height:30px; display:flex; justify-content:center; align-items:center; box-shadow:0 0 10px rgba(0,0,0,0.5); font-weight:bold; font-size:18px;'>↑</div>`,
          iconSize: [30, 30],
          iconAnchor: [15, 15]
        })
    }).addTo(this.map).bindPopup('Départ: ' + sourceCity);

    // Marker Destination
    if (this.markerDest) this.markerDest.remove();
    this.markerDest = L.marker([c2.lat, c2.lon], {
        icon: L.divIcon({
          className: 'custom-div-icon',
          html: `<div style='background-color:#ef4444; color:white; border-radius:50%; width:30px; height:30px; display:flex; justify-content:center; align-items:center; box-shadow:0 0 10px rgba(0,0,0,0.5); font-weight:bold; font-size:18px;'>↓</div>`,
          iconSize: [30, 30],
          iconAnchor: [15, 15]
        })
    }).addTo(this.map).bindPopup('Destination: ' + destCity);

    this.refCoords = { lat1: c1.lat, lon1: c1.lon, lat2: c2.lat, lon2: c2.lon };
    
    // Polyline
    if (this.routeLayer) this.routeLayer.remove();
    this.routeLayer = L.polyline([[c1.lat, c1.lon], [c2.lat, c2.lon]], {
      color: '#3b82f6', 
      weight: 4, 
      dashArray: '5, 10'
    }).addTo(this.map);

    this.map.fitBounds(this.routeLayer.getBounds(), { padding: [50, 50], animate: false });
    this.plotTruck(livraison, c1.lat, c1.lon, c2.lat, c2.lon);
  }

  plotTruck(livraison: any, lat1: number, lon1: number, lat2: number, lon2: number) {
    if (!this.map) return;
    const statut = livraison.statut;
    let truckLat = 0, truckLon = 0, gpsActif = false;

    if (livraison.currentLat && livraison.currentLon) {
        truckLat = livraison.currentLat;
        truckLon = livraison.currentLon;
        gpsActif = true;
    } else {
        // Mode Dégradé from Frontend
        let ratio = 0.5;
        if (['NON_PLANIFIE', 'PLANIFIE'].includes(statut)) ratio = 0.0;
        else if (['EN_COURS_DE_CHARGEMENT', 'CHARGE'].includes(statut)) ratio = 0.1;
        else if (['LIVRE', 'Fin'].includes(statut)) ratio = 1.0;
        truckLat = lat1 + (lat2 - lat1) * ratio;
        truckLon = lon1 + (lon2 - lon1) * ratio;
    }

    const color = gpsActif ? '#10b981' : '#f5921e';
    const gpsLabel = gpsActif ? "<br><span style='color:green; font-weight:bold;'>Connexion GPS Live ✓</span>" : "<br><span style='color:orange;'>Position Estimée (Pas de Signal)</span>";
    const speed = livraison.speed || 0;
    const truckInfo = livraison.camion ? `<br><b>Camion:</b> ${livraison.camion}` : '';

    const popupContent = `
        <div style="font-family: Arial, sans-serif; min-width: 150px;">
            <b style="color:#2563eb; font-size:14px;">Ordre: ${livraison.orderNumber || livraison.id}</b>
            ${truckInfo}
            <br><b>Chauffeur:</b> ${livraison.chauffeur || 'Non assigné'}
            <br><b>Vitesse:</b> <span style="color:${speed > 0 ? 'green' : 'red'}; font-weight:bold;">${speed} km/h</span>
            <hr style="margin: 5px 0;">
            ${gpsLabel}
        </div>
    `;

    if (this.truckMarker) {
        this.truckMarker.setLatLng([truckLat, truckLon]);
        this.truckMarker.setPopupContent(popupContent);
    } else {
        this.truckMarker = L.marker([truckLat, truckLon], {
            icon: L.divIcon({
               className: 'custom-div-icon',
               html: `<div style='background-color:${color}; color:white; border-radius:5px; padding:5px; font-size:16px; border:2px solid white; box-shadow:0 0 10px rgba(0,0,0,0.5); font-weight:bold;'>🚛</div>`,
               iconSize: [36, 36],
               iconAnchor: [18, 18]
            })
        }).bindPopup(popupContent).addTo(this.map!);
    }
  }

  getStatusKey(statut: string): string {
    const mapStatuses: Record<string, string> = {
      NON_CONFIRME: 'pending',
      NON_PLANIFIE: 'pending',
      EN_ATTENTE: 'pending',
      PLANIFIE: 'ready',
      CHARGE: 'ready',
      EN_COURS_DE_LIVRAISON: 'transit',
      EN_LIVRAISON: 'transit',
      LIVRE: 'done',
      FIN: 'done'
    };
    return mapStatuses[statut] || 'pending';
  }

  getStatusLabel(statut: string): string {
    const labels: any = {
      'NON_PLANIFIE': 'En attente',
      'PLANIFIE': 'Planifié',
      'EN_COURS_DE_LIVRAISON': 'En livraison',
      'LIVRE': 'Livré',
      'FIN': 'Terminé'
    };
    return labels[statut] || statut;
  }

  goToNotifications() {
    this.navCtrl.navigateForward('/notifications');
  }

  toggleExpand() {
    this.isExpanded = !this.isExpanded;
    setTimeout(() => {
      if (this.map) this.map.invalidateSize();
    }, 300);
  }

  async replotRoute() {
    if (this.selectedLivraison && this.map) {
      console.log('🔄 Replotting route (exact frontend style)...');
      this.geocodeAndPlot(this.selectedLivraison);
      
      const toast = await this.toastCtrl.create({
        message: 'Mise à jour du tracé...',
        duration: 1500,
        position: 'top',
        color: 'primary',
        cssClass: 'custom-toast'
      });
      toast.present();
    }
  }

  logout() {
    this.navCtrl.navigateRoot('/login');
  }
}


