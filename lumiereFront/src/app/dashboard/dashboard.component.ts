import { Component, ViewChild, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { NgApexchartsModule, ChartComponent } from 'ng-apexcharts';
import {
  ApexNonAxisChartSeries, ApexResponsive, ApexChart,
  ApexAxisChartSeries, ApexDataLabels, ApexXAxis,
  ApexPlotOptions, ApexLegend, ApexTooltip
} from 'ng-apexcharts';
import { DashboardService } from '../dashboard.service';
import { AuthService } from '../auth.service';
import { OrdreService } from '../ordre.service';
import { forkJoin } from 'rxjs';
import { PlanningCalendarComponent } from '../planning-calendar/planning-calendar.component';

export type DonutChartOptions = {
  series: ApexNonAxisChartSeries;
  chart: ApexChart;
  responsive: ApexResponsive[];
  labels: any;
  colors: string[];
  legend: ApexLegend;
  tooltip: ApexTooltip;
};

export type BarChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  dataLabels: ApexDataLabels;
  plotOptions: ApexPlotOptions;
  xaxis: ApexXAxis;
  colors: string[];
  responsive: ApexResponsive[];
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, DatePipe, RouterModule, NgApexchartsModule, PlanningCalendarComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {

  // ── Current user ─────────────────────────────────────────────────────────
  today = new Date();
  currentUser: any = null;
  userRole: 'ADMIN' | 'COMMERCIAL' | 'CLIENT' | 'EMPLOYER_LUMIERE' | '' = '';

  // Role helpers (used in template with *ngIf)
  get isAdmin() { return this.userRole === 'ADMIN'; }
  get isCommercial() { return this.userRole === 'COMMERCIAL'; }
  get isClient() { return this.userRole === 'CLIENT'; }
  get isEmployer() { return this.userRole === 'EMPLOYER_LUMIERE'; }
  get isStaff() { return this.isAdmin || this.isCommercial || this.isEmployer; }

  // ── KPI — shared ─────────────────────────────────────────────────────────
  ordersCount = 0;
  livreCount = 0;
  enCoursDeLivraisonCount = 0;
  nonPlanifieOrdersCount = 0;
  completionRate = 0;

  // ── KPI — Staff only ─────────────────────────────────────────────────────
  clientsCount = 0;
  articlesCount = 0;
  usersCount = 0;
  pendingUsersCount = 0;
  planifieOrdersCount = 0;
  enCoursDeChargementCount = 0;
  chargeCount = 0;

  // ── Alerts feed ──────────────────────────────────────────────────────────
  alerts: { type: 'warning' | 'info' | 'danger'; icon: string; message: string; link: string }[] = [];

  // ── Chart refs ───────────────────────────────────────────────────────────
  @ViewChild('donutChart') donutChart: ChartComponent | undefined;
  @ViewChild('barChart') barChart: ChartComponent | undefined;

  public donutChartOptions: DonutChartOptions;
  public barChartOptions: BarChartOptions;
  private refreshInterval: any;

  constructor(
    private service: DashboardService,
    private authService: AuthService,
    private ordreService: OrdreService,
    private cdr: ChangeDetectorRef
  ) {
    this.donutChartOptions = this.buildDonutOptions();
    this.barChartOptions = this.buildBarOptions();
  }

  ngOnInit(): void {
    // First, load current user profile to know the role
    this.authService.profile().subscribe(user => {
      this.currentUser = user;
      this.userRole = user.role?.toUpperCase() ?? '';
      this.loadData();
      
      // Auto-refresh every 30 seconds
      this.refreshInterval = setInterval(() => {
        this.loadData();
      }, 30000);
    });
  }

  ngOnDestroy(): void {
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
    }
  }

  // ── Data loading ────────────────────────────────────────────────────────
  private loadData(): void {
    this.service.getDashboardStats().subscribe(data => {
      // Common mapping
      this.ordersCount = data.ordersCount || 0;
      this.nonPlanifieOrdersCount = data.nonPlanifieOrdersCount || 0;
      this.planifieOrdersCount = data.planifieOrdersCount || 0;
      this.enCoursDeChargementCount = data.enCoursDeChargementCount || 0;
      this.chargeCount = data.chargeCount || 0;
      this.enCoursDeLivraisonCount = data.enCoursDeLivraisonCount || 0;
      this.livreCount = data.livreCount || 0;

      this.completionRate = this.ordersCount > 0
        ? Math.round((this.livreCount / this.ordersCount) * 100) : 0;

      // Staff specific (may be undefined for CLIENT)
      this.clientsCount = data.clientsCount || 0;
      this.articlesCount = data.articlesCount || 0;
      this.usersCount = data.usersCount || 0;
      this.pendingUsersCount = data.pendingUsersCount || 0;

      // Refresh UI
      if (this.isClient) {
        this.refreshClientCharts();
        this.buildClientAlerts();
      } else {
        this.refreshStaffCharts();
        this.buildStaffAlerts();
      }
      this.cdr.detectChanges();
    });
  }

  // ── Chart builders ───────────────────────────────────────────────────────
  private buildDonutOptions(): DonutChartOptions {
    return {
      series: [1, 1, 1, 1, 1, 1],
      chart: { type: 'donut', height: 300, animations: { enabled: true, easing: 'easeinout', speed: 900 } } as any,
      labels: ['Non Planifié', 'Planifié', 'En Chargement', 'Chargé', 'En Livraison', 'Livré'],
      colors: ['#ef4444', '#f59e0b', '#3b82f6', '#8b5cf6', '#06b6d4', '#10b981'],
      legend: { position: 'bottom', fontSize: '12px', fontFamily: 'Inter, sans-serif', labels: { colors: '#64748b' } },
      tooltip: { y: { formatter: (v: number) => `${v} ordres` } },
      responsive: [{ breakpoint: 768, options: { chart: { height: 250 } } }]
    };
  }

  private buildBarOptions(): BarChartOptions {
    return {
      series: [{ name: 'Ordres', data: [1, 1, 1, 1, 1, 1] }],
      chart: { type: 'bar', height: 300, animations: { enabled: true, easing: 'easeinout', speed: 900 } } as any,
      plotOptions: { bar: { horizontal: true, barHeight: '65%', distributed: true } },
      dataLabels: { enabled: true, style: { fontFamily: 'Inter', fontSize: '11px' } },
      colors: ['#ef4444', '#f59e0b', '#3b82f6', '#8b5cf6', '#06b6d4', '#10b981'],
      xaxis: {
        categories: ['Non Planifié', 'Planifié', 'En Chargement', 'Chargé', 'En Livraison', 'Livré'],
        labels: { style: { fontFamily: 'Inter', fontSize: '11px', colors: '#64748b' } }
      },
      responsive: [{ breakpoint: 768, options: { chart: { height: 250 } } }]
    };
  }

  private refreshStaffCharts(): void {
    const series = [
      this.nonPlanifieOrdersCount, this.planifieOrdersCount,
      this.enCoursDeChargementCount, this.chargeCount,
      this.enCoursDeLivraisonCount, this.livreCount
    ];
    this.donutChartOptions = { ...this.donutChartOptions, series };
    this.barChartOptions = { ...this.barChartOptions, series: [{ name: 'Ordres', data: series }] };
  }

  /** CLIENT: donut shows only their order lifecycle */
  private refreshClientCharts(): void {
    const series = [this.nonPlanifieOrdersCount, this.enCoursDeLivraisonCount, this.livreCount];
    this.donutChartOptions = {
      ...this.donutChartOptions,
      series,
      labels: ['En Attente', 'En Livraison', 'Livré'],
      colors: ['#f59e0b', '#06b6d4', '#10b981']
    };
    // No bar chart for clients
  }

  // ── Alerts builders ──────────────────────────────────────────────────────
  private buildStaffAlerts(): void {
    this.alerts = [];
    if (this.nonPlanifieOrdersCount > 0) {
      this.alerts.push({
        type: 'danger', icon: 'fa-exclamation-triangle',
        message: `${this.nonPlanifieOrdersCount} ordre(s) non planifié(s) — action requise.`,
        link: '/site/ajouter'
      });
    }
    if ((this.isAdmin || this.isCommercial) && this.pendingUsersCount > 0) {
      this.alerts.push({
        type: 'warning', icon: 'fa-user-clock',
        message: `${this.pendingUsersCount} utilisateur(s) en attente d'approbation.`,
        link: '/site/utilisateurs'
      });
    }
    if (this.livreCount > 0) {
      this.alerts.push({
        type: 'info', icon: 'fa-check-circle',
        message: `${this.livreCount} livraison(s) complétée(s).`,
        link: '/site/ordres'
      });
    }
    if (this.alerts.length === 0) {
      this.alerts.push({
        type: 'info', icon: 'fa-check-double',
        message: 'Tout est à jour. Aucune action requise.', link: '/site/dashboard'
      });
    }
  }

  private buildClientAlerts(): void {
    this.alerts = [];
    if (this.enCoursDeLivraisonCount > 0) {
      this.alerts.push({
        type: 'info', icon: 'fa-shipping-fast',
        message: `${this.enCoursDeLivraisonCount} commande(s) en cours de livraison.`,
        link: '/site/ordres'
      });
    }
    if (this.livreCount > 0) {
      this.alerts.push({
        type: 'info', icon: 'fa-check-circle',
        message: `${this.livreCount} commande(s) livrée(s) avec succès.`,
        link: '/site/ordres'
      });
    }
    if (this.nonPlanifieOrdersCount > 0) {
      this.alerts.push({
        type: 'warning', icon: 'fa-clock',
        message: `${this.nonPlanifieOrdersCount} commande(s) en attente de prise en charge.`,
        link: '/site/ordres'
      });
    }
    if (this.alerts.length === 0) {
      this.alerts.push({
        type: 'info', icon: 'fa-box',
        message: 'Aucune commande active pour le moment.', link: '/site/ordres'
      });
    }
  }

  // ── Dynamic progress widths ──────────────────────────────────────────────
  getClientsProgress(): string { return `${Math.min(this.clientsCount * 5, 100)}%`; }
  getArticlesProgress(): string { return `${Math.min(this.articlesCount * 8, 100)}%`; }
  getOrdersProgress(): string { return `${Math.min(this.ordersCount * 3, 100)}%`; }
  getUsersProgress(): string { return `${Math.min(this.usersCount * 10, 100)}%`; }
  getDeliveryProgress(): string {
    return this.ordersCount > 0 ? `${Math.round((this.enCoursDeLivraisonCount / this.ordersCount) * 100)}%` : '5%';
  }

  /** Returns the client code used to filter the planning calendar for CLIENT role */
  get clientCode(): string {
    return this.currentUser?.linkedClientId || this.currentUser?.username || this.currentUser?.email || '';
  }
}



