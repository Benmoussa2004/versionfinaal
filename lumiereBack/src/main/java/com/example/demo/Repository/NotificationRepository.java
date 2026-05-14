package com.example.demo.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.Entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Méthode pour trouver des notifications non lues
    List<Notification> findByIsReadFalse();

    // Méthode pour trouver des notifications par type
    List<Notification> findByType(@Param("type") String type);

    @org.springframework.data.jpa.repository.Query("SELECT n FROM Notification n " +
            "WHERE n.targetUserId = :userId " +
            "OR n.targetRole = :role " +
            "OR (n.targetRole = 'ADMIN' AND :role IN (com.example.demo.Entity.Role.COMMERCIAL, com.example.demo.Entity.Role.EMPLOYER_LUMIERE)) " +
            "ORDER BY n.timestamp DESC")
    List<Notification> findForUser(@Param("userId") Integer userId, @Param("role") com.example.demo.Entity.Role role);

}