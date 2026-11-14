package com.srliq.engine.strategy;

import java.util.List;
import java.util.Objects;

/**
 * Paramètres de la stratégie (équivalent de {@code Config} de la référence, mêmes valeurs
 * par défaut). Construire avec {@link #defaults()} ou {@link #builder()}.
 *
 * @param htfPivotLeft     bougies HTF à gauche d'un pivot
 * @param htfPivotRight    bougies HTF à droite d'un pivot (délai de confirmation)
 * @param zoneMergeAtr     tolérance de fusion des pivots en zone, × ATR(HTF)
 * @param zoneMaxWidthAtr  largeur maximale d'une zone, × ATR(HTF)
 * @param minTouches       contacts minimum pour qu'une zone compte
 * @param zoneMaxAgeBars   âge maximal d'une zone, en bougies HTF
 * @param sweepMinAtr      pénétration minimale du sweep, × ATR(LTF)
 * @param sweepMaxAtr      pénétration maximale du sweep, × ATR(HTF)
 * @param ltfPivotLeft     bougies LTF à gauche d'un pivot
 * @param ltfPivotRight    bougies LTF à droite d'un pivot
 * @param confirmBars      bougies LTF pour confirmer un sweep
 * @param slBufferAtr      marge du stop, × ATR(LTF)
 * @param stopMode         placement du stop
 * @param targetMode       choix de l'objectif
 * @param tpLookback       bougies du TF de tendance pour la liquidité visée
 * @param minRr            R:R minimum avec un objectif structurel
 * @param rr               R:R fixe (mode "rr" ou à défaut d'order block)
 * @param maxRiskHtfAtr    risque maximal, × ATR du TF du stop
 * @param neutralAllowsBoth un biais "neutral" autorise les deux sens
 * @param trendMode        filtre de tendance
 * @param trendLen         période de l'EMA HTF (mode "ema")
 * @param direction        sens autorisés
 * @param sessions         sessions autorisées ; {@code null} ou vide = toutes
 * @param atrPeriod        période des ATR
 */
public record StrategyConfig(
        int htfPivotLeft,
        int htfPivotRight,
        double zoneMergeAtr,
        double zoneMaxWidthAtr,
        int minTouches,
        int zoneMaxAgeBars,
        double sweepMinAtr,
        double sweepMaxAtr,
        int ltfPivotLeft,
        int ltfPivotRight,
        int confirmBars,
        double slBufferAtr,
        StopMode stopMode,
        TargetMode targetMode,
        int tpLookback,
        double minRr,
        double rr,
        double maxRiskHtfAtr,
        boolean neutralAllowsBoth,
        TrendMode trendMode,
        int trendLen,
        DirectionFilter direction,
        List<String> sessions,
        int atrPeriod) {

    public StrategyConfig {
        Objects.requireNonNull(stopMode, "stopMode");
        Objects.requireNonNull(targetMode, "targetMode");
        Objects.requireNonNull(trendMode, "trendMode");
        Objects.requireNonNull(direction, "direction");
        sessions = sessions == null ? null : List.copyOf(sessions);
        if (htfPivotLeft < 1 || htfPivotRight < 1 || ltfPivotLeft < 1 || ltfPivotRight < 1) {
            throw new IllegalArgumentException("Les paramètres de pivots doivent être >= 1");
        }
        if (atrPeriod < 1 || trendLen < 1 || tpLookback < 1) {
            throw new IllegalArgumentException("atrPeriod, trendLen et tpLookback doivent être >= 1");
        }
    }

    public static StrategyConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Filtre de sessions actif (Python : {@code if cfg.sessions and ...}). */
    public boolean hasSessionFilter() {
        return sessions != null && !sessions.isEmpty();
    }

    /** Builder avec les valeurs par défaut de la référence. */
    public static final class Builder {
        private int htfPivotLeft = 5;
        private int htfPivotRight = 5;
        private double zoneMergeAtr = 0.35;
        private double zoneMaxWidthAtr = 1.0;
        private int minTouches = 2;
        private int zoneMaxAgeBars = 300;
        private double sweepMinAtr = 0.2;
        private double sweepMaxAtr = 1.0;
        private int ltfPivotLeft = 3;
        private int ltfPivotRight = 2;
        private int confirmBars = 12;
        private double slBufferAtr = 0.2;
        private StopMode stopMode = StopMode.SWEEP;
        private TargetMode targetMode = TargetMode.OB;
        private int tpLookback = 20;
        private double minRr = 1.0;
        private double rr = 2.0;
        private double maxRiskHtfAtr = 1.5;
        private boolean neutralAllowsBoth = true;
        private TrendMode trendMode = TrendMode.STRUCTURE;
        private int trendLen = 50;
        private DirectionFilter direction = DirectionFilter.BOTH;
        private List<String> sessions = null;
        private int atrPeriod = 14;

        private Builder() {
        }

        private Builder(StrategyConfig c) {
            htfPivotLeft = c.htfPivotLeft;
            htfPivotRight = c.htfPivotRight;
            zoneMergeAtr = c.zoneMergeAtr;
            zoneMaxWidthAtr = c.zoneMaxWidthAtr;
            minTouches = c.minTouches;
            zoneMaxAgeBars = c.zoneMaxAgeBars;
            sweepMinAtr = c.sweepMinAtr;
            sweepMaxAtr = c.sweepMaxAtr;
            ltfPivotLeft = c.ltfPivotLeft;
            ltfPivotRight = c.ltfPivotRight;
            confirmBars = c.confirmBars;
            slBufferAtr = c.slBufferAtr;
            stopMode = c.stopMode;
            targetMode = c.targetMode;
            tpLookback = c.tpLookback;
            minRr = c.minRr;
            rr = c.rr;
            maxRiskHtfAtr = c.maxRiskHtfAtr;
            neutralAllowsBoth = c.neutralAllowsBoth;
            trendMode = c.trendMode;
            trendLen = c.trendLen;
            direction = c.direction;
            sessions = c.sessions;
            atrPeriod = c.atrPeriod;
        }

        public Builder htfPivotLeft(int v) {
            htfPivotLeft = v;
            return this;
        }

        public Builder htfPivotRight(int v) {
            htfPivotRight = v;
            return this;
        }

        public Builder zoneMergeAtr(double v) {
            zoneMergeAtr = v;
            return this;
        }

        public Builder zoneMaxWidthAtr(double v) {
            zoneMaxWidthAtr = v;
            return this;
        }

        public Builder minTouches(int v) {
            minTouches = v;
            return this;
        }

        public Builder zoneMaxAgeBars(int v) {
            zoneMaxAgeBars = v;
            return this;
        }

        public Builder sweepMinAtr(double v) {
            sweepMinAtr = v;
            return this;
        }

        public Builder sweepMaxAtr(double v) {
            sweepMaxAtr = v;
            return this;
        }

        public Builder ltfPivotLeft(int v) {
            ltfPivotLeft = v;
            return this;
        }

        public Builder ltfPivotRight(int v) {
            ltfPivotRight = v;
            return this;
        }

        public Builder confirmBars(int v) {
            confirmBars = v;
            return this;
        }

        public Builder slBufferAtr(double v) {
            slBufferAtr = v;
            return this;
        }

        public Builder stopMode(StopMode v) {
            stopMode = v;
            return this;
        }

        public Builder stopMode(String v) {
            return stopMode(StopMode.fromPython(v));
        }

        public Builder targetMode(TargetMode v) {
            targetMode = v;
            return this;
        }

        public Builder targetMode(String v) {
            return targetMode(TargetMode.fromPython(v));
        }

        public Builder tpLookback(int v) {
            tpLookback = v;
            return this;
        }

        public Builder minRr(double v) {
            minRr = v;
            return this;
        }

        public Builder rr(double v) {
            rr = v;
            return this;
        }

        public Builder maxRiskHtfAtr(double v) {
            maxRiskHtfAtr = v;
            return this;
        }

        public Builder neutralAllowsBoth(boolean v) {
            neutralAllowsBoth = v;
            return this;
        }

        public Builder trendMode(TrendMode v) {
            trendMode = v;
            return this;
        }

        public Builder trendMode(String v) {
            return trendMode(TrendMode.fromPython(v));
        }

        public Builder trendLen(int v) {
            trendLen = v;
            return this;
        }

        public Builder direction(DirectionFilter v) {
            direction = v;
            return this;
        }

        public Builder direction(String v) {
            return direction(DirectionFilter.fromPython(v));
        }

        public Builder sessions(List<String> v) {
            sessions = v;
            return this;
        }

        public Builder atrPeriod(int v) {
            atrPeriod = v;
            return this;
        }

        public StrategyConfig build() {
            return new StrategyConfig(htfPivotLeft, htfPivotRight, zoneMergeAtr, zoneMaxWidthAtr, minTouches,
                    zoneMaxAgeBars, sweepMinAtr, sweepMaxAtr, ltfPivotLeft, ltfPivotRight, confirmBars, slBufferAtr,
                    stopMode, targetMode, tpLookback, minRr, rr, maxRiskHtfAtr, neutralAllowsBoth, trendMode,
                    trendLen, direction, sessions, atrPeriod);
        }
    }
}
