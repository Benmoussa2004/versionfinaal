package com.example.demo.Entity;

import java.util.Date;
import java.util.List;
import java.util.Set;

import com.example.demo.Service.MatriculeService;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@AllArgsConstructor
@RequiredArgsConstructor
@Builder
@Data

@Entity

public class Ordre {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String orderNumber;;

	private String client;
	private String nomclient;
	private String siteclient;
	private String idedi;

	private String codeclientcharg;
	private String chargementNom;
	private String chargementAdr1;
	private String chargementAdr2;

	private String chargementVille;
	@Temporal(TemporalType.TIMESTAMP)
	private Date chargementDate;

	private String codeclientliv;
	private String livraisonNom;
	private String livraisonAdr1;
	private String livraisonAdr2;
	private String codepostalliv;
	private String livraisonVille;
	@Temporal(TemporalType.TIMESTAMP)
	private Date livraisonDate;

	private String codeArticle;
	@Column(name = "designation")
	private String designation;

	/*
	 * @Column(name = "qte_trs")
	 * private Double qteTrs;
	 * 
	 * @Column(name = "qte_taxee")
	 * private Double qteTaxee;
	 */
	private Double poids;
	private Double volume;
	private Integer nombrePalettes;
	private Integer nombreColis;
	private Double longueur;
	@Temporal(TemporalType.TIMESTAMP)
	@Column(nullable = false, updatable = false)
	private Date dateSaisie;
	@Enumerated(EnumType.STRING)
	private Statut statut;

	private String voycle;
	private String chauffeur;
	private String telchauffeur;
	private String camion;
	private String datevoy;
	private Set<String> commentaires;

	private Set<String> events;

	@Column(name = "current_lat")
	private Double currentLat;

	@Column(name = "current_lon")
	private Double currentLon;

	@Column(name = "speed")
	private Double speed;

	@OneToOne
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private Tranck trancking;

	@ManyToOne
	@JoinColumn(name = "owner_id")
	private User owner;

	@PrePersist
	protected void onCreate() {
		dateSaisie = new Date();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getOrderNumber() {
		return orderNumber;
	}

	public void setOrderNumber(String orderNumber) {
		this.orderNumber = orderNumber;
	}

	public String getClient() {
		return client;
	}

	public void setClient(String client) {
		this.client = client;
	}

	public String getNomclient() {
		return nomclient;
	}

	public void setNomclient(String nomclient) {
		this.nomclient = nomclient;
	}

	public String getSiteclient() {
		return siteclient;
	}

	public void setSiteclient(String siteclient) {
		this.siteclient = siteclient;
	}

	public String getIdedi() {
		return idedi;
	}

	public void setIdedi(String idedi) {
		this.idedi = idedi;
	}

	public String getCodeclientcharg() {
		return codeclientcharg;
	}

	public void setCodeclientcharg(String codeclientcharg) {
		this.codeclientcharg = codeclientcharg;
	}

	public String getChargementNom() {
		return chargementNom;
	}

	public void setChargementNom(String chargementNom) {
		this.chargementNom = chargementNom;
	}

	public String getChargementAdr1() {
		return chargementAdr1;
	}

	public void setChargementAdr1(String chargementAdr1) {
		this.chargementAdr1 = chargementAdr1;
	}

	public String getChargementAdr2() {
		return chargementAdr2;
	}

	public void setChargementAdr2(String chargementAdr2) {
		this.chargementAdr2 = chargementAdr2;
	}

	public String getChargementVille() {
		return chargementVille;
	}

	public void setChargementVille(String chargementVille) {
		this.chargementVille = chargementVille;
	}

	public Date getChargementDate() {
		return chargementDate;
	}

	public void setChargementDate(Date chargementDate) {
		this.chargementDate = chargementDate;
	}

	public String getCodeclientliv() {
		return codeclientliv;
	}

	public void setCodeclientliv(String codeclientliv) {
		this.codeclientliv = codeclientliv;
	}

	public String getLivraisonNom() {
		return livraisonNom;
	}

	public void setLivraisonNom(String livraisonNom) {
		this.livraisonNom = livraisonNom;
	}

	public String getLivraisonAdr1() {
		return livraisonAdr1;
	}

	public void setLivraisonAdr1(String livraisonAdr1) {
		this.livraisonAdr1 = livraisonAdr1;
	}

	public String getLivraisonAdr2() {
		return livraisonAdr2;
	}

	public void setLivraisonAdr2(String livraisonAdr2) {
		this.livraisonAdr2 = livraisonAdr2;
	}

	public String getCodepostalliv() {
		return codepostalliv;
	}

	public void setCodepostalliv(String codepostalliv) {
		this.codepostalliv = codepostalliv;
	}

	public String getLivraisonVille() {
		return livraisonVille;
	}

	public void setLivraisonVille(String livraisonVille) {
		this.livraisonVille = livraisonVille;
	}

	public Date getLivraisonDate() {
		return livraisonDate;
	}

	public void setLivraisonDate(Date livraisonDate) {
		this.livraisonDate = livraisonDate;
	}

	public String getCodeArticle() {
		return codeArticle;
	}

	public void setCodeArticle(String codeArticle) {
		this.codeArticle = codeArticle;
	}

	public String getDesignation() {
		return designation;
	}

	public void setDesignation(String designation) {
		this.designation = designation;
	}

	public Double getPoids() {
		return poids;
	}

	public void setPoids(Double poids) {
		this.poids = poids;
	}

	public Double getVolume() {
		return volume;
	}

	public void setVolume(Double volume) {
		this.volume = volume;
	}

	public Integer getNombrePalettes() {
		return nombrePalettes;
	}

	public void setNombrePalettes(Integer nombrePalettes) {
		this.nombrePalettes = nombrePalettes;
	}

	public Integer getNombreColis() {
		return nombreColis;
	}

	public void setNombreColis(Integer nombreColis) {
		this.nombreColis = nombreColis;
	}

	public Double getLongueur() {
		return longueur;
	}

	public void setLongueur(Double longueur) {
		this.longueur = longueur;
	}

	public Date getDateSaisie() {
		return dateSaisie;
	}

	public void setDateSaisie(Date dateSaisie) {
		this.dateSaisie = dateSaisie;
	}

	public Statut getStatut() {
		return statut;
	}

	public void setStatut(Statut statut) {
		this.statut = statut;
	}

	public String getVoycle() {
		return voycle;
	}

	public void setVoycle(String voycle) {
		this.voycle = voycle;
	}

	public String getChauffeur() {
		return chauffeur;
	}

	public void setChauffeur(String chauffeur) {
		this.chauffeur = chauffeur;
	}

	public String getTelchauffeur() {
		return telchauffeur;
	}

	public void setTelchauffeur(String telchauffeur) {
		this.telchauffeur = telchauffeur;
	}

	public String getCamion() {
		return camion;
	}

	public void setCamion(String camion) {
		this.camion = camion;
	}

	public String getDatevoy() {
		return datevoy;
	}

	public void setDatevoy(String datevoy) {
		this.datevoy = datevoy;
	}

	public Set<String> getCommentaires() {
		return commentaires;
	}

	public void setCommentaires(Set<String> commentaires) {
		this.commentaires = commentaires;
	}

	public Set<String> getEvents() {
		return events;
	}

	public void setEvents(Set<String> events) {
		this.events = events;
	}

	public Tranck getTrancking() {
		return trancking;
	}

	public void setTrancking(Tranck trancking) {
		this.trancking = trancking;
	}

	public Double getCurrentLat() {
		return currentLat;
	}

	public void setCurrentLat(Double currentLat) {
		this.currentLat = currentLat;
	}

	public Double getCurrentLon() {
		return currentLon;
	}

	public void setCurrentLon(Double currentLon) {
		this.currentLon = currentLon;
	}

	public Double getSpeed() {
		return speed;
	}

	public void setSpeed(Double speed) {
		this.speed = speed;
	}

	public User getOwner() {
		return owner;
	}

	public void setOwner(User owner) {
		this.owner = owner;
	}

}
