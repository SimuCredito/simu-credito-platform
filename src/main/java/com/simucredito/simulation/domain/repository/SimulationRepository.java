package com.simucredito.simulation.domain.repository;

import com.simucredito.simulation.domain.model.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SimulationRepository extends JpaRepository<Simulation, Long> {

    List<Simulation> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Simulation> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<Simulation> findByStatusOrderByCreatedAtDesc(Simulation.SimulationStatus status);

    @Query("SELECT s FROM Simulation s WHERE s.userId = :userId AND s.status = :status ORDER BY s.createdAt DESC")
    List<Simulation> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") Simulation.SimulationStatus status);

    @Query("SELECT COUNT(s) FROM Simulation s WHERE s.userId = :userId AND s.createdAt >= :startDate")
    Long countSimulationsByUserSince(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COUNT(s) FROM Simulation s WHERE s.createdAt >= :startDate")
    Long countSimulationsSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT AVG(s.monthlyPayment) FROM Simulation s WHERE s.status = 'COMPLETED' AND s.createdAt >= :startDate")
    Double getAverageMonthlyPaymentSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT SUM(s.totalInterest) FROM Simulation s WHERE s.status = 'COMPLETED' AND s.createdAt >= :startDate")
    Double getTotalPaymentsVolumeSince(@Param("startDate") LocalDateTime startDate);

    long countByUserId(Long userId);

    @Query(value = "SELECT cast(s.created_at as date) as date, COUNT(*) as count " +
            "FROM simulations s " +
            "WHERE s.user_id = :userId AND s.created_at >= :startDate " +
            "GROUP BY cast(s.created_at as date) " +
            "ORDER BY date ASC", nativeQuery = true)
    List<Object[]> getDailySimulationStats(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);
}