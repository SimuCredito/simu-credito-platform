package com.simucredito.dashboard.application.service;

import com.simucredito.client.domain.model.Client;
import com.simucredito.client.domain.model.Person;
import com.simucredito.client.domain.repository.ClientRepository;
import com.simucredito.client.domain.repository.PersonRepository;
import com.simucredito.dashboard.application.dto.DashboardMetricsDTO;
import com.simucredito.dashboard.application.dto.RecentActivityDTO;
import com.simucredito.iam.domain.model.User;
import com.simucredito.iam.domain.repository.UserRepository;
import com.simucredito.property.domain.model.Property;
import com.simucredito.property.domain.repository.PropertyRepository;
import com.simucredito.simulation.domain.model.Simulation;
import com.simucredito.simulation.domain.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final SimulationRepository simulationRepository;
    private final ClientRepository clientRepository;
    private final PersonRepository personRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User) {
            return ((User) principal).getId();
        }
        throw new RuntimeException("Usuario no encontrado en la sesión");
    }
    public DashboardMetricsDTO getDashboardMetrics() {
        Long userId = getCurrentUserId(); // <-- OBTENER ID DEL USUARIO
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // --- Simulation metrics (FILTRADAS POR USUARIO) ---
        Long totalSimulations = simulationRepository.countByUserId(userId);

        // Asumiendo que agregaste este método en SimulationRepo o usas el query existente cambiando parámetros
        Long simulationsThisMonth = simulationRepository.countSimulationsByUserSince(userId, startOfMonth);

        Long completedSimulations = (long) simulationRepository.findByUserIdAndStatus(
                userId,
                com.simucredito.simulation.domain.model.Simulation.SimulationStatus.COMPLETED
        ).size();

        // Nota: Para promedios y sumas (avgMonthlyPayment, totalPaymentsVolume),
        // deberías crear queries @Query en el repositorio que incluyan "WHERE s.userId = :userId"
        // Por ahora los pondré en 0 o requerirás actualizar el repositorio.
        BigDecimal avgMonthlyPayment = BigDecimal.ZERO;
        BigDecimal totalPaymentsVolume = BigDecimal.ZERO;


        // --- Client metrics (FILTRADAS POR USUARIO) ---
        Long totalClients = clientRepository.countByUserId(userId);

        // Usamos el método nuevo sugerido en el paso 2
        Long clientsThisMonth = clientRepository.countClientsByUserSince(userId, startOfMonth);

        Long preQualifiedClients = clientRepository.countByUserIdAndPreQualifiedTrue(userId);


        // --- Property metrics (FILTRADAS POR USUARIO) ---
        Long totalProperties = propertyRepository.countByUserId(userId);
        Long propertiesThisMonth = propertyRepository.countPropertiesByUserSince(userId, startOfMonth);
        Long sustainableProperties = propertyRepository.countByUserIdAndIsSustainableTrue(userId);


        // --- Financial metrics (Calculados en memoria solo sobre la data del usuario) ---
        List<Simulation> userSimulations = simulationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        Double avgLoanAmount = userSimulations.stream()
                .mapToDouble(s -> s.getFinancingAmount().doubleValue())
                .average().orElse(0.0);

        Double totalLoanAmountVal = userSimulations.stream()
                .mapToDouble(s -> s.getFinancingAmount().doubleValue())
                .sum();

        Double avgBonusAmount = userSimulations.stream()
                .mapToDouble(s -> s.getStateContribution().doubleValue())
                .average().orElse(0.0);

        // User metrics (Estos pueden quedar globales si es un admin, o restringirse)
        Long totalUsers = userRepository.count();
        Long activeUsers = userRepository.count(); // Simplificado

        return DashboardMetricsDTO.builder()
                .totalSimulations(totalSimulations)
                .simulationsThisMonth(simulationsThisMonth)
                .completedSimulations(completedSimulations)
                // ... resto de mapeo usando las variables calculadas arriba ...
                .averageLoanAmount(BigDecimal.valueOf(avgLoanAmount))
                .totalLoanAmount(BigDecimal.valueOf(totalLoanAmountVal))
                .averageBonusAmount(BigDecimal.valueOf(avgBonusAmount))
                .build();
    }

    public List<RecentActivityDTO> getRecentActivity(int limit) {
        Long userId = getCurrentUserId(); // <-- FILTRAR POR USUARIO
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        // Get recent simulations (DEL USUARIO)
        List<RecentActivityDTO> simulationActivities = simulationRepository
                .findByUserIdOrderByCreatedAtDesc(userId) // Usar el método que ya existe filtrado por ID
                .stream()
                .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().isAfter(since))
                .limit(limit)
                .map(s -> RecentActivityDTO.builder()
                        .id(s.getId())
                        .type("SIMULATION")
                        .description("Nueva simulación")
                        .createdAt(s.getCreatedAt())
                        .amount(s.getFinancingAmount())
                        .status(s.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        // Get recent clients (DEL USUARIO)
        List<RecentActivityDTO> clientActivities = clientRepository
                .findByUserId(userId) // Usar método existente
                .stream()
                .filter(c -> c.getRegistrationDate() != null && c.getRegistrationDate().isAfter(since))
                .limit(limit)
                // Nota: aquí necesitarías hacer fetch del Person holder para obtener el nombre
                .map(c -> RecentActivityDTO.builder()
                        .id(c.getId())
                        .type("CLIENT")
                        .description("Nuevo cliente registrado")
                        .createdAt(c.getRegistrationDate())
                        .build())
                .collect(Collectors.toList());

        // Get recent properties (DEL USUARIO)
        List<RecentActivityDTO> propertyActivities = propertyRepository
                .findByUserId(userId) // Usar método existente
                .stream()
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(since))
                .limit(limit)
                .map(p -> RecentActivityDTO.builder()
                        .id(p.getId())
                        .type("PROPERTY")
                        .description("Propiedad: " + p.getNombreProyecto())
                        .createdAt(p.getCreatedAt())
                        .amount(p.getPropertyPrice())
                        .build())
                .collect(Collectors.toList());

        // Combinar y ordenar
        List<RecentActivityDTO> allActivities = new java.util.ArrayList<>();
        allActivities.addAll(simulationActivities);
        allActivities.addAll(clientActivities);
        allActivities.addAll(propertyActivities);

        return allActivities.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}