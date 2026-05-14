export interface User {
    id: number;
    firstname: string;
    lastname: string;
    email: string;
    password?: string;
    role: 'CLIENT' | 'COMMERCIAL' | 'ADMIN' | 'EMPLOYER_LUMIERE';
    status: 'PENDING' | 'ACTIVE' | 'REJECTED';
}



