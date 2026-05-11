import {
  Component, OnInit, Input, OnChanges, SimpleChanges
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrdreService } from '../ordre.service';

interface CalendarDay {
  date: Date;
  isCurrentMonth: boolean;
  isToday: boolean;
  orders: any[];
}

@Component({
  selector: 'app-planning-calendar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './planning-calendar.component.html',
  styleUrls: ['./planning-calendar.component.css']
})
export class PlanningCalendarComponent implements OnInit, OnChanges {

  /** When true: ADMIN/COMMERCIAL — all orders + site filter */
  @Input() isAdmin = false;
  /** When provided: CLIENT — their own client code */
  @Input() clientCode = '';

  allOrders: any[] = [];
  filteredOrders: any[] = [];
  calendarDays: CalendarDay[] = [];
  viewDate: Date = new Date();
  selectedDay: CalendarDay | null = null;

  // Filters (admin/commercial only)
  siteFilter = '';
  statutFilter = '';
  sites: string[] = [];

  weekDays = ['Dim', 'Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam'];
  months = [
    'Janvier','Février','Mars','Avril','Mai','Juin',
    'Juillet','Août','Septembre','Octobre','Novembre','Décembre'
  ];

  readonly statusColors: Record<string, string> = {
    NON_PLANIFIE:        '#ef4444',
    PLANIFIE:            '#f59e0b',
    EN_COURS_CHARGEMENT: '#3b82f6',
    CHARGE:              '#8b5cf6',
    EN_COURS_LIVRAISON:  '#06b6d4',
    LIVRE:               '#10b981',
  };

  statuts = [
    { value: 'NON_PLANIFIE',        label: 'Non Planifié' },
    { value: 'PLANIFIE',            label: 'Planifié' },
    { value: 'EN_COURS_CHARGEMENT', label: 'En Chargement' },
    { value: 'CHARGE',              label: 'Chargé' },
    { value: 'EN_COURS_LIVRAISON',  label: 'En Livraison' },
    { value: 'LIVRE',               label: 'Livré' },
  ];

  constructor(private ordreService: OrdreService) {}

  ngOnInit(): void {
    this.loadOrders();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['clientCode'] || changes['isAdmin']) {
      this.loadOrders();
    }
  }

  private loadOrders(): void {
    const obs = this.clientCode
      ? this.ordreService.afficherByClient(this.clientCode)
      : this.ordreService.afficher();

    obs.subscribe({
      next: (orders) => {
        this.allOrders = orders;
        this.sites = [...new Set(orders.map((o: any) => o.siteclient).filter(Boolean))] as string[];
        this.applyFilters();
      },
      error: () => { this.allOrders = []; this.buildCalendar(); }
    });
  }

  applyFilters(): void {
    this.filteredOrders = this.allOrders.filter(o => {
      const matchSite   = !this.siteFilter   || o.siteclient === this.siteFilter;
      const matchStatut = !this.statutFilter || o.statut === this.statutFilter;
      return matchSite && matchStatut;
    });
    this.buildCalendar();
  }

  clearFilters(): void {
    this.siteFilter = '';
    this.statutFilter = '';
    this.applyFilters();
  }

  private buildCalendar(): void {
    const year  = this.viewDate.getFullYear();
    const month = this.viewDate.getMonth();
    const today = new Date(); today.setHours(0,0,0,0);

    const firstDay = new Date(year, month, 1);
    const lastDay  = new Date(year, month + 1, 0);
    const startPad = firstDay.getDay();
    const endPad   = 6 - lastDay.getDay();

    const days: CalendarDay[] = [];

    for (let i = startPad; i > 0; i--) {
      days.push(this.makeDay(new Date(year, month, 1 - i), false, today));
    }
    for (let d = 1; d <= lastDay.getDate(); d++) {
      days.push(this.makeDay(new Date(year, month, d), true, today));
    }
    for (let i = 1; i <= endPad; i++) {
      days.push(this.makeDay(new Date(year, month + 1, i), false, today));
    }

    this.calendarDays = days;
  }

  private makeDay(date: Date, isCurrentMonth: boolean, today: Date): CalendarDay {
    return {
      date,
      isCurrentMonth,
      isToday: date.getTime() === today.getTime(),
      orders: this.getOrdersForDate(date)
    };
  }

  private getOrdersForDate(date: Date): any[] {
    return this.filteredOrders.filter(o => {
      const raw = o.chargementDate || o.livraisonDate || o.dateSaisie;
      if (!raw) return false;
      const od = new Date(raw); od.setHours(0,0,0,0);
      return od.getTime() === date.getTime();
    });
  }

  prevMonth(): void {
    this.viewDate = new Date(this.viewDate.getFullYear(), this.viewDate.getMonth() - 1, 1);
    this.buildCalendar();
    this.selectedDay = null;
  }

  nextMonth(): void {
    this.viewDate = new Date(this.viewDate.getFullYear(), this.viewDate.getMonth() + 1, 1);
    this.buildCalendar();
    this.selectedDay = null;
  }

  goToday(): void {
    this.viewDate = new Date();
    this.buildCalendar();
    this.selectedDay = null;
  }

  selectDay(day: CalendarDay): void {
    this.selectedDay = (this.selectedDay?.date?.getTime() === day.date.getTime()) ? null : day;
  }

  getStatusColor(statut: string): string {
    return this.statusColors[statut] ?? '#94a3b8';
  }

  getStatusLabel(statut: string): string {
    const map: Record<string, string> = {
      NON_PLANIFIE:        'Non Planifié',
      PLANIFIE:            'Planifié',
      EN_COURS_CHARGEMENT: 'En Chargement',
      CHARGE:              'Chargé',
      EN_COURS_LIVRAISON:  'En Livraison',
      LIVRE:               'Livré',
    };
    return map[statut] ?? statut;
  }

  get monthLabel(): string {
    return `${this.months[this.viewDate.getMonth()]} ${this.viewDate.getFullYear()}`;
  }

  get totalThisMonth(): number {
    const y = this.viewDate.getFullYear(), m = this.viewDate.getMonth();
    return this.filteredOrders.filter(o => {
      const d = new Date(o.chargementDate || o.livraisonDate || o.dateSaisie);
      return d.getFullYear() === y && d.getMonth() === m;
    }).length;
  }
}
