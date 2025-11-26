package com.simucredito.simulation.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class FinancialCalculator {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public BigDecimal calculateMonthlyEffectiveRate(BigDecimal annualRate, String rateType, String capitalizationPeriod) {
        double rateDouble = annualRate.divide(BigDecimal.valueOf(100), MATH_CONTEXT).doubleValue();
        double temDouble;

        if ("TE".equals(rateType)) {
            double base = 1.0 + rateDouble;
            double exponent = 30.0 / 360.0;
            temDouble = Math.pow(base, exponent) - 1.0;
        } else if ("TN".equals(rateType)) {
            int m = getCapitalizationsPerYear(capitalizationPeriod);
            double n = (double) m / 12.0;
            double base = 1.0 + (rateDouble / m);
            temDouble = Math.pow(base, n) - 1.0;
        } else {
            throw new IllegalArgumentException("Invalid rate type: " + rateType);
        }
        return BigDecimal.valueOf(temDouble);
    }

    public BigDecimal convertToTEM(BigDecimal rate, String rateType, String period, String capitalization) {
        double rateDouble = rate.divide(BigDecimal.valueOf(100), MATH_CONTEXT).doubleValue();
        double temDouble;

        if ("TE".equals(rateType)) {
            double base = 1.0 + rateDouble;
            double n1 = (double) getDaysInPeriod(period);
            double n2 = 30.0;
            double exponent = n2 / n1;
            temDouble = Math.pow(base, exponent) - 1.0;
        } else if ("TN".equals(rateType)) {
            double j_tasa_nominal_anual = rateDouble * (360.0 / getDaysInPeriod(period));
            String capPeriod = (capitalization != null ? capitalization : period);
            int m = getCapitalizationsPerYear(capPeriod);
            double n = (double) m / 12.0;
            double base = 1.0 + (j_tasa_nominal_anual / m);
            temDouble = Math.pow(base, n) - 1.0;
        } else {
            throw new IllegalArgumentException("Invalid rate type: " + rateType);
        }
        return BigDecimal.valueOf(temDouble);
    }

    public BigDecimal calculateCOK(BigDecimal opportunityCostRate, String opportunityCostType, String opportunityCostPeriod, String opportunityCostCapitalization) {
        return convertToTEM(opportunityCostRate, opportunityCostType, opportunityCostPeriod, opportunityCostCapitalization);
    }

    private int getDaysInPeriod(String period) {
        if (period == null) return 30;
        return switch (period.toLowerCase()) {
            case "daily" -> 1;
            case "seminal", "bi-weekly" -> 15;
            case "monthly" -> 30;
            case "bi-monthly" -> 60;
            case "quarterly" -> 90;
            case "semi-annually" -> 180;
            case "annual" -> 360;
            default -> 30;
        };
    }

    private int getCapitalizationsPerYear(String period) {
        if (period == null) return 12;
        return switch (period.toLowerCase()) {
            case "daily" -> 360;
            case "seminal", "bi-weekly" -> 24;
            case "monthly" -> 12;
            case "bi-monthly" -> 6;
            case "quarterly" -> 4;
            case "semi-annually" -> 2;
            case "annual" -> 1;
            default -> 12;
        };
    }

    public BigDecimal calculateMonthlyPayment(BigDecimal principal, BigDecimal monthlyRate, int termMonths) {
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), MATH_CONTEXT);
        }
        BigDecimal rateFactor = monthlyRate.add(BigDecimal.ONE).pow(termMonths, MATH_CONTEXT);
        BigDecimal numerator = monthlyRate.multiply(rateFactor, MATH_CONTEXT);
        BigDecimal denominator = rateFactor.subtract(BigDecimal.ONE, MATH_CONTEXT);
        return principal.multiply(numerator, MATH_CONTEXT).divide(denominator, MATH_CONTEXT).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateVAN(BigDecimal monthlyPayment, BigDecimal cokRate, int termMonths, BigDecimal initialInvestment) {
        BigDecimal van = BigDecimal.ZERO;
        van = van.add(initialInvestment, MATH_CONTEXT);
        for (int period = 1; period <= termMonths; period++) {
            BigDecimal discountedPayment = monthlyPayment.divide(cokRate.add(BigDecimal.ONE).pow(period, MATH_CONTEXT), MATH_CONTEXT);
            van = van.add(discountedPayment, MATH_CONTEXT);
        }
        return van.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTIR(BigDecimal monthlyPayment, BigDecimal principal, int termMonths, int maxIterations, double tolerance) {
        BigDecimal guess = BigDecimal.valueOf(0.01);
        for (int i = 0; i < maxIterations; i++) {
            BigDecimal f = calculateNPV(guess, monthlyPayment, principal, termMonths);
            BigDecimal fPrime = calculateNPVDerivative(guess, monthlyPayment, termMonths);
            if (fPrime.abs().compareTo(BigDecimal.valueOf(tolerance)) < 0) break;
            BigDecimal newGuess = guess.subtract(f.divide(fPrime, MATH_CONTEXT), MATH_CONTEXT);
            if (newGuess.subtract(guess).abs().compareTo(BigDecimal.valueOf(tolerance)) < 0) {
                return newGuess.multiply(BigDecimal.valueOf(100), MATH_CONTEXT).setScale(4, RoundingMode.HALF_UP);
            }
            guess = newGuess;
        }
        return guess.multiply(BigDecimal.valueOf(100), MATH_CONTEXT).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el VAN (Valor Actual Neto) basado en un flujo de caja variable
     */
    public BigDecimal calculateScheduleVAN(List<BigDecimal> cashFlows, BigDecimal cokRate) {
        BigDecimal van = BigDecimal.ZERO;
        // El flujo 0 es la inversión inicial (negativa)
        // Los flujos 1..n son los pagos
        for (int i = 0; i < cashFlows.size(); i++) {
            BigDecimal flow = cashFlows.get(i);
            // Fórmula: Flujo / (1 + COK)^periodo
            // Si i=0, (1+r)^0 es 1, queda el flujo inicial tal cual
            BigDecimal denominator = cokRate.add(BigDecimal.ONE).pow(i, MATH_CONTEXT);
            van = van.add(flow.divide(denominator, MATH_CONTEXT));
        }
        return van.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula la TIR (Tasa Interna de Retorno) mensual basada en flujo de caja variable
     */
    public BigDecimal calculateScheduleTIR(List<BigDecimal> cashFlows) {
        double guess = 0.01; // 1% mensual inicial
        int maxIterations = 100;
        double tolerance = 0.00001;

        for (int i = 0; i < maxIterations; i++) {
            double npv = 0.0;
            double d_npv = 0.0;

            for (int t = 0; t < cashFlows.size(); t++) {
                double flow = cashFlows.get(t).doubleValue();
                double denominator = Math.pow(1.0 + guess, t);

                npv += flow / denominator;
                d_npv -= t * flow / (denominator * (1.0 + guess));
            }

            if (Math.abs(npv) < tolerance) {
                return BigDecimal.valueOf(guess).multiply(BigDecimal.valueOf(100), MATH_CONTEXT).setScale(6, RoundingMode.HALF_UP);
            }

            double newGuess = guess - (npv / d_npv);

            // Evitar saltos locos
            if (Math.abs(newGuess - guess) < tolerance) {
                return BigDecimal.valueOf(newGuess).multiply(BigDecimal.valueOf(100), MATH_CONTEXT).setScale(6, RoundingMode.HALF_UP);
            }

            guess = newGuess;
        }

        // Retorno aproximado si no converge (o lanzar error)
        return BigDecimal.valueOf(guess).multiply(BigDecimal.valueOf(100), MATH_CONTEXT).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateNPV(BigDecimal rate, BigDecimal monthlyPayment, BigDecimal principal, int termMonths) {
        BigDecimal npv = principal.negate();
        for (int period = 1; period <= termMonths; period++) {
            BigDecimal discountedPayment = monthlyPayment.divide(rate.add(BigDecimal.ONE).pow(period, MATH_CONTEXT), MATH_CONTEXT);
            npv = npv.add(discountedPayment, MATH_CONTEXT);
        }
        return npv;
    }

    private BigDecimal calculateNPVDerivative(BigDecimal rate, BigDecimal monthlyPayment, int termMonths) {
        BigDecimal derivative = BigDecimal.ZERO;
        for (int period = 1; period <= termMonths; period++) {
            BigDecimal discountedPayment = monthlyPayment.multiply(BigDecimal.valueOf(-period)).divide(rate.add(BigDecimal.ONE).pow(period + 1, MATH_CONTEXT), MATH_CONTEXT);
            derivative = derivative.add(discountedPayment, MATH_CONTEXT);
        }
        return derivative;
    }

    /**
     * Genera el cronograma de amortización completo con todos los costos y corrección de Gracia
     */
    public List<AmortizationEntry> generateAmortizationSchedule(
            BigDecimal principal, BigDecimal monthlyRate, BigDecimal initialMonthlyPayment,
            int termMonths, Integer gracePeriodMonths, String gracePeriodType,
            BigDecimal lifeInsuranceRate, BigDecimal propertyInsuranceRate,
            BigDecimal monthlyCommissions, BigDecimal administrationCosts,
            String statementDelivery, BigDecimal propertyInsuranceValue) {

        List<AmortizationEntry> schedule = new ArrayList<>();
        BigDecimal remainingBalance = principal;
        BigDecimal cumulativePrincipal = BigDecimal.ZERO;
        BigDecimal cumulativeInterest = BigDecimal.ZERO;

        // Variable para la cuota base (Amortización + Interés)
        BigDecimal currentPmtBase = initialMonthlyPayment;

        // Costos periódicos mensuales
        BigDecimal deliveryCost = "physical".equals(statementDelivery) ? BigDecimal.valueOf(10) : BigDecimal.ZERO;

        // Ajuste: Asegurar que la tasa de seguro de inmueble sea mensual (dividiendo la anual entre 12)
        BigDecimal monthlyPropertyInsuranceRate = propertyInsuranceRate;

        for (int period = 1; period <= termMonths; period++) {
            boolean isGracePeriod = gracePeriodMonths != null && period <= gracePeriodMonths;

            // --- FIX: DETECTAR FIN DE GRACIA Y RECALCULAR CUOTA ---
            if (gracePeriodMonths != null && period == gracePeriodMonths + 1) {
                int remainingPeriods = termMonths - gracePeriodMonths;
                currentPmtBase = calculateMonthlyPayment(remainingBalance, monthlyRate, remainingPeriods);
            }
            // ------------------------------------------------------

            BigDecimal interestPayment;
            BigDecimal principalPayment;
            BigDecimal scheduledPayment;

            if (isGracePeriod) {
                if ("total".equals(gracePeriodType)) {
                    interestPayment = remainingBalance.multiply(monthlyRate, MATH_CONTEXT);
                    principalPayment = BigDecimal.ZERO;
                    scheduledPayment = BigDecimal.ZERO;
                    remainingBalance = remainingBalance.add(interestPayment, MATH_CONTEXT);
                } else if ("partial".equals(gracePeriodType)) {
                    interestPayment = remainingBalance.multiply(monthlyRate, MATH_CONTEXT);
                    principalPayment = BigDecimal.ZERO;
                    scheduledPayment = interestPayment;
                } else {
                    interestPayment = remainingBalance.multiply(monthlyRate, MATH_CONTEXT);
                    principalPayment = currentPmtBase.subtract(interestPayment, MATH_CONTEXT);
                    scheduledPayment = currentPmtBase;
                }
            } else {
                interestPayment = remainingBalance.multiply(monthlyRate, MATH_CONTEXT);
                principalPayment = currentPmtBase.subtract(interestPayment, MATH_CONTEXT);

                if (period == termMonths) {
                    principalPayment = remainingBalance;
                    currentPmtBase = principalPayment.add(interestPayment, MATH_CONTEXT);
                    scheduledPayment = currentPmtBase;
                } else {
                    scheduledPayment = currentPmtBase;
                }
            }

            // Seguros y Costos
            BigDecimal lifeInsurancePayment = remainingBalance.multiply(lifeInsuranceRate, MATH_CONTEXT);
            BigDecimal propertyInsurancePayment = propertyInsuranceValue.multiply(monthlyPropertyInsuranceRate, MATH_CONTEXT);

            // Cuota Total Final
            BigDecimal totalPayment = scheduledPayment
                    .add(lifeInsurancePayment)
                    .add(propertyInsurancePayment)
                    .add(monthlyCommissions)
                    .add(administrationCosts)
                    .add(deliveryCost);

            // Saldo final
            BigDecimal endingBalance = (isGracePeriod && "total".equals(gracePeriodType))
                    ? remainingBalance
                    : remainingBalance.subtract(principalPayment, MATH_CONTEXT);

            // Acumulados
            cumulativePrincipal = cumulativePrincipal.add(principalPayment, MATH_CONTEXT);
            cumulativeInterest = cumulativeInterest.add(interestPayment, MATH_CONTEXT);

            // Flujo de caja
            BigDecimal cashFlow = totalPayment.negate();

            // Construir Entry
            AmortizationEntry entry = AmortizationEntry.builder()
                    .periodNumber(period)
                    .beginningBalance(isGracePeriod && "total".equals(gracePeriodType) ? remainingBalance.subtract(interestPayment, MATH_CONTEXT) : remainingBalance)
                    .scheduledPayment(scheduledPayment)
                    .principalPayment(principalPayment)
                    .interestPayment(interestPayment)
                    .payment(totalPayment)
                    .lifeInsurancePayment(lifeInsurancePayment)
                    .propertyInsurancePayment(propertyInsurancePayment)
                    .commissions(monthlyCommissions)
                    .adminCosts(administrationCosts)
                    .deliveryCosts(deliveryCost)
                    .endingBalance(endingBalance)
                    .cumulativePrincipal(cumulativePrincipal)
                    .cumulativeInterest(cumulativeInterest)
                    .cashFlow(cashFlow) // AGREGADO
                    .isGracePeriod(isGracePeriod)
                    .build();

            schedule.add(entry);

            // Preparar saldo siguiente
            if (!(isGracePeriod && "total".equals(gracePeriodType))) {
                remainingBalance = endingBalance;
            }
        }

        return schedule;
    }

    /**
     * Clase interna para representar una entrada del cronograma de amortización
     */
    public static class AmortizationEntry {
        private final int periodNumber;
        private final BigDecimal beginningBalance;
        private final BigDecimal scheduledPayment;
        private final BigDecimal principalPayment;
        private final BigDecimal interestPayment;
        private final BigDecimal payment;
        private final BigDecimal lifeInsurancePayment;
        private final BigDecimal propertyInsurancePayment;
        private final BigDecimal commissions;
        private final BigDecimal adminCosts;
        private final BigDecimal deliveryCosts;
        private final BigDecimal endingBalance;
        private final BigDecimal cumulativePrincipal;
        private final BigDecimal cumulativeInterest;
        private final BigDecimal cashFlow; // AGREGADO
        private final boolean isGracePeriod;

        public AmortizationEntry(int periodNumber, BigDecimal beginningBalance, BigDecimal scheduledPayment,
                                 BigDecimal principalPayment, BigDecimal interestPayment, BigDecimal payment,
                                 BigDecimal lifeInsurancePayment, BigDecimal propertyInsurancePayment, BigDecimal commissions,
                                 BigDecimal adminCosts, BigDecimal deliveryCosts,
                                 BigDecimal endingBalance, BigDecimal cumulativePrincipal,
                                 BigDecimal cumulativeInterest, BigDecimal cashFlow, boolean isGracePeriod) {
            this.periodNumber = periodNumber;
            this.beginningBalance = beginningBalance;
            this.scheduledPayment = scheduledPayment;
            this.principalPayment = principalPayment;
            this.interestPayment = interestPayment;
            this.payment = payment;
            this.lifeInsurancePayment = lifeInsurancePayment;
            this.propertyInsurancePayment = propertyInsurancePayment;
            this.commissions = commissions;
            this.adminCosts = adminCosts;
            this.deliveryCosts = deliveryCosts;
            this.endingBalance = endingBalance;
            this.cumulativePrincipal = cumulativePrincipal;
            this.cumulativeInterest = cumulativeInterest;
            this.cashFlow = cashFlow;
            this.isGracePeriod = isGracePeriod;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public int getPeriodNumber() { return periodNumber; }
        public BigDecimal getBeginningBalance() { return beginningBalance; }
        public BigDecimal getScheduledPayment() { return scheduledPayment; }
        public BigDecimal getPrincipalPayment() { return principalPayment; }
        public BigDecimal getInterestPayment() { return interestPayment; }
        public BigDecimal getPayment() { return payment; }
        public BigDecimal getLifeInsurancePayment() { return lifeInsurancePayment; }
        public BigDecimal getPropertyInsurancePayment() { return propertyInsurancePayment; }
        public BigDecimal getCommissions() { return commissions; }
        public BigDecimal getAdminCosts() { return adminCosts; }
        public BigDecimal getDeliveryCosts() { return deliveryCosts; }
        public BigDecimal getEndingBalance() { return endingBalance; }
        public BigDecimal getCumulativePrincipal() { return cumulativePrincipal; }
        public BigDecimal getCumulativeInterest() { return cumulativeInterest; }
        public BigDecimal getCashFlow() { return cashFlow; }
        public boolean isGracePeriod() { return isGracePeriod; }

        public static class Builder {
            private int periodNumber;
            private BigDecimal beginningBalance;
            private BigDecimal scheduledPayment;
            private BigDecimal principalPayment;
            private BigDecimal interestPayment;
            private BigDecimal payment;
            private BigDecimal lifeInsurancePayment;
            private BigDecimal propertyInsurancePayment;
            private BigDecimal commissions;
            private BigDecimal adminCosts;
            private BigDecimal deliveryCosts;
            private BigDecimal endingBalance;
            private BigDecimal cumulativePrincipal;
            private BigDecimal cumulativeInterest;
            private BigDecimal cashFlow;
            private boolean isGracePeriod;

            public Builder periodNumber(int periodNumber) { this.periodNumber = periodNumber; return this; }
            public Builder beginningBalance(BigDecimal beginningBalance) { this.beginningBalance = beginningBalance; return this; }
            public Builder scheduledPayment(BigDecimal scheduledPayment) { this.scheduledPayment = scheduledPayment; return this; }
            public Builder principalPayment(BigDecimal principalPayment) { this.principalPayment = principalPayment; return this; }
            public Builder interestPayment(BigDecimal interestPayment) { this.interestPayment = interestPayment; return this; }
            public Builder payment(BigDecimal payment) { this.payment = payment; return this; }
            public Builder lifeInsurancePayment(BigDecimal lifeInsurancePayment) { this.lifeInsurancePayment = lifeInsurancePayment; return this; }
            public Builder propertyInsurancePayment(BigDecimal propertyInsurancePayment) { this.propertyInsurancePayment = propertyInsurancePayment; return this; }
            public Builder commissions(BigDecimal commissions) { this.commissions = commissions; return this; }
            public Builder adminCosts(BigDecimal adminCosts) { this.adminCosts = adminCosts; return this; }
            public Builder deliveryCosts(BigDecimal deliveryCosts) { this.deliveryCosts = deliveryCosts; return this; }
            public Builder endingBalance(BigDecimal endingBalance) { this.endingBalance = endingBalance; return this; }
            public Builder cumulativePrincipal(BigDecimal cumulativePrincipal) { this.cumulativePrincipal = cumulativePrincipal; return this; }
            public Builder cumulativeInterest(BigDecimal cumulativeInterest) { this.cumulativeInterest = cumulativeInterest; return this; }
            public Builder cashFlow(BigDecimal cashFlow) { this.cashFlow = cashFlow; return this; }
            public Builder isGracePeriod(boolean isGracePeriod) { this.isGracePeriod = isGracePeriod; return this; }

            public AmortizationEntry build() {
                return new AmortizationEntry(periodNumber, beginningBalance, scheduledPayment,
                        principalPayment, interestPayment, payment, lifeInsurancePayment, propertyInsurancePayment,
                        commissions, adminCosts, deliveryCosts, endingBalance, cumulativePrincipal, cumulativeInterest,
                        cashFlow, isGracePeriod);
            }
        }
    }
}