package com.example.demo;

import com.example.demo.Repository.UserRepository;
import com.example.demo.Entity.Role;
import com.example.demo.Entity.Status;
import com.example.demo.Entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs once at startup to migrate existing users.
 * Any user without a status (NULL from before the feature was added) is set to
 * ACTIVE
 * so they are not locked out by the new isEnabled() check.
 */
@Component
public class UserStatusMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserStatusMigration.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public UserStatusMigration(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        System.out.println("DEBUG: UserStatusMigration IS RUNNING!");
        log.info("🛠️ Checking database schema for missing AUTO_INCREMENT...");

        // Disable foreign key checks to allow modifying columns being referenced
        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            log.debug("Disabled foreign key checks for schema migration");

            try {
                jdbcTemplate.execute("ALTER TABLE _user MODIFY id INT NOT NULL AUTO_INCREMENT");
                log.info("✅ Database fix: Added AUTO_INCREMENT to _user.id");
            } catch (Exception e) {
                log.warn("⚠️ Failed to add AUTO_INCREMENT to _user.id: {}", e.getMessage());
            }

            try {
                jdbcTemplate.execute("ALTER TABLE notifications MODIFY id BIGINT NOT NULL AUTO_INCREMENT");
                log.info("✅ Database fix: Added AUTO_INCREMENT to notifications.id");
            } catch (Exception e) {
                log.warn("⚠️ Failed to add AUTO_INCREMENT to notifications.id: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("❌ Error during schema migration: {}", e.getMessage());
        } finally {
            try {
                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
                log.debug("Re-enabled foreign key checks");
            } catch (Exception e) {
                log.error("❌ Failed to re-enable foreign key checks: {}", e.getMessage());
            }
        }

        // Verify the schema status
        try {
            System.out.println("DEBUG: Current _user table structure:");
            jdbcTemplate.query("DESCRIBE _user", rs -> {
                System.out.println(" - Field: " + rs.getString("Field") +
                        " | Type: " + rs.getString("Type") +
                        " | Extra: " + rs.getString("Extra"));
            });

            System.out.println("DEBUG: Current client table structure:");
            jdbcTemplate.query("DESCRIBE client", rs -> {
                System.out.println(" - Field: " + rs.getString("Field") +
                        " | Type: " + rs.getString("Type") +
                        " | Extra: " + rs.getString("Extra"));
            });
        } catch (Exception e) {
            System.out.println("DEBUG: Could not describe tables: " + e.getMessage());
        }

        var usersWithNullStatus = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == null)
                .toList();

        if (!usersWithNullStatus.isEmpty()) {
            usersWithNullStatus.forEach(u -> u.setStatus(Status.ACTIVE));
            userRepository.saveAll(usersWithNullStatus);
            log.info("✅ Migrated {} existing user(s) to ACTIVE status", usersWithNullStatus.size());
        } else {
            log.info("✅ No user status migration needed");
        }

        // Ensure the main admin user exists and has ADMIN role
        String adminEmail = "razanshriif@gmail.com";
        userRepository.findFirstByEmailOrderByIdAsc(adminEmail).ifPresentOrElse(
                user -> {
                    // Update to ADMIN if they have a lower role, or downgrade from SUPERADMIN if requested
                    if (user.getRole() != Role.ADMIN) {
                        user.setRole(Role.ADMIN);
                        userRepository.save(user);
                        log.info("✅ Updated user {} to ADMIN role", adminEmail);
                    }
                },
                () -> {
                    User admin = new User();
                    admin.setFirstname("Razan");
                    admin.setLastname("Shriif");
                    admin.setEmail(adminEmail);
                    admin.setPasswd(passwordEncoder.encode("123456"));
                    admin.setRole(Role.ADMIN);
                    admin.setStatus(Status.ACTIVE);
                    userRepository.save(admin);
                    log.info("✅ Created NEW admin user: {}", adminEmail);
                });
    }
}
