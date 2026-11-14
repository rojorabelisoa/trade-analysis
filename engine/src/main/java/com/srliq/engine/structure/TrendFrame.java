package com.srliq.engine.structure;

import java.time.Instant;

/**
 * Tendance HTF connue à la clôture de chaque bougie HTF (résultat de {@code htf_trend}).
 * Index = heure de clôture. Valeurs absentes = NaN.
 */
public final class TrendFrame {

    private final long[] closeTimes;
    private final int[] bos;
    private final int[] strict;
    private final double[] ema;
    private final double[] lastH;
    private final double[] lastL;
    private final double[] hh;
    private final double[] ll;
    private final double[] atr;

    TrendFrame(long[] closeTimes, int[] bos, int[] strict, double[] ema, double[] lastH, double[] lastL,
               double[] hh, double[] ll, double[] atr) {
        this.closeTimes = closeTimes;
        this.bos = bos;
        this.strict = strict;
        this.ema = ema;
        this.lastH = lastH;
        this.lastL = lastL;
        this.hh = hh;
        this.ll = ll;
        this.atr = atr;
    }

    public int size() {
        return closeTimes.length;
    }

    public long[] closeTimesMillis() {
        return closeTimes.clone();
    }

    public Instant closeTime(int i) {
        return Instant.ofEpochMilli(closeTimes[i]);
    }

    /** Index de la dernière bougie HTF clôturée à l'instant {@code t} (-1 si aucune). */
    public int indexAt(long tMillis) {
        return com.srliq.engine.util.PyMath.searchRight(closeTimes, tMillis) - 1;
    }

    public int bos(int i) {
        return bos[i];
    }

    public int strict(int i) {
        return strict[i];
    }

    public double ema(int i) {
        return ema[i];
    }

    public double lastH(int i) {
        return lastH[i];
    }

    public double lastL(int i) {
        return lastL[i];
    }

    public double hh(int i) {
        return hh[i];
    }

    public double ll(int i) {
        return ll[i];
    }

    public double atr(int i) {
        return atr[i];
    }
}
