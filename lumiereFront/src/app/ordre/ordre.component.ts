import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule, HttpClient } from '@angular/common/http';
import { NgbModule, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { OrdreService } from '../ordre.service';
import { Observable } from 'rxjs';
import * as L from 'leaflet';


@Component({
  selector: 'app-ordre',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule,
    NgbModule
  ],
  templateUrl: './ordre.component.html',
  styleUrls: ['./ordre.component.css']
})
export class OrdreComponent implements OnInit {
  isModalOpen = false;
  isMapModalOpen = false;
  selectedOrdreForMap: any = null;
  map: any = null;
  truckMarker: any = null;
  trailPolyline: any = null;
  // reference points to avoid disappearing during refresh
  refCoords = { lat1: 0, lon1: 0, lat2: 0, lon2: 0 }; 
  simulationLine: any = null;
  totalDistance: string = '--';
  private refreshInterval: any;


  dateDebut: string = this.getTodayDate();
  dateFin: string = this.getTodayDate();
  filtreClient: any;
  filtreSite: string = '';
  filtreStatut: string = "";
  filtreChauffeur: string = "";
  filtreDestination: string = "";
  filtreSource: string = "";
  statutOptions: string[] = ["PLANIFIE", "NON_PLANIFIE", "EN_COURS_DE_CHARGEMENT", "CHARGE", "EN_COURS_DE_LIVRAISON", "LIVRE"];
  siteOptions: string[] = [
    'BAR', 'SAL', 'BKS', 'SFX', 'TUN',
    'GAB', 'GAS', 'BSL', 'JER', 'BIZ', 'NAS'
  ];

  email = {
    to: "",
    subject: "",
    body: ""
  };

  sms = {
    mobile: '',
    message: ''
  };
  ordresFiltres: any[] = [];
  ordres: any[] = [];
  ordresPlanifies: any[] = [];
  // Define statutMap as a class property
  statutMap: { [key: string]: number } = {
    'PLANIFIE': 0,
    'Départ': 0,
    'Chargement': 1,
    'Chargé': 2,
    'Livraison': 3,
    'Livré': 4,
    'Fin': 5
  };
  eventCount: number = 0;

  constructor(
    private modalService: NgbModal, 
    private service: OrdreService, 
    private http: HttpClient,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit(): void {
    // Initial fetch
    this.filtrerParDate();
    
    // Set up periodic refresh (every 30 seconds)
    this.refreshInterval = setInterval(() => {
      this.filtrerParDate();
    }, 30000);
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
    }
  }

  private getTodayDate(): string {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, '0');
    const day = String(today.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  openModal() {
    console.log('open')
    this.isModalOpen = true;
  }

  closeModal(event?: MouseEvent) {
    this.isModalOpen = false;
  }

  onSubmit() {
    this.service.sendEmail(this.email).subscribe(
      response => {
        console.log('Email envoyé avec succès', response);
      },
      error => {
        console.error('Error sending email', error);
      }
    );
    this.closeModal();
  }

  getEmail(clientId: number): void {
    console.log('get email')
    this.service.getEmail(clientId).subscribe(
      response => {
        this.email.to = response;
      },
      error => {
        console.error('Error fetching email:', error);
      }
    );
  }

  getTelephone(clientId: number): Observable<string> {
    return this.service.gettelephone(clientId);
  }

  sendSms(clientId: number, ordre: any) {
    this.getTelephone(clientId).subscribe(
      (telephone: string) => {
        this.sms.mobile = telephone;
        this.sms.message = `Bonjour, votre voyage est : ${ordre.statut}`;

        this.service.sendSms(this.sms.mobile, this.sms.message).subscribe(
          response => {
            console.log('SMS envoyé avec succès', response);
          },
          error => {
            console.error("Erreur lors de l'envoi du SMS", error);
          }
        );
      },
      error => {
        console.error("Erreur lors de la récupération du téléphone", error);
      }
    );
  }


  afficher() {
    this.service.afficher().subscribe(ordres => {
      this.ordres = ordres;
      this.ordresFiltres = this.ordres;
      this.sortEvents();
      this.cdr.detectChanges();
    });
  }

  voirMap(ordre: any) {
    // 1. Arrêter TOUT rafraîchissement en arrière-plan
    if (this.refreshInterval) {
        clearInterval(this.refreshInterval);
        this.refreshInterval = null;
    }
    this.selectedOrdreForMap = ordre;
    this.isMapModalOpen = true;
    this.initMap();
  }

  closeMapModal() {
    this.isMapModalOpen = false;
    this.selectedOrdreForMap = null;
    this.truckMarker = null;
    this.trailPolyline = null;
    this.simulationLine = null;
    this.totalDistance = '--';
    if (this.refreshInterval) {
        clearInterval(this.refreshInterval);
        this.refreshInterval = null;
    }
    
    // Restart the general refresh
    this.refreshInterval = setInterval(() => this.filtrerParDate(), 30000);
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }

  initMap() {
    setTimeout(() => {
      if (this.map) {
         this.map.remove();
      }

      this.map = L.map('osm-map').setView([33.8869, 9.5375], 6);

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors'
      }).addTo(this.map);

      if (this.selectedOrdreForMap) {
         // Initialize ref coords to 0
         this.refCoords = { lat1: 0, lon1: 0, lat2: 0, lon2: 0 };

         // Show truck immediately if GPS is available
         if (this.selectedOrdreForMap.currentLat && this.selectedOrdreForMap.currentLon) {
             this.plotTruck(0, 0, 0, 0); 
             this.map.setView([this.selectedOrdreForMap.currentLat, this.selectedOrdreForMap.currentLon], 11);
         }

         // If order is delivered, show the full trail
         if (this.selectedOrdreForMap.statut === 'LIVRE') {
             this.fetchTrail(this.selectedOrdreForMap.id);
         }
         
         this.geocodeAndPlot(this.selectedOrdreForMap.chargementVille, this.selectedOrdreForMap.livraisonVille);

         // LIVE TRACKING: Secure refresh ONLY if NOT delivered
         const currentOrderNumber = this.selectedOrdreForMap.orderNumber;
         if (this.refreshInterval) clearInterval(this.refreshInterval);
         
         if (this.selectedOrdreForMap.statut !== 'LIVRE') {
             this.refreshInterval = setInterval(() => {
             this.service.search({orderNumber: currentOrderNumber}).subscribe(res => {
                 // Only update if it's EXACTLY the same order
                 const updatedOrder = res.find((o: any) => o.orderNumber === currentOrderNumber);
                 if (updatedOrder && this.selectedOrdreForMap) {
                     this.selectedOrdreForMap.currentLat = updatedOrder.currentLat;
                     this.selectedOrdreForMap.currentLon = updatedOrder.currentLon;
                     this.plotTruck(this.refCoords.lat1, this.refCoords.lon1, this.refCoords.lat2, this.refCoords.lon2);
                 }
             });
             }, 10000);
         }
      }
    }, 300);
  }

  geocodeAndPlot(sourceCity: string, destCity: string) {
    if (!sourceCity || !destCity) return;
    const urlBase = 'https://nominatim.openstreetmap.org/search?format=json&limit=1&q=';
    
    // Geocode Source
    this.http.get<any[]>(urlBase + encodeURIComponent(sourceCity + ', Tunisia')).subscribe(res1 => {
        let lat1 = 36.8065, lon1 = 10.1815; // default Tunis
        if(res1 && res1.length > 0) {
            lat1 = parseFloat(res1[0].lat);
            lon1 = parseFloat(res1[0].lon);
        }

        L.marker([lat1, lon1], {
            icon: L.divIcon({
              className: 'custom-div-icon',
              html: "<div style='background-color:#10b981; color:white; border-radius:50%; width:30px; height:30px; display:flex; justify-content:center; align-items:center; box-shadow:0 0 10px rgba(0,0,0,0.5);'><i class='fa fa-arrow-up'></i></div>",
              iconSize: [30, 30],
              iconAnchor: [15, 15]
            })
        }).bindPopup('Départ: ' + sourceCity).addTo(this.map);

        // Geocode Destination
        this.http.get<any[]>(urlBase + encodeURIComponent(destCity + ', Tunisia')).subscribe(res2 => {
            let lat2 = 34.7398, lon2 = 10.7600; // default Sfax
            if(res2 && res2.length > 0) {
                lat2 = parseFloat(res2[0].lat);
                lon2 = parseFloat(res2[0].lon);
            }

            L.marker([lat2, lon2], {
                icon: L.divIcon({
                  className: 'custom-div-icon',
                  html: "<div style='background-color:#ef4444; color:white; border-radius:50%; width:30px; height:30px; display:flex; justify-content:center; align-items:center; box-shadow:0 0 10px rgba(0,0,0,0.5);'><i class='fa fa-arrow-down'></i></div>",
                  iconSize: [30, 30],
                  iconAnchor: [15, 15]
                })
            }).bindPopup('Destination: ' + destCity).addTo(this.map);

            // Draw connecting dashed line
            const latlngs: L.LatLngTuple[] = [ [lat1, lon1], [lat2, lon2] ];
            this.simulationLine = L.polyline(latlngs, {color: '#94a3b8', weight: 2, dashArray: '5, 10', opacity: 0.5}).addTo(this.map);
            
            // Adjust bounds to fit both points
            this.map.fitBounds(this.simulationLine.getBounds(), { padding: [50, 50] });

            // Store ref coords for refresh
            this.refCoords = { lat1, lon1, lat2, lon2 };

            // Place dynamic truck marker
            this.plotTruck(lat1, lon1, lat2, lon2);
        });
    });
  }

  plotTruck(lat1: number, lon1: number, lat2: number, lon2: number) {
      if (!this.selectedOrdreForMap) return;
      const statut = this.selectedOrdreForMap.statut;
      
      let truckLat = 0;
      let truckLon = 0;
      let gpsActif = false;

      // 1. Priorité absolue : Les VRAIES coordonnées envoyées par le boîtier GPS matériel
      if (this.selectedOrdreForMap.currentLat && this.selectedOrdreForMap.currentLon) {
          truckLat = this.selectedOrdreForMap.currentLat;
          truckLon = this.selectedOrdreForMap.currentLon;
          gpsActif = true;
      } else {
          // 2. Mode Dégradé (Simulation Visuelle) si pas de GPS installé
          let ratio = 0.5; // default center
          
          if (['NON_PLANIFIE', 'PLANIFIE'].includes(statut)) ratio = 0.0;
          else if (['EN_COURS_DE_CHARGEMENT', 'CHARGE'].includes(statut)) ratio = 0.1;
          else if (['LIVRE', 'Fin'].includes(statut)) ratio = 1.0;
          
          truckLat = lat1 + (lat2 - lat1) * ratio;
          truckLon = lon1 + (lon2 - lon1) * ratio;
      }

      // Couleur Verte si GPS Réel, Orange si Simulation
      const color = gpsActif ? '#10b981' : '#f5921e';
      const gpsLabel = gpsActif ? "<br><span style='color:green; font-weight:bold;'>Connexion GPS Live ✓</span>" : "<br><span style='color:orange;'>Position Estimée (Pas de Signal)</span>";
      
      const speed = this.selectedOrdreForMap.speed || 0;
      const truckInfo = this.selectedOrdreForMap.camion ? `<br><b>Camion:</b> ${this.selectedOrdreForMap.camion}` : '';

      // Update or create truck marker
      if (this.truckMarker) {
          this.truckMarker.setLatLng([truckLat, truckLon]);
          this.truckMarker.getPopup().setContent(`
              <div style="font-family: Arial, sans-serif; min-width: 150px;">
                  <b style="color:#2563eb; font-size:14px;">Ordre: ${this.selectedOrdreForMap.orderNumber}</b>
                  ${truckInfo}
                  <br><b>Chauffeur:</b> ${this.selectedOrdreForMap.chauffeur || 'Non assigné'}
                  <br><b>Vitesse:</b> <span style="color:${speed > 0 ? 'green' : 'red'}; font-weight:bold;">${speed} km/h</span>
                  <hr style="margin: 5px 0;">
                  ${gpsLabel}
              </div>
          `);
      } else {
          this.truckMarker = L.marker([truckLat, truckLon], {
              icon: L.divIcon({
                 className: 'custom-div-icon',
                 html: `<div style='background-color:${color}; color:white; border-radius:5px; padding:5px; font-size:16px; border:2px solid white; box-shadow:0 0 10px rgba(0,0,0,0.5);'><i class='fa fa-truck'></i></div>`,
                 iconSize: [36, 36],
                 iconAnchor: [18, 18]
              })
          }).bindPopup(`
              <div style="font-family: Arial, sans-serif; min-width: 150px;">
                  <b style="color:#2563eb; font-size:14px;">Ordre: ${this.selectedOrdreForMap.orderNumber}</b>
                  ${truckInfo}
                  <br><b>Chauffeur:</b> ${this.selectedOrdreForMap.chauffeur || 'Non assigné'}
                  <br><b>Vitesse:</b> <span style="color:${speed > 0 ? 'green' : 'red'}; font-weight:bold;">${speed} km/h</span>
                  <hr style="margin: 5px 0;">
                  ${gpsLabel}
              </div>
          `).addTo(this.map);
      }
  }

  fetchTrail(ordreId: number) {
      this.http.get<any[]>(`http://localhost:8090/api/v1/ordres/${ordreId}/parcours`).subscribe(points => {
          if (points && points.length > 1) {
              const latlngs: L.LatLngExpression[] = points.map(p => [p.lat, p.lng]);
              
              if (this.trailPolyline) this.map.removeLayer(this.trailPolyline);
              if (this.simulationLine) this.map.removeLayer(this.simulationLine);
              
              // 1. Calcul de la distance totale
              let dist = 0;
              for (let i = 0; i < latlngs.length - 1; i++) {
                  const p1 = L.latLng(latlngs[i] as L.LatLngTuple);
                  const p2 = L.latLng(latlngs[i+1] as L.LatLngTuple);
                  dist += p1.distanceTo(p2);
              }
              this.totalDistance = (dist / 1000).toFixed(2);

              // 2. Tracé professionnel
              this.trailPolyline = L.polyline(latlngs, {
                  color: '#2563eb', // Bleu royal
                  weight: 5,
                  opacity: 0.8,
                  smoothFactor: 1
              }).addTo(this.map);

              this.map.fitBounds(this.trailPolyline.getBounds(), { padding: [50, 50] });
              
              // 3. Icônes personnalisées
              const warehouseIcon = L.divIcon({
                  html: '<div class="map-marker-pro start"><i class="fa fa-warehouse"></i></div>',
                  className: 'custom-div-icon', iconSize: [30, 30], iconAnchor: [15, 15]
              });

              const flagIcon = L.divIcon({
                  html: '<div class="map-marker-pro end"><i class="fa fa-flag-checkered"></i></div>',
                  className: 'custom-div-icon', iconSize: [30, 30], iconAnchor: [15, 15]
              });

              L.marker(latlngs[0], {icon: warehouseIcon}).bindPopup('<b>Départ Réel</b><br>Heure: ' + new Date(points[0].date).toLocaleString()).addTo(this.map);
              L.marker(latlngs[latlngs.length - 1], {icon: flagIcon}).bindPopup('<b>Arrivée Réelle</b><br>Heure: ' + new Date(points[points.length-1].date).toLocaleString()).addTo(this.map);

              // 4. Points de passage (vitesse) - tous les 10 points pour ne pas surcharger
              for (let i = 5; i < points.length - 5; i += 15) {
                  L.circleMarker([points[i].lat, points[i].lng], {radius: 3, color: '#ffffff', fillColor: '#2563eb', fillOpacity: 1, weight: 1})
                  .bindTooltip(`Vitesse: ${points[i].speed} km/h<br>Heure: ${new Date(points[i].date).toLocaleTimeString()}`)
                  .addTo(this.map);
              }
          }
      });
  }

  detail(ordre: any) {
    this.service.detail = ordre;
    console.log(this.service.detail);
  }

  sortEvents() {
    for (let ordre of this.ordres) {
      if (ordre.events) {
        ordre.events.sort((a: string, b: string) => a.localeCompare(b, 'fr', { sensitivity: 'base' }));
      }
    }
  }


  getTimelineClass(index: number, events: any[], statut: string): string {
    let eventCount = events ? events.filter(event => event !== null && event !== undefined).length : 0;

    // Fallback if events are empty: use the status to estimate progress
    if (eventCount === 0) {
      if (statut === 'EN_COURS_DE_LIVRAISON') eventCount = 4;
      else if (statut === 'CHARGE') eventCount = 3;
      else if (statut === 'EN_COURS_DE_CHARGEMENT') eventCount = 2;
      else if (statut === 'PLANIFIE') eventCount = 1;
      else if (statut === 'LIVRE' || statut === 'Fin') eventCount = 6;
    }

    if (statut === 'NON_PLANIFIE' && eventCount === 0) return 'inactive';

    if (index < eventCount) {
      if (index === eventCount - 1 && eventCount < 6) return 'pending';
      return 'completed';
    }

    return 'inactive';
  }

  getTimelineClassLine(index: number, events: any[], statut: string): string {
    let eventCount = events ? events.filter(event => event !== null && event !== undefined).length : 0;
    
    // Fallback
    if (eventCount === 0) {
      if (statut === 'EN_COURS_DE_LIVRAISON') eventCount = 4;
      else if (statut === 'CHARGE') eventCount = 3;
      else if (statut === 'EN_COURS_DE_CHARGEMENT') eventCount = 2;
      else if (statut === 'PLANIFIE') eventCount = 1;
      else if (statut === 'LIVRE' || statut === 'Fin') eventCount = 6;
    }

    if (index < eventCount - 1) return 'active';
    return 'inactive';
  }


  autoRefreshPage(): void {
    // Legacy method, kept for compatibility if needed, but periodic fetch is better
  }


  filtrerParDate() {
    const params = {
      client: this.filtreClient,
      statut: this.filtreStatut,
      startDate: this.dateDebut,
      endDate: this.dateFin,
      site: this.filtreSite,
      chauffeur: this.filtreChauffeur,
      destination: this.filtreDestination
    };

    this.service.search(params).subscribe(ordres => {
      this.ordresFiltres = ordres.filter(o => {
        const matchesSource = !this.filtreSource || 
          (o.chargementVille && o.chargementVille.toLowerCase().includes(this.filtreSource.toLowerCase()));
        return o.statut !== 'NON_CONFIRME' && matchesSource;
      });
      this.cdr.detectChanges();
    });
  }



  resetFiltre() {
    this.dateDebut = this.getTodayDate();
    this.dateFin = this.getTodayDate();
    this.filtreClient = "";
    this.filtreSite = "";
    this.filtreStatut = "";
    this.filtreChauffeur = "";
    this.filtreDestination = "";
    this.filtreSource = "";

    this.filtrerParDate();
  }


  exporterExcel() {
    const headers = [
      'dateSaisie', 'livraisonDate', 'orderNumber', 'client', 'siteclient',
      'statut', 'chauffeur', 'camion', 'datevoy'
    ];

    const filename = `suivi_ordres_${new Date().getTime()}.csv`;
    this.service.exportToCsv(this.ordresFiltres, filename, headers);
  }



}



