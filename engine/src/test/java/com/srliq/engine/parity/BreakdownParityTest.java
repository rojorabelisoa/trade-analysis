package com.srliq.engine.parity;

import com.srliq.engine.backtest.Backtester;
import com.srliq.engine.backtest.Breakdown;
import com.srliq.engine.model.Trade;
import com.srliq.engine.strategy.SignalEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ventilation {@code breakdown} sur le scénario s2 (sans intraday, coût 0.3, 288 bougies).
 * Valeurs attendues obtenues en exécutant {@code backtest.breakdown} de la référence Python
 * (elles ne figurent pas dans les fixtures générées).
 */
class BreakdownParityTest {

    private static List<Trade> trades() {
        ParityFixtures.ScenarioInputs in = ParityFixtures.scenario("s2_sans_tendance_rr_fixe");
        return Backtester.run(in.ltf(), SignalEngine.generate(in.zones(), in.ltf(), in.symbol(), in.config(),
                in.bias(), in.news(), in.trend()), 0.3, 288, null);
    }

    private static void row(Breakdown.Row r, String key, int n, double win, double esp, double total) {
        assertEquals(key, r.key());
        assertEquals(n, r.trades(), key + " trades");
        assertEquals(win, r.winRatePct(), key + " taux_reussite_%");
        assertEquals(esp, r.expectancyR(), key + " esperance_R");
        assertEquals(total, r.totalR(), key + " total_R");
    }

    @Test
    void breakdownMatchesReference() {
        List<Trade> t = trades();
        assertEquals(58, t.size());
        List<Breakdown.Row> s = Breakdown.bySession(t);
        assertEquals(5, s.size());
        row(s.get(0), "asie", 19, 15.8, -0.540, -10.25);
        row(s.get(1), "fermeture", 8, 12.5, -0.683, -5.47);
        row(s.get(2), "londres", 16, 12.5, -0.682, -10.91);
        row(s.get(3), "new_york", 11, 27.3, -0.389, -4.28);
        row(s.get(4), "overlap_londres_ny", 4, 25.0, -0.310, -1.24);
        List<Breakdown.Row> d = Breakdown.byDirection(t);
        row(d.get(0), "long", 32, 18.8, -0.468, -14.96);
        row(d.get(1), "short", 26, 15.4, -0.661, -17.18);
        List<Breakdown.Row> o = Breakdown.byOutcome(t);
        row(o.get(0), "sl", 47, 0.0, -1.059, -49.78);
        row(o.get(1), "timeout", 2, 50.0, 0.120, 0.24);
        row(o.get(2), "tp", 9, 100.0, 1.932, 17.39);
    }
}
