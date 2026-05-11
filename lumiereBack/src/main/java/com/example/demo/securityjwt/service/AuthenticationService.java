package com.example.demo.securityjwt.service;

import com.example.demo.securityjwt.controller.dto.AuthenticationRequest;
import com.example.demo.securityjwt.controller.dto.AuthenticationResponse;
import com.example.demo.securityjwt.controller.dto.RegisterRequest;
import com.example.demo.Repository.UserRepository;
import com.example.demo.Entity.Role;
import com.example.demo.Entity.User;
import com.example.demo.securityjwt.utils.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.demo.Repository.ClientRepository;
import com.example.demo.Entity.Client;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final com.example.demo.Service.EmailService emailService;
    private final com.example.demo.Service.NotificationService notificationService;

    public AuthenticationService(UserRepository userRepository,
            ClientRepository clientRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            com.example.demo.Service.EmailService emailService,
            com.example.demo.Service.NotificationService notificationService) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    public AuthenticationResponse register(RegisterRequest request) {
        // Create the User record (even for PENDING clients)
        final var user = new User();
        user.setEmail(request.email());
        user.setFirstname(request.firstname());
        user.setLastname(request.lastname());
        user.setPasswd(passwordEncoder.encode(request.password()));
        user.setRole(request.role());

        // All registrations are set to PENDING by default
        user.setStatus(com.example.demo.Entity.Status.PENDING);
        userRepository.save(user);

        // Create a Client record for ALL users to track their approval/profile
        Client client = new Client();
        client.setNom(request.lastname());
        client.setEmail(request.email());
        client.setOwner(user);
        client.setRegistrationApproved(false);
        client.setProfileCompleted(false);
        
        // Map registration fields
        client.setTelephone(request.telephone());
        client.setAdresse(request.adresse());
        client.setVille(request.ville());
        client.setPays(request.pays());
        client.setCodepostal(request.codepostal());
        client.setCivilite(request.civilite());
        client.setType(request.type());
        client.setSocieteFacturation(request.societeFacturation());
        
        clientRepository.save(client);

        logger.info("Pending registration created for: {}", request.email());

        // Notifications...
        sendNotifications(request);

        return new AuthenticationResponse(null); // No token for pending users
    }

    private void sendNotifications(RegisterRequest request) {
        // Send registration email
        try {
            emailService.sendRegistrationEmail(request.email(), request.firstname() + " " + request.lastname());
        } catch (Exception e) {
            logger.error("Failed to send email: {}", e.getMessage());
        }

        // Notify admins
        try {
            emailService.sendNewRegistrationNotificationToAdmins(
                    request.firstname() + " " + request.lastname(),
                    request.email());
        } catch (Exception e) {
            logger.error("Failed to notify admins: {}", e.getMessage());
        }

        // In-app notification
        try {
            com.example.demo.Entity.Notification notification = new com.example.demo.Entity.Notification();
            notification.setType("Inscription");
            notification.setMessage("Nouvelle inscription : " + request.firstname() + " " + request.lastname());
            notification.setRead(false);
            notification.setTargetRole(com.example.demo.Entity.Role.ADMIN); // Changed to ADMIN to match user role
            notificationService.createNotification(notification);
        } catch (Exception e) {
            logger.error("Failed to create UI notification: {}", e.getMessage());
        }
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()));
        final var user = userRepository.findFirstByEmailOrderByIdAsc(request.email()).orElseThrow();

        if (user.getStatus() != com.example.demo.Entity.Status.ACTIVE) {
            logger.warn("Login attempt for non-active account: {} (Status: {})", request.email(), user.getStatus());
            throw new RuntimeException("Votre compte est en attente de validation ou a été désactivé.");
        }

        final var token = JwtService.generateToken(user);
        return new AuthenticationResponse(token);
    }
}
