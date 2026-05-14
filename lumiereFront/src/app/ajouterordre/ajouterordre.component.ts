import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrdreService } from '../ordre.service';
import { NotificationService } from '../notification.service';

@Component({
  selector: 'app-ajouterordre',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './ajouterordre.component.html',
  styleUrl: './ajouterordre.component.css'
})
export class AjouterordreComponent implements OnInit {


  constructor(
    private service: OrdreService, 
    private notificationService: NotificationService,
    private cdr: ChangeDetectorRef
  ) { };


  ordre = {
    id: 0,
    orderNumber: "",
    matricule: "",
    client: '',
    nomclient: '',
    siteclient: '',
    idedi: '',
    codeclientcharg: "",
    chargementNom: "",
    chargementAdr1: "",
    chargementAdr2: "",
    chargementVille: "",
    chargementDate: "",
    codeclientliv: "",
    livraisonNom: "",
    livraisonAdr1: "",
    livraisonAdr2: "",
    codepostalliv: "",
    livraisonVille: "",
    livraisonDate: "",
    codeArticle: "",
    designation: "",
    poids: 0.0,
    volume: 0.0,
    nombrePalettes: 0,
    nombreColis: 0,
    longueur: 0.0,
    dateSaisie: "",
    statut: "NON_CONFIRME",
    commentaires: []
  }

  ordres: any[] = [];
  ordrenconf: any[] = [];

  // Duplication modal
  isDuplicateModalOpen = false;
  duplicateCount = 1;
  ordreToDuplicate: any = null;

  // Multi-select
  allSelected: boolean = false;
  selectedCount: number = 0;

  ngOnInit(): void {
    this.afficher();
    console.log(this.ordres)
  }

  // Méthode pour afficher ou masquer le formulaire d'ajout de tâche

  afficher() {
    this.service.afficher().subscribe(ordres => {
      this.ordres = ordres;
      this.ordrenconf = [];
      for (let i of ordres) {
        if (i.statut == 'NON_CONFIRME') {
          // Initialize selected property
          this.ordrenconf.push({ ...i, selected: false });
        }
      }
      this.updateSelectionCount();
      this.cdr.detectChanges();
    });
  }
  ajouter() {
    this.service.ajouter(this.ordre).subscribe((res) => {
      console.log(res);

    });
  }
  isModalOpen = false;



  consulter(i: any) {

    this.ordre = i;
    this.openModal();
  }
  openModal() {
    this.isModalOpen = true;

  }

  closeModal(event?: Event) {
    if (event) {
      event.stopPropagation();
    }
    this.isModalOpen = false;
  }

  onSubmit() {
    // Handle form submission, e.g., save the ordre object

    this.ajouter();
    console.log('Ordre saved:', this.ordre);
    this.closeModal();
  }

  openDuplicateModal(ordre: any) {
    this.ordreToDuplicate = ordre;
    this.duplicateCount = 1;
    this.isDuplicateModalOpen = true;
  }

  closeDuplicateModal(event?: Event) {
    if (event) event.stopPropagation();
    this.isDuplicateModalOpen = false;
    this.ordreToDuplicate = null;
  }

  submitDuplicate() {
    if (!this.ordreToDuplicate || this.duplicateCount < 1) return;

    this.service.dupliquerMultiple(this.ordreToDuplicate.id, this.duplicateCount).subscribe(
      (copies) => {
        this.notificationService.showSuccess(`${copies.length} copies créées`);
        this.closeDuplicateModal();
        this.afficher();
        // Removed window.location.reload() to avoid flickering, afficher() should handle it
      },
      (error) => {
        this.notificationService.showError('Erreur lors de la duplication');
      }
    );
  }


  confirmer(i: any) {

    this.service.confirmer(i).subscribe((res) => {
      window.location.reload();
    });
  } supprimer(id: number): void {
    this.service.supprimer(id).subscribe(
      response => {
        console.log('Ordre supprimé avec succès!', response);
        window.location.reload();

        // Rediriger ou rafraîchir la liste après suppression
        // Remplacez cette ligne selon vos besoins
      },
      error => {
        console.error('Erreur lors de la suppression de l\'ordre', error);
      }
    );
  }

  dupliquerOrdre(ordre: any): void {
    this.openDuplicateModal(ordre);
  }

  // ═══════════════════════════════════════
  // Multi-select Actions
  // ═══════════════════════════════════════

  toggleSelectAll() {
    this.ordrenconf.forEach(o => o.selected = this.allSelected);
    this.updateSelectionCount();
  }

  updateSelectionCount() {
    this.selectedCount = this.ordrenconf.filter(o => o.selected).length;
    this.allSelected = this.ordrenconf.length > 0 && this.ordrenconf.every(o => o.selected);
  }

  getSelectedOrdres(): any[] {
    return this.ordrenconf.filter(o => o.selected);
  }

  async deleteSelected() {
    const selected = this.getSelectedOrdres();
    if (selected.length === 0) return;
    
    const confirmed = await this.notificationService.confirm(
        'Suppression multiple',
        `Supprimer ${selected.length} ordre(s) sélectionné(s) ?`,
        'Oui, supprimer'
    );
    if (!confirmed) return;

    let completed = 0;
    selected.forEach(o => {
      this.service.supprimer(o.id).subscribe(() => {
        completed++;
        if (completed === selected.length) {
          this.notificationService.showSuccess(`${selected.length} ordres supprimés`);
          this.afficher();
        }
      });
    });
  }

  async confirmerSelected() {
    const selected = this.getSelectedOrdres();
    if (selected.length === 0) return;
    
    const confirmed = await this.notificationService.confirm(
        'Validation multiple',
        `Confirmer/Valider ${selected.length} ordre(s) sélectionné(s) ?`,
        'Oui, valider'
    );
    if (!confirmed) return;

    const ids = selected.map(o => o.id);
    this.service.confirmerMultiple(ids).subscribe({
      next: () => {
        this.notificationService.showSuccess('✅ Ordres confirmés et fichier PLA généré !');
        this.afficher();
      },
      error: (err) => {
        this.notificationService.showError('❌ Erreur lors de la confirmation.');
      }
    });
  }


  exporterSelectedCsv() {
    const selected = this.getSelectedOrdres();
    if (selected.length === 0) return;

    const headers = [
      'id', 'orderNumber', 'client', 'nomclient', 'siteclient',
      'chargementNom', 'chargementVille', 'chargementDate',
      'livraisonNom', 'livraisonVille', 'livraisonDate',
      'codeArticle', 'designation', 'poids', 'volume', 'statut'
    ];

    const filename = `ordres_non_confirmes_${new Date().getTime()}.csv`;
    this.service.exportToCsv(selected, filename, headers);
  }

  deselectAll() {
    this.ordrenconf.forEach(o => o.selected = false);
    this.allSelected = false;
    this.selectedCount = 0;
  }

}



