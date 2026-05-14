package com.example.demo.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Entity.Notification;
import com.example.demo.Repository.NotificationRepository;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;
    
    @Autowired
    private PermissionService permissionService;
    
    @Autowired
    private com.example.demo.Repository.UserRepository userRepository;

    public List<Notification> getAllNotifications(Integer userId, com.example.demo.Entity.Role role) {
        if (userId != null || role != null) {
            return notificationRepository.findForUser(userId, role);
        }
        return java.util.Collections.emptyList();
    }

    public Optional<Notification> getNotificationById(Long id) {
        return notificationRepository.findById(id);
    }

    public Notification createNotification(Notification notification) {
        // Map notification type to permission key
        String permissionKey = mapTypeToPermission(notification.getType());
        
        // If it's a specific user notification, check if they have permission
        if (notification.getTargetUserId() != null) {
            return userRepository.findById(notification.getTargetUserId())
                .filter(user -> permissionService.hasPermission(user, permissionKey))
                .map(user -> notificationRepository.save(notification))
                .orElse(null); // Drop if no permission
        }
        
        // If it's a role-based notification, we save it (the filtering happens on fetch usually, 
        // but for role-based we can check if the role generally has it enabled)
        if (notification.getTargetRole() != null) {
            Map<String, Boolean> rolePerms = permissionService.getPermissionMapByRole(notification.getTargetRole());
            if (rolePerms.getOrDefault(permissionKey, true)) {
                return notificationRepository.save(notification);
            }
            return null;
        }
        
        return notificationRepository.save(notification);
    }

    private String mapTypeToPermission(String type) {
        if (type == null) return "DASHBOARD";
        switch (type.toUpperCase()) {
            case "INSCRIPTION": return "NOTIF_INSCRIPTION";
            case "ORDRE": return "NOTIF_ORDRE_NEW";
            case "CONFIRMATION": return "NOTIF_ORDRE_CONFIRMED";
            case "ORDRE_UPDATE": return "NOTIF_ORDRE_UPDATE";
            default: return "DASHBOARD";
        }
    }

    public Notification updateNotification(Long id, Notification updatedNotification) {
        Optional<Notification> optionalNotification = notificationRepository.findById(id);
        if (optionalNotification.isPresent()) {
            Notification existingNotification = optionalNotification.get();
            existingNotification.setMessage(updatedNotification.getMessage());
            existingNotification.setType(updatedNotification.getType());
            existingNotification.setRead(updatedNotification.isRead());
            return notificationRepository.save(existingNotification);
        }
        return null;
    }

    public List<Notification> getUnreadNotifications(Integer userId, com.example.demo.Entity.Role role) {
        if (userId != null && role != null) {
            List<Notification> targeted = notificationRepository.findForUser(userId, role);
            return targeted.stream().filter(n -> !n.isRead()).toList();
        }
        return java.util.Collections.emptyList();
    }

    public long getUnreadCount(Integer userId, com.example.demo.Entity.Role role) {
        return getUnreadNotifications(userId, role).size();
    }

    public boolean markAsRead(Long id) {
        Optional<Notification> opt = notificationRepository.findById(id);
        if (opt.isPresent()) {
            notificationRepository.delete(opt.get());
            return true;
        }
        return false;
    }

    public void markAllAsRead(Integer userId, com.example.demo.Entity.Role role) {
        if (userId == null && role == null) {
            notificationRepository.deleteAll();
        } else {
            List<Notification> toDelete = notificationRepository.findForUser(userId, role);
            notificationRepository.deleteAll(toDelete);
        }
    }

    public void deleteAllRead() {
        List<Notification> all = notificationRepository.findAll();
        List<Notification> read = all.stream().filter(Notification::isRead).toList();
        notificationRepository.deleteAll(read);
    }

    public boolean deleteNotification(Long id) {
        Optional<Notification> opt = notificationRepository.findById(id);
        if (opt.isPresent()) {
            notificationRepository.delete(opt.get());
            return true;
        }
        return false;
    }
}