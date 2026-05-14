import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';
import { NotificationService } from '../notification.service';

@Component({
    selector: 'app-register',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        RouterModule
    ],
    templateUrl: './register.component.html',
    styleUrls: ['./register.component.css']
})
export class RegisterComponent {
    user = {
        firstname: '',
        lastname: '',
        email: '',
        password: '',
        role: 'CLIENT',
        civilite: '',
        telephone: '',
        adresse: '',
        ville: '',
        pays: '',
        codepostal: null as any,
        type: 'Standard',
        societeFacturation: ''
    };
    error: string = '';
    success: boolean = false;

    constructor(private authService: AuthService, private router: Router, private notificationService: NotificationService) { }

    onSubmit(): void {
        this.authService.register(this.user)
            .subscribe(
                response => {
                    this.notificationService.showSuccess('Inscription réussie ! Votre compte est en attente d\'approbation.');
                    this.success = true;
                },
                error => {
                    this.notificationService.showError('Échec de l\'inscription');
                    if (error.error && error.error.message) {
                        this.error = error.error.message;
                    } else {
                        this.error = 'Registration failed. Please try again.';
                    }
                    this.success = false;
                }
            );
    }
}



