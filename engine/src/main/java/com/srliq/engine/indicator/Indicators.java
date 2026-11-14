package com.srliq.engine.indicator;

import com.srliq.engine.model.CandleSeries;

/**
 * Indicateurs identiques à ceux de la référence (pandas).
 *
 * <p>Le lissage exponentiel reproduit {@code Series.ewm(..., adjust=False)} de pandas à l'ULP près :
 * {@code alpha = 1 / (1 + com)} (com dérivé de {@code alpha} ou de {@code span}), puis
 * {@code y = ((1-alpha)*y + alpha*x) / ((1-alpha) + alpha)}, sauf si {@code y == x}.
 */
public final class Indicators {

    private Indicators() {
    }

    /** True range ; première bougie = high - low. */
    public static double[] trueRange(CandleSeries s) {
        int n = s.size();
        double[] tr = new double[n];
        for (int i = 0; i < n; i++) {
            double hl = s.high(i) - s.low(i);
            if (i == 0) {
                tr[i] = hl;
            } else {
                double pc = s.close(i - 1);
                tr[i] = Math.max(hl, Math.max(Math.abs(s.high(i) - pc), Math.abs(s.low(i) - pc)));
            }
        }
        return tr;
    }

    /** ATR de Wilder ({@code structure.atr}) : NaN tant qu'il y a moins de {@code period} valeurs. */
    public static double[] atr(CandleSeries s, int period) {
        double alpha = 1.0 / period;
        double com = (1 - alpha) / alpha;
        return ewm(trueRange(s), com, period);
    }

    /** {@code close.ewm(span=span, adjust=False).mean()}. */
    public static double[] ema(double[] values, int span) {
        double com = (span - 1) / 2.0;
        return ewm(values, com, 0);
    }

    /**
     * {@code ewm(com=com, adjust=False, min_periods=minPeriods).mean()} sur une série sans
     * trou (les NaN sont ignorés comme dans pandas avec {@code ignore_na=False}).
     */
    public static double[] ewm(double[] x, double com, int minPeriods) {
        int n = x.length;
        double[] out = new double[n];
        if (n == 0) {
            return out;
        }
        double alpha = 1.0 / (1.0 + com);
        double oldWtFactor = 1.0 - alpha;
        double newWt = alpha;
        int minp = Math.max(minPeriods, 1);
        double weighted = x[0];
        int nobs = Double.isNaN(weighted) ? 0 : 1;
        out[0] = nobs >= minp ? weighted : Double.NaN;
        double oldWt = 1.0;
        for (int i = 1; i < n; i++) {
            double cur = x[i];
            boolean obs = !Double.isNaN(cur);
            if (obs) {
                nobs++;
            }
            if (!Double.isNaN(weighted)) {
                // ignore_na=False : le poids décroît aussi sur un NaN
                oldWt *= oldWtFactor;
                if (obs) {
                    if (weighted != cur) {
                        weighted = oldWt * weighted + newWt * cur;
                        weighted /= (oldWt + newWt);
                    }
                    oldWt = 1.0;
                }
            } else if (obs) {
                weighted = cur;
            }
            out[i] = nobs >= minp ? weighted : Double.NaN;
        }
        return out;
    }

    /** {@code rolling(window, min_periods=1).max()}. */
    public static double[] rollingMax(double[] x, int window) {
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            double m = Double.NaN;
            for (int j = Math.max(0, i - window + 1); j <= i; j++) {
                if (!Double.isNaN(x[j]) && (Double.isNaN(m) || x[j] > m)) {
                    m = x[j];
                }
            }
            out[i] = m;
        }
        return out;
    }

    /** {@code rolling(window, min_periods=1).min()}. */
    public static double[] rollingMin(double[] x, int window) {
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            double m = Double.NaN;
            for (int j = Math.max(0, i - window + 1); j <= i; j++) {
                if (!Double.isNaN(x[j]) && (Double.isNaN(m) || x[j] < m)) {
                    m = x[j];
                }
            }
            out[i] = m;
        }
        return out;
    }
}
