import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgbModule, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';
import { ClientService } from '../client.service';
import { NotificationService } from '../notification.service';
import { AuthService } from '../auth.service';
import { ExportService } from '../export.service';

@Component({
  selector: 'app-client',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    NgbModule
  ],
  templateUrl: './client.component.html',
  styleUrls: ['./client.component.css']
})
export class ClientComponent implements OnInit, OnDestroy {

  client = {
    code: 0,
    codeclient: "",
    civilite: "",
    type: "",
    statut: "",
    sType: "",
    confiere: false,
    societeFacturation: "",
    siteExploitation: "",
    service: "",
    nom: "",
    adresse: "",
    codepostal: 0,
    ville: "",
    pays: "",
    client: "",
    siret: "",
    idEdi: 0,
    idTva: 0,
    codeIso: 0,
    contact: "",
    numeroPortable: "",
    telephone: "",
    fax: "",
    email: ""
  };

  user = {
    id: 0,
    firstname: "",
    lastname: "",
    email: "",
    password: "",
    role: ""
  };

  filteredClients: any[] = [];
  searchName: string = '';
  searchCode: string = '';
  clients: any[] = [];
  Detail = true;
  activeTab: 'all' | 'pending' = 'all'; // Default to all clients
  isEditMode: boolean = false;
  private refreshInterval: any;

  constructor(
    private modalService: NgbModal,
    private service: ClientService,
    private ser: NotificationService,
    private authService: AuthService,
    private exportService: ExportService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit(): void {
    this.afficher();
    this.profile();
    
    this.refreshInterval = setInterval(() => {
      this.afficher();
    }, 30000);
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
    }
  }

  open(content: any) {
    this.modalService.open(content);
  }

  afficher() {
    this.service.afficher().subscribe(clients => {
      this.clients = clients;
      this.filterClients();
      this.cdr.detectChanges();
    });
  }

  ajouter() {
    this.service.ajouter(this.client).subscribe((res) => {
      this.ser.showSuccess(`Création du client : ${this.client.nom}`);
    });
  }

  async supprimer(id: number) {
    const confirmed = await this.ser.confirm('Suppression', 'Voulez-vous vraiment supprimer ce client ?', 'Supprimer');
    if (confirmed) {
      this.service.supprimer(id).subscribe(
        (res) => {
          this.ser.showSuccess("Client supprimé avec succès");
          this.afficher();
        },
        err => this.ser.showError("Erreur lors de la suppression")
      );
    }
  }

  creer() {
    this.client = {
      code: 0,
      codeclient: "",
      civilite: "",
      type: "",
      statut: "",
      sType: "",
      confiere: false,
      societeFacturation: "",
      siteExploitation: "",
      service: "",
      nom: "",
      adresse: "",
      codepostal: 0,
      ville: "",
      pays: "",
      client: "",
      siret: "",
      idEdi: 0,
      idTva: 0,
      codeIso: 0,
      contact: "",
      numeroPortable: "",
      telephone: "",
      fax: "",
      email: ""
    };
    this.Detail = true;
    this.isEditMode = false;
  }

  detail(c: any) {
    this.client = c;
    this.Detail = false;
  }

  editer(c: any) {
    this.client = c;
    this.Detail = true;
    this.isEditMode = true;
  }

  filterClients(): void {
    this.filteredClients = this.clients.filter(client => {
      const matchesSearch = (this.searchName ? client.nom.toLowerCase().includes(this.searchName.toLowerCase()) : true) &&
        (this.searchCode ? (client.codeclient || '').toLowerCase().includes(this.searchCode.toLowerCase()) : true);

      if (this.activeTab === 'pending') {
        return matchesSearch && !client.profileCompleted;
      }
      // "All" tab now only shows COMPLETED clients, per user request to separate pending ones
      return matchesSearch && client.profileCompleted;
    });
  }

  setTab(tab: 'all' | 'pending'): void {
    this.activeTab = tab;
    this.filterClients();
  }

  getPendingCount(): number {
    return this.clients.filter(c => !c.profileCompleted).length;
  }

  saveClient() {
    if (this.isEditMode) {
      if (!this.client.code) {
        console.error("No client code to update!");
        return;
      }
      this.service.modifier(this.client.code, this.client).subscribe(
        res => {
          this.ser.showSuccess("Client mis à jour : " + this.client.nom);
          this.afficher();
          this.modalService.dismissAll();
        },
        err => this.ser.showError('Erreur lors de la mise à jour')
      );
    } else {
      this.ajouter();
      this.modalService.dismissAll();
    }
  }

  ngOnChanges(): void {
    this.filterClients();
  }

  profile() {
    this.authService.profile().subscribe(
      (data) => {
        this.user = data;
        this.cdr.detectChanges();
      },
      (error) => console.error('Erreur lors du chargement du profil', error)
    );
  }

  exportToExcel() {
    const columns = [
      { header: 'Code', key: 'code' },
      { header: 'Code Client', key: 'codeclient' },
      { header: 'Nom', key: 'nom' },
      { header: 'Type', key: 'type' },
      { header: 'Email', key: 'email' },
      { header: 'Ville', key: 'ville' },
      { header: 'Pays', key: 'pays' }
    ];

    const data = this.filteredClients.map(c => ({
      code: c.code,
      codeclient: c.codeclient,
      nom: c.nom,
      type: c.type,
      email: c.email,
      ville: c.ville,
      pays: c.pays
    }));

    this.exportService.exportExcel(data, 'Liste des Clients', 'Clients_Export', columns);
  }

  openReport() {
    this.router.navigate(['/material/report'], {
      state: {
        title: 'Liste des Clients',
        columns: [
          { key: 'code', label: 'Code' },
          { key: 'codeclient', label: 'Code Client' },
          { key: 'nom', label: 'Nom' },
          { key: 'type', label: 'Type' },
          { key: 'email', label: 'Email' },
          { key: 'ville', label: 'Ville' },
          { key: 'pays', label: 'Pays' }
        ],
        data: this.filteredClients
      }
    });
  }

}



