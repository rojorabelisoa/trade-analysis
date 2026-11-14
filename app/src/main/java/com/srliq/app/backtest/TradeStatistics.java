package com.srliq.app.backtest;

import com.srliq.engine.backtest.BacktestSummary;
import com.srliq.engine.backtest.Breakdown;
import com.srliq.engine.model.Trade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Statistiques d'une liste de trades, calculées par le moteur ({@link BacktestSummary},
 * {@link Breakdown}) et mises en forme pour l'API (JSON sans NaN ni Infinity).
 */
public final class TradeStatistics {

    private TradeStatistics() {
    }

    /**
     * Résumé de {@link BacktestSummary}. Les valeurs non définies (aucun trade) valent {@code null} ;
     * sans perte, {@code profitFactor} vaut {@code null} et {@code noLosses} {@code true}.
     */
    public record SummaryView(int trades, Double winRatePct, Double expectancyR, Double totalR, Double profitFactor,
                              boolean noLosses, Double maxDrawdownR, int worstLosingStreak, String exitsTpSlEod,
                              Double avgDurationMin) {

        public static SummaryView of(BacktestSummary s) {
            return new SummaryView(s.trades(), finite(s.winRatePct()), finite(s.expectancyR()), finite(s.totalR()),
                    finite(s.profitFactor()), s.trades() > 0 && Double.isInfinite(s.profitFactor()),
                    finite(s.maxDrawdownR()), s.worstLosingStreak(), s.exitsTpSlEod(), finite(s.avgDurationMin()));
        }
    }

    /** Point de la courbe cumulée : R cumulé après la sortie du trade à l'heure {@code time}. */
    public record EquityPoint(Instant time, double r) {
    }

    /** Résumé + ventilations (session, sens, type de sortie) + courbe cumulée en R. */
    public record Stats(SummaryView summary, List<Breakdown.Row> bySession, List<Breakdown.Row> byDirection,
                        List<Breakdown.Row> byOutcome, List<EquityPoint> equityCurve) {
    }

    /** {@code trades} dans l'ordre du backtest (heure d'entrée). */
    public static Stats of(List<Trade> trades) {
        return new Stats(SummaryView.of(BacktestSummary.of(trades)), Breakdown.bySession(trades),
                Breakdown.byDirection(trades), Breakdown.byOutcome(trades), equityCurve(trades));
    }

    /** R cumulé, point par point, dans l'ordre des sorties (tri stable). */
    public static List<EquityPoint> equityCurve(List<Trade> trades) {
        List<Trade> sorted = new ArrayList<>(trades);
        sorted.sort(Comparator.comparing(Trade::exitTime));
        List<EquityPoint> out = new ArrayList<>(sorted.size());
        double cum = 0.0;
        for (Trade t : sorted) {
            cum += t.r();
            out.add(new EquityPoint(t.exitTime(), cum));
        }
        return out;
    }

    private static Double finite(double v) {
        return Double.isFinite(v) ? v : null;
    }
}
