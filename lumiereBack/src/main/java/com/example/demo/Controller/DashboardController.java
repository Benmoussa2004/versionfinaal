package com.example.demo.Controller;

import com.example.demo.Entity.Client;
import com.example.demo.Entity.Role;
import com.example.demo.Entity.Statut;
import com.example.demo.Entity.Status;
import com.example.demo.Entity.User;
import com.example.demo.Repository.ArticleRepository;
import com.example.demo.Repository.ClientRepository;
import com.example.demo.Repository.OrdreRepository;
import com.example.demo.Repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin("*")
public class DashboardController {

    private final OrdreRepository ordreRepository;
    private final ClientRepository clientRepository;
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;

    public DashboardController(OrdreRepository ordreRepository, 
                               ClientRepository clientRepository,
                               ArticleRepository articleRepository,
                               UserRepository userRepository) {
        this.ordreRepository = ordreRepository;
        this.clientRepository = clientRepository;
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> stats = new HashMap<>();
        Role role = currentUser.getRole();

        if (role == Role.ADMIN || role == Role.COMMERCIAL || role == Role.EMPLOYER_LUMIERE) {
            // Staff statistics
            stats.put("ordersCount", ordreRepository.count());
            stats.put("nonPlanifieOrdersCount", ordreRepository.countNonPlanifieOrders());
            stats.put("planifieOrdersCount", ordreRepository.countPlanifieOrders());
            stats.put("enCoursDeChargementCount", ordreRepository.countEnCoursDeChargementOrders());
            stats.put("chargeCount", ordreRepository.countChargeOrders());
            stats.put("enCoursDeLivraisonCount", ordreRepository.countEnCoursDeLivraisonOrders());
            stats.put("livreCount", ordreRepository.countLivreOrders());
            
            stats.put("clientsCount", clientRepository.count());
            stats.put("articlesCount", articleRepository.count());

            // Specific "Sensitive" things only for ADMIN and COMMERCIAL
            if (role == Role.ADMIN || role == Role.COMMERCIAL) {
                stats.put("usersCount", userRepository.count());
                stats.put("pendingUsersCount", userRepository.countByStatus(Status.PENDING));
            }
        } else {
            // Client statistics - Only their own data
            List<String> clientCodes = clientRepository.findByOwner(currentUser).stream()
                    .map(Client::getCodeclient)
                    .filter(code -> code != null && !code.isEmpty())
                    .collect(Collectors.toList());

            if (clientCodes.isEmpty()) {
                stats.put("ordersCount", 0L);
                stats.put("nonPlanifieOrdersCount", 0L);
                stats.put("enCoursDeLivraisonCount", 0L);
                stats.put("livreCount", 0L);
            } else {
                stats.put("ordersCount", ordreRepository.countByClientCodes(clientCodes));
                stats.put("nonPlanifieOrdersCount", ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.NON_PLANIFIE));
                stats.put("enCoursDeLivraisonCount", ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.EN_COURS_DE_LIVRAISON));
                stats.put("livreCount", ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.LIVRE));
                
                // Add some other counts for consistency
                stats.put("planifieOrdersCount", ordreRepository.countByClientCodesAndStatut(clientCodes, Statut.PLANIFIE));
            }
        }

        return ResponseEntity.ok(stats);
    }
}
