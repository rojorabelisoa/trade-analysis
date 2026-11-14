package com.srliq.engine.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reproduction exacte des arrondis et sommes de la référence Python/numpy.
 *
 * <ul>
 *   <li>{@link #pyRound} : {@code round(x, n)} sur un {@code float} Python (arrondi
 *       demi-pair de la valeur binaire exacte) ;</li>
 *   <li>{@link #npRound} : {@code round(np.float64, n)} / {@code np.round} (multiplier,
 *       {@code rint}, diviser) ;</li>
 *   <li>{@link #pairwiseSum} : sommation par paires de numpy ({@code ndarray.sum()}) ;</li>
 *   <li>{@link #format2} : format Python {@code "%.2f"}.</li>
 * </ul>
 */
public final class PyMath {

    private PyMath() {
    }

    /** {@code round(x, ndigits)} de Python sur un float. */
    public static double pyRound(double x, int ndigits) {
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return x;
        }
        double r = new BigDecimal(x).setScale(ndigits, RoundingMode.HALF_EVEN).doubleValue();
        // Python conserve le signe de zéro (round(-0.001, 2) == -0.0).
        return r == 0.0 ? Math.copySign(0.0, x) : r;
    }

    /** {@code round(np.float64(x), decimals)} : rint(x * 10^d) / 10^d (decimals >= 0). */
    public static double npRound(double x, int decimals) {
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals < 0 non supporté");
        }
        double f = Math.pow(10.0, decimals);
        return Math.rint(x * f) / f;
    }

    /** Format Python {@code f"{x:.2f}"} (arrondi demi-pair sur la valeur binaire exacte). */
    public static String format2(double x) {
        return formatFixed(x, 2);
    }

    /** Format Python {@code f"{x:.Nf}"}. */
    public static String formatFixed(double x, int decimals) {
        if (Double.isNaN(x)) {
            return "nan";
        }
        if (Double.isInfinite(x)) {
            return x > 0 ? "inf" : "-inf";
        }
        BigDecimal bd = new BigDecimal(x).setScale(decimals, RoundingMode.HALF_EVEN);
        String s = bd.toPlainString();
        if (bd.signum() == 0 && (x < 0 || (x == 0.0 && 1.0 / x < 0))) {
            s = "-" + s; // Python affiche "-0.00"
        }
        return s;
    }

    /** Somme numpy ({@code np.add.reduce}) : 0 + somme par paires (blocs de 8, seuil 128). */
    public static double pairwiseSum(double[] a) {
        return 0.0 + pairwise(a, 0, a.length);
    }

    /** {@code np.mean} : somme par paires / n. NaN si vide. */
    public static double mean(double[] a) {
        if (a.length == 0) {
            return Double.NaN;
        }
        return pairwiseSum(a) / a.length;
    }

    private static double pairwise(double[] a, int from, int n) {
        if (n < 8) {
            double res = 0.0;
            for (int i = 0; i < n; i++) {
                res += a[from + i];
            }
            return res;
        } else if (n <= 128) {
            double[] r = new double[8];
            for (int j = 0; j < 8; j++) {
                r[j] = a[from + j];
            }
            int i;
            for (i = 8; i < n - (n % 8); i += 8) {
                for (int j = 0; j < 8; j++) {
                    r[j] += a[from + i + j];
                }
            }
            double res = ((r[0] + r[1]) + (r[2] + r[3])) + ((r[4] + r[5]) + (r[6] + r[7]));
            for (; i < n; i++) {
                res += a[from + i];
            }
            return res;
        } else {
            int n2 = n / 2;
            n2 -= n2 % 8;
            return pairwise(a, from, n2) + pairwise(a, from + n2, n - n2);
        }
    }

    /** Somme compensée (Kahan) utilisée par les agrégations groupby de pandas. */
    public static double kahanSum(double[] a) {
        double sum = 0.0;
        double comp = 0.0;
        for (double v : a) {
            double y = v - comp;
            double t = sum + y;
            comp = t - sum - y;
            sum = t;
        }
        return sum;
    }

    /**
     * {@code np.searchsorted(a, x, side="right")} : nombre d'éléments {@code <= x}.
     */
    public static int searchRight(long[] a, long x) {
        int lo = 0;
        int hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] <= x) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * {@code np.searchsorted(a, x, side="left")} : nombre d'éléments {@code < x}.
     */
    public static int searchLeft(long[] a, long x) {
        int lo = 0;
        int hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] < x) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
