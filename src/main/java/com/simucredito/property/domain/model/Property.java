package com.simucredito.property.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "inmuebles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long userId;

    @Column(name = "tipo_inmueble_id")
    private Long propertyTypeId;

    @Column(nullable = false)
    private String nombreProyecto;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    private String estadoInmueble;

    @Column(columnDefinition = "TEXT")
    private String ubicacionGeografica;

    @Column(name = "area_construida", precision = 10, scale = 2)
    private BigDecimal builtArea;

    @Column(name = "area_terreno", precision = 10, scale = 2)
    private BigDecimal landArea;

    @Column(name = "num_dormitorios")
    private Integer bedrooms;

    @Column(name = "num_banos")
    private Integer bathrooms;

    @Column(name = "num_cocheras")
    private Integer garages;

    @Column(name = "precio_vivienda", nullable = false, precision = 12, scale = 2)
    private BigDecimal propertyPrice;

    @Column(name = "es_sostenible")
    @Builder.Default
    private Boolean isSustainable = false;

    @Column(columnDefinition = "TEXT")
    private String photos;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime registrationDate;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        registrationDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
