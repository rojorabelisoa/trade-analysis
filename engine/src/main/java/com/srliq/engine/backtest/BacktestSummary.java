package com.srliq.engine.backtest;

import com.srliq.engine.model.Outcome;
import com.srliq.engine.model.Trade;
import com.srliq.engine.util.PyMath;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Statistiques d'un backtest (équivalent de {@code summarize}, mêmes arrondis numpy).
 * Si {@code trades == 0}, les autres champs valent NaN / null et {@link #toMap()} ne contient
 * que {@code {"trades": 0}}.
 *
 * @param winRatePct      "taux_reussite_%" (1 décimale)
 * @param expectancyR     "esperance_R" (3 décimales)
 * @param totalR          "total_R" (2 décimales)
 * @param profitFactor    "profit_factor" (2 décimales, +Infinity sans perte)
 * @param maxDrawdownR    "drawdown_max_R" (2 décimales)
 * @param worstLosingStreak "pire_serie_pertes"
 * @param exitsTpSlEod    "sorties_tp_sl_fin" ("tp/sl/eod", les sorties timeout ne sont pas comptées)
 * @param avgDurationMin  "duree_moyenne_min" (arrondie à l'unité)
 */
public record BacktestSummary(int trades, double winRatePct, double expectancyR, double totalR, double profitFactor,
                              double maxDrawdownR, int worstLosingStreak, String exitsTpSlEod,
                              double avgDurationMin) {

    public static BacktestSummary of(List<Trade> trades) {
        int n = trades.size();
        if (n == 0) {
            return new BacktestSummary(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, null,
                    Double.NaN);
        }
        double[] r = new double[n];
        for (int i = 0; i < n; i++) {
            r[i] = trades.get(i).r();
        }
        // equity = cumsum ; dd = equity - max.accumulate([0] + equity)[1:]
        double equity = 0.0;
        double peak = 0.0;
        double ddMin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            equity = i == 0 ? r[0] : equity + r[i];
            peak = Math.max(peak, equity);
            ddMin = Math.min(ddMin, equity - peak);
        }
        int wins = 0;
        int nPos = 0;
        int nNeg = 0;
        for (double x : r) {
            if (x > 0) {
                nPos++;
            } else if (x < 0) {
                nNeg++;
            }
        }
        wins = nPos;
        double[] pos = new double[nPos];
        double[] neg = new double[nNeg];
        for (int i = 0, p = 0, q = 0; i < n; i++) {
            if (r[i] > 0) {
                pos[p++] = r[i];
            } else if (r[i] < 0) {
                neg[q++] = r[i];
            }
        }
        double gains = PyMath.pairwiseSum(pos);
        double losses = -PyMath.pairwiseSum(neg);
        double[] boolMean = new double[n];
        for (int i = 0; i < n; i++) {
            boolMean[i] = r[i] > 0 ? 1.0 : 0.0;
        }
        double winRate = PyMath.npRound(100 * PyMath.mean(boolMean), 1);
        double expectancy = PyMath.npRound(PyMath.mean(r), 3);
        double total = PyMath.npRound(PyMath.pairwiseSum(r), 2);
        double pf = losses > 0 ? PyMath.npRound(gains / losses, 2) : Double.POSITIVE_INFINITY;
        double dd = PyMath.npRound(ddMin, 2);
        int tp = 0;
        int sl = 0;
        int eod = 0;
        double[] durations = new double[n];
        for (int i = 0; i < n; i++) {
            Trade t = trades.get(i);
            if (t.outcome() == Outcome.TP) {
                tp++;
            } else if (t.outcome() == Outcome.SL) {
                sl++;
            } else if (t.outcome() == Outcome.EOD) {
                eod++;
            }
            Duration d = Duration.between(t.entryTime(), t.exitTime());
            double seconds = d.getSeconds() + d.getNano() / 1e9; // total_seconds()
            durations[i] = seconds / 60;
        }
        double avgDur = PyMath.npRound(PyMath.mean(durations), 0);
        return new BacktestSummary(n, winRate, expectancy, total, pf, dd, worstStreak(r), tp + "/" + sl + "/" + eod,
                avgDur);
    }

    private static int worstStreak(double[] r) {
        int worst = 0;
        int cur = 0;
        for (double x : r) {
            cur = x <= 0 ? cur + 1 : 0;
            worst = Math.max(worst, cur);
        }
        return worst;
    }

    /** Mêmes clés, dans le même ordre, que le dict Python. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trades", trades);
        if (trades == 0) {
            return m;
        }
        m.put("taux_reussite_%", winRatePct);
        m.put("esperance_R", expectancyR);
        m.put("total_R", totalR);
        m.put("profit_factor", profitFactor);
        m.put("drawdown_max_R", maxDrawdownR);
        m.put("pire_serie_pertes", worstLosingStreak);
        m.put("sorties_tp_sl_fin", exitsTpSlEod);
        m.put("duree_moyenne_min", avgDurationMin);
        return m;
    }
}
