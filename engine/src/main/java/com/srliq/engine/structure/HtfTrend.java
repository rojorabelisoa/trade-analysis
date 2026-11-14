package com.srliq.engine.structure;

import com.srliq.engine.indicator.Indicators;
import com.srliq.engine.model.CandleSeries;

import java.time.Duration;
import java.util.List;

/** Tendance du timeframe supérieur ({@code htf_trend}). */
public final class HtfTrend {

    private HtfTrend() {
    }

    /**
     * @param pivots  pivots du même timeframe, triés par {@code confirmedAt}
     * @param emaLen  période de la moyenne mobile exponentielle
     * @param tpLook  nombre de bougies pour {@code hh} / {@code ll}
     */
    public static TrendFrame compute(CandleSeries htf, List<Pivot> pivots, Duration dur, int emaLen, int tpLook,
                                     int atrPeriod) {
        int n = htf.size();
        long d = dur.toMillis();
        long[] closeTimes = new long[n];
        int[] bos = new int[n];
        int[] strict = new int[n];
        double[] lh = new double[n];
        double[] ll = new double[n];
        Double lastH = null;
        Double prevH = null;
        Double lastL = null;
        Double prevL = null;
        int trBos = 0;
        int pI = 0;
        for (int i = 0; i < n; i++) {
            closeTimes[i] = htf.timeMillis(i) + d;
            while (pI < pivots.size() && pivots.get(pI).confirmedAtMillis() <= closeTimes[i]) {
                Pivot p = pivots.get(pI);
                if (p.kind() == PivotKind.HIGH) {
                    prevH = lastH;
                    lastH = p.price();
                } else {
                    prevL = lastL;
                    lastL = p.price();
                }
                pI++;
            }
            double c = htf.close(i);
            if (lastH != null && c > lastH) {
                trBos = 1;
            }
            if (lastL != null && c < lastL) {
                trBos = -1;
            }
            int st;
            if (prevH == null || prevL == null) {
                st = 0;
            } else if (lastH > prevH && lastL > prevL) {
                st = 1;
            } else if (lastH < prevH && lastL < prevL) {
                st = -1;
            } else {
                st = 0;
            }
            bos[i] = trBos;
            strict[i] = st;
            lh[i] = lastH == null ? Double.NaN : lastH;
            ll[i] = lastL == null ? Double.NaN : lastL;
        }
        double[] ema = Indicators.ema(htf.closes(), emaLen);
        double[] hh = Indicators.rollingMax(htf.highs(), tpLook);
        double[] lo = Indicators.rollingMin(htf.lows(), tpLook);
        double[] atr = Indicators.atr(htf, atrPeriod);
        return new TrendFrame(closeTimes, bos, strict, ema, lh, ll, hh, lo, atr);
    }
}
