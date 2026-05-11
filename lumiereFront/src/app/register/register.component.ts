import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

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

    constructor(private authService: AuthService, private router: Router) { }

    onSubmit(): void {
        this.authService.register(this.user)
            .subscribe(
                response => {
                    this.success = true;
                    this.error = '';
                    // Do NOT redirect immediately. Show the success message which should explain the pending status.
                },
                error => {
                    console.error('Registration failed', error);
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



