package com.example.demo.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.example.demo.Entity.Article;
import com.example.demo.Entity.Ordre;
import com.example.demo.Entity.Statut;
import com.example.demo.Entity.Client;
import com.example.demo.Entity.OrderCounter;
import com.example.demo.Entity.Tranck;
import com.example.demo.Repository.ClientRepository;
import com.example.demo.Repository.OrderCounterRepository;
import com.example.demo.Repository.OrdreRepository;
import com.example.demo.Repository.TranckRepository;

import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import com.example.demo.Entity.Client;

@Service
public class OrdreService {

	@Autowired
	private OrdreRepository ordreRepository;
	@Autowired
	private OrderCounterRepository orderCounterRepository;

	private static final int MAX_ORDER_NUMBER = 9999999;

	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private MatriculeService matriculeService;
	@Autowired
	private TranckRepository tranckRepository;
	@Autowired
	private EmailService emailService;
	@Autowired
	private NotificationService notificationService;

	public List<Ordre> findAll() {
		return ordreRepository.findTop1000ByOrderByIdDesc();
	}

	public List<Ordre> findByClientCode(String clientCode) {
		return ordreRepository.findByClientOrderByIdDesc(clientCode);
	}

	public List<Ordre> findByClientCodes(List<String> clientCodes) {
		return ordreRepository.findAll((root, query, cb) -> {
			query.orderBy(cb.desc(root.get("id")));
			return root.get("client").in(clientCodes);
		});
	}

	public List<Ordre> findByClientCodesAndOwner(List<String> clientCodes, com.example.demo.Entity.User owner) {
		return ordreRepository.findByClientCodesAndOwner(clientCodes, owner);
	}

	public Optional<Ordre> findById(Long id) {
		return ordreRepository.findById(id);
	}

	@Transactional
	public Ordre save(Ordre ordre, com.example.demo.Entity.User owner) {
		// 1. Générer le numéro d'ordre en premier (peut déclencher un flush de session)
		String orderNumber = "DIV" + generateOrderNumber();
		ordre.setOrderNumber(orderNumber);
		ordre.setStatut(Statut.NON_CONFIRME);
		ordre.setOwner(owner);

		// 2. Créer et sauvegarder le tracking d'abord
		Tranck tranck = new Tranck();
		tranck.setDepart(false);
		tranck.setChargement(false);
		tranck.setLivraison(false);
		tranck = tranckRepository.save(tranck);

		// 3. Lier le tracking à l'ordre et sauvegarder l'ordre
		ordre.setTrancking(tranck);
		Ordre savedOrdre = ordreRepository.save(ordre);

		// 4. Lier l'ordre au tracking (back-reference)
		tranck.setOrdre(savedOrdre);
		tranckRepository.save(tranck);

		// 5. Envoyer l'email de notification
		try {
			emailService.sendOrderCreatedEmail(savedOrdre);
			
			// In-app notification for admins
			com.example.demo.Entity.Notification notification = new com.example.demo.Entity.Notification();
			notification.setType("ORDRE");
			notification.setMessage("Nouvel ordre créé : " + savedOrdre.getOrderNumber() + " pour " + savedOrdre.getNomclient());
			notification.setRead(false);
			// [FIX] Target ADMIN by default, so it shows up for staff
			notification.setTargetRole(com.example.demo.Entity.Role.ADMIN);
			notificationService.createNotification(notification);
		} catch (Exception e) {
			System.err.println("Failed to send order creation notifications: " + e.getMessage());
		}

		return savedOrdre;
	}

	private String generateOrderNumber() {
		// Récupérer le compteur actuel
		OrderCounter counter = orderCounterRepository.findAll().stream().findFirst().orElse(null);

		if (counter == null) {
			// Initialiser le compteur s'il n'existe pas
			counter = new OrderCounter();
			counter.setCurrentValue(0);
		}

		// Incrémenter le compteur
		int newOrderNumber = counter.getCurrentValue() + 1;

		if (newOrderNumber > MAX_ORDER_NUMBER) {
			newOrderNumber = 1; // Réinitialiser le compteur à 1
		}

		// Mettre à jour le compteur
		counter.setCurrentValue(newOrderNumber);
		orderCounterRepository.save(counter);

		// Retourner le numéro d'ordre formaté sur 7 chiffres
		return String.format("%07d", newOrderNumber);
	}

	@Transactional
	public void deleteById(Long id) {
		ordreRepository.deleteById(id);
	}

	@Transactional
	public Ordre confirmer(Long id) {

		Optional<Ordre> Ordre = ordreRepository.findById(id);
		Ordre ordre = Ordre.get();
		ordre.setStatut(Statut.NON_PLANIFIE);

		final Ordre updatedOrdre = ordreRepository.save(ordre);

		// Envoyer l'email de confirmation au client
		sendOrderConfirmationEmail(updatedOrdre);

		// In-app notification for the client
		try {
			com.example.demo.Entity.Notification notification = new com.example.demo.Entity.Notification();
			notification.setType("CONFIRMATION");
			notification.setMessage("Votre ordre " + updatedOrdre.getOrderNumber() + " a été confirmé !");
			notification.setRead(false);
			
			// Find the user ID associated with this client to target them specifically
			clientRepository.findByCodeclient(updatedOrdre.getClient()).ifPresent(client -> {
				if (!client.getOwners().isEmpty()) {
					notification.setTargetUserId(client.getOwners().get(0).getId());
				} else {
					// Fallback to role-based if owners list is empty
					notification.setTargetRole(com.example.demo.Entity.Role.CLIENT);
				}
			});
			
			notificationService.createNotification(notification);
		} catch (Exception e) {
			System.err.println("Failed to create confirmation notification: " + e.getMessage());
		}

		return updatedOrdre;

	}

	private void sendOrderConfirmationEmail(Ordre ordre) {
		if (ordre.getClient() != null) {
			clientRepository.findByCodeclient(ordre.getClient()).ifPresent(client -> {
				if (client.getEmail() != null && !client.getEmail().isEmpty()) {
					emailService.sendOrderConfirmedEmail(ordre, client.getEmail());
				}
			});
		}
	}

	@Transactional
	public List<Ordre> confirmerMultiple(List<Long> ids) {
		List<Ordre> confirmed = new ArrayList<>();
		for (Long id : ids) {
			Optional<Ordre> opt = ordreRepository.findById(id);
			if (opt.isPresent()) {
				Ordre o = opt.get();
				o.setStatut(Statut.NON_PLANIFIE);
				Ordre saved = ordreRepository.save(o);
				confirmed.add(saved);
				sendOrderConfirmationEmail(saved);
			}
		}
		return confirmed;
	}

	@Transactional
	public Ordre update(Long id, Ordre ordreDetails) {
		Optional<Ordre> Ordre = ordreRepository.findById(id);
		Ordre ordre = Ordre.get();

		ordre.setClient(ordreDetails.getClient());
		ordre.setChargementNom(ordreDetails.getChargementNom());
		ordre.setChargementAdr1(ordreDetails.getChargementAdr1());
		ordre.setChargementAdr2(ordreDetails.getChargementAdr2());
		ordre.setChargementVille(ordreDetails.getChargementVille());
		ordre.setChargementDate(ordreDetails.getChargementDate());
		ordre.setLivraisonNom(ordreDetails.getLivraisonNom());
		ordre.setLivraisonAdr1(ordreDetails.getLivraisonAdr1());
		ordre.setLivraisonAdr2(ordreDetails.getLivraisonAdr2());
		ordre.setLivraisonVille(ordreDetails.getLivraisonVille());
		ordre.setLivraisonDate(ordreDetails.getLivraisonDate());
		ordre.setCodeArticle(ordreDetails.getCodeArticle());
		ordre.setDesignation(ordreDetails.getDesignation());

		ordre.setPoids(ordreDetails.getPoids());
		ordre.setVolume(ordreDetails.getVolume());
		ordre.setNombrePalettes(ordreDetails.getNombrePalettes());
		ordre.setNombreColis(ordreDetails.getNombreColis());
		ordre.setLongueur(ordreDetails.getLongueur());

		ordre.setStatut(ordreDetails.getStatut());
		ordre.setCommentaires(ordreDetails.getCommentaires());
		ordre.setTrancking(ordreDetails.getTrancking());

		final Ordre updatedOrdre = ordreRepository.save(ordre);
		
		// Notify client of the update
		try {
			com.example.demo.Entity.Notification notification = new com.example.demo.Entity.Notification();
			notification.setType("ORDRE_UPDATE");
			notification.setMessage("Votre ordre " + updatedOrdre.getOrderNumber() + " a été mis à jour.");
			notification.setRead(false);
			
			clientRepository.findByCodeclient(updatedOrdre.getClient()).ifPresent(client -> {
				if (!client.getOwners().isEmpty()) {
					notification.setTargetUserId(client.getOwners().get(0).getId());
				}
			});
			
			notificationService.createNotification(notification);
		} catch (Exception e) {
			// Silent error
		}
		
		return updatedOrdre;
	}

	public long countAllOrders() {
		return ordreRepository.count(); // Or ordreRepository.countAllOrders();
	}

	public long countNonPlanifieOrders() {
		return ordreRepository.countNonPlanifieOrders();
	}

	public long countPlanifieOrders() {
		return ordreRepository.countPlanifieOrders();
	}

	public long getEnCoursDeChargementOrdersCount() {
		return ordreRepository.countEnCoursDeChargementOrders();
	}

	public long getChargeOrdersCount() {
		return ordreRepository.countChargeOrders();
	}

	public long getEnCoursDeLivraisonOrdersCount() {
		return ordreRepository.countEnCoursDeLivraisonOrders();
	}

	public long getLivreOrdersCount() {
		return ordreRepository.countLivreOrders();
	}

	public long countByClientCodes(List<String> clientCodes) {
		return ordreRepository.countByClientCodes(clientCodes);
	}

	public long countByClientCodesAndStatut(List<String> clientCodes, Statut statut) {
		return ordreRepository.countByClientCodesAndStatut(clientCodes, statut);
	}

	public long countByClientCodesAndOwner(List<String> clientCodes, com.example.demo.Entity.User owner) {
		return ordreRepository.countByClientCodesAndOwner(clientCodes, owner);
	}

	public long countByClientCodesAndOwnerAndStatut(List<String> clientCodes, com.example.demo.Entity.User owner, Statut statut) {
		return ordreRepository.countByClientCodesAndOwnerAndStatut(clientCodes, owner, statut);
	}

	public List<Ordre> search(String client, Statut statut, Date startSaisie, Date endSaisie, String chauffeur,
			String site, String destination) {
		return searchExtended(client, statut, startSaisie, endSaisie, chauffeur, site, destination, null, null);
	}

	public List<Ordre> searchExtended(String client, Statut statut, Date startSaisie, Date endSaisie, String chauffeur,
			String site, String destination, List<String> restrictedClientCodes, com.example.demo.Entity.User owner) {
		return ordreRepository.findAll((root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (restrictedClientCodes != null && !restrictedClientCodes.isEmpty()) {
				predicates.add(root.get("client").in(restrictedClientCodes));
			}

			if (owner != null) {
				predicates.add(cb.equal(root.get("owner"), owner));
			}

			if (client != null && !client.isEmpty()) {
				predicates.add(cb.like(cb.lower(root.get("client")), "%" + client.toLowerCase() + "%"));
			}
			if (statut != null) {
				predicates.add(cb.equal(root.get("statut"), statut));
			}
			if (startSaisie != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("dateSaisie"), startSaisie));
			}
			if (endSaisie != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("dateSaisie"), endSaisie));
			}
			if (chauffeur != null && !chauffeur.isEmpty()) {
				predicates.add(cb.like(cb.lower(root.get("chauffeur")), "%" + chauffeur.toLowerCase() + "%"));
			}
			if (site != null && !site.isEmpty()) {
				predicates.add(cb.equal(root.get("siteclient"), site));
			}
			if (destination != null && !destination.isEmpty()) {
				predicates.add(cb.like(cb.lower(root.get("livraisonVille")), "%" + destination.toLowerCase() + "%"));
			}

			// Trier par dateSaisie décroissante par défaut (les plus récents en premier)
			query.orderBy(cb.desc(root.get("dateSaisie")));

			return cb.and(predicates.toArray(new Predicate[0]));
		});
	}

	@Transactional
	public Ordre updateGpsPosition(Long id, Double lat, Double lon) {
		Optional<Ordre> optionalOrdre = ordreRepository.findById(id);
		if (optionalOrdre.isPresent()) {
			Ordre ordre = optionalOrdre.get();
			ordre.setCurrentLat(lat);
			ordre.setCurrentLon(lon);
			return ordreRepository.save(ordre);
		}
		throw new RuntimeException("Ordre introuvable avec l'ID: " + id);
	}

}
