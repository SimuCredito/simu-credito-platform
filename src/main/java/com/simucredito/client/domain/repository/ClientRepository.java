package com.simucredito.client.domain.repository;

import com.simucredito.client.domain.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findByUserId(Long userId);

    Optional<Client> findByHolderId(Long holderId);

    boolean existsByHolderId(Long holderId);

    boolean existsBySpouseId(Long spouseId);

    @Query("SELECT c FROM Client c WHERE c.userId = :userId AND (c.holderId = :personId OR c.spouseId = :personId)")
    Optional<Client> findByUserIdAndPersonId(@Param("userId") Long userId, @Param("personId") Long personId);

    long countByUserId(Long userId);
    long countByUserIdAndPreQualifiedTrue(Long userId);
    // Para clientes de este mes:
    @Query("SELECT COUNT(c) FROM Client c WHERE c.userId = :userId AND c.registrationDate >= :startDate")
    long countClientsByUserSince(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);
}