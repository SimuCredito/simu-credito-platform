package com.simucredito.property.application.service;

import com.simucredito.property.application.dto.CreatePropertyRequestDTO;
import com.simucredito.property.application.dto.PropertyDTO;
import com.simucredito.property.domain.model.Property;
import com.simucredito.property.domain.repository.PropertyRepository;
import com.simucredito.iam.domain.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public PropertyDTO createProperty(CreatePropertyRequestDTO request) {
        Long userId = getCurrentUserId();

        // Convert photos array to comma-separated string for storage
        String photosString = null;
        if (request.getPhotos() != null && request.getPhotos().length > 0) {
            photosString = String.join(",", request.getPhotos());
        }

        Property property = Property.builder()
                .userId(userId)
                .propertyTypeId(request.getPropertyTypeId())
                .nombreProyecto(request.getNombreProyecto())
                .descripcion(request.getDescripcion())
                .estadoInmueble(request.getEstadoInmueble())
                .ubicacionGeografica(request.getUbicacionGeografica())
                .builtArea(request.getBuiltArea())
                .landArea(request.getLandArea())
                .bedrooms(request.getBedrooms())
                .bathrooms(request.getBathrooms())
                .garages(request.getGarages())
                .propertyPrice(request.getPropertyPrice())
                .isSustainable(request.getIsSustainable())
                .photos(photosString)
                .build();

        property = propertyRepository.save(property);
        return convertToDTO(property);
    }

    public List<PropertyDTO> getPropertiesByUser() {
        Long userId = getCurrentUserId();
        List<Property> properties = propertyRepository.findByUserId(userId);

        return properties.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public PropertyDTO getPropertyById(Long propertyId) {
        Long userId = getCurrentUserId();
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found"));

        // Verify property belongs to current user
        if (!property.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        return convertToDTO(property);
    }

    public List<PropertyDTO> searchProperties(String projectName, BigDecimal minPrice, BigDecimal maxPrice,
                                            Long propertyTypeId, Boolean isSustainable, String status) {
        Long userId = getCurrentUserId();
        List<Property> properties;

        // Apply filters based on provided parameters
        if (projectName != null && !projectName.trim().isEmpty()) {
            properties = propertyRepository.findByUserIdAndProjectName(userId, projectName.trim());
        } else if (minPrice != null && maxPrice != null) {
            properties = propertyRepository.findByUserIdAndPriceRange(userId, minPrice, maxPrice);
        } else if (status != null && !status.trim().isEmpty()) {
            properties = propertyRepository.findByUserIdAndStatus(userId, status.trim());
        } else if (propertyTypeId != null) {
            properties = propertyRepository.findByUserId(userId).stream()
                    .filter(p -> propertyTypeId.equals(p.getPropertyTypeId()))
                    .collect(Collectors.toList());
        } else if (isSustainable != null) {
            properties = propertyRepository.findByUserId(userId).stream()
                    .filter(p -> isSustainable.equals(p.getIsSustainable()))
                    .collect(Collectors.toList());
        } else {
            properties = propertyRepository.findByUserId(userId);
        }

        return properties.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public PropertyDTO updateProperty(Long propertyId, CreatePropertyRequestDTO request) {
        Long userId = getCurrentUserId();
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found"));

        if (!property.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        // Update property fields
        property.setPropertyTypeId(request.getPropertyTypeId());
        property.setNombreProyecto(request.getNombreProyecto());
        property.setDescripcion(request.getDescripcion());
        property.setEstadoInmueble(request.getEstadoInmueble());
        property.setUbicacionGeografica(request.getUbicacionGeografica());
        property.setBuiltArea(request.getBuiltArea());
        property.setLandArea(request.getLandArea());
        property.setBedrooms(request.getBedrooms());
        property.setBathrooms(request.getBathrooms());
        property.setGarages(request.getGarages());
        property.setPropertyPrice(request.getPropertyPrice());
        property.setIsSustainable(request.getIsSustainable());

        // Convert photos array to comma-separated string for storage
        String photosString = null;
        if (request.getPhotos() != null && request.getPhotos().length > 0) {
            photosString = String.join(",", request.getPhotos());
        }
        property.setPhotos(photosString);

        property = propertyRepository.save(property);
        return convertToDTO(property);
    }

    @Transactional
    public void deleteProperty(Long propertyId) {
        Long userId = getCurrentUserId();
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found"));

        if (!property.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        propertyRepository.delete(property);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            return user.getId();
        }
        throw new RuntimeException("User not authenticated");
    }

    private PropertyDTO convertToDTO(Property property) {
        PropertyDTO dto = modelMapper.map(property, PropertyDTO.class);

        // Convert comma-separated photos string to array
        if (property.getPhotos() != null && !property.getPhotos().trim().isEmpty()) {
            dto.setPhotos(property.getPhotos().split(","));
        } else {
            dto.setPhotos(new String[0]);
        }

        return dto;
    }

    private String convertPhotosToString(String photos) {
        if (photos == null || photos.trim().isEmpty()) {
            return null;
        }

        // Check if it's a JSON array string (starts with [ and ends with ])
        if (photos.trim().startsWith("[") && photos.trim().endsWith("]")) {
            try {
                // Parse JSON array string to String array
                String[] photoArray = objectMapper.readValue(photos, String[].class);
                return String.join(",", photoArray);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse photos JSON array, treating as comma-separated string: {}", photos);
                // If JSON parsing fails, treat as comma-separated string
                return photos;
            }
        } else {
            // It's already a comma-separated string or single value
            return photos;
        }
    }
}