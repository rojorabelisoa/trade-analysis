package com.srliq.engine.backtest;

import com.srliq.engine.model.Trade;
import com.srliq.engine.util.PyMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Ventilation des résultats (équivalent de {@code breakdown}) : par session, par sens ou par
 * type de sortie. Comme la référence, chaque {@code r} est d'abord arrondi à 3 décimales
 * ({@code trades_frame}), les groupes sont triés par clé, la moyenne et la somme sont
 * compensées (groupby pandas) puis arrondies façon numpy.
 */
public final class Breakdown {

    /** Clé de regroupement. */
    public enum Key {
        SESSION(t -> t.signal().session()),
        DIRECTION(t -> t.signal().direction().pyName()),
        OUTCOME(t -> t.outcome().pyName()),
        BIAS(t -> t.signal().bias());

        private final Function<Trade, String> extractor;

        Key(Function<Trade, String> extractor) {
            this.extractor = extractor;
        }

        public String of(Trade t) {
            return extractor.apply(t);
        }
    }

    /** Ligne : "trades", "taux_reussite_%", "esperance_R", "total_R". */
    public record Row(String key, int trades, double winRatePct, double expectancyR, double totalR) {
    }

    private Breakdown() {
    }

    public static List<Row> of(List<Trade> trades, Key key) {
        Map<String, List<Double>> groups = new TreeMap<>();
        for (Trade t : trades) {
            groups.computeIfAbsent(key.of(t), k -> new ArrayList<>()).add(PyMath.pyRound(t.r(), 3));
        }
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : groups.entrySet()) {
            double[] r = e.getValue().stream().mapToDouble(Double::doubleValue).toArray();
            int wins = 0;
            for (double x : r) {
                if (x > 0) {
                    wins++;
                }
            }
            double sum = PyMath.kahanSum(r);
            out.add(new Row(e.getKey(), r.length,
                    PyMath.npRound(100 * ((double) wins / r.length), 1),
                    PyMath.npRound(sum / r.length, 3),
                    PyMath.npRound(sum, 2)));
        }
        return out;
    }

    public static List<Row> bySession(List<Trade> trades) {
        return of(trades, Key.SESSION);
    }

    public static List<Row> byDirection(List<Trade> trades) {
        return of(trades, Key.DIRECTION);
    }

    public static List<Row> byOutcome(List<Trade> trades) {
        return of(trades, Key.OUTCOME);
    }
}
