package com.example.demo.securityjwt.controller;

import com.example.demo.Entity.Notification;
import com.example.demo.Entity.Client;
import com.example.demo.Repository.NotificationRepository;
import com.example.demo.Service.EmailService;

import com.example.demo.Repository.UserRepository;
import com.example.demo.Entity.Status;
import com.example.demo.Entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin("*")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    private final UserRepository userRepository;
    private final com.example.demo.Repository.ClientRepository clientRepository;
    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public AdminController(UserRepository userRepository,
            com.example.demo.Repository.ClientRepository clientRepository,
            EmailService emailService,
            NotificationRepository notificationRepository,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.emailService = emailService;
        this.notificationRepository = notificationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers(
            @org.springframework.security.core.annotation.AuthenticationPrincipal User currentUser) {
        logger.info("getAllUsers called by user: {} with role: {}", currentUser.getEmail(), currentUser.getRole());

        // ADMIN sees all users, COMMERCIAL sees only CLIENT users
        List<User> users;
        if (currentUser.isAdmin()) {
            users = userRepository.findAll();
            logger.info("ADMIN user - returning all {} users", users.size());
        } else if (currentUser.isCommercial()) {
            users = userRepository.findByRole(com.example.demo.Entity.Role.CLIENT);
            logger.info("COMMERCIAL user - returning {} CLIENT users", users.size());
        } else {
            // CLIENT users should not access this endpoint
            logger.warn("CLIENT user {} attempted to access admin endpoint", currentUser.getEmail());
            return ResponseEntity.status(403).build();
        }

        // Populate registrationApproved flag from the associated Client entity
        for (User u : users) {
            if (u.getRole() == com.example.demo.Entity.Role.CLIENT) {
                List<Client> clients = userRepository.findClientsByUserId(u.getId());
                if (clients != null && !clients.isEmpty()) {
                    Client c = clients.get(0);
                    u.setRegistrationApproved(c.isRegistrationApproved());
                    u.setLinkedClientId(c.getCode());
                }
            }
        }

        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/count/pending")
    public ResponseEntity<Long> countPendingUsers() {
        return ResponseEntity.ok(userRepository.countByStatus(Status.PENDING));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable Integer id, @RequestParam Status status) {
        try {
            User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("Utilisateur non trouvé avec l'ID: " + id));

            // Custom logic for CLIENT role: "Accepting" registration doesn't activate the user yet
            if (user.getRole() == com.example.demo.Entity.Role.CLIENT && status == Status.ACTIVE) {
                List<Client> clients = userRepository.findClientsByUserId(id);
                if (clients != null && !clients.isEmpty()) {
                    Client client = clients.get(0);
                    client.setRegistrationApproved(true);
                    clientRepository.save(client);
                    user.setStatus(Status.PENDING);
                } else {
                    return ResponseEntity.status(404).body(java.util.Map.of("message", "Impossible d'activer : Enregistrement client manquant pour cet utilisateur."));
                }
            } else {
                user.setStatus(status);
            }

            userRepository.save(user);

            String fullName = user.getFirstname() + " " + user.getLastname();

            // Send email notification (non-blocking)
            if (status == Status.ACTIVE) {
                // If it's a client, the email should say "Registration Accepted" instead of
                // "Activated"
                if (user.getRole() == com.example.demo.Entity.Role.CLIENT) {
                    emailService.sendRegistrationAcceptedEmail(user.getEmail(), fullName);
                } else {
                    emailService.sendAccountActivatedEmail(user.getEmail(), fullName);
                }

                // Create in-app notification for Admins/Commercials
                Notification notification = new Notification();
                notification.setType("ACCOUNT_APPROVED");
                notification.setMessage(
                        "✅ Inscription acceptée pour " + fullName + ". Complétez son profil pour l'activer.");
                notification.setRead(false);
                notification.setTargetRole(com.example.demo.Entity.Role.ADMIN); // Hide from clients
                notificationRepository.save(notification);
            } else if (status == Status.REJECTED) {
                emailService.sendAccountRejectedEmail(user.getEmail(), fullName);
            }
        } catch (Exception e) {
            logger.error("Error updating user status for {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(java.util.Map.of("message", "Erreur serveur lors de la mise à jour : " + e.getMessage()));
        }
        return ResponseEntity.ok("Statut mis à jour avec succès");
    }

    @GetMapping("/status")
    public ResponseEntity<?> checkStatus(@RequestParam String email) {
        return userRepository.findFirstByEmailOrderByIdAsc(email)
                .map(user -> ResponseEntity.ok().body(
                        java.util.Map.of("status", user.getStatus(), "email", user.getEmail())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/{id}/approve-client")
    public ResponseEntity<?> approveClient(
            @PathVariable Integer id,
            @RequestParam String codeClient,
            @RequestParam String idEdi) {
        try {
            logger.info("Approving client user ID: {} with code: {} and edi: {}", id, codeClient, idEdi);
            
            User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("Utilisateur non trouvé (ID: " + id + ")"));
            
            // Find associated client
            List<Client> clients = userRepository.findClientsByUserId(id);
            if (clients == null || clients.isEmpty()) {
                return ResponseEntity.status(404).body(java.util.Map.of("message", "Erreur critique : Aucun enregistrement client n'est lié à cet utilisateur dans la table 'user_clients'."));
            }
            
            Client client = clients.get(0);
            client.setCodeclient(codeClient);
            client.setIdEdi(idEdi);
            client.setRegistrationApproved(true);
            client.setProfileCompleted(true);
            clientRepository.save(client);
            
            // Activate user
            user.setStatus(Status.ACTIVE);
            userRepository.save(user);
        
            String fullName = user.getFirstname() + " " + user.getLastname();
            try {
                emailService.sendRegistrationAcceptedEmail(user.getEmail(), fullName);
            } catch (Exception e) {
                logger.error("Failed to send approval email: {}", e.getMessage());
            }
            
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            logger.error("Error in approveClient for {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(java.util.Map.of("message", "Échec de l'activation : " + e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable Integer id) {
        logger.info("Suppression de l'utilisateur ID: {}", id);
        try {
            // 1. Nettoyer les ordres (nullifier owner_id)
            jdbcTemplate.update("UPDATE ordre SET owner_id = NULL WHERE owner_id = ?", id);
            
            // 2. Nettoyer les clients (nullifier owner_id s'il existe encore)
            try {
                jdbcTemplate.update("UPDATE client SET owner_id = NULL WHERE owner_id = ?", id);
            } catch (Exception e) {
                logger.debug("La colonne owner_id dans client n'existe plus ou est déjà vide.");
            }
            
            // 3. Nettoyer les notifications (nullifier target_user_id)
            jdbcTemplate.update("UPDATE notifications SET target_user_id = NULL WHERE target_user_id = ?", id);
            
            // 4. Supprimer les permissions spécifiques
            jdbcTemplate.update("DELETE FROM user_permissions WHERE user_id = ?", id);
            
            // 5. Supprimer les liens avec les clients (table de jointure)
            jdbcTemplate.update("DELETE FROM user_clients WHERE user_id = ?", id);
            
            // 6. Supprimer l'utilisateur lui-même
            int deleted = jdbcTemplate.update("DELETE FROM _user WHERE id = ?", id);
            
            if (deleted > 0) {
                logger.info("Utilisateur ID: {} supprimé avec succès", id);
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Erreur critique lors de la suppression de l'utilisateur {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
}
