import { Component } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { StatusBar } from '@capacitor/status-bar';
import { CommonModule } from '@angular/common';
import { IonApp, IonRouterOutlet, NavController, IonIcon, IonFab, IonFabButton } from '@ionic/angular/standalone';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { addIcons } from 'ionicons';
import { ThemeService } from './services/theme.service';
import { LoadingService } from './services/loading.service';
import { NotificationService } from './services/notification.service';
import { AuthService } from './services/auth.service';
import {
  add,
  addCircleOutline,
  addOutline,
  alertCircleOutline,
  arrowBackOutline,
  arrowDown,
  arrowForwardOutline,
  bulb,
  calendarOutline,
  carOutline,
  cartOutline,
  chatbubbleEllipsesOutline,
  checkmarkCircle,
  checkmarkCircleOutline,
  checkmarkDoneOutline,
  checkmarkOutline,
  chevronForward,
  close,
  closeOutline,
  cloudUploadOutline,
  copyOutline,
  createOutline,
  cubeOutline,
  documentTextOutline,
  downloadOutline,
  eyeOffOutline,
  eyeOutline,
  flashOutline,
  homeOutline,
  hourglassOutline,
  informationCircleOutline,
  keyOutline,
  listOutline,
  locate,
  locationOutline,
  lockClosedOutline,
  logOutOutline,
  mailOutline,
  map,
  mapOutline,
  moon,
  navigateOutline,
  notificationsOffOutline,
  notificationsOutline,
  peopleOutline,
  person,
  personAddOutline,
  personCircleOutline,
  personOutline,
  printOutline,
  refreshOutline,
  searchOutline,
  send,
  shieldCheckmarkOutline,
  sunny,
  timeOutline,
  trashOutline,
  shareOutline,
  pricetagOutline,
  barbellOutline,
  layersOutline,
  businessOutline,
  chevronDownOutline,
  documentText,
  pencilOutline,
  swapHorizontalOutline,
  chatbubbleEllipses,
  warningOutline,
  paperPlaneOutline
} from 'ionicons/icons';

// Routes where the bottom nav should be visible
const TAB_ROUTES = ['/home', '/demandes', '/livraisons', '/clients', '/profile', '/demandes/create'];

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
  standalone: true,
  imports: [CommonModule, IonApp, IonRouterOutlet, IonIcon, IonFab, IonFabButton],
})
export class AppComponent {

  showNav = false;
  showAddMenu = false;
  currentUrl = '';

  constructor(
    private themeService: ThemeService,
    private router: Router,
    private navCtrl: NavController,
    private loadingService: LoadingService,
    private notificationService: NotificationService,
    private authService: AuthService
  ) {
    addIcons({
      'add': add,
      'add-circle-outline': addCircleOutline,
      'add-outline': addOutline,
      'alert-circle-outline': alertCircleOutline,
      'arrow-back-outline': arrowBackOutline,
      'arrow-down': arrowDown,
      'arrow-forward-outline': arrowForwardOutline,
      'bulb': bulb,
      'calendar-outline': calendarOutline,
      'car-outline': carOutline,
      'cart-outline': cartOutline,
      'chatbubble-ellipses-outline': chatbubbleEllipsesOutline,
      'checkmark-circle': checkmarkCircle,
      'checkmark-circle-outline': checkmarkCircleOutline,
      'checkmark-done-outline': checkmarkDoneOutline,
      'checkmark-outline': checkmarkOutline,
      'chevron-forward': chevronForward,
      'close': close,
      'close-outline': closeOutline,
      'cloud-upload-outline': cloudUploadOutline,
      'copy-outline': copyOutline,
      'create-outline': createOutline,
      'cube-outline': cubeOutline,
      'document-text-outline': documentTextOutline,
      'download-outline': downloadOutline,
      'eye-off-outline': eyeOffOutline,
      'eye-outline': eyeOutline,
      'flash-outline': flashOutline,
      'home-outline': homeOutline,
      'hourglass-outline': hourglassOutline,
      'information-circle-outline': informationCircleOutline,
      'key-outline': keyOutline,
      'list-outline': listOutline,
      'locate': locate,
      'location-outline': locationOutline,
      'lock-closed-outline': lockClosedOutline,
      'log-out-outline': logOutOutline,
      'mail-outline': mailOutline,
      'map': map,
      'map-outline': mapOutline,
      'moon': moon,
      'navigate-outline': navigateOutline,
      'notifications-off-outline': notificationsOffOutline,
      'notifications-outline': notificationsOutline,
      'people-outline': peopleOutline,
      'person': person,
      'person-add-outline': personAddOutline,
      'person-circle-outline': personCircleOutline,
      'person-outline': personOutline,
      'print-outline': printOutline,
      'refresh-outline': refreshOutline,
      'search-outline': searchOutline,
      'send': send,
      'shield-checkmark-outline': shieldCheckmarkOutline,
      'sunny': sunny,
      'time-outline': timeOutline,
      'trash-outline': trashOutline,
      'warning-outline': warningOutline,
      'share-outline': shareOutline,
      'pricetag-outline': pricetagOutline,
      'barbell-outline': barbellOutline,
      'layers-outline': layersOutline,
      'business-outline': businessOutline,
      'chevron-down-outline': chevronDownOutline,
      'document-text': documentText,
      'pencil-outline': pencilOutline,
      'swap-horizontal-outline': swapHorizontalOutline,
      'chatbubble-ellipses': chatbubbleEllipses,
      'create-pro': createOutline,
      'trash-pro': trashOutline,
      'checkmark-pro': checkmarkOutline,
      'copy-pro': copyOutline,
      'add-pro': addOutline,
      'document-text-pro': documentTextOutline,
      'person-pro': personOutline,
      'arrow-forward-pro': arrowForwardOutline,
      'search-pro': searchOutline,
      'calendar-pro': calendarOutline,
      'paper-plane-outline': paperPlaneOutline
    });

    // Hide system status bar (time, wifi, etc.) only on native device
    if (Capacitor.isNativePlatform()) {
      StatusBar.hide();
    }

    // Track current route to show/hide nav
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e: any) => {
        this.currentUrl = e.urlAfterRedirects || e.url;
        this.showNav = TAB_ROUTES.some(r => this.currentUrl.startsWith(r));

        // 🛡️ Focus Management: Resolve "aria-hidden focus" blocker
        // Explicitly blur the current focused element to prevent it from being "trapped" in a hidden page
        if (document.activeElement instanceof HTMLElement) {
          document.activeElement.blur();
        }

        setTimeout(() => {
          const activePage = document.querySelector('ion-router-outlet > .ion-page:not(.ion-page-hidden)');
          if (activePage) {
            activePage.removeAttribute('aria-hidden');
            (activePage as HTMLElement).focus();
          }
        }, 100);
      });

    this.loadingService.hide();

    // ✅ Initialiser les notifications si déjà connecté au démarrage
    if (this.authService.isLoggedIn()) {
      this.notificationService.initAfterLogin();
    }
  }

  isActive(tab: string): boolean {
    if (tab === 'home') return this.currentUrl === '/home' || this.currentUrl === '/';
    return this.currentUrl.startsWith('/' + tab);
  }

  goTo(path: string) {
    const route = path.startsWith('/') ? path : '/' + path;
    const [url, queryString] = route.split('?');
    const queryParams: any = {};

    if (queryString) {
      queryString.split('&').forEach(param => {
        const [key, value] = param.split('=');
        queryParams[key] = value;
      });
    }

    // Use Angular Router for smoother transitions especially with queryParams
    this.router.navigate([url], { queryParams });
  }

  toggleAddMenu() {
    this.showAddMenu = !this.showAddMenu;
  }
}

