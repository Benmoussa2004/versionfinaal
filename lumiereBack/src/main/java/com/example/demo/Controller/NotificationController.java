package com.example.demo.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Entity.Notification;
import com.example.demo.Service.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    // ✅ GET toutes les notifications (filtrées par userId et role)
    @GetMapping
    public List<Notification> getAllNotifications(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.example.demo.Entity.User currentUser,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String role) {
        
        // If parameters are missing, use current user info
        Integer effectiveUserId = (userId != null) ? userId : (currentUser != null ? currentUser.getId() : null);
        com.example.demo.Entity.Role roleEnum = (role != null) ? parseRole(role) : (currentUser != null ? currentUser.getRole() : null);

        return notificationService.getAllNotifications(effectiveUserId, roleEnum);
    }

    // ✅ GET une notification par ID
    @GetMapping("/{id}")
    public ResponseEntity<Notification> getNotificationById(@PathVariable Long id) {
        Optional<Notification> notification = notificationService.getNotificationById(id);
        return notification.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ✅ GET notifications non lues
    @GetMapping("/unread")
    public List<Notification> getUnreadNotifications(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String role) {
        com.example.demo.Entity.Role roleEnum = parseRole(role);
        return notificationService.getUnreadNotifications(userId, roleEnum);
    }

    // ✅ GET nombre de notifications non lues
    @GetMapping("/unread/count")
    public ResponseEntity<Long> getUnreadCount(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String role) {
        com.example.demo.Entity.Role roleEnum = parseRole(role);
        return ResponseEntity.ok(notificationService.getUnreadCount(userId, roleEnum));
    }

    /** Safely convert a role string to Role enum, returns null if invalid */
    private com.example.demo.Entity.Role parseRole(String role) {
        if (role == null || role.isBlank()) return null;
        try { return com.example.demo.Entity.Role.valueOf(role.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    // ✅ POST créer une notification
    @PostMapping
    public Notification createNotification(@RequestBody Notification notification) {
        return notificationService.createNotification(notification);
    }

    // ✅ PUT marquer une notification comme lue
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        boolean updated = notificationService.markAsRead(id);
        if (updated)
            return ResponseEntity.ok().build();
        return ResponseEntity.notFound().build();
    }

    // ✅ PUT marquer toutes comme lues (supprime les notifications visibles par l'utilisateur)
    @PutMapping("/mark-all-read")
    public ResponseEntity<Void> markAllAsRead(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.example.demo.Entity.User currentUser) {
        
        Integer userId = (currentUser != null) ? currentUser.getId() : null;
        com.example.demo.Entity.Role role = (currentUser != null) ? currentUser.getRole() : null;
        
        notificationService.markAllAsRead(userId, role);
        return ResponseEntity.ok().build();
    }

    // ✅ PUT mettre à jour une notification
    @PutMapping("/{id}")
    public ResponseEntity<Notification> updateNotification(@PathVariable Long id,
            @RequestBody Notification updatedNotification) {
        Notification notification = notificationService.updateNotification(id, updatedNotification);
        if (notification != null) {
            return ResponseEntity.ok(notification);
        }
        return ResponseEntity.notFound().build();
    }

    // ✅ DELETE une notification
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        if (notificationService.deleteNotification(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ✅ DELETE toutes les notifications lues
    @DeleteMapping("/read")
    public ResponseEntity<Void> deleteAllRead() {
        notificationService.deleteAllRead();
        return ResponseEntity.noContent().build();
    }

    // ✅ GET paramètres de notification (stub retournant des valeurs par défaut)
    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("emailNotifications", true);
        settings.put("pushNotifications", true);
        settings.put("smsNotifications", false);
        return ResponseEntity.ok(settings);
    }

    // ✅ PUT mettre à jour paramètres de notification (stub)
    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> settings) {
        return ResponseEntity.ok(settings);
    }

    // ✅ POST enregistrer un device token (push notifications)
    @PostMapping("/device-token")
    public ResponseEntity<Void> registerDeviceToken(@RequestBody Map<String, String> body) {
        // Ici on pourrait sauvegarder le token FCM en base de données
        String token = body.get("token");
        if (token != null && !token.isEmpty()) {
            // TODO: persister le token pour les push notifications
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().build();
    }

    // ✅ POST test notification
    @PostMapping("/test")
    public ResponseEntity<Void> testNotification() {
        Notification notification = new Notification();
        notification.setType("TEST");
        notification.setMessage("Notification de test");
        notificationService.createNotification(notification);
        return ResponseEntity.ok().build();
    }
}