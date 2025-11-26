package com.simucredito.configuration.presentation.controller;

import com.simucredito.configuration.application.dto.BonusParameterDTO;
import com.simucredito.configuration.application.dto.FinancialEntityDTO;
import com.simucredito.configuration.application.dto.GlobalValueDTO;
import com.simucredito.configuration.application.dto.UploadFinancialEntityPhotosResponseDTO;
import com.simucredito.configuration.application.service.ConfigurationService;
import com.simucredito.configuration.infrastructure.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/configuration")
@RequiredArgsConstructor
@Tag(name = "System Configuration", description = "System configuration management APIs")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class ConfigurationController {

    private final ConfigurationService configurationService;
    private final ExchangeRateService exchangeRateService;

    // Financial Entity endpoints
    @GetMapping("/financial-entities")
    @Operation(summary = "Get all financial entities", description = "Retrieve all financial entities in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Financial entities retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FinancialEntityDTO.class)))
    })
    public ResponseEntity<List<FinancialEntityDTO>> getAllFinancialEntities() {
        List<FinancialEntityDTO> entities = configurationService.getAllFinancialEntities();
        return ResponseEntity.ok(entities);
    }

    /*
    @GetMapping("/financial-entities/active")
    @Operation(summary = "Get active financial entities", description = "Retrieve all active financial entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Active financial entities retrieved successfully")
    })
    public ResponseEntity<List<FinancialEntityDTO>> getActiveFinancialEntities() {
        List<FinancialEntityDTO> entities = configurationService.getActiveFinancialEntities();
        return ResponseEntity.ok(entities);
    }
    */

    /*
    @GetMapping("/financial-entities/{id}")
    @Operation(summary = "Get financial entity by ID", description = "Retrieve a specific financial entity by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Financial entity found"),
        @ApiResponse(responseCode = "404", description = "Financial entity not found")
    })
    public ResponseEntity<FinancialEntityDTO> getFinancialEntityById(@PathVariable Long id) {
        return configurationService.getFinancialEntityById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    */

    /*
    @GetMapping("/financial-entities/code/{code}")
    @Operation(summary = "Get financial entity by code", description = "Retrieve a financial entity by its code")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Financial entity found"),
        @ApiResponse(responseCode = "404", description = "Financial entity not found")
    })
    public ResponseEntity<FinancialEntityDTO> getFinancialEntityByCode(@PathVariable String code) {
        return configurationService.getFinancialEntityByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    */

    /*
    @GetMapping("/financial-entities/for-loan/{loanAmount}")
    @Operation(summary = "Get financial entities for loan amount",
        description = "Retrieve financial entities that can handle a specific loan amount")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Financial entities retrieved successfully")
    })
    public ResponseEntity<List<FinancialEntityDTO>> getFinancialEntitiesForLoanAmount(@PathVariable BigDecimal loanAmount) {
        List<FinancialEntityDTO> entities = configurationService.getFinancialEntitiesForLoanAmount(loanAmount);
        return ResponseEntity.ok(entities);
    }
    */

    @PostMapping(value = "/financial-entities/upload-photos", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload photos for financial entity", description = "Upload multiple photos for financial entities to Firebase Storage (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Photos uploaded successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UploadFinancialEntityPhotosResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid files or too many files",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Upload failed",
                    content = @Content)
    })
    public ResponseEntity<UploadFinancialEntityPhotosResponseDTO> uploadFinancialEntityPhotos(
            @Parameter(description = "Entity photos to upload") @RequestParam("files") List<MultipartFile> files)
            throws IOException {
        UploadFinancialEntityPhotosResponseDTO response = configurationService.uploadFinancialEntityPhotos(files);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/financial-entities")
    @Operation(summary = "Create financial entity", description = "Create a new financial entity with photo URL (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Financial entity created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "Entity code already exists")
    })
    public ResponseEntity<FinancialEntityDTO> createFinancialEntity(@Valid @RequestBody FinancialEntityDTO dto) {
        FinancialEntityDTO created = configurationService.createFinancialEntity(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/financial-entities/{id}")
    @Operation(summary = "Update financial entity", description = "Update an existing financial entity (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Financial entity updated successfully"),
            @ApiResponse(responseCode = "404", description = "Financial entity not found"),
            @ApiResponse(responseCode = "409", description = "Entity code conflict")
    })
    public ResponseEntity<FinancialEntityDTO> updateFinancialEntity(@PathVariable Long id, @Valid @RequestBody FinancialEntityDTO dto) {
        return configurationService.updateFinancialEntity(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/financial-entities/{id}")
    @Operation(summary = "Delete financial entity", description = "Delete a financial entity (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Financial entity deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Financial entity not found")
    })
    public ResponseEntity<Void> deleteFinancialEntity(@PathVariable Long id) {
        return configurationService.deleteFinancialEntity(id) ?
                ResponseEntity.noContent().build() :
                ResponseEntity.notFound().build();
    }

    // Bonus Parameter endpoints
    @GetMapping("/bonus-parameters")
    @Operation(summary = "Get all bonus parameters", description = "Retrieve all bonus parameters")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bonus parameters retrieved successfully")
    })
    public ResponseEntity<List<BonusParameterDTO>> getAllBonusParameters() {
        List<BonusParameterDTO> parameters = configurationService.getAllBonusParameters();
        return ResponseEntity.ok(parameters);
    }

    /*
    @GetMapping("/bonus-parameters/type/{bonusType}")
    @Operation(summary = "Get bonus parameters by type", description = "Retrieve bonus parameters by type (BBP, BFH, etc.)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bonus parameters retrieved successfully")
    })
    public ResponseEntity<List<BonusParameterDTO>> getBonusParametersByType(@PathVariable String bonusType) {
        List<BonusParameterDTO> parameters = configurationService.getBonusParametersByType(bonusType);
        return ResponseEntity.ok(parameters);
    }
    */

    /*
    @GetMapping("/bonus-parameters/applicable")
    @Operation(summary = "Get applicable bonuses",
        description = "Get bonus parameters applicable to specific property criteria")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Applicable bonuses retrieved successfully")
    })
    public ResponseEntity<List<BonusParameterDTO>> getApplicableBonuses(
            @Parameter(description = "Bonus type (BBP, BFH)") @RequestParam String bonusType,
            @Parameter(description = "Bonus subtype") @RequestParam(required = false) String bonusSubtype,
            @Parameter(description = "Property value") @RequestParam BigDecimal propertyValue,
            @Parameter(description = "Is property sustainable") @RequestParam(required = false) Boolean isSustainable) {
        List<BonusParameterDTO> bonuses = configurationService.getApplicableBonuses(
                bonusType, bonusSubtype, propertyValue, isSustainable);
        return ResponseEntity.ok(bonuses);
    }
    */

    /*
    @PostMapping("/bonus-parameters")
    @Operation(summary = "Create bonus parameter", description = "Create a new bonus parameter (Admin only)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bonus parameter created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<BonusParameterDTO> createBonusParameter(@Valid @RequestBody BonusParameterDTO dto) {
        BonusParameterDTO created = configurationService.createBonusParameter(dto);
        return ResponseEntity.ok(created);
    }
    */

    @PutMapping("/bonus-parameters/{id}")
    @Operation(summary = "Update bonus parameter", description = "Update an existing bonus parameter (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bonus parameter updated successfully"),
            @ApiResponse(responseCode = "404", description = "Bonus parameter not found")
    })
    public ResponseEntity<BonusParameterDTO> updateBonusParameter(@PathVariable Long id, @Valid @RequestBody BonusParameterDTO dto) {
        return configurationService.updateBonusParameter(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/bonus-parameters/{id}")
    @Operation(summary = "Delete bonus parameter", description = "Delete a bonus parameter (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Bonus parameter deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Bonus parameter not found")
    })
    public ResponseEntity<Void> deleteBonusParameter(@PathVariable Long id) {
        return configurationService.deleteBonusParameter(id) ?
                ResponseEntity.noContent().build() :
                ResponseEntity.notFound().build();
    }

    // Global Value endpoints
    @GetMapping("/global-values")
    @Operation(summary = "Get all global values", description = "Retrieve all global values in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Global values retrieved successfully")
    })
    public ResponseEntity<List<GlobalValueDTO>> getAllGlobalValues() {
        List<GlobalValueDTO> values = configurationService.getAllGlobalValues();
        return ResponseEntity.ok(values);
    }

    /*
    @GetMapping("/global-values/active")
    @Operation(summary = "Get active global values", description = "Retrieve all currently active global values")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Active global values retrieved successfully")
    })
    public ResponseEntity<List<GlobalValueDTO>> getActiveGlobalValues() {
        List<GlobalValueDTO> values = configurationService.getActiveGlobalValues();
        return ResponseEntity.ok(values);
    }
    */

    /*
    @GetMapping("/global-values/key/{key}")
    @Operation(summary = "Get global value by key", description = "Retrieve a global value by its key")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Global value found"),
        @ApiResponse(responseCode = "404", description = "Global value not found")
    })
    public ResponseEntity<GlobalValueDTO> getGlobalValueByKey(@PathVariable String key) {
        return configurationService.getGlobalValueByKey(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    */

    @PostMapping("/global-values")
    @Operation(summary = "Create global value", description = "Create a new global value (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Global value created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "Value key already exists")
    })
    public ResponseEntity<GlobalValueDTO> createGlobalValue(@Valid @RequestBody GlobalValueDTO dto) {
        GlobalValueDTO created = configurationService.createGlobalValue(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/global-values/{id}")
    @Operation(summary = "Update global value", description = "Update an existing global value (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Global value updated successfully"),
            @ApiResponse(responseCode = "404", description = "Global value not found"),
            @ApiResponse(responseCode = "409", description = "Value key conflict")
    })
    public ResponseEntity<GlobalValueDTO> updateGlobalValue(@PathVariable Long id, @Valid @RequestBody GlobalValueDTO dto) {
        return configurationService.updateGlobalValue(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }


    @GetMapping("/exchange-rate/current")
    @Operation(summary = "Get current exchange rate (SUNAT)", description = "Retrieve current USD/PEN exchange rate from external API")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exchange rate retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Error fetching external API")
    })
    public ResponseEntity<ExchangeRateService.ExchangeRateDTO> getCurrentExchangeRate() {
        ExchangeRateService.ExchangeRateDTO rate = exchangeRateService.getCurrentExchangeRate();
        return ResponseEntity.ok(rate);
    }

    /*
    @DeleteMapping("/global-values/{id}")
    @Operation(summary = "Delete global value", description = "Delete a global value (Admin only)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Global value deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Global value not found")
    })
    public ResponseEntity<Void> deleteGlobalValue(@PathVariable Long id) {
        return configurationService.deleteGlobalValue(id) ?
                ResponseEntity.noContent().build() :
                ResponseEntity.notFound().build();
    }
    */
}