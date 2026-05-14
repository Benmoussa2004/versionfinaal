import { Component, ViewChild, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { BreakpointObserver } from '@angular/cdk/layout';
import { NotificationService, SystemNotification } from '../notification.service';
import { AuthService } from '../auth.service';
import { PermissionService } from '../permission.service';

@Component({
  selector: 'app-material',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule
  ],
  templateUrl: './material.component.html',
  styleUrls: ['./material.component.css']
})
export class MaterialComponent implements OnInit, OnDestroy {
  title = 'material-responsive-sidenav';
  @ViewChild(MatSidenav)
  sidenav!: MatSidenav;
  isMobile = false;
  showOrdersSubMenu = false;
  isCollapsed = false;

  user: any = {};
  showNotifications = false;
  clients: SystemNotification[] = [];
  hasNewNotifications = false;
  permissions: { [key: string]: boolean } = {};

  private pollingInterval: any;

  constructor(
    private observer: BreakpointObserver,
    private service: NotificationService,
    private authService: AuthService,
    private router: Router,
    private permissionService: PermissionService,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit() {
    this.observer.observe(['(max-width: 600px)']).subscribe((screenSize: any) => {
      this.isMobile = screenSize.matches;
      if (this.isMobile) {
        this.isCollapsed = true;
        this.sidenav?.close();
      } else {
        this.isCollapsed = false;
        this.sidenav?.open();
      }
    });

    this.profile();
    this.afficher();

    // Poll every 30s for new notifications
    this.pollingInterval = setInterval(() => this.afficher(), 30000);
  }

  ngOnDestroy(): void {
    if (this.pollingInterval) clearInterval(this.pollingInterval);
  }

  toggleMenu() {
    if (this.isMobile) {
      this.sidenav.toggle();
      this.isCollapsed = !this.sidenav.opened;
    } else {
      this.isCollapsed = !this.isCollapsed;
    }
  }

  toggleSubMenu(menu: string) {
    if (menu === 'orders') {
      this.showOrdersSubMenu = !this.showOrdersSubMenu;
    }
  }

  toggleNotifications(): void {
    this.showNotifications = !this.showNotifications;
    console.log('Toggle notifications:', this.showNotifications);
    console.log('Notifications count:', this.clients?.length);
  }

  afficher() {
    this.service.afficher().subscribe({
      next: (notifs: SystemNotification[]) => {
        console.log('Notifications received:', notifs?.length);
        this.clients = (notifs || []).sort((a: SystemNotification, b: SystemNotification) =>
          new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
        );
        this.hasNewNotifications = this.clients.some(n => !n.isRead);
        this.cdr.detectChanges();
      },
      error: (err: any) => console.error('Error fetching notifications:', err)
    });
  }

  get unreadCount(): number {
    return this.clients.filter(n => !n.isRead).length;
  }

  markAllRead(): void {
    this.service.markAllAsRead().subscribe(() => {
      this.clients.forEach(n => n.isRead = true);
      this.hasNewNotifications = false;
    });
  }

  markOneRead(notif: any): void {
    if (!notif.isRead) {
      notif.isRead = true;
      this.hasNewNotifications = this.clients.some(n => !n.isRead);
      this.service.markAsRead(notif.id).subscribe();
    }
  }

  deleteNotif(notif: any, event: MouseEvent): void {
    event.stopPropagation();
    this.service.deleteNotification(notif.id).subscribe(() => {
      this.clients = this.clients.filter(n => n.id !== notif.id);
      this.hasNewNotifications = this.clients.some(n => !n.isRead);
    });
  }

  getNotifIcon(type: string): string {
    const t = (type || '').toLowerCase();
    if (t.includes('inscription') || t.includes('user')) return 'fas fa-user-plus';
    if (t.includes('ordre') || t.includes('commande'))   return 'fas fa-file-alt';
    if (t.includes('alerte') || t.includes('alert'))     return 'fas fa-exclamation-triangle';
    if (t.includes('gps') || t.includes('position'))     return 'fas fa-map-marker-alt';
    if (t.includes('livraison'))                          return 'fas fa-truck';
    if (t.includes('client'))                             return 'fas fa-handshake';
    if (t.includes('paiement') || t.includes('facture')) return 'fas fa-credit-card';
    return 'fas fa-bell';
  }

  getNotifColor(type: string): string {
    const t = (type || '').toLowerCase();
    if (t.includes('inscription')) return 'icon--blue';
    if (t.includes('alerte'))      return 'icon--red';
    if (t.includes('livraison'))   return 'icon--green';
    if (t.includes('gps'))         return 'icon--purple';
    if (t.includes('paiement'))    return 'icon--teal';
    return 'icon--orange';
  }

  timeAgo(timestamp: string): string {
    if (!timestamp) return '';
    const diff = (Date.now() - new Date(timestamp).getTime()) / 1000;
    if (diff < 60)    return 'À l\'instant';
    if (diff < 3600)  return `il y a ${Math.floor(diff / 60)} min`;
    if (diff < 86400) return `il y a ${Math.floor(diff / 3600)} h`;
    return `il y a ${Math.floor(diff / 86400)} j`;
  }

  getUserInitials(): string {
    const f = this.user?.firstname?.charAt(0) || '';
    const l = this.user?.lastname?.charAt(0) || '';
    return (f + l).toUpperCase() || 'U';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  profile() {
    this.authService.profile().subscribe({
      next: (data: any) => {
        this.user = data;
        this.loadPermissions();
      },
      error: (e: any) => console.error('Erreur profil', e)
    });
  }

  loadPermissions() {
    if (!this.user || !this.user.id) return;
    this.permissionService.getPermissionsByUser(this.user.id).subscribe({
      next: (perms: any) => { this.permissions = perms; },
      error: (err: any) => console.error('Failed to load permissions', err)
    });
  }

  hasPermission(featureKey: string): boolean {
    if (this.user.role === 'ADMIN') return true;
    return this.permissions[featureKey] === true;
  }
}



