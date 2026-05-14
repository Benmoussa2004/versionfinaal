import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders, HttpClientModule } from '@angular/common/http';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css'
})
export class ProfileComponent implements OnInit {

  user: any = {
    id: null,
    firstname: '',
    lastname: '',
    email: '',
    role: '',
    profileImageBase64: null
  };

  // Password form
  passwordForm = {
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  };

  // UI states
  activeTab: 'info' | 'password' = 'info';
  isLoading = false;
  isSavingInfo = false;
  isSavingPassword = false;
  successMessage = '';
  errorMessage = '';
  imagePreview: string | null = null;

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.loadProfile();
  }

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders(token ? { 'Authorization': `Bearer ${token}` } : {});
  }

  loadProfile(): void {
    this.isLoading = true;
    // /api/v1/auth/profile returns the current authenticated user including its id
    this.http.get<any>(`${environment.authUrl}/profile`, { headers: this.getHeaders() }).subscribe({
      next: (data) => {
        this.user = { ...data };
        this.imagePreview = data.profileImageBase64 || null;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isLoading = false;
      }
    });
  }

  onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];

    // Resize and convert to base64
    const reader = new FileReader();
    reader.onload = (e: any) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        const MAX = 200;
        let { width, height } = img;
        if (width > height) {
          if (width > MAX) { height = (height * MAX) / width; width = MAX; }
        } else {
          if (height > MAX) { width = (width * MAX) / height; height = MAX; }
        }
        canvas.width = width;
        canvas.height = height;
        canvas.getContext('2d')!.drawImage(img, 0, 0, width, height);
        const base64 = canvas.toDataURL('image/jpeg', 0.85);
        this.imagePreview = base64;
        this.user.profileImageBase64 = base64;
      };
      img.src = e.target.result;
    };
    reader.readAsDataURL(file);
  }

  saveProfileInfo(): void {
    if (!this.user.id) return;
    this.isSavingInfo = true;
    this.clearMessages();

    const body = {
      firstname: this.user.firstname,
      lastname: this.user.lastname,
      profileImageBase64: this.user.profileImageBase64
    };

    this.http.put<any>(`${environment.apiUrl}/users/${this.user.id}/profile`, body, { headers: this.getHeaders() }).subscribe({
      next: (updated) => {
        this.user = { ...updated };
        this.imagePreview = updated.profileImageBase64 || this.imagePreview;
        this.isSavingInfo = false;
        this.showSuccess('Profil mis à jour avec succès !');
      },
      error: () => {
        this.isSavingInfo = false;
        this.showError('Erreur lors de la mise à jour du profil.');
      }
    });
  }

  savePassword(): void {
    if (!this.user.id) return;
    this.clearMessages();

    if (this.passwordForm.newPassword !== this.passwordForm.confirmPassword) {
      this.showError('Les mots de passe ne correspondent pas.');
      return;
    }
    if (this.passwordForm.newPassword.length < 6) {
      this.showError('Le nouveau mot de passe doit comporter au moins 6 caractères.');
      return;
    }

    this.isSavingPassword = true;
    const body = {
      currentPassword: this.passwordForm.currentPassword,
      newPassword: this.passwordForm.newPassword
    };

    this.http.put<any>(`${environment.apiUrl}/users/${this.user.id}/password`, body, { headers: this.getHeaders() }).subscribe({
      next: () => {
        this.isSavingPassword = false;
        this.passwordForm = { currentPassword: '', newPassword: '', confirmPassword: '' };
        this.showSuccess('Mot de passe modifié avec succès !');
      },
      error: (err) => {
        this.isSavingPassword = false;
        this.showError(err?.error?.error || 'Ancien mot de passe incorrect.');
      }
    });
  }

  getRoleLabel(role: string): string {
    const map: any = {
      ADMIN: 'Administrateur',
      SUPERADMIN: 'Super Administrateur',
      COMMERCIAL: 'Commercial',
      CLIENT: 'Client',
      LIVREUR: 'Livreur',
    };
    return map[role] || role;
  }

  getRoleIcon(role: string): string {
    const map: any = {
      ADMIN: 'fas fa-shield-alt',
      SUPERADMIN: 'fas fa-crown',
      COMMERCIAL: 'fas fa-briefcase',
      CLIENT: 'fas fa-user',
      LIVREUR: 'fas fa-truck',
    };
    return map[role] || 'fas fa-user';
  }

  getUserInitials(): string {
    const f = this.user.firstname?.charAt(0) || '';
    const l = this.user.lastname?.charAt(0) || '';
    return (f + l).toUpperCase() || 'U';
  }

  private showSuccess(msg: string): void {
    this.successMessage = msg;
    setTimeout(() => this.successMessage = '', 4000);
  }

  private showError(msg: string): void {
    this.errorMessage = msg;
    setTimeout(() => this.errorMessage = '', 5000);
  }

  private clearMessages(): void {
    this.successMessage = '';
    this.errorMessage = '';
  }
}



