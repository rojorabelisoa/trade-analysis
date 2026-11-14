package com.srliq.app.config;

import com.srliq.engine.backtest.IntradayRules;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Règles intraday du paper trading ({@code srliq.intraday.*}), heures UTC. Valeurs par défaut :
 * celles de {@link IntradayRules#defaults()} (entrées de 7 h à 19 h, à plat à 20:45, 3 trades et
 * -2 R maximum par jour). {@code enabled: false} = pas de règle intraday (positions de nuit permises).
 */
@Validated
@ConfigurationProperties(prefix = "srliq.intraday")
public class IntradayProperties {

    private static final IntradayRules D = IntradayRules.defaults();

    private boolean enabled = true;
    @Min(0)
    @Max(23)
    private int entryStartHour = D.entryStartH();
    @Min(1)
    @Max(24)
    private int entryEndHour = D.entryEndH();
    @Min(0)
    @Max(23)
    private int flatHour = D.flatH();
    @Min(0)
    @Max(59)
    private int flatMinute = D.flatM();
    @Min(1)
    private int maxTradesPerDay = D.maxTradesDay();
    @DecimalMin(value = "0.0", inclusive = false)
    private double maxLossPerDayR = D.maxLossDayR();

    /** Règles du moteur, ou {@code null} si désactivées (convention de {@code Backtester.run}). */
    public IntradayRules toRules() {
        if (!enabled) {
            return null;
        }
        if (entryStartHour >= entryEndHour) {
            throw new IllegalArgumentException("srliq.intraday : entry-start-hour doit précéder entry-end-hour");
        }
        return new IntradayRules(entryStartHour, entryEndHour, flatHour, flatMinute, maxTradesPerDay, maxLossPerDayR);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getEntryStartHour() {
        return entryStartHour;
    }

    public void setEntryStartHour(int entryStartHour) {
        this.entryStartHour = entryStartHour;
    }

    public int getEntryEndHour() {
        return entryEndHour;
    }

    public void setEntryEndHour(int entryEndHour) {
        this.entryEndHour = entryEndHour;
    }

    public int getFlatHour() {
        return flatHour;
    }

    public void setFlatHour(int flatHour) {
        this.flatHour = flatHour;
    }

    public int getFlatMinute() {
        return flatMinute;
    }

    public void setFlatMinute(int flatMinute) {
        this.flatMinute = flatMinute;
    }

    public int getMaxTradesPerDay() {
        return maxTradesPerDay;
    }

    public void setMaxTradesPerDay(int maxTradesPerDay) {
        this.maxTradesPerDay = maxTradesPerDay;
    }

    public double getMaxLossPerDayR() {
        return maxLossPerDayR;
    }

    public void setMaxLossPerDayR(double maxLossPerDayR) {
        this.maxLossPerDayR = maxLossPerDayR;
    }
}
