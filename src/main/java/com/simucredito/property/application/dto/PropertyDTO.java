package com.simucredito.property.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyDTO {

    private Long id;
    private Long userId;
    private Long propertyTypeId;
    private String nombreProyecto;
    private String descripcion;
    private String estadoInmueble;
    private String ubicacionGeografica;
    private BigDecimal builtArea;
    private BigDecimal landArea;
    private Integer bedrooms;
    private Integer bathrooms;
    private Integer garages;
    private BigDecimal propertyPrice;
    private Boolean isSustainable;
    private String[] photos;
    private LocalDateTime registrationDate;
}