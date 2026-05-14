import { Component, EventEmitter, OnInit, OnDestroy, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { NotificationService, SystemNotification } from '../notification.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css'
})
export class HeaderComponent implements OnInit, OnDestroy {

  @Output() menuClicked = new EventEmitter<boolean>();

  userName: string = '';
  userRole: string = '';
  userAvatar: string = '';
  showNotifications: boolean = false;
  notifications: SystemNotification[] = [];
  private pollingInterval: any;

  constructor(private notifService: NotificationService) {
    try {
      const token = localStorage.getItem('token');
      if (token) {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const firstname = payload.firstname || '';
        const lastname = payload.lastname || '';
        this.userName = firstname
          ? `${firstname} ${lastname}`.trim()
          : (payload.sub || payload.email || 'Utilisateur');
        this.userRole = payload.role || '';
        // Build initials avatar
        this.userAvatar = (firstname.charAt(0) + lastname.charAt(0)).toUpperCase() || 'U';
      }
    } catch (e) {
      this.userName = 'Admin';
      this.userAvatar = 'A';
    }
  }

  ngOnInit(): void {
    this.fetchNotifications();
    // Poll every 30 seconds for new notifications
    this.pollingInterval = setInterval(() => this.fetchNotifications(), 30000);
  }

  ngOnDestroy(): void {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
    }
  }

  fetchNotifications(): void {
    this.notifService.afficher().subscribe({
      next: (data: SystemNotification[]) => {
        this.notifications = (data || []).sort((a: SystemNotification, b: SystemNotification) =>
          new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
        );
      },
      error: () => {}
    });
  }

  get unreadCount(): number {
    return this.notifications.filter(n => !n.isRead).length;
  }

  get hasUnread(): boolean {
    return this.unreadCount > 0;
  }

  toggleNotifications(): void {
    this.showNotifications = !this.showNotifications;
  }

  markAllRead(): void {
    this.notifService.markAllAsRead().subscribe(() => {
      this.notifications = [];
    });
  }

  markOneRead(notif: any): void {
    // Instant deletion on click as requested
    this.notifService.markAsRead(notif.id).subscribe(() => {
      this.notifications = this.notifications.filter(n => n.id !== notif.id);
    });
  }

  deleteNotif(notif: any, event: MouseEvent): void {
    event.stopPropagation();
    this.notifService.deleteNotification(notif.id).subscribe(() => {
      this.notifications = this.notifications.filter(n => n.id !== notif.id);
    });
  }

  /** Returns the appropriate icon class based on notification type */
  getNotifIcon(type: string): string {
    const t = (type || '').toLowerCase();
    if (t.includes('inscription') || t.includes('user')) return 'fas fa-user-plus';
    if (t.includes('ordre') || t.includes('commande')) return 'fas fa-file-alt';
    if (t.includes('alerte') || t.includes('alert')) return 'fas fa-exclamation-triangle';
    if (t.includes('gps') || t.includes('position')) return 'fas fa-map-marker-alt';
    if (t.includes('livraison') || t.includes('delivery')) return 'fas fa-truck';
    if (t.includes('client')) return 'fas fa-handshake';
    if (t.includes('paiement') || t.includes('facture')) return 'fas fa-credit-card';
    return 'fas fa-bell';
  }

  /** Returns icon color class based on type */
  getNotifColor(type: string): string {
    const t = (type || '').toLowerCase();
    if (t.includes('inscription')) return 'notif-icon--blue';
    if (t.includes('alerte') || t.includes('alert')) return 'notif-icon--red';
    if (t.includes('livraison')) return 'notif-icon--green';
    if (t.includes('gps')) return 'notif-icon--purple';
    if (t.includes('paiement')) return 'notif-icon--teal';
    return 'notif-icon--orange';
  }

  /** Relative time like "il y a 5 min" */
  timeAgo(timestamp: string): string {
    if (!timestamp) return '';
    const diff = (Date.now() - new Date(timestamp).getTime()) / 1000;
    if (diff < 60) return 'À l\'instant';
    if (diff < 3600) return `il y a ${Math.floor(diff / 60)} min`;
    if (diff < 86400) return `il y a ${Math.floor(diff / 3600)} h`;
    return `il y a ${Math.floor(diff / 86400)} j`;
  }
}



