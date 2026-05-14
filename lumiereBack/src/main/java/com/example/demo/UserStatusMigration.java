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

            // [NEW] Data Migration: Update legacy roles to new values BEFORE changing enum definition
            try {
                jdbcTemplate.execute("UPDATE _user SET role = 'ADMIN' WHERE role = 'SUPERADMIN'");
                jdbcTemplate.execute("UPDATE _user SET role = 'CLIENT' WHERE role IN ('USER', 'USER_OTFLOW')");
                jdbcTemplate.execute("UPDATE _user SET role = 'EMPLOYER_LUMIERE' WHERE role = 'USER_LUMIERE'");
                log.info("✅ Data Migration: Updated legacy roles in _user");
            } catch (Exception e) {
                log.warn("⚠️ Data migration for _user failed: {}", e.getMessage());
            }

            try {
                // [FIX] Only clear if we need a fresh start, otherwise we lose user-defined permissions
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class);
                if (count == null || count == 0) {
                    log.info("ℹ️ role_permissions is empty, seeding defaults...");
                } else {
                    log.info("ℹ️ role_permissions already has data, skipping cleanup to preserve user changes.");
                    // Optional: If you want to force re-seed, keep DELETE. 
                    // But for now, let's COMMENT it out to solve the user's problem.
                    // jdbcTemplate.execute("DELETE FROM role_permissions"); 
                }
            } catch (Exception e) {
                log.warn("⚠️ Failed to check/clear role_permissions: {}", e.getMessage());
            }

            // Fix: Add consolidated roles to role enum in _user table
            try {
                jdbcTemplate.execute("ALTER TABLE _user MODIFY COLUMN role ENUM('ADMIN','CLIENT','COMMERCIAL','EMPLOYER_LUMIERE')");
                log.info("✅ Database fix: Updated _user.role enum to 4 main actors");
            } catch (Exception e) {
                log.warn("⚠️ Failed to update _user.role enum: {}", e.getMessage());
            }
 
            // Fix: Add consolidated roles to role enum in role_permissions table
            try {
                jdbcTemplate.execute("ALTER TABLE role_permissions MODIFY COLUMN role ENUM('ADMIN','CLIENT','COMMERCIAL','EMPLOYER_LUMIERE')");
                log.info("✅ Database fix: Updated role_permissions.role enum to 4 main actors");
            } catch (Exception e) {
                log.warn("⚠️ Failed to update role_permissions.role enum: {}", e.getMessage());
            }

            // Drop deprecated owner_id column from client table if it exists
            try {
                jdbcTemplate.execute("ALTER TABLE client DROP COLUMN owner_id");
                log.info("✅ Database fix: Dropped deprecated owner_id from client table");
            } catch (Exception e) {
                // Column might already be gone
            }

            // Fix Ordre foreign key to allow deleting users (ON DELETE SET NULL)
            try {
                // First find the constraint name (usually something like FK_...)
                String constraintName = jdbcTemplate.queryForObject(
                    "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                    "WHERE TABLE_NAME = 'ordre' AND COLUMN_NAME = 'owner_id' AND REFERENCED_TABLE_NAME = '_user' LIMIT 1", String.class);
                
                if (constraintName != null) {
                    jdbcTemplate.execute("ALTER TABLE ordre DROP FOREIGN KEY " + constraintName);
                    jdbcTemplate.execute("ALTER TABLE ordre ADD CONSTRAINT " + constraintName + 
                        " FOREIGN KEY (owner_id) REFERENCES _user(id) ON DELETE SET NULL");
                    log.info("✅ Database fix: Updated Ordre foreign key with ON DELETE SET NULL");
                }
            } catch (Exception e) {
                log.warn("⚠️ Could not update Ordre foreign key: {}", e.getMessage());
            }

            // Create join table for user_clients if it doesn't exist
            try {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_clients (user_id INT, client_id BIGINT, PRIMARY KEY (user_id, client_id))");
                log.info("✅ Database fix: Created user_clients join table");
            } catch (Exception e) {
                log.warn("⚠️ Failed to create user_clients table: {}", e.getMessage());
            }

            // [NEW] Data Migration: Ensure every CLIENT user has a record in user_clients
            try {
                // Fix: ensure confiere has a default value to avoid INSERT failures
                try {
                    jdbcTemplate.execute("ALTER TABLE client MODIFY COLUMN confiere BIT(1) DEFAULT 0");
                } catch (Exception e) {}

                // 1. Create a client record if missing for any CLIENT user
                jdbcTemplate.execute(
                    "INSERT INTO client (email, nom, registration_approved, profile_completed, pays, codepostal, type, confiere) " +
                    "SELECT email, lastname, 0, 0, 'Tunisie', 0, 'Standard', 0 FROM _user " +
                    "WHERE role = 'CLIENT' AND email NOT IN (SELECT email FROM client)"
                );
                
                // 2. Link any unlinked CLIENT users to their client record (via email)
                int linked = jdbcTemplate.update(
                    "INSERT INTO user_clients (user_id, client_id) " +
                    "SELECT u.id, c.code FROM _user u JOIN client c ON u.email = c.email " +
                    "WHERE u.role = 'CLIENT' " +
                    "AND u.id NOT IN (SELECT user_id FROM user_clients)"
                );
                
                log.info("✅ Data Migration: Linked {} orphan CLIENT users to client records", linked);
            } catch (Exception e) {
                log.warn("⚠️ Data migration for user_clients failed: {}", e.getMessage());
            }

            // [NEW] Debug: Print all CLIENT users and their links
            try {
                log.info("DEBUG: Checking all CLIENT users and their links...");
                jdbcTemplate.query("SELECT u.id, u.email, (SELECT COUNT(*) FROM user_clients uc WHERE uc.user_id = u.id) as link_count FROM _user u WHERE u.role = 'CLIENT'", rs -> {
                    log.info(" - User ID: {} | Email: {} | Links: {}", rs.getInt("id"), rs.getString("email"), rs.getInt("link_count"));
                });
            } catch (Exception e) {}

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

        // Migrate statuses (legacy loop removed to prevent enum crash)
        jdbcTemplate.execute("UPDATE _user SET status = 'ACTIVE' WHERE status IS NULL");
        log.info("✅ Migrated user statuses to ACTIVE");

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

        // Ensure test client user exists and is ACTIVE
        String testEmail = "amin@gmail.com";
        userRepository.findFirstByEmailOrderByIdAsc(testEmail).ifPresentOrElse(
                user -> {
                    boolean changed = false;
                    if (user.getStatus() != Status.ACTIVE) {
                        user.setStatus(Status.ACTIVE);
                        changed = true;
                    }
                    // Force password reset to amin123 for testing
                    user.setPasswd(passwordEncoder.encode("amin123"));
                    changed = true;
                    
                    if (changed) {
                        userRepository.save(user);
                        log.info("✅ Activated and reset password for existing user {}", testEmail);
                    }
                },
                () -> {
                    User testUser = new User();
                    testUser.setFirstname("Amin");
                    testUser.setLastname("Client");
                    testUser.setEmail(testEmail);
                    testUser.setPasswd(passwordEncoder.encode("amin123"));
                    testUser.setRole(Role.CLIENT);
                    testUser.setStatus(Status.ACTIVE);
                    userRepository.save(testUser);
                    log.info("✅ Created NEW test client user: {}", testEmail);
                });
    }
}
