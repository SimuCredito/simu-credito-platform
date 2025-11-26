package com.simucredito.simulation.presentation.controller;

import com.simucredito.simulation.application.dto.CreateSimulationRequestDTO;
import com.simucredito.simulation.application.dto.SimulationResponseDTO;
import com.simucredito.simulation.application.service.SimulationService;
import com.simucredito.simulation.domain.model.Simulation;
import com.simucredito.configuration.application.service.ConfigurationService;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
@Tag(name = "Credit Simulation", description = "Credit simulation management APIs")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class SimulationController {

    private final SimulationService simulationService;
    private final ConfigurationService configurationService;

    @PostMapping
    @Operation(summary = "Create credit simulation", description = "Create a new credit simulation with full financial calculations including TCEA, VAN, TIR, and complete amortization schedule")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Simulation created successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = SimulationResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "Client, property or financial entity not found",
            content = @Content)
    })
    public ResponseEntity<SimulationResponseDTO> createSimulation(@Valid @RequestBody CreateSimulationRequestDTO request) {
        // Validate financial entity limits if entity is selected
        if (request.getFinancialEntityId() != null) {
            validateFinancialEntityLimits(request);
        }

        SimulationResponseDTO response = simulationService.createSimulation(request);
        return ResponseEntity.ok(response);
    }

    private void validateFinancialEntityLimits(CreateSimulationRequestDTO request) {
        // Get financial entity details
        var financialEntityOpt = configurationService.getFinancialEntityById(request.getFinancialEntityId());
        if (financialEntityOpt.isEmpty()) {
            return; // Entity not found, skip validation
        }

        var financialEntity = financialEntityOpt.get();

        BigDecimal financingAmountInPen = request.getCalculatedValues().getFinancingAmount();

        if ("USD".equals(request.getFinancingDetails().getCurrency())) {
            BigDecimal exchangeRate = request.getFinancingDetails().getUsdValue();
            if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Exchange rate (usdValue) is required for USD simulations");
            }
            financingAmountInPen = financingAmountInPen.multiply(exchangeRate);
        }

        // Check financing amount against entity limits
        if (financialEntity.getMaxLoanAmount() != null &&
                financingAmountInPen.compareTo(financialEntity.getMaxLoanAmount()) > 0) {
            throw new IllegalArgumentException("Financing amount (converted to PEN) exceeds entity maximum limit");
        }

        if (financialEntity.getMinLoanAmount() != null &&
                financingAmountInPen.compareTo(financialEntity.getMinLoanAmount()) < 0) {
            throw new IllegalArgumentException("Financing amount (converted to PEN) is below entity minimum limit");
        }

        // Validate term against entity limits
        int termMonths = request.getFinancingDetails().getTermYears() * 12;
        if (financialEntity.getMaxTermMonths() != null && termMonths > financialEntity.getMaxTermMonths()) {
            throw new IllegalArgumentException("Loan term exceeds entity maximum term limit");
        }

        if (financialEntity.getMinTermMonths() != null && termMonths < financialEntity.getMinTermMonths()) {
            throw new IllegalArgumentException("Loan term is below entity minimum term limit");
        }

        // Validate interest rate within entity ranges (if entity has defined rates)
        BigDecimal requestRate = request.getFinancingDetails().getInterestRate().getRate();
        if (financialEntity.getInterestRateTea() != null &&
            "TE".equals(request.getFinancingDetails().getInterestRate().getType())) {
            // Allow some tolerance (e.g., 10% above entity rate)
            BigDecimal maxAllowedRate = financialEntity.getInterestRateTea().multiply(BigDecimal.valueOf(1.1));
            if (requestRate.compareTo(maxAllowedRate) > 0) {
                throw new IllegalArgumentException("Interest rate exceeds entity allowed range");
            }
        }

        if (financialEntity.getInterestRateTna() != null &&
            "TN".equals(request.getFinancingDetails().getInterestRate().getType())) {
            BigDecimal maxAllowedRate = financialEntity.getInterestRateTna().multiply(BigDecimal.valueOf(1.1));
            if (requestRate.compareTo(maxAllowedRate) > 0) {
                throw new IllegalArgumentException("Interest rate exceeds entity allowed range");
            }
        }
    }

    @GetMapping
    @Operation(summary = "Get user simulations", description = "Retrieve all simulations for the authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Simulations retrieved successfully")
    })
    public ResponseEntity<List<SimulationResponseDTO>> getUserSimulations() {
        List<SimulationResponseDTO> simulations = simulationService.getUserSimulations();
        return ResponseEntity.ok(simulations);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get simulation by ID", description = "Retrieve a specific simulation with full amortization schedule")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Simulation found",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = SimulationResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Simulation not found")
    })
    public ResponseEntity<SimulationResponseDTO> getSimulationById(@PathVariable Long id) {
        return simulationService.getSimulationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/client/{clientId}")
    @Operation(summary = "Get client simulations", description = "Retrieve all simulations for a specific client")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Client simulations retrieved successfully")
    })
    public ResponseEntity<List<SimulationResponseDTO>> getClientSimulations(@PathVariable Long clientId) {
        List<SimulationResponseDTO> simulations = simulationService.getClientSimulations(clientId);
        return ResponseEntity.ok(simulations);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update simulation status", description = "Update the status of a simulation (DRAFT, COMPLETED, APPROVED, etc.)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(responseCode = "404", description = "Simulation not found"),
        @ApiResponse(responseCode = "400", description = "Invalid status")
    })
    public ResponseEntity<Void> updateSimulationStatus(
            @PathVariable Long id,
            @Parameter(description = "New status", required = true) @RequestParam Simulation.SimulationStatus status) {
        return simulationService.updateSimulationStatus(id, status) ?
                ResponseEntity.ok().build() :
                ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/amortization")
    @Operation(summary = "Get amortization schedule with pagination", description = "Retrieve paginated amortization schedule for a simulation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Amortization schedule retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Simulation not found")
    })
    public ResponseEntity<SimulationResponseDTO.AmortizationSchedule> getAmortizationSchedule(
            @PathVariable Long id,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return simulationService.getSimulationById(id)
                .map(sim -> {
                    if (sim.getAmortizationSchedule() != null) {
                        var allPayments = sim.getAmortizationSchedule().getPayments();
                        int totalPayments = allPayments.size();
                        int startIndex = page * size;
                        int endIndex = Math.min(startIndex + size, totalPayments);

                        if (startIndex >= totalPayments) {
                            return SimulationResponseDTO.AmortizationSchedule.builder()
                                    .totalPayments(totalPayments)
                                    .currentPage(page)
                                    .pageSize(size)
                                    .payments(java.util.List.of())
                                    .build();
                        }

                        var paginatedPayments = allPayments.subList(startIndex, endIndex);
                        return SimulationResponseDTO.AmortizationSchedule.builder()
                                .totalPayments(totalPayments)
                                .currentPage(page)
                                .pageSize(size)
                                .payments(paginatedPayments)
                                .build();
                    }
                    return null;
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete simulation", description = "Delete a simulation and its amortization schedule")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Simulation deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Simulation not found")
    })
    public ResponseEntity<Void> deleteSimulation(@PathVariable Long id) {
        return simulationService.deleteSimulation(id) ?
                ResponseEntity.noContent().build() :
                ResponseEntity.notFound().build();
    }
}