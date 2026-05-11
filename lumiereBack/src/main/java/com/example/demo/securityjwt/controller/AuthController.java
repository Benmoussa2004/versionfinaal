package com.example.demo.securityjwt.controller;

import com.example.demo.securityjwt.controller.dto.AuthenticationRequest;
import com.example.demo.securityjwt.controller.dto.AuthenticationResponse;
import com.example.demo.securityjwt.service.AuthenticationService;
import com.example.demo.securityjwt.controller.dto.RegisterRequest;
import com.example.demo.Entity.User;
import com.example.demo.Repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin("*")
public record AuthController(AuthenticationService authenticationService, UserRepository userRepository) {

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authenticationService.register(request));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> login(@RequestBody AuthenticationRequest request) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

    @PutMapping("/update")
    public ResponseEntity<User> updateUser(@RequestBody RegisterRequest request) {

        User user = userRepository.findById(request.id()).orElseThrow(() -> new RuntimeException("User not found"));

        // Mettre à jour les informations de l'utilisateur
        user.setFirstname(request.firstname());
        user.setLastname(request.lastname());
        user.setEmail(request.email());

        // Sauvegarder les changements
        userRepository.save(user);

        return ResponseEntity.ok(user);
    }

    @GetMapping("/profile")
    public ResponseEntity<User> getProfile() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String email = authentication.getName();
        if (email == null || email.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findFirstByEmailOrderByIdAsc(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());

    }

    @GetMapping("/profileALL")
    public List<User> getALLProfile() {
        List<User> users = userRepository.findAll();
        users.forEach(u -> {
            if (!u.getOwnedClients().isEmpty()) {
                com.example.demo.Entity.Client firstClient = u.getOwnedClients().get(0);
                u.setRegistrationApproved(firstClient.isRegistrationApproved());
                u.setLinkedClientId(firstClient.getCode());
            }
        });
        return users;
    }
}
