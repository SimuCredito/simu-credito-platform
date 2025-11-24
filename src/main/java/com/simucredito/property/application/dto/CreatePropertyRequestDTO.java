package com.simucredito.property.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePropertyRequestDTO {

    private Long propertyTypeId;

    @NotBlank(message = "Project name is required")
    private String nombreProyecto;

    private String descripcion;

    private String estadoInmueble;

    private String ubicacionGeografica;

    private BigDecimal builtArea;

    private BigDecimal landArea;

    private Integer bedrooms;

    private Integer bathrooms;

    private Integer garages;

    @NotNull(message = "Property price is required")
    private BigDecimal propertyPrice;

    private Boolean isSustainable;

    private String[] photos;
}