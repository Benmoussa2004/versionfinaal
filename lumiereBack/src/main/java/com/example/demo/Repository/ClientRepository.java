package com.example.demo.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.example.demo.Entity.Client;
import com.example.demo.Entity.User;

public interface ClientRepository extends JpaRepository<Client, Long> {

	Optional<Client> findByCodeclient(String codeclient);

	Optional<Client> findByEmail(String email);

	// Multi-tenancy: find clients by owner
	@org.springframework.data.jpa.repository.Query("SELECT c FROM Client c JOIN c.owners o WHERE o = :owner")
	List<Client> findByOwner(@Param("owner") User owner);

	// Find specific client by owner and code
	@org.springframework.data.jpa.repository.Query("SELECT c FROM Client c JOIN c.owners o WHERE o = :owner AND c.codeclient = :codeclient")
	Optional<Client> findByOwnerAndCodeclient(@Param("owner") User owner, @Param("codeclient") String codeclient);

}
