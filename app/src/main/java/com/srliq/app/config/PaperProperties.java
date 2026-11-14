package com.srliq.app.config;

import com.srliq.app.instrument.Symbols;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paper trading ({@code srliq.paper.*}).
 *
 * <ul>
 *   <li>{@code enabled} : rejeu des trades simulés après chaque calcul de signaux ;</li>
 *   <li>{@code default-cost} / {@code costs.<SYMBOLE>} : coût par trade (commission + glissement),
 *       en points de prix, retiré du résultat de chaque trade comme dans le backtest ;</li>
 *   <li>{@code max-hold-bars} : sortie forcée après ce nombre de bougies du timeframe d'entrée ;</li>
 *   <li>{@code capital} et {@code risk-percent} : dimensionnement (capital fixe, sans capitalisation).</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "srliq.paper")
public class PaperProperties {

    private boolean enabled = true;

    /** Coût par défaut, en points de prix (valeur des backtests de la référence). */
    @DecimalMin("0.0")
    private double defaultCost = 0.3;

    /** Coût par instrument ("GC" -> 0.3) ; absent = {@link #defaultCost}. */
    private Map<String, Double> costs = new LinkedHashMap<>();

    @Min(1)
    private int maxHoldBars = 288;

    @DecimalMin(value = "0.0", inclusive = false)
    private double capital = 100_000;

    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100.0")
    private double riskPercent = 1.0;

    /** Coût d'un trade sur {@code symbol} (symbole normalisé). */
    public double costFor(String symbol) {
        String s = Symbols.normalize(symbol);
        for (Map.Entry<String, Double> e : costs.entrySet()) {
            if (Symbols.normalize(e.getKey()).equals(s) && e.getValue() != null) {
                return e.getValue();
            }
        }
        return defaultCost;
    }

    /** Budget de risque par trade, en devise : capital x risque %. */
    public double riskBudget() {
        return capital * riskPercent / 100.0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getDefaultCost() {
        return defaultCost;
    }

    public void setDefaultCost(double defaultCost) {
        this.defaultCost = defaultCost;
    }

    public Map<String, Double> getCosts() {
        return costs;
    }

    public void setCosts(Map<String, Double> costs) {
        this.costs = costs == null ? new LinkedHashMap<>() : costs;
    }

    public int getMaxHoldBars() {
        return maxHoldBars;
    }

    public void setMaxHoldBars(int maxHoldBars) {
        this.maxHoldBars = maxHoldBars;
    }

    public double getCapital() {
        return capital;
    }

    public void setCapital(double capital) {
        this.capital = capital;
    }

    public double getRiskPercent() {
        return riskPercent;
    }

    public void setRiskPercent(double riskPercent) {
        this.riskPercent = riskPercent;
    }
}
