import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PermissionService } from '../permission.service';
import { UsersService } from '../users.service';
import { NotificationService } from '../notification.service';

interface Action {
    key: string;
    label: string;
}

interface Module {
    key: string;
    label: string;
    icon: string;
    actions: Action[];
}

@Component({
    selector: 'app-permission-management',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './permission-management.component.html',
    styleUrls: ['./permission-management.component.css']
})
export class PermissionManagementComponent implements OnInit {
    roles = ['ADMIN', 'COMMERCIAL', 'CLIENT', 'EMPLOYER_LUMIERE'];
    users: any[] = [];
    configMode: 'ROLE' | 'USER' = 'ROLE';
    selectedRole: string = 'ADMIN';
    selectedUserId: number | null = null;
    searchTerm: string = '';
    permissions: any[] = [];
    isLoading = false;

    get filteredUsers() {
        if (!this.searchTerm) {
            return this.users;
        }
        const term = this.searchTerm.toLowerCase();
        return this.users.filter(user =>
            (user.firstname + ' ' + user.lastname).toLowerCase().includes(term) ||
            user.role.toLowerCase().includes(term)
        );
    }

    modules: Module[] = [
        {
            key: 'DASHBOARD',
            label: 'Tableau de Bord',
            icon: 'fas fa-chart-line',
            actions: []
        },
        {
            key: 'ORDRES',
            label: 'Gestion des Ordres',
            icon: 'fas fa-clipboard-list',
            actions: [
                { key: 'ORDRES_ADD', label: 'Ajouter des ordres' },
                { key: 'ORDRES_EDIT', label: 'Modifier les ordres' },
                { key: 'ORDRES_DELETE', label: 'Supprimer les ordres' },
                { key: 'ORDRES_VALIDATE', label: 'Valider (Ordres non confirmés)' },
                { key: 'ORDRES_TRACK', label: 'Suivre (Suivi en temps réel)' }
            ]
        },
        {
            key: 'CLIENTS',
            label: 'Gestion des Clients',
            icon: 'fas fa-user-friends',
            actions: [
                { key: 'CLIENTS_ADD', label: 'Ajouter un client' },
                { key: 'CLIENTS_EDIT', label: 'Modifier un client' },
                { key: 'CLIENTS_DELETE', label: 'Supprimer un client' }
            ]
        },
        {
            key: 'ARTICLES',
            label: 'Gestion des Articles',
            icon: 'fas fa-box',
            actions: [
                { key: 'ARTICLES_ADD', label: 'Ajouter un article' },
                { key: 'ARTICLES_EDIT', label: 'Modifier un article' },
                { key: 'ARTICLES_DELETE', label: 'Supprimer un article' }
            ]
        },
        {
            key: 'USERS',
            label: 'Gestion des Utilisateurs',
            icon: 'fas fa-users-cog',
            actions: [
                { key: 'USERS_ADD', label: 'Ajouter un utilisateur' },
                { key: 'USERS_EDIT', label: 'Modifier un utilisateur' },
                { key: 'USERS_DELETE', label: 'Supprimer un utilisateur' }
            ]
        },
        {
            key: 'NOTIFICATIONS',
            label: 'Gestion des Notifications',
            icon: 'fas fa-bell',
            actions: [
                { key: 'NOTIF_INSCRIPTION', label: 'Notifications des Nouvelles Inscriptions' },
                { key: 'NOTIF_ORDRE_NEW', label: 'Notifications de Création d\'Ordre' },
                { key: 'NOTIF_ORDRE_CONFIRMED', label: 'Notifications de Confirmation d\'Ordre' },
                { key: 'NOTIF_ORDRE_UPDATE', label: 'Notifications de Mise à jour d\'Ordre' }
            ]
        }
    ];

    expandedModule: string | null = 'ORDRES';

    constructor(
        private permissionService: PermissionService,
        private userService: UsersService,
        private cdr: ChangeDetectorRef,
        private notificationService: NotificationService
    ) { }

    ngOnInit(): void {
        this.loadInitialData();
    }

    loadInitialData(): void {
        this.isLoading = true;
        this.permissionService.getAllPermissions().subscribe({
            next: (data) => {
                this.permissions = data;
                this.isLoading = false;
                this.cdr.detectChanges();
            },
            error: (err) => {
                console.error('Failed to load permissions', err);
                this.isLoading = false;
            }
        });

        this.userService.getClients().subscribe({
            next: (data) => {
                this.users = data;
                this.cdr.detectChanges();
            },
            error: (err) => console.error('Failed to load users', err)
        });
    }

    onModeChange(): void {
        this.permissions = [];
        if (this.configMode === 'ROLE') {
            this.loadPermissionsByRole();
        } else if (this.selectedUserId) {
            this.loadPermissionsByUser();
        }
    }

    loadPermissionsByRole(): void {
        this.isLoading = true;
        this.permissionService.getAllPermissions().subscribe({
            next: (data) => {
                this.permissions = data;
                this.isLoading = false;
                this.cdr.detectChanges();
            },
            error: (err) => {
                console.error('Failed to load permissions', err);
                this.isLoading = false;
            }
        });
    }

    loadPermissionsByUser(): void {
        if (!this.selectedUserId) return;
        this.isLoading = true;
        this.permissionService.getPermissionsByUser(this.selectedUserId).subscribe({
            next: (data) => {
                const featureKeys = this.getAllFeatureKeys();
                this.permissions = featureKeys.map(key => ({
                    userId: this.selectedUserId,
                    featureKey: key,
                    enabled: data[key] || false
                }));
                this.isLoading = false;
                this.cdr.detectChanges();
            },
            error: (err) => {
                console.error('Failed to load user permissions', err);
                this.isLoading = false;
            }
        });
    }

    private getAllFeatureKeys(): string[] {
        const keys: string[] = [];
        this.modules.forEach(m => {
            keys.push(m.key);
            m.actions.forEach(a => keys.push(a.key));
        });
        return keys;
    }

    toggleModule(key: string) {
        this.expandedModule = this.expandedModule === key ? null : key;
    }

    toggleAllInModule(mod: Module) {
        const modPerm = this.getPermission(mod.key);
        if (!modPerm) return;

        const newState = !modPerm.enabled;
        modPerm.enabled = newState;

        mod.actions.forEach(action => {
            const actionPerm = this.getPermission(action.key);
            if (actionPerm) {
                actionPerm.enabled = newState;
            }
        });
    }

    getPermission(key: string): any {
        if (this.configMode === 'ROLE') {
            return this.permissions.find(p => p.role === this.selectedRole && p.featureKey === key);
        } else {
            return this.permissions.find(p => p.featureKey === key);
        }
    }

    savePermissions(): void {
        this.isLoading = true;
        const saveObs = this.configMode === 'ROLE'
            ? this.permissionService.updatePermissions(this.permissions)
            : this.permissionService.updateUserPermissions(this.selectedUserId!, this.permissions);

        saveObs.subscribe({
            next: () => {
                this.notificationService.showSuccess('Configuration sauvegardée avec succès !');
                this.isLoading = false;
            },
            error: (err) => {
                this.notificationService.showError('Erreur lors de la sauvegarde');
                this.isLoading = false;
            }
        });
    }
}



