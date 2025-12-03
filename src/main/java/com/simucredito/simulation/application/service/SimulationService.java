package com.simucredito.simulation.application.service;

import com.simucredito.client.application.service.ClientService;
import com.simucredito.configuration.application.service.ConfigurationService;
import com.simucredito.iam.domain.model.User;
import com.simucredito.iam.domain.repository.UserRepository;
import com.simucredito.property.application.service.PropertyService;
import com.simucredito.simulation.application.dto.CreateSimulationRequestDTO;
import com.simucredito.simulation.application.dto.SimulationResponseDTO;
import com.simucredito.simulation.application.dto.AmortizationEntryDTO;
import com.simucredito.simulation.domain.model.AmortizationSchedule;
import com.simucredito.simulation.domain.model.Simulation;
import com.simucredito.simulation.domain.repository.SimulationRepository;
import com.simucredito.simulation.domain.service.FinancialCalculator;
import com.simucredito.client.application.dto.ClientDTO;
import com.simucredito.property.application.dto.PropertyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    private final SimulationRepository simulationRepository;
    private final UserRepository userRepository;
    private final ClientService clientService;
    private final PropertyService propertyService;
    private final ConfigurationService configurationService;
    private final FinancialCalculator financialCalculator;
    private final ModelMapper modelMapper;

    @Transactional
    public SimulationResponseDTO createSimulation(CreateSimulationRequestDTO request) {
        // ... (Lógica de validación de usuario, cliente, propiedad... igual que antes) ...
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        var client = clientService.getClientById(request.getClientId());
        var property = propertyService.getPropertyById(request.getPropertyId());

        if (request.getFinancialEntityId() != null) {
            configurationService.getFinancialEntityById(request.getFinancialEntityId());
        }

        // 1. Conversión de Tasas (Igual que antes)
        BigDecimal interestRateTEM = financialCalculator.convertToTEM(
                request.getFinancingDetails().getInterestRate().getRate(),
                request.getFinancingDetails().getInterestRate().getType(),
                request.getFinancingDetails().getInterestRate().getPeriod(),
                request.getFinancingDetails().getInterestRate().getCapitalization()
        );

        /*
        BigDecimal rateForAnnuity = interestRateTEM;
        if (Boolean.TRUE.equals(request.getFinancingDetails().getInsurance().getDesgravamen().getEnabled())) {
            rateForAnnuity = rateForAnnuity.add(
                    request.getFinancingDetails().getInsurance().getDesgravamen().getRate()
            );
        }

         */


        BigDecimal opportunityCostTEM = financialCalculator.calculateCOK(
                request.getFinancingDetails().getOpportunityCost().getRate(),
                request.getFinancingDetails().getOpportunityCost().getType(),
                request.getFinancingDetails().getOpportunityCost().getPeriod(),
                request.getFinancingDetails().getOpportunityCost().getCapitalization()
        );

        BigDecimal opportunityCostTEMPercentage = opportunityCostTEM.multiply(BigDecimal.valueOf(100), MATH_CONTEXT)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal monthlyPaymentRef = financialCalculator.calculateMonthlyPayment(
                request.getCalculatedValues().getFinancingAmount(),
                interestRateTEM, // Usamos la tasa combinada aquí
                request.getFinancingDetails().getTermYears() * 12
        );

        /*
        // 2. Calculo Inicial Referencial (Igual que antes)
        BigDecimal monthlyPaymentRef = financialCalculator.calculateMonthlyPayment(
                request.getCalculatedValues().getFinancingAmount(),
                interestRateTEM,
                request.getFinancingDetails().getTermYears() * 12
        );

         */

        // 3. Crear Entidad (Guardamos temporalmente valores referenciales)
        Simulation simulation = Simulation.builder()
                .userId(user.getId())
                .clientId(request.getClientId())
                .propertyId(request.getPropertyId())
                .financialEntityId(request.getFinancialEntityId())
                .programType(request.getProgramType())
                .currency(request.getFinancingDetails().getCurrency())
                .usdValue(request.getFinancingDetails().getUsdValue())
                .propertyPrice(request.getCalculatedValues().getPropertyPrice())
                .stateContribution(request.getCalculatedValues().getStateContribution())
                .initialPayment(request.getCalculatedValues().getInitialPayment())
                .initialCosts(request.getCalculatedValues().getInitialCosts())
                .financingAmount(request.getCalculatedValues().getFinancingAmount())
                .termYears(request.getFinancingDetails().getTermYears())
                .interestRate(request.getFinancingDetails().getInterestRate().getRate())
                .interestRateType(request.getFinancingDetails().getInterestRate().getType())
                .interestRatePeriod(request.getFinancingDetails().getInterestRate().getPeriod())
                .interestRateCapitalization(request.getFinancingDetails().getInterestRate().getCapitalization())
                .opportunityCostRate(request.getFinancingDetails().getOpportunityCost().getRate())
                .opportunityCostType(request.getFinancingDetails().getOpportunityCost().getType())
                .opportunityCostPeriod(request.getFinancingDetails().getOpportunityCost().getPeriod())
                .opportunityCostCapitalization(request.getFinancingDetails().getOpportunityCost().getCapitalization())
                .gracePeriodType(request.getFinancingDetails().getGracePeriod().getType())
                .gracePeriodDurationMonths(request.getFinancingDetails().getGracePeriod().getDurationMonths())
                .monthlyCommissions(request.getFinancingDetails().getMonthlyCosts().getConstantCommissions())
                .administrationCosts(request.getFinancingDetails().getMonthlyCosts().getAdministrationCosts())
                .statementDelivery(request.getFinancingDetails().getStatementDelivery())
                .desgravamenEnabled(request.getFinancingDetails().getInsurance().getDesgravamen().getEnabled())
                .desgravamenRate(request.getFinancingDetails().getInsurance().getDesgravamen().getEnabled() ?
                        request.getFinancingDetails().getInsurance().getDesgravamen().getRate() : BigDecimal.ZERO)
                .propertyInsuranceEnabled(request.getFinancingDetails().getInsurance().getPropertyInsurance().getEnabled())
                .propertyInsuranceRate(request.getFinancingDetails().getInsurance().getPropertyInsurance().getEnabled() ?
                        request.getFinancingDetails().getInsurance().getPropertyInsurance().getRate() : BigDecimal.ZERO)
                .propertyInsuranceValue(request.getFinancingDetails().getInsurance().getPropertyInsurance().getValue())
                // Valores temporales, se actualizarán abajo
                .monthlyPayment(BigDecimal.ZERO)
                .tcea(BigDecimal.ZERO)
                .cok(opportunityCostTEMPercentage)
                .van(BigDecimal.ZERO)
                .tir(BigDecimal.ZERO)
                .totalInterest(BigDecimal.ZERO)
                .status(Simulation.SimulationStatus.COMPLETED)
                .build();

        // 4. Generar Cronograma (Aquí ocurre la magia real)
        List<FinancialCalculator.AmortizationEntry> entries = financialCalculator.generateAmortizationSchedule(
                simulation.getFinancingAmount(),
                interestRateTEM,
                monthlyPaymentRef,
                simulation.getTermYears() * 12,
                simulation.getGracePeriodDurationMonths(),
                simulation.getGracePeriodType(),
                simulation.getDesgravamenEnabled() ? simulation.getDesgravamenRate() : BigDecimal.ZERO,
                simulation.getPropertyInsuranceEnabled() ? simulation.getPropertyInsuranceRate() : BigDecimal.ZERO,
                simulation.getMonthlyCommissions(),
                simulation.getAdministrationCosts(),
                simulation.getStatementDelivery(),
                simulation.getPropertyInsuranceValue()
        );

        BigDecimal realTotalInterest = BigDecimal.ZERO;
        BigDecimal sumPrincipal = BigDecimal.ZERO;
        BigDecimal sumDesgravamen = BigDecimal.ZERO;
        BigDecimal sumRisk = BigDecimal.ZERO;
        BigDecimal sumCommissions = BigDecimal.ZERO;
        BigDecimal sumAdmin = BigDecimal.ZERO;
        // Lista de flujos para la TIR: [ -Prestamo, Cuota1, Cuota2, ..., CuotaN ]
        List<BigDecimal> cashFlows = new java.util.ArrayList<>();
        cashFlows.add(request.getCalculatedValues().getFinancingAmount()); // Periodo 0: Desembolso (Negativo)

        BigDecimal representativeMonthlyPayment = BigDecimal.ZERO;

        List<AmortizationSchedule> schedule = new java.util.ArrayList<>();
        for (FinancialCalculator.AmortizationEntry entry : entries) {
            realTotalInterest = realTotalInterest.add(entry.getInterestPayment());
            sumPrincipal = sumPrincipal.add(entry.getPrincipalPayment());
            sumDesgravamen = sumDesgravamen.add(entry.getLifeInsurancePayment());
            sumRisk = sumRisk.add(entry.getPropertyInsurancePayment());
            sumCommissions = sumCommissions.add(entry.getCommissions());
            sumAdmin = sumAdmin.add(entry.getAdminCosts()).add(entry.getDeliveryCosts());

            cashFlows.add(entry.getPayment().negate());

            // Lógica para elegir qué cuota mostrar en el resumen ("Cuota Mensual Total")
            // Si hay gracia, mostramos la primera cuota "NORMAL" (después de la gracia).
            // Si no hay gracia (o estamos en ella), tomamos la del periodo actual si no se ha seteado.
            int graceMonths = simulation.getGracePeriodDurationMonths() != null ? simulation.getGracePeriodDurationMonths() : 0;

            if (entry.getPeriodNumber() == graceMonths + 1) {
                representativeMonthlyPayment = entry.getPayment();
            } else if (graceMonths == 0 && entry.getPeriodNumber() == 1) {
                representativeMonthlyPayment = entry.getScheduledPayment();
            }

            // Fallback por si acaso
            if (representativeMonthlyPayment.compareTo(BigDecimal.ZERO) == 0 && entry.getPeriodNumber() == 1) {
                representativeMonthlyPayment = entry.getScheduledPayment();
            }

            schedule.add(AmortizationSchedule.builder()
                    .simulation(simulation)
                    .periodNumber(entry.getPeriodNumber())
                    .paymentDate(LocalDate.now().plusMonths(entry.getPeriodNumber()))
                    .tem(interestRateTEM)
                    .gracePeriod(simulation.getGracePeriodDurationMonths() != null ? simulation.getGracePeriodDurationMonths() : 0)
                    .initialBalance(entry.getBeginningBalance())
                    .interest(entry.getInterestPayment())
                    .payment(entry.getScheduledPayment())
                    .principal(entry.getPrincipalPayment())
                    .lifeInsurance(entry.getLifeInsurancePayment())
                    .propertyInsurance(entry.getPropertyInsurancePayment())
                    .commissions(entry.getCommissions())
                    .adminCosts(entry.getAdminCosts())
                    .deliveryCosts(entry.getDeliveryCosts())
                    .finalBalance(entry.getEndingBalance())
                    .cashFlow(entry.getCashFlow())
                    .cumulativePrincipal(entry.getCumulativePrincipal())
                    .cumulativeInterest(entry.getCumulativeInterest())
                    .isGracePeriod(entry.isGracePeriod())
                    .build());
        }

        // 6. Calcular VAN y TIR reales basados en el flujo de caja EXACTO

        // TIR Mensual
        BigDecimal tirMensual = financialCalculator.calculateScheduleTIR(cashFlows);

        // TIR Anual (TCEA) = (1 + TIR_Mensual)^12 - 1
        // Nota: tirMensual viene en porcentaje (ej: 0.85), hay que dividir por 100
        BigDecimal tirDecimal = tirMensual.divide(BigDecimal.valueOf(100), MATH_CONTEXT);
        BigDecimal tcea = tirDecimal.add(BigDecimal.ONE)
                .pow(12, MATH_CONTEXT)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100));

        // VAN
        BigDecimal van = financialCalculator.calculateScheduleVAN(cashFlows, opportunityCostTEM);

        // 7. Actualizar Entidad con Valores Reales
        simulation.setMonthlyPayment(representativeMonthlyPayment.setScale(2, RoundingMode.HALF_UP));
        simulation.setTcea(tcea.setScale(2, RoundingMode.HALF_UP));
        simulation.setVan(van.setScale(2, RoundingMode.HALF_UP));
        simulation.setTir(tirMensual.setScale(4, RoundingMode.HALF_UP)); // TIR Mensual

        simulation.setTotalInterest(realTotalInterest.setScale(2, RoundingMode.HALF_UP));
        simulation.setTotalCapitalAmortization(sumPrincipal.setScale(2, RoundingMode.HALF_UP));
        simulation.setTotalDesgravamen(sumDesgravamen.setScale(2, RoundingMode.HALF_UP));
        simulation.setTotalRiskInsurance(sumRisk.setScale(2, RoundingMode.HALF_UP));
        simulation.setTotalCommissions(sumCommissions.setScale(2, RoundingMode.HALF_UP));
        simulation.setTotalAdminExpenses(sumAdmin.setScale(2, RoundingMode.HALF_UP));

        // Guardar todo
        simulation.setAmortizationSchedule(schedule);
        simulation = simulationRepository.save(simulation);

        return mapToResponseDTO(simulation, client, property, false);
    }

    private BigDecimal calculateMonthlyCosts(CreateSimulationRequestDTO request) {
        BigDecimal costs = request.getFinancingDetails().getMonthlyCosts().getConstantCommissions()
                .add(request.getFinancingDetails().getMonthlyCosts().getAdministrationCosts());

        // Add delivery cost if physical
        if ("physical".equals(request.getFinancingDetails().getStatementDelivery())) {
            costs = costs.add(BigDecimal.valueOf(10));
        }

        // Add insurance costs - with null checks
        if (request.getFinancingDetails().getInsurance() != null) {
            if (request.getFinancingDetails().getInsurance().getDesgravamen() != null &&
                Boolean.TRUE.equals(request.getFinancingDetails().getInsurance().getDesgravamen().getEnabled())) {
                costs = costs.add(request.getCalculatedValues().getFinancingAmount()
                        .multiply(request.getFinancingDetails().getInsurance().getDesgravamen().getRate()));
            }

            if (request.getFinancingDetails().getInsurance().getPropertyInsurance() != null &&
                Boolean.TRUE.equals(request.getFinancingDetails().getInsurance().getPropertyInsurance().getEnabled())) {
                costs = costs.add(request.getFinancingDetails().getInsurance().getPropertyInsurance().getValue()
                        .multiply(request.getFinancingDetails().getInsurance().getPropertyInsurance().getRate()));
            }
        }

        return costs;
    }

    private BigDecimal calculateTotalCosts(CreateSimulationRequestDTO request) {
        return calculateMonthlyCosts(request).multiply(BigDecimal.valueOf(request.getFinancingDetails().getTermYears() * 12));
    }

    private BigDecimal calculateApplicableBonus(CreateSimulationRequestDTO request) {
        // Simplified bonus calculation - return default bonus
        return BigDecimal.valueOf(5000);
    }

    private void generateAmortizationSchedule(Simulation simulation, BigDecimal monthlyRate, BigDecimal monthlyPayment) {
        List<FinancialCalculator.AmortizationEntry> entries = financialCalculator.generateAmortizationSchedule(
                simulation.getFinancingAmount(),
                monthlyRate,
                monthlyPayment,
                simulation.getTermYears() * 12,
                simulation.getGracePeriodDurationMonths(),
                simulation.getGracePeriodType(),
                simulation.getDesgravamenEnabled() ? simulation.getDesgravamenRate() : BigDecimal.ZERO,
                simulation.getPropertyInsuranceEnabled() ? simulation.getPropertyInsuranceRate() : BigDecimal.ZERO,
                simulation.getMonthlyCommissions(),
                simulation.getAdministrationCosts(),
                simulation.getStatementDelivery(),
                simulation.getPropertyInsuranceValue()
        );

        List<AmortizationSchedule> schedule = entries.stream()
                .map(entry -> AmortizationSchedule.builder()
                        .simulation(simulation)
                        .periodNumber(entry.getPeriodNumber())
                        .paymentDate(LocalDate.now().plusMonths(entry.getPeriodNumber() - 1))
                        .tem(monthlyRate)
                        .gracePeriod(simulation.getGracePeriodDurationMonths() != null ?
                                simulation.getGracePeriodDurationMonths() : 0)
                        .initialBalance(entry.getBeginningBalance())
                        .interest(entry.getInterestPayment())
                        .payment(entry.getScheduledPayment())
                        .principal(entry.getPrincipalPayment())
                        .lifeInsurance(entry.getLifeInsurancePayment())
                        .propertyInsurance(entry.getPropertyInsurancePayment())
                        .commissions(simulation.getMonthlyCommissions())
                        .adminCosts(simulation.getAdministrationCosts())
                        .deliveryCosts("physical".equals(simulation.getStatementDelivery()) ?
                                BigDecimal.valueOf(10) : BigDecimal.ZERO)
                        .finalBalance(entry.getEndingBalance())
                        .cashFlow(entry.getScheduledPayment().negate())
                        .cumulativePrincipal(entry.getCumulativePrincipal())
                        .cumulativeInterest(entry.getCumulativeInterest())
                        .isGracePeriod(entry.isGracePeriod())
                        .build())
                .collect(Collectors.toList());
        // Captura la primera cuota total real de la tabla generada
        BigDecimal firstTotalPayment = schedule.stream()
                .filter(e -> e.getPeriodNumber() == 1)
                .map(AmortizationSchedule::getPayment) // 'payment' es la cuota total de la tabla
                .findFirst()
                .orElseGet(() -> {
                    // Fallback si la tabla está vacía (no debería pasar)
                    // Recalcula los costos del primer mes usando el objeto 'simulation'
                    BigDecimal costs = simulation.getMonthlyCommissions().add(simulation.getAdministrationCosts());

                    if ("physical".equals(simulation.getStatementDelivery())) {
                        costs = costs.add(BigDecimal.valueOf(10));
                    }
                    if (Boolean.TRUE.equals(simulation.getDesgravamenEnabled())) {
                        // El seguro de desgravamen del primer mes se calcula sobre el saldo inicial total
                        costs = costs.add(simulation.getFinancingAmount().multiply(simulation.getDesgravamenRate(), MATH_CONTEXT));
                    }
                    if (Boolean.TRUE.equals(simulation.getPropertyInsuranceEnabled())) {
                        // El seguro de inmueble es fijo
                        // ¡¡AQUÍ TAMBIÉN APLICA LA DIVISIÓN ENTRE 12!!
                        BigDecimal monthlyPropertyInsuranceRate = simulation.getPropertyInsuranceRate().divide(BigDecimal.valueOf(12), MATH_CONTEXT);
                        costs = costs.add(simulation.getPropertyInsuranceValue().multiply(monthlyPropertyInsuranceRate));
                    }
                    // Fallback es la cuota (C+I) + costos
                    return monthlyPayment.add(costs);
                });

        simulation.setAmortizationSchedule(schedule);
        // Guarda la primera cuota total real en el campo monthlyPayment
        simulation.setMonthlyPayment(firstTotalPayment.setScale(2, RoundingMode.HALF_UP));
        simulationRepository.save(simulation);
    }

    public List<SimulationResponseDTO> getUserSimulations() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return simulationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(sim -> {
                    try {
                        var client = clientService.getClientById(sim.getClientId());
                        var property = propertyService.getPropertyById(sim.getPropertyId());
                        return mapToResponseDTO(sim, client, property, false);
                    } catch (Exception e) {
                        // Fallback if client/property not found
                        return mapToResponseDTO(sim, false);
                    }
                })
                .collect(Collectors.toList());
    }

    public Optional<SimulationResponseDTO> getSimulationById(Long id) {
        return simulationRepository.findById(id)
                .map(sim -> {
                    var client = clientService.getClientById(sim.getClientId());
                    var property = propertyService.getPropertyById(sim.getPropertyId());
                    return mapToResponseDTO(sim, client, property, true); // Include amortization schedule
                });
    }

    public List<SimulationResponseDTO> getClientSimulations(Long clientId) {
        return simulationRepository.findByClientIdOrderByCreatedAtDesc(clientId)
                .stream()
                .map(sim -> {
                    try {
                        var client = clientService.getClientById(sim.getClientId());
                        var property = propertyService.getPropertyById(sim.getPropertyId());
                        return mapToResponseDTO(sim, client, property, false);
                    } catch (Exception e) {
                        // Fallback if client/property not found
                        return mapToResponseDTO(sim, false);
                    }
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean updateSimulationStatus(Long id, Simulation.SimulationStatus status) {
        return simulationRepository.findById(id)
                .map(simulation -> {
                    simulation.setStatus(status);
                    simulationRepository.save(simulation);
                    log.info("Updated simulation {} status to {}", id, status);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean deleteSimulation(Long id) {
        return simulationRepository.findById(id)
                .map(simulation -> {
                    simulationRepository.delete(simulation);
                    log.info("Deleted simulation {}", id);
                    return true;
                })
                .orElse(false);
    }

    private SimulationResponseDTO mapToResponseDTO(Simulation simulation, boolean includeSchedule) {
        return mapToResponseDTO(simulation, null, null, includeSchedule);
    }

    private SimulationResponseDTO mapToResponseDTO(Simulation simulation, ClientDTO client, PropertyDTO property, boolean includeSchedule) {
        SimulationResponseDTO.ClientInfo clientInfo = null;
        if (client != null && client.getHolder() != null) {
            clientInfo = SimulationResponseDTO.ClientInfo.builder()
                    .id(client.getId())
                    .name(client.getHolder().getNombres() + " " + client.getHolder().getApellidos())
                    .documentNumber(client.getHolder().getDocumentNumber())
                    .build();
        }

        SimulationResponseDTO.PropertyInfo propertyInfo = null;
        if (property != null) {
            propertyInfo = SimulationResponseDTO.PropertyInfo.builder()
                    .id(property.getId())
                    .name(property.getNombreProyecto())
                    .price(property.getPropertyPrice())
                    .build();
        }

        SimulationResponseDTO.Summary summary = SimulationResponseDTO.Summary.builder()
                .propertyValue(simulation.getPropertyPrice())
                .stateContribution(simulation.getStateContribution())
                .initialPayment(simulation.getInitialPayment())
                .financingAmount(simulation.getFinancingAmount())
                .build();

        SimulationResponseDTO.KeyIndicators keyIndicators = SimulationResponseDTO.KeyIndicators.builder()
                .monthlyPayment(simulation.getMonthlyPayment())
                .tcea(simulation.getTcea())
                .cok(simulation.getCok())
                .van(simulation.getVan())
                .tir(simulation.getTir())
                .build();

        SimulationResponseDTO.InputParameters inputs = SimulationResponseDTO.InputParameters.builder()
                .currency(simulation.getCurrency())
                .usdValue(simulation.getUsdValue())
                .termYears(simulation.getTermYears())
                .interestRate(simulation.getInterestRate())
                .interestRateType(simulation.getInterestRateType())
                .interestRatePeriod(simulation.getInterestRatePeriod())
                .interestRateCapitalization(simulation.getInterestRateCapitalization())
                .opportunityCostRate(simulation.getOpportunityCostRate())
                .opportunityCostType(simulation.getOpportunityCostType())
                .opportunityCostPeriod(simulation.getOpportunityCostPeriod())
                .opportunityCostCapitalization(simulation.getOpportunityCostCapitalization())
                .gracePeriodType(simulation.getGracePeriodType())
                .gracePeriodDurationMonths(simulation.getGracePeriodDurationMonths())
                .monthlyCommissions(simulation.getMonthlyCommissions())
                .administrationCosts(simulation.getAdministrationCosts())
                .statementDelivery(simulation.getStatementDelivery())
                .desgravamenEnabled(simulation.getDesgravamenEnabled())
                .desgravamenRate(simulation.getDesgravamenRate())
                .propertyInsuranceEnabled(simulation.getPropertyInsuranceEnabled())
                .propertyInsuranceRate(simulation.getPropertyInsuranceRate())
                .build();

        SimulationResponseDTO.TotalResults totalResults = SimulationResponseDTO.TotalResults.builder()
                .totalInterest(simulation.getTotalInterest())
                .totalCapitalAmortization(simulation.getTotalCapitalAmortization())
                .totalDesgravamen(simulation.getTotalDesgravamen())
                .totalRiskInsurance(simulation.getTotalRiskInsurance())
                .totalCommissions(simulation.getTotalCommissions())
                .totalAdminExpenses(simulation.getTotalAdminExpenses())
                .build();

        SimulationResponseDTO dto = SimulationResponseDTO.builder()
                .simulationId(simulation.getId().toString())
                .clientInfo(clientInfo)
                .propertyInfo(propertyInfo)
                .summary(summary)
                .keyIndicators(keyIndicators)
                .totalResults(totalResults)
                .inputs(inputs)
                .calculationMethod("French Method (Ordinary Annuity)")
                .generatedAt(simulation.getCreatedAt())
                .build();

        if (includeSchedule && simulation.getAmortizationSchedule() != null) {
            List<SimulationResponseDTO.Payment> allPayments = simulation.getAmortizationSchedule()
                    .stream()
                    .sorted((a, b) -> Integer.compare(a.getPeriodNumber(), b.getPeriodNumber()))
                    .map(entry -> {
                        // Lógica para determinar el texto del periodo de gracia
                        String graceDesc = "Sin gracia";
                        if (Boolean.TRUE.equals(entry.getIsGracePeriod())) {
                            String type = simulation.getGracePeriodType();
                            if ("total".equalsIgnoreCase(type)) {
                                graceDesc = "Total";
                            } else if ("partial".equalsIgnoreCase(type)) {
                                graceDesc = "Parcial";
                            }
                        }

                        return SimulationResponseDTO.Payment.builder()
                                .paymentNumber(entry.getPeriodNumber())
                                .tem(entry.getTem())
                                .gracePeriod(entry.getGracePeriod())
                                .gracePeriodDescription(graceDesc)
                                .initialBalance(entry.getInitialBalance())
                                .interest(entry.getInterest())
                                .payment(entry.getPayment())
                                .principal(entry.getPrincipal())
                                .lifeInsurance(entry.getLifeInsurance())
                                .propertyInsurance(entry.getPropertyInsurance())
                                .commissions(entry.getCommissions())
                                .adminCosts(entry.getAdminCosts())
                                .deliveryCosts(entry.getDeliveryCosts())
                                .finalBalance(entry.getFinalBalance())
                                .cashFlow(entry.getCashFlow())
                                .build();
                    })
                    .collect(Collectors.toList());

            int totalPayments = allPayments.size();
            int pageSize = totalPayments > 0 ? totalPayments : 10; // Evitar pageSize 0
            int currentPage = 1; // Se devuelve la "página" 1 (completa)

            SimulationResponseDTO.AmortizationSchedule schedule = SimulationResponseDTO.AmortizationSchedule.builder()
                    .totalPayments(totalPayments)
                    .currentPage(currentPage)
                    .pageSize(pageSize)
                    .payments(allPayments)
                    .build();

            dto.setAmortizationSchedule(schedule);
        }

        return dto;
    }
}