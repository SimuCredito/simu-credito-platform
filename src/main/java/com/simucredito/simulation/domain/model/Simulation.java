package com.simucredito.simulation.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Table(name = "simulations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Simulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "financial_entity_id")
    private Long financialEntityId; // Optional

    @Column(name = "program_type", nullable = false)
    private String programType; // "mivivienda", "techo_propio"

    @Column(name = "currency", nullable = false)
    private String currency; // "PEN", "USD"

    @Column(name = "usd_value")
    private BigDecimal usdValue; // Optional, if USD selected

    @Column(name = "property_price", nullable = false)
    private BigDecimal propertyPrice;

    @Column(name = "state_contribution", nullable = false)
    private BigDecimal stateContribution;

    @Column(name = "initial_payment", nullable = false)
    private BigDecimal initialPayment;

    @Column(name = "initial_costs", nullable = false)
    private BigDecimal initialCosts;

    @Column(name = "financing_amount", nullable = false)
    private BigDecimal financingAmount;

    @Column(name = "term_years", nullable = false)
    private Integer termYears;

    @Column(name = "interest_rate", precision = 12, scale = 9)
    private BigDecimal interestRate;

    @Column(name = "interest_rate_type", nullable = false)
    private String interestRateType; // "TE", "TN"

    @Column(name = "interest_rate_period", nullable = false)
    private String interestRatePeriod;

    @Column(name = "interest_rate_capitalization")
    private String interestRateCapitalization; // Only for TN

    @Column(name = "opportunity_cost_rate", precision = 12, scale = 9)
    private BigDecimal opportunityCostRate;

    @Column(name = "opportunity_cost_type", nullable = false)
    private String opportunityCostType; // "TE", "TN"

    @Column(name = "opportunity_cost_period", nullable = false)
    private String opportunityCostPeriod;

    @Column(name = "opportunity_cost_capitalization")
    private String opportunityCostCapitalization; // Only for TN

    @Column(name = "grace_period_type", nullable = false)
    private String gracePeriodType; // "none", "partial", "total"

    @Column(name = "grace_period_duration_months")
    private Integer gracePeriodDurationMonths; // Optional

    @Column(name = "monthly_commissions", nullable = false)
    private BigDecimal monthlyCommissions;

    @Column(name = "administration_costs", nullable = false)
    private BigDecimal administrationCosts;

    @Column(name = "statement_delivery", nullable = false)
    private String statementDelivery; // "electronic", "physical"

    @Column(name = "desgravamen_enabled", nullable = false)
    private Boolean desgravamenEnabled;

    @Column(name = "desgravamen_rate", precision = 12, scale = 9)
    private BigDecimal desgravamenRate;

    @Column(name = "property_insurance_enabled", nullable = false)
    private Boolean propertyInsuranceEnabled;

    @Column(name = "property_insurance_rate", precision = 12, scale = 9)
    private BigDecimal propertyInsuranceRate;

    @Column(name = "property_insurance_value", nullable = false)
    private BigDecimal propertyInsuranceValue;

    // Calculated fields
    @Column(name = "monthly_payment", nullable = false)
    private BigDecimal monthlyPayment;

    @Column(name = "tcea", nullable = false)
    private BigDecimal tcea;

    @Column(name = "cok", nullable = false)
    private BigDecimal cok;

    @Column(name = "van", nullable = false)
    private BigDecimal van;

    @Column(name = "tir", nullable = false)
    private BigDecimal tir;

    @Column(name = "total_interest", nullable = false)
    private BigDecimal totalInterest;

    @Column(name = "total_capital_amortization", precision = 19, scale = 2)
    private BigDecimal totalCapitalAmortization;

    @Column(name = "total_desgravamen", precision = 19, scale = 2)
    private BigDecimal totalDesgravamen;

    @Column(name = "total_risk_insurance", precision = 19, scale = 2)
    private BigDecimal totalRiskInsurance;

    @Column(name = "total_commissions", precision = 19, scale = 2)
    private BigDecimal totalCommissions;

    @Column(name = "total_admin_expenses", precision = 19, scale = 2)
    private BigDecimal totalAdminExpenses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SimulationStatus status;

    @Column(name = "simulation_date", nullable = false)
    private LocalDateTime simulationDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "simulation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AmortizationSchedule> amortizationSchedule;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        simulationDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum SimulationStatus {
        DRAFT, COMPLETED, APPROVED, REJECTED, CANCELLED
    }
}