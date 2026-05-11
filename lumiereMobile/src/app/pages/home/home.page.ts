import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import {
  IonHeader, IonToolbar, IonIcon, IonContent, IonRefresher, IonRefresherContent,
  IonFab, IonFabButton, NavController, IonLabel, IonFabList
} from '@ionic/angular/standalone';
import {
  cubeOutline,
  peopleOutline,
  navigateOutline,
  documentText,
  documentTextOutline,
  pencilOutline,
  chevronForward,
  chevronForwardOutline,
  swapHorizontalOutline,
  sunny,
  sunnyOutline,
  moon,
  moonOutline,
  personOutline,
  logOutOutline,
  chatbubbleEllipses,
  chatbubbleEllipsesOutline,
  arrowForwardOutline,
  chevronDownOutline,
  addCircleOutline,
  addOutline,
  notificationsOutline,
  barcodeOutline,
  locationOutline,
  calendarOutline,
  carOutline,
  barChartOutline,
  informationCircleOutline,
  sparkles,
  sparklesOutline,
  refresh,
  refreshOutline,
  alarmOutline,
  chatbubbleOutline,
  personAddOutline, timeOutline, appsOutline
} from 'ionicons/icons';
import { HttpClient, HttpClientModule, HttpHeaders } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';
import { ToastService } from '../../services/toast.service';
import { ClientService } from '../../services/client.service';
import { NotificationService } from '../../services/notification.service';
import { addIcons } from 'ionicons';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-home',
  templateUrl: './home.page.html',
  styleUrls: ['./home.page.scss'],
  standalone: true,
  imports: [
    CommonModule,
    IonHeader,
    IonIcon,
    IonContent,
    IonRefresher,
    IonRefresherContent
  ]
})
export class HomePage implements OnInit {

  user: any = {
    firstname: '',
    lastname: '',
    email: ''
  };

  stats: any = {
    mesDemandesEnCours: 0,
    mesDemandesEnAttente: 0,
    mesDemandesTerminees: 0,
    mesLivraisonsEnCours: 0,
    totalMesDemandes: 0,
    totalMesLivraisons: 0,
    notifications: 0
  };

  mesDemandesRecentes: any[] = [];
  mesDrafts: any[] = [];
  mesLivraisonsActives: any[] = [];

  // Dashboard state
  isLoading = true;
  darkMode$: Observable<boolean>;
  pendingRappelsCount = 0;
  clientCount = 0;
  unreadNotifsCount = 0;

  // Dashboard sections
  mainSections = [
    {
      id: 'orders',
      title: 'Mes Commandes',
      subtitle: 'Gérer vos expéditions',
      icon: 'cube-outline',
      color: 'primary',
      route: '/demandes/list',
      count: 0
    },
    {
      id: 'clients',
      title: 'Mes Clients',
      subtitle: 'Carnet d\'adresses',
      icon: 'people-outline',
      color: 'secondary',
      route: '/clients',
      count: null
    },
    {
      id: 'tracking',
      title: 'Suivi Colis',
      subtitle: 'Localisation en temps réel',
      icon: 'navigate-outline',
      color: 'tertiary',
      route: '/livraisons/tracking',
      count: 0
    },
    {
      id: 'new-order',
      title: 'Nouvel Ordre',
      subtitle: 'Saisie rapide',
      icon: 'add-circle-outline',
      color: 'success',
      route: '/demandes/create',
      count: null
    }
  ];

  constructor(
    private router: Router,
    private navCtrl: NavController,
    private http: HttpClient,
    private authService: AuthService,
    private themeService: ThemeService,
    private toastService: ToastService,
    private clientService: ClientService,
    private notificationService: NotificationService
  ) {
    this.darkMode$ = this.themeService.darkMode$;
    addIcons({ 
      'arrow-forward': arrowForwardOutline,
      notificationsOutline, 
      logOutOutline, 
      timeOutline, 
      cubeOutline, 
      arrowForwardOutline, 
      peopleOutline, 
      navigateOutline, 
      addCircleOutline, 
      documentTextOutline, 
      addOutline, 
      personAddOutline, 
      documentText, 
      pencilOutline, 
      chevronForward, 
      chevronForwardOutline, 
      swapHorizontalOutline, 
      sunny, 
      sunnyOutline, 
      moon, 
      moonOutline, 
      personOutline, 
      chatbubbleEllipses, 
      chatbubbleEllipsesOutline, 
      chevronDownOutline, 
      barcodeOutline, 
      locationOutline, 
      calendarOutline, 
      carOutline, 
      barChartOutline, 
      informationCircleOutline, 
      sparkles, 
      sparklesOutline, 
      refresh, 
      refreshOutline, 
      alarmOutline, 
      chatbubbleOutline,
      appsOutline
    });
  }

  onTestClick(source: string) {
    // Interaction active: ${source}
  }




  ngOnInit() {
    // Check for login success flag
    const showLoginSuccess = sessionStorage.getItem('login_success');
    if (showLoginSuccess) {
      this.toastService.show('Connexion réussie ! Bienvenue sur OTFLOW.', 'success');
      sessionStorage.removeItem('login_success');
    }

    // Subscribe to notification count
    this.notificationService.unreadCount$.subscribe(count => {
      this.unreadNotifsCount = count;
    });
  }

  ionViewWillEnter() {
    this.loadAllData();
    this.loadPendingRappels();
    this.notificationService.loadUnreadCount();
  }

  loadPendingRappels() {
    const raw = localStorage.getItem('rappels');
    if (raw) {
      const all = JSON.parse(raw);
      this.pendingRappelsCount = all.filter((r: any) => !r.fait).length;
    } else {
      this.pendingRappelsCount = 0;
    }
  }

  toggleTheme() {
    this.themeService.toggleTheme();
  }

  loadAllData() {
    this.isLoading = true;

    // Simulate loading delay for skeleton effect (1s)
    setTimeout(() => {
      this.loadUserProfile();
      this.loadMyStats();
      this.loadMesDemandesRecentes();
      this.loadMesDrafts();
      this.loadMesLivraisonsActives();
      this.loadClientCount();
    }, 100);

    // 🛡️ Safety fallback: Force isLoading to false after 10s if API hangs
    setTimeout(() => {
      if (this.isLoading) {
        console.warn('⚠️ Loading safety fallback triggered. Check network connectivity.');
        this.isLoading = false;
      }
    }, 10000);
  }

  loadUserProfile() {
    this.authService.getProfile().subscribe({
      next: (res: any) => {
        this.user = res;
        console.log('✅ User profile loaded:', this.user);
      },
      error: (err) => {
        console.error('❌ Error loading user:', err);
        if (err.status === 401 || err.status === 403) {
          this.navCtrl.navigateRoot(['/login']);
        }
      }
    });
  }

  loadMyStats() {
    const headers = this.getAuthHeaders();
    this.http.get(`${environment.apiUrl}/v1/client/dashboard/stats`, { headers }).subscribe({
      next: (res: any) => {
        this.stats = res;
        this.updateSectionCounts();

        // Data loading finished
        this.isLoading = false;
        console.log('✅ Stats loaded:', this.stats);
      },
      error: (err) => {
        console.error('❌ Error loading stats:', err);
        this.isLoading = false;
      }
    });
  }

  updateSectionCounts() {
    // Update counts in the main sections array
    const ordersSection = this.mainSections.find(s => s.id === 'orders');
    if (ordersSection) ordersSection.count = this.stats.mesDemandesEnCours + this.stats.mesDemandesEnAttente;

    const trackingSection = this.mainSections.find(s => s.id === 'tracking');
    if (trackingSection) trackingSection.count = this.stats.mesLivraisonsEnCours;
  }

  loadMesDemandesRecentes() {
    const headers = this.getAuthHeaders();
    this.http.get(`${environment.apiUrl}/v1/client/dashboard/mes-demandes/recentes`, { headers }).subscribe({
      next: (res: any) => {
        const data = Array.isArray(res) ? res : (res.content || []);
        this.mesDemandesRecentes = data.filter((d: any) => d.statut !== 'NON_CONFIRME');
      },
      error: (err) => console.error('❌ Error loading demandes:', err)
    });
  }

  loadMesDrafts() {
    const headers = this.getAuthHeaders();
    // Fetch all ordres for the user and filter for NON_CONFIRME
    this.http.get(`${environment.apiUrl}/v1/ordres`, { headers }).subscribe({
      next: (res: any) => {
        // If it's a paged response, handle content
        const data = res.content || res;
        this.mesDrafts = data.filter((d: any) => d.statut === 'NON_CONFIRME');
        console.log('✅ Drafts loaded:', this.mesDrafts.length);
      },
      error: (err) => console.error('❌ Error loading drafts:', err)
    });
  }

  loadMesLivraisonsActives() {
    const headers = this.getAuthHeaders();
    this.http.get(`${environment.apiUrl}/v1/client/dashboard/mes-livraisons/actives`, { headers }).subscribe({
      next: (res: any) => {
        this.mesLivraisonsActives = Array.isArray(res) ? res : (res.content || []);
      },
      error: (err) => console.error('❌ Error loading livraisons:', err)
    });
  }

  loadClientCount() {
    this.clientService.getAll().subscribe({
      next: (res: any) => {
        this.clientCount = res.length;
        const clientSection = this.mainSections.find(s => s.id === 'clients');
        if (clientSection) clientSection.count = this.clientCount;
      },
      error: (err) => console.error('❌ Error loading client count:', err)
    });
  }

  refreshData(event: any) {
    this.loadUserProfile();
    this.loadMyStats();
    this.loadMesDemandesRecentes();
    this.loadMesLivraisonsActives();

    setTimeout(() => {
      event.target.complete();
    }, 1500);
  }

  navigateTo(route: string) {
    const [path, queryString] = route.split('?');
    const queryParams: any = {};
    if (queryString) {
      queryString.split('&').forEach(param => {
        const [key, value] = param.split('=');
        queryParams[key] = value;
      });
    }
    this.router.navigate([path], { queryParams });
  }

  viewDemandeDetails(demande: any) {
    this.navCtrl.navigateForward(['/demandes/details'], { queryParams: { id: demande.id } });
  }

  trackLivraison(livraison: any) {
    this.navCtrl.navigateForward(['/livraisons/tracking'], { queryParams: { id: livraison.id } });
  }

  goToNotifications() {
    this.navCtrl.navigateForward(['/notifications']);
  }

  goToProfile() {
    this.navCtrl.navigateForward(['/profile']);
  }

  goToRappels() {
    this.navCtrl.navigateForward('/rappel');
  }

  createNewDemande() {
    this.navCtrl.navigateForward(['/demandes/create']);
  }

  logout() {
    this.authService.logout();
    this.navCtrl.navigateRoot(['/login']);
  }

  formatDate(date: string | Date): string {
    if (!date) return '';
    const d = new Date(date);
    return d.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
  }

  getCurrentDate(): string {
    return new Date().toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    });
  }

  // Get username from email (part before @) or firstname/lastname fallback
  getUsername(): string {
    if (this.user?.firstname && this.user?.lastname) {
      return `${this.user.firstname} ${this.user.lastname}`;
    }
    if (this.user?.firstname) {
      return this.user.firstname;
    }
    if (this.user?.email) {
      return this.user.email.split('@')[0];
    }
    return '';
  }

  getUserInitials(): string {
    const username = this.getUsername();
    if (username) return username.charAt(0).toUpperCase();
    return 'U';
  }

  getStatusClass(statut: string): string {
    return statut || 'EN_ATTENTE';
  }

  getStatusLabel(statut: string): string {
    const labels: any = {
      'EN_ATTENTE': 'En attente',
      'PLANIFIE': 'Validé',
      'EN_COURS_DE_LIVRAISON': 'En cours',
      'LIVRE': 'Livrée',
      'CHARGE': 'Chargé',
      'NON_LIVRE': 'Non livrée',
      'ANNULEE': 'Annulée'
    };
    return labels[statut] || statut;
  }

  trackByDemande(index: number, item: any): number {
    return item.id;
  }

  trackByLivraison(index: number, item: any): number {
    return item.id;
  }

  private getAuthHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }
}

