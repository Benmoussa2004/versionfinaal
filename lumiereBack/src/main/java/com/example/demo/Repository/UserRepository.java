package com.example.demo.Repository;

import com.example.demo.Entity.Role;
import com.example.demo.Entity.Status;
import com.example.demo.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(@Param("email") String email);

    List<User> findByRole(Role role);

    long countByStatus(Status status);

    Optional<User> findFirstByEmailOrderByIdAsc(@Param("email") String email);

    boolean existsByEmail(String email);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Client c JOIN c.owners o WHERE o.id = :userId")
    List<com.example.demo.Entity.Client> findClientsByUserId(@Param("userId") Integer userId);

}
