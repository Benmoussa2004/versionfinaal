package com.example.demo.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.UserRepository;
import com.example.demo.Entity.User;
import com.example.demo.Entity.Role;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Integer id) {
        return userRepository.findById(id);
    }

    public User saveUser(User user) {
        // [NEW] Duplicate email check for new users
        if (user.getId() == null && user.getEmail() != null && userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Un utilisateur avec cet email existe déjà.");
        }

        // Only encode if it's not null and doesn't look like an already encoded bcrypt string
        if (user.getPasswd() != null && !user.getPasswd().startsWith("$2a$")) {
            user.setPasswd(passwordEncoder.encode(user.getPasswd()));
        }
        return userRepository.save(user);
    }

    public User updateProfileInfo(Integer userId, String firstname, String lastname, String profileImageBase64) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (firstname != null) user.setFirstname(firstname);
            if (lastname != null) user.setLastname(lastname);
            if (profileImageBase64 != null) user.setProfileImageBase64(profileImageBase64);
            return userRepository.save(user);
        }
        throw new RuntimeException("Utilisateur introuvable");
    }

    public boolean updatePassword(Integer userId, String currentPassword, String newPassword) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (passwordEncoder.matches(currentPassword, user.getPasswd())) {
                user.setPasswd(passwordEncoder.encode(newPassword));
                userRepository.save(user);
                return true;
            }
        }
        return false;
    }

    public void deleteUser(Integer id) {
        userRepository.deleteById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findFirstByEmailOrderByIdAsc(email);
    }

    // Get only CLIENT users (for COMMERCIAL role access)
    public List<User> getClientUsers() {
        return userRepository.findByRole(Role.CLIENT);
    }
}
