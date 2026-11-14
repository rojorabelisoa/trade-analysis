package com.srliq.app.config;

import com.srliq.engine.strategy.DirectionFilter;
import com.srliq.engine.strategy.StopMode;
import com.srliq.engine.strategy.StrategyConfig;
import com.srliq.engine.strategy.TargetMode;
import com.srliq.engine.strategy.TrendMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Paramètres de stratégie {@code srliq.strategy.*}. Les valeurs par défaut sont lues dans
 * {@link StrategyConfig#defaults()} : sans configuration, l'application calcule exactement comme
 * le moteur (et donc comme la référence Python).
 */
@ConfigurationProperties(prefix = "srliq.strategy")
public class StrategyProperties {

    private static final StrategyConfig D = StrategyConfig.defaults();

    private int htfPivotLeft = D.htfPivotLeft();
    private int htfPivotRight = D.htfPivotRight();
    private double zoneMergeAtr = D.zoneMergeAtr();
    private double zoneMaxWidthAtr = D.zoneMaxWidthAtr();
    private int minTouches = D.minTouches();
    private int zoneMaxAgeBars = D.zoneMaxAgeBars();
    private double sweepMinAtr = D.sweepMinAtr();
    private double sweepMaxAtr = D.sweepMaxAtr();
    private int ltfPivotLeft = D.ltfPivotLeft();
    private int ltfPivotRight = D.ltfPivotRight();
    private int confirmBars = D.confirmBars();
    private double slBufferAtr = D.slBufferAtr();
    private StopMode stopMode = D.stopMode();
    private TargetMode targetMode = D.targetMode();
    private int tpLookback = D.tpLookback();
    private double minRr = D.minRr();
    private double rr = D.rr();
    private double maxRiskHtfAtr = D.maxRiskHtfAtr();
    private boolean neutralAllowsBoth = D.neutralAllowsBoth();
    private TrendMode trendMode = D.trendMode();
    private int trendLen = D.trendLen();
    private DirectionFilter direction = D.direction();
    /** Sessions autorisées ; vide = toutes. */
    private List<String> sessions = D.sessions();
    private int atrPeriod = D.atrPeriod();

    /** Construit (et valide) la configuration du moteur. */
    public StrategyConfig toConfig() {
        return StrategyConfig.builder()
                .htfPivotLeft(htfPivotLeft)
                .htfPivotRight(htfPivotRight)
                .zoneMergeAtr(zoneMergeAtr)
                .zoneMaxWidthAtr(zoneMaxWidthAtr)
                .minTouches(minTouches)
                .zoneMaxAgeBars(zoneMaxAgeBars)
                .sweepMinAtr(sweepMinAtr)
                .sweepMaxAtr(sweepMaxAtr)
                .ltfPivotLeft(ltfPivotLeft)
                .ltfPivotRight(ltfPivotRight)
                .confirmBars(confirmBars)
                .slBufferAtr(slBufferAtr)
                .stopMode(stopMode)
                .targetMode(targetMode)
                .tpLookback(tpLookback)
                .minRr(minRr)
                .rr(rr)
                .maxRiskHtfAtr(maxRiskHtfAtr)
                .neutralAllowsBoth(neutralAllowsBoth)
                .trendMode(trendMode)
                .trendLen(trendLen)
                .direction(direction)
                .sessions(sessions == null || sessions.isEmpty() ? null : sessions)
                .atrPeriod(atrPeriod)
                .build();
    }

    public int getHtfPivotLeft() {
        return htfPivotLeft;
    }

    public void setHtfPivotLeft(int htfPivotLeft) {
        this.htfPivotLeft = htfPivotLeft;
    }

    public int getHtfPivotRight() {
        return htfPivotRight;
    }

    public void setHtfPivotRight(int htfPivotRight) {
        this.htfPivotRight = htfPivotRight;
    }

    public double getZoneMergeAtr() {
        return zoneMergeAtr;
    }

    public void setZoneMergeAtr(double zoneMergeAtr) {
        this.zoneMergeAtr = zoneMergeAtr;
    }

    public double getZoneMaxWidthAtr() {
        return zoneMaxWidthAtr;
    }

    public void setZoneMaxWidthAtr(double zoneMaxWidthAtr) {
        this.zoneMaxWidthAtr = zoneMaxWidthAtr;
    }

    public int getMinTouches() {
        return minTouches;
    }

    public void setMinTouches(int minTouches) {
        this.minTouches = minTouches;
    }

    public int getZoneMaxAgeBars() {
        return zoneMaxAgeBars;
    }

    public void setZoneMaxAgeBars(int zoneMaxAgeBars) {
        this.zoneMaxAgeBars = zoneMaxAgeBars;
    }

    public double getSweepMinAtr() {
        return sweepMinAtr;
    }

    public void setSweepMinAtr(double sweepMinAtr) {
        this.sweepMinAtr = sweepMinAtr;
    }

    public double getSweepMaxAtr() {
        return sweepMaxAtr;
    }

    public void setSweepMaxAtr(double sweepMaxAtr) {
        this.sweepMaxAtr = sweepMaxAtr;
    }

    public int getLtfPivotLeft() {
        return ltfPivotLeft;
    }

    public void setLtfPivotLeft(int ltfPivotLeft) {
        this.ltfPivotLeft = ltfPivotLeft;
    }

    public int getLtfPivotRight() {
        return ltfPivotRight;
    }

    public void setLtfPivotRight(int ltfPivotRight) {
        this.ltfPivotRight = ltfPivotRight;
    }

    public int getConfirmBars() {
        return confirmBars;
    }

    public void setConfirmBars(int confirmBars) {
        this.confirmBars = confirmBars;
    }

    public double getSlBufferAtr() {
        return slBufferAtr;
    }

    public void setSlBufferAtr(double slBufferAtr) {
        this.slBufferAtr = slBufferAtr;
    }

    public StopMode getStopMode() {
        return stopMode;
    }

    public void setStopMode(StopMode stopMode) {
        this.stopMode = stopMode;
    }

    public TargetMode getTargetMode() {
        return targetMode;
    }

    public void setTargetMode(TargetMode targetMode) {
        this.targetMode = targetMode;
    }

    public int getTpLookback() {
        return tpLookback;
    }

    public void setTpLookback(int tpLookback) {
        this.tpLookback = tpLookback;
    }

    public double getMinRr() {
        return minRr;
    }

    public void setMinRr(double minRr) {
        this.minRr = minRr;
    }

    public double getRr() {
        return rr;
    }

    public void setRr(double rr) {
        this.rr = rr;
    }

    public double getMaxRiskHtfAtr() {
        return maxRiskHtfAtr;
    }

    public void setMaxRiskHtfAtr(double maxRiskHtfAtr) {
        this.maxRiskHtfAtr = maxRiskHtfAtr;
    }

    public boolean isNeutralAllowsBoth() {
        return neutralAllowsBoth;
    }

    public void setNeutralAllowsBoth(boolean neutralAllowsBoth) {
        this.neutralAllowsBoth = neutralAllowsBoth;
    }

    public TrendMode getTrendMode() {
        return trendMode;
    }

    public void setTrendMode(TrendMode trendMode) {
        this.trendMode = trendMode;
    }

    public int getTrendLen() {
        return trendLen;
    }

    public void setTrendLen(int trendLen) {
        this.trendLen = trendLen;
    }

    public DirectionFilter getDirection() {
        return direction;
    }

    public void setDirection(DirectionFilter direction) {
        this.direction = direction;
    }

    public List<String> getSessions() {
        return sessions;
    }

    public void setSessions(List<String> sessions) {
        this.sessions = sessions;
    }

    public int getAtrPeriod() {
        return atrPeriod;
    }

    public void setAtrPeriod(int atrPeriod) {
        this.atrPeriod = atrPeriod;
    }
}
