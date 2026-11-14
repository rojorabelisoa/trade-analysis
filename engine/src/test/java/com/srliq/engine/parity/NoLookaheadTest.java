package com.srliq.engine.parity;

import com.srliq.engine.data.Resampler;
import com.srliq.engine.model.CandleSeries;
import com.srliq.engine.model.Signal;
import com.srliq.engine.strategy.SignalEngine;
import com.srliq.engine.strategy.StrategyConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Équivalent de {@code test_no_lookahead} : couper les données ne change aucun signal déjà émis.
 * Appliqué à chaque scénario de parité (séries, timeframes et configs variés), à 2/3 et à 1/2.
 */
class NoLookaheadTest {

    static List<String> scenarios() {
        return ParityFixtures.scenarioNames();
    }

    /** HTF reconstruit sur la série coupée, réduit aux bougies complètes avant la coupure. */
    private static CandleSeries completeBefore(CandleSeries ltfCut, Duration rule, Instant cut) {
        CandleSeries htf = Resampler.resample(ltfCut, rule);
        int n = 0;
        while (n < htf.size() && !htf.time(n).plus(rule).isAfter(cut)) {
            n++;
        }
        return htf.slice(0, n);
    }

    private static List<String> keys(List<Signal> signals) {
        List<String> out = new ArrayList<>();
        for (Signal s : signals) {
            // mêmes champs que la référence : (time, direction, entry, stop) — comparés exactement
            out.add(s.time() + " " + s.direction() + " " + s.entry() + " " + s.stop());
        }
        return out;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void cuttingTheSeriesNeverChangesPastSignals(String name) {
        ParityFixtures.ScenarioInputs in = ParityFixtures.scenario(name);
        CandleSeries ltf = in.ltf();
        StrategyConfig cfg = in.config();
        List<Signal> full = SignalEngine.generate(in.zones(), ltf, in.symbol(), cfg, in.bias(), in.news(), in.trend());
        assertFalse(full.isEmpty(), name + " : aucun signal sur la série complète");
        Duration zoneRule = Resampler.parseRule(in.doc().get("zone_rule").asText());
        Duration trendRule = in.trend() == null ? null : Resampler.parseRule(in.doc().get("trend_rule").asText());
        Duration ltfBar = ltf.barDuration();

        for (int[] frac : new int[][]{{2, 3}, {1, 2}}) {
            Instant cut = ltf.time(ltf.size() * frac[0] / frac[1]);
            CandleSeries ltfCut = ltf.before(cut);
            CandleSeries zonesCut = completeBefore(ltfCut, zoneRule, cut);
            CandleSeries trendCut = trendRule == null ? null : completeBefore(ltfCut, trendRule, cut);
            List<Signal> part = SignalEngine.generate(zonesCut, ltfCut, in.symbol(), cfg, in.bias(), in.news(),
                    trendCut);
            Instant lastKnown = ltfCut.time(ltfCut.size() - 1).plus(ltfBar);
            List<Signal> expected = full.stream().filter(s -> !s.time().isAfter(lastKnown)).toList();
            assertEquals(keys(expected), keys(part), name + " coupé à " + cut);
        }
    }
}
