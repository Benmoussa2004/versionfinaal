import { Component, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../auth.service';
import { UsersService } from '../users.service';
import { Router } from '@angular/router';
import { ExportService } from '../export.service';
import { NotificationService } from '../notification.service';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule
  ],
  templateUrl: './users.component.html',
  styleUrl: './users.component.css'
})
export class UsersComponent {
  helloMessage: string = '';
  clients: any[] = [];
  filteredUsers: any[] = [];
  searchTerm: string = '';
  currentUser: any = { role: '' }; // Track current user's role
  isEditing: boolean = false;

  user = {
    id: 0,
    firstname: "",
    lastname: "",
    email: "",
    password: "",
    role: "CLIENT",
    civilite: '',
    telephone: '',
    adresse: '',
    ville: '',
    pays: 'Tunisie',
    codepostal: 0,
    type: 'Standard',
    societeFacturation: ''
  }

  // Approval Modal properties
  selectedUser: any = null;
  approvalData = {
    codeClient: '',
    idEdi: ''
  };

  constructor(
    private authService: AuthService,
    private router: Router,
    private exportService: ExportService,
    private cdr: ChangeDetectorRef,
    private notificationService: NotificationService
  ) { }

  ngOnInit(): void {
    this.loadCurrentUser();
    this.loadUsers();
  }

  loadCurrentUser() {
    // Get current user from localStorage or auth service
    const token = localStorage.getItem('token');
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        this.currentUser = payload;
      } catch (e) {
        console.error('Error decoding token:', e);
      }
    }
  }

  loadUsers() {
    this.authService.getAllUsers().subscribe(
      users => {
        this.clients = users;
        this.searchUsers();
        this.cdr.detectChanges();
      },
      error => {
        this.notificationService.showError('Échec de la récupération des utilisateurs');
      }
    );
  }

  approve(id: number) {
    this.authService.updateUserStatus(id, 'ACTIVE').subscribe(
      res => {
        this.notificationService.showSuccess('Utilisateur approuvé avec succès');
        this.loadUsers();
      },
      err => this.notificationService.showError('Erreur lors de l\'approbation')
    );
  }

  openApprovalModal(user: any) {
    this.selectedUser = user;
    this.approvalData = { codeClient: '', idEdi: '' };
    const modal = document.getElementById('approveClientModal');
    if (modal) modal.classList.add('show');
  }

  closeApprovalModal() {
    const modal = document.getElementById('approveClientModal');
    if (modal) modal.classList.remove('show');
    this.selectedUser = null;
  }

  onConfirmApproval() {
    if (!this.selectedUser || !this.approvalData.codeClient || !this.approvalData.idEdi) return;

    this.authService.approveClient(
      this.selectedUser.id, 
      this.approvalData.codeClient, 
      this.approvalData.idEdi
    ).subscribe(
      res => {
        this.notificationService.showSuccess('Client lié et approuvé !');
        this.closeApprovalModal();
        this.loadUsers();
      },
      err => {
        this.notificationService.showError('Erreur lors de l\'approbation. Vérifiez que le client est bien lié.');
      }
    );
  }

  reject(id: number) {
    this.authService.updateUserStatus(id, 'REJECTED').subscribe(
      res => {
        this.notificationService.showSuccess('Utilisateur rejeté');
        this.loadUsers();
      },
      err => this.notificationService.showError('Erreur lors du rejet')
    );
  }

  blockUser(id: number) {
    this.authService.updateUserStatus(id, 'REJECTED').subscribe(
      res => {
        this.notificationService.showWarning('Utilisateur bloqué');
        this.loadUsers();
      },
      err => this.notificationService.showError('Erreur lors du blocage')
    );
  }

  async deleteUser(id: number) {
    const confirmed = await this.notificationService.confirm(
      'Suppression', 
      'Êtes-vous sûr de vouloir supprimer cet utilisateur ?',
      'Oui, supprimer'
    );
    
    if (confirmed) {
      this.authService.deleteUser(id).subscribe(
        res => {
          this.notificationService.showSuccess('Utilisateur supprimé');
          this.loadUsers();
        },
        err => this.notificationService.showError('Erreur lors de la suppression')
      );
    }
  }

  editUser(user: any) {
    this.isEditing = true;
    this.user = { ...user };
    this.openModal();
  }

  addNewUser() {
    this.isEditing = false;
    this.user = {
      id: 0,
      firstname: "",
      lastname: "",
      email: "",
      password: "",
      role: "CLIENT",
      civilite: '',
      telephone: '',
      adresse: '',
      ville: '',
      pays: 'Tunisie',
      codepostal: 0,
      type: 'Standard',
      societeFacturation: ''
    };
    this.openModal();
  }

  openModal() {
    const modal = document.getElementById('addUserModal');
    if (modal) {
      modal.classList.add('show');
    }
  }

  closeModal() {
    const modal = document.getElementById('addUserModal');
    if (modal) {
      modal.classList.remove('show');
    }
  }

  onSubmit(): void {
    if (this.isEditing) {
      this.authService.updateUser(this.user.id, this.user)
        .subscribe(response => {
          this.notificationService.showSuccess('Utilisateur mis à jour avec succès');
          this.closeModal();
          this.loadUsers();
        }, error => {
          this.notificationService.showError('Erreur lors de la mise à jour');
        });
    } else {
      this.authService.register(this.user)
        .subscribe(response => {
          this.notificationService.showSuccess('Utilisateur enregistré avec succès');
          this.closeModal();
          this.loadUsers();
        }, error => {
          this.notificationService.showError('Erreur lors de l\'enregistrement');
        });
    }
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  searchUsers() {
    if (!this.clients) {
      this.filteredUsers = [];
      return;
    }
    const term = (this.searchTerm || '').toLowerCase();
    this.filteredUsers = this.clients.filter(u => {
      const first = u.firstname || '';
      const last = u.lastname || '';
      const email = u.email || '';
      const role = u.role || '';
      return (first + ' ' + last).toLowerCase().includes(term) ||
             email.toLowerCase().includes(term) ||
             role.toLowerCase().includes(term);
    });
  }

  exportToExcel() {
    const columns = [
      { header: 'ID', key: 'ID' },
      { header: 'Prénom', key: 'Prénom' },
      { header: 'Nom', key: 'Nom' },
      { header: 'Email', key: 'Email' },
      { header: 'Rôle', key: 'Rôle' },
      { header: 'Statut', key: 'Statut' }
    ];
    const data = this.filteredUsers.map(u => ({
      'ID': u.id,
      'Prénom': u.firstname,
      'Nom': u.lastname,
      'Email': u.email,
      'Rôle': u.role,
      'Statut': u.status
    }));
    this.exportService.exportExcel(data, 'Liste des Utilisateurs', 'Users_Export', columns);
  }

  openReport() {
    this.router.navigate(['/material/report'], {
      state: {
        title: 'Liste des Utilisateurs',
        columns: [
          { key: 'id', label: 'ID' },
          { key: 'firstname', label: 'Prénom' },
          { key: 'lastname', label: 'Nom' },
          { key: 'email', label: 'Email' },
          { key: 'role', label: 'Rôle' },
          { key: 'status', label: 'Statut' }
        ],
        data: this.filteredUsers
      }
    });
  }

  get currentUserRole(): string {
    return (this.currentUser && this.currentUser.role) ? this.currentUser.role.toUpperCase() : '';
  }
}



