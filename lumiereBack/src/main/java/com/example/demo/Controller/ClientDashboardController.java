package com.example.demo.Controller;

import com.example.demo.Entity.Client;
import com.example.demo.Entity.Ordre;
import com.example.demo.Entity.User;
import com.example.demo.Entity.Statut;
import com.example.demo.Repository.ClientRepository;
import com.example.demo.Repository.OrdreRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/client/dashboard")
public class ClientDashboardController {

    private final OrdreRepository ordreRepository;
    private final ClientRepository clientRepository;

    public ClientDashboardController(OrdreRepository ordreRepository, ClientRepository clientRepository) {
        this.ordreRepository = ordreRepository;
        this.clientRepository = clientRepository;
    }

    @GetMapping("/mes-livraisons/actives")
    public ResponseEntity<List<Ordre>> getActiveDeliveries(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        List<String> clientCodes = getClientCodesForUser(currentUser);

        if (clientCodes.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Ordre> activeOrders = ordreRepository.findActiveOrdersByClientCodes(clientCodes);
        return ResponseEntity.ok(activeOrders);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getDashboardStats(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        List<String> clientCodes = getClientCodesForUser(currentUser);
        if (clientCodes.isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }

        Map<String, Long> stats = new HashMap<>();
        // Maps based on HomePage stats expectations
        // Improved stats mapping
        long enAttente = ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.NON_PLANIFIE) + 
                         ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.PLANIFIE);
        
        long enCours = ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.EN_COURS_DE_CHARGEMENT) +
                       ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.CHARGE) +
                       ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.EN_COURS_DE_LIVRAISON);

        stats.put("mesDemandesEnCours", enCours);
        stats.put("mesDemandesEnAttente", enAttente);
        stats.put("mesDemandesTerminees", ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.LIVRE));
        stats.put("mesLivraisonsEnCours",
                ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.EN_COURS_DE_LIVRAISON));
        stats.put("totalMesDemandes", ordreRepository.countByClientCodes(clientCodes));
        stats.put("totalMesLivraisons", ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.LIVRE));
        stats.put("notifications", 0L);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/mes-demandes/recentes")
    public ResponseEntity<List<Ordre>> getRecentOrders(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        List<String> clientCodes = getClientCodesForUser(currentUser);
        if (clientCodes.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Ordre> recentOrders = ordreRepository.findRecentOrdersByClientCodes(clientCodes,
                org.springframework.data.domain.PageRequest.of(0, 10));
        return ResponseEntity.ok(recentOrders);
    }

    private List<String> getClientCodesForUser(User user) {
        // [FIX] Use the ManyToMany relationship directly from the user object or via repository
        // Reloading user to ensure the collection is accessible if needed, 
        // but findByOwner in repository is cleaner.
        return clientRepository.findByOwner(user).stream()
                .map(Client::getCodeclient)
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toList());
    }
}
