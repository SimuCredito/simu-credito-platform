package com.simucredito.property.domain.repository;

import com.simucredito.property.domain.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findByUserId(Long userId);

    List<Property> findByPropertyTypeId(Long propertyTypeId);

    List<Property> findByIsSustainable(Boolean isSustainable);

    @Query("SELECT p FROM Property p WHERE p.userId = :userId AND p.propertyPrice BETWEEN :minPrice AND :maxPrice")
    List<Property> findByUserIdAndPriceRange(@Param("userId") Long userId,
                                           @Param("minPrice") BigDecimal minPrice,
                                           @Param("maxPrice") BigDecimal maxPrice);

    @Query("SELECT p FROM Property p WHERE p.userId = :userId AND LOWER(p.nombreProyecto) LIKE LOWER(CONCAT('%', :projectName, '%'))")
    List<Property> findByUserIdAndProjectName(@Param("userId") Long userId,
                                            @Param("projectName") String projectName);

    @Query("SELECT p FROM Property p WHERE p.userId = :userId AND p.estadoInmueble = :status")
    List<Property> findByUserIdAndStatus(@Param("userId") Long userId,
                                       @Param("status") String status);

    long countByUserId(Long userId);
    long countByUserIdAndIsSustainableTrue(Long userId);
    @Query("SELECT COUNT(p) FROM Property p WHERE p.userId = :userId AND p.createdAt >= :startDate")
    long countPropertiesByUserSince(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate);
}