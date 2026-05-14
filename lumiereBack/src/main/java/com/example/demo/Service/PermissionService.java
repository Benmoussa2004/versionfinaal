package com.example.demo.Service;

import com.example.demo.Entity.Role;
import com.example.demo.Entity.RolePermission;
import com.example.demo.Entity.User;
import com.example.demo.Entity.UserPermission;
import com.example.demo.Repository.RolePermissionRepository;
import com.example.demo.Repository.UserPermissionRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final RolePermissionRepository repository;
    private final UserPermissionRepository userPermissionRepository;

    public PermissionService(RolePermissionRepository repository, UserPermissionRepository userPermissionRepository) {
        this.repository = repository;
        this.userPermissionRepository = userPermissionRepository;
    }

    public List<RolePermission> getAllPermissions() {
        return repository.findAll();
    }

    public List<RolePermission> getPermissionsByRole(Role role) {
        return repository.findByRole(role);
    }

    public Map<String, Boolean> getPermissionMapByRole(Role role) {
        return repository.findByRole(role).stream()
                .collect(Collectors.toMap(RolePermission::getFeatureKey, RolePermission::isEnabled));
    }

    public List<UserPermission> getPermissionsByUser(User user) {
        return userPermissionRepository.findByUser(user);
    }

    public Map<String, Boolean> getPermissionMapByUser(User user) {
        return userPermissionRepository.findByUser(user).stream()
                .collect(Collectors.toMap(UserPermission::getFeatureKey, UserPermission::isEnabled));
    }

    public boolean hasPermission(User user, String featureKey) {
        if (user == null) return false;

        // 1. Check for specific user permission
        Optional<UserPermission> up = userPermissionRepository.findByUserAndFeatureKey(user, featureKey);
        if (up.isPresent()) {
            return up.get().isEnabled();
        }

        // 2. Fallback to role-based permission
        return repository.findByRoleAndFeatureKey(user.getRole(), featureKey)
                .map(RolePermission::isEnabled)
                .orElse(false);
    }

    @Transactional
    public void updatePermissions(List<RolePermission> permissions) {
        for (RolePermission p : permissions) {
            repository.findByRoleAndFeatureKey(p.getRole(), p.getFeatureKey())
                    .ifPresentOrElse(
                            existing -> {
                                existing.setEnabled(p.isEnabled());
                                repository.save(existing);
                            },
                            () -> repository.save(p));
        }
    }

    @Transactional
    public void updateUserPermissions(User user, List<UserPermission> permissions) {
        for (UserPermission p : permissions) {
            userPermissionRepository.findByUserAndFeatureKey(user, p.getFeatureKey())
                    .ifPresentOrElse(
                            existing -> {
                                existing.setEnabled(p.isEnabled());
                                userPermissionRepository.save(existing);
                            },
                            () -> {
                                p.setUser(user);
                                userPermissionRepository.save(p);
                            });
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedPermissions() {
        String[] features = {
                "DASHBOARD",
                "ORDRES", "ORDRES_ADD", "ORDRES_EDIT", "ORDRES_DELETE", "ORDRES_VALIDATE", "ORDRES_TRACK",
                "CLIENTS", "CLIENTS_ADD", "CLIENTS_EDIT", "CLIENTS_DELETE",
                "ARTICLES", "ARTICLES_ADD", "ARTICLES_EDIT", "ARTICLES_DELETE",
                "USERS", "USERS_ADD", "USERS_EDIT", "USERS_DELETE",
                "EMAILS",
                "NOTIF_INSCRIPTION", "NOTIF_ORDRE_NEW", "NOTIF_ORDRE_CONFIRMED", "NOTIF_ORDRE_UPDATE"
        };

        for (Role role : Role.values()) {
            for (String feature : features) {
                if (repository.findByRoleAndFeatureKey(role, feature).isEmpty()) {
                    boolean enabled = (role == Role.ADMIN || role == Role.COMMERCIAL);

                    // Defaults for CLIENT / EMPLOYER_LUMIERE
                    if ((role == Role.CLIENT || role == Role.EMPLOYER_LUMIERE)) {
                        List<String> allowedForAll = Arrays.asList(
                                "DASHBOARD", "ORDRES", "ORDRES_TRACK", "EMAILS");
                        if (allowedForAll.contains(feature)) {
                            enabled = true;
                        }

                        // Employer Lumiere can also add orders
                        if (role == Role.EMPLOYER_LUMIERE
                                && (feature.equals("ORDRES_ADD") || feature.equals("ORDRES_VALIDATE"))) {
                            enabled = true;
                        }
                    }

                    RolePermission rp = new RolePermission();
                    rp.setRole(role);
                    rp.setFeatureKey(feature);
                    rp.setEnabled(enabled);
                    repository.save(rp);
                }
            }
        }
    }
}
