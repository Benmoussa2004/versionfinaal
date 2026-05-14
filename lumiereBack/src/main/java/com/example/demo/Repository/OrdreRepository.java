package com.example.demo.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.Entity.Client;
import com.example.demo.Entity.Ordre;
import com.example.demo.Entity.Statut;

public interface OrdreRepository extends JpaRepository<Ordre, Long>, JpaSpecificationExecutor<Ordre> {
	long countByStatut(Statut statut);
	List<Ordre> findByVoycle(String voycle);

	@Query("SELECT COUNT(o) FROM Ordre o WHERE o.statut = 'NON_PLANIFIE'")
	long countNonPlanifieOrders();

	@Query("SELECT COUNT(o) FROM Ordre o WHERE o.statut = 'PLANIFIE'")
	long countPlanifieOrders();

	@Query("SELECT COUNT(o) FROM Ordre o WHERE o.statut = 'EN_COURS_DE_CHARGEMENT'")
	long countEnCoursDeChargementOrders();

	@Query("SELECT COUNT(o) FROM Ordre o WHERE o.statut = 'CHARGE'")
	long countChargeOrders();

	@Query("SELECT COUNT(o) FROM Ordre o WHERE o.statut = 'EN_COURS_DE_LIVRAISON'")
	long countEnCoursDeLivraisonOrders();

	@Query("SELECT COUNT(o) FROM Ordre o WHERE o.statut = 'LIVRE'")
	long countLivreOrders();

	List<Ordre> findByStatut(Statut nonPlanifie);

	List<Ordre> findTop1000ByOrderByIdDesc();

	@Query("SELECT o FROM Ordre o WHERE o.client IN :clientCodes AND o.statut IN (com.example.demo.Entity.Statut.NON_PLANIFIE, com.example.demo.Entity.Statut.PLANIFIE, com.example.demo.Entity.Statut.EN_COURS_DE_CHARGEMENT, com.example.demo.Entity.Statut.CHARGE, com.example.demo.Entity.Statut.EN_COURS_DE_LIVRAISON)")
	List<Ordre> findActiveOrdersByClientCodes(@Param("clientCodes") List<String> clientCodes);

	@Query("SELECT COUNT(o) FROM Ordre o WHERE o.client IN :clientCodes AND o.statut = :statut")
	long countByClientCodesAndStatut(@Param("clientCodes") List<String> clientCodes, @Param("statut") Statut statut);

	@Query("SELECT COUNT(o) FROM Ordre o WHERE o.client IN :clientCodes")
	long countByClientCodes(@Param("clientCodes") List<String> clientCodes);

	@Query("SELECT o FROM Ordre o WHERE o.client IN :clientCodes AND o.statut != com.example.demo.Entity.Statut.NON_CONFIRME ORDER BY o.id DESC")
	List<Ordre> findRecentOrdersByClientCodes(@Param("clientCodes") List<String> clientCodes,
			org.springframework.data.domain.Pageable pageable);

	List<Ordre> findByClientOrderByIdDesc(String client);
	
	Optional<Ordre> findByOrderNumber(String orderNumber);

	@Query("SELECT o FROM Ordre o WHERE o.client IN :clientCodes OR o.owner = :owner ORDER BY o.id DESC")
	List<Ordre> findByClientCodesAndOwner(@Param("clientCodes") List<String> clientCodes, @Param("owner") com.example.demo.Entity.User owner);

	@Query("SELECT COUNT(o) FROM Ordre o WHERE o.client IN :clientCodes AND o.owner = :owner")
	long countByClientCodesAndOwner(@Param("clientCodes") List<String> clientCodes, @Param("owner") com.example.demo.Entity.User owner);

	@Query("SELECT COUNT(o) FROM Ordre o WHERE o.client IN :clientCodes AND o.owner = :owner AND o.statut = :statut")
	long countByClientCodesAndOwnerAndStatut(@Param("clientCodes") List<String> clientCodes, @Param("owner") com.example.demo.Entity.User owner, @Param("statut") Statut statut);
}

