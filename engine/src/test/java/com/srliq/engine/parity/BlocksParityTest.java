package com.srliq.engine.parity;

import com.fasterxml.jackson.databind.JsonNode;
import com.srliq.engine.data.Resampler;
import com.srliq.engine.data.TradingViewCsv;
import com.srliq.engine.indicator.Indicators;
import com.srliq.engine.model.CandleSeries;
import com.srliq.engine.structure.HtfTrend;
import com.srliq.engine.structure.OrderBlock;
import com.srliq.engine.structure.Pivot;
import com.srliq.engine.structure.Pivots;
import com.srliq.engine.structure.TrendFrame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parité des briques intermédiaires ({@code blocks.json}, {@code candles/*__1h.csv}, {@code __4h.csv}). */
class BlocksParityTest {

    private static final double TOL = 1e-9;
    /** Les CSV agrégés sont écrits avec 10 chiffres significatifs. */
    private static final double CSV_TOL = 1e-6;

    private static JsonNode block(String series) {
        return ParityFixtures.json("blocks.json").get(series);
    }

    private static void assertNum(double exp, double act, double tol, String msg) {
        if (Double.isNaN(exp)) {
            assertTrue(Double.isNaN(act), msg + " : attendu NaN, obtenu " + act);
        } else {
            assertTrue(Math.abs(exp - act) <= tol, msg + " : attendu " + exp + ", obtenu " + act);
        }
    }

    private static double num(JsonNode n) {
        return n == null || n.isNull() ? Double.NaN : n.asDouble();
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo_5m", "trend_15m"})
    void resamplerMatchesPandas(String series) {
        for (String rule : new String[]{"1h", "4h"}) {
            CandleSeries exp = TradingViewCsv.read(ParityFixtures.path("candles/" + series + "__" + rule + ".csv"));
            CandleSeries act = Resampler.resample(ParityFixtures.ltf(series), rule);
            String ctx = series + " " + rule;
            assertEquals(exp.size(), act.size(), ctx + " : nombre de bougies");
            for (int i = 0; i < exp.size(); i++) {
                String m = ctx + " bougie #" + i + " " + exp.time(i);
                assertEquals(exp.time(i), act.time(i), m + " time");
                assertNum(exp.open(i), act.open(i), CSV_TOL, m + " open");
                assertNum(exp.high(i), act.high(i), CSV_TOL, m + " high");
                assertNum(exp.low(i), act.low(i), CSV_TOL, m + " low");
                assertNum(exp.close(i), act.close(i), CSV_TOL, m + " close");
                assertNum(exp.volume(i), act.volume(i), TOL, m + " volume");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo_5m", "trend_15m"})
    void atrMatchesPandas(String series) {
        JsonNode b = block(series);
        double[] ltfAtr = Indicators.atr(ParityFixtures.ltf(series), 14);
        int[] idx = {13, 14, 100, ltfAtr.length - 1};
        for (int k = 0; k < idx.length; k++) {
            assertNum(num(b.get("atr14_ltf_tail").get(k)), ltfAtr[idx[k]], TOL, series + " ATR LTF [" + idx[k] + "]");
        }
        double[] h1Atr = Indicators.atr(ParityFixtures.resampled(series, "1h"), 14);
        JsonNode at = b.get("atr14_h1_at");
        for (int k = 0; k < at.get("index").size(); k++) {
            int i = at.get("index").get(k).asInt();
            assertNum(num(at.get("values").get(k)), h1Atr[i], TOL, series + " ATR H1 [" + i + "]");
        }
        assertTrue(Double.isNaN(h1Atr[12]), "ATR NaN tant qu'il y a moins de 14 valeurs");
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo_5m", "trend_15m"})
    void pivotsMatchReference(String series) {
        JsonNode exp = block(series).get("h1_pivots_5_5");
        List<Pivot> act = Pivots.find(ParityFixtures.resampled(series, "1h"), 5, 5, Duration.ofHours(1));
        assertEquals(exp.size(), act.size(), series + " : nombre de pivots H1");
        for (int k = 0; k < exp.size(); k++) {
            JsonNode e = exp.get(k);
            Pivot p = act.get(k);
            String m = series + " pivot #" + k;
            assertEquals(e.get("kind").asText(), p.kind().pyName(), m + " kind");
            assertEquals(e.get("time").asText(), p.time().toString(), m + " time");
            assertEquals(e.get("confirmed_at").asText(), p.confirmedAt().toString(), m + " confirmed_at");
            assertNum(e.get("price").asDouble(), p.price(), TOL, m + " price");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo_5m", "trend_15m"})
    void orderBlocksMatchReference(String series) {
        JsonNode exp = block(series).get("h1_order_blocks");
        List<OrderBlock> act = OrderBlock.find(ParityFixtures.resampled(series, "1h"), Duration.ofHours(1));
        assertEquals(exp.size(), act.size(), series + " : nombre d'order blocks H1");
        for (int k = 0; k < exp.size(); k++) {
            JsonNode e = exp.get(k);
            OrderBlock o = act.get(k);
            String m = series + " OB #" + k;
            assertEquals(e.get("dir").asInt(), o.dir(), m + " dir");
            assertEquals(e.get("known_at").asText(), o.knownAt().toString(), m + " known_at");
            assertNum(e.get("top").asDouble(), o.top(), TOL, m + " top");
            assertNum(e.get("bot").asDouble(), o.bot(), TOL, m + " bot");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo_5m", "trend_15m"})
    void h4TrendMatchesReference(String series) {
        JsonNode exp = block(series).get("h4_trend_structure");
        CandleSeries h4 = ParityFixtures.resampled(series, "4h");
        Duration d4 = Duration.ofHours(4);
        TrendFrame tr = HtfTrend.compute(h4, Pivots.find(h4, 5, 5, d4), d4, 50, 20, 14);
        assertEquals(exp.size(), tr.size(), series + " : nombre de lignes de tendance");
        for (int k = 0; k < exp.size(); k++) {
            JsonNode e = exp.get(k);
            String m = series + " tendance #" + k + " " + e.get("close_time").asText();
            assertEquals(e.get("close_time").asText(), tr.closeTime(k).toString(), m + " close_time");
            assertEquals(e.get("bos").asInt(), tr.bos(k), m + " bos");
            assertEquals(e.get("strict").asInt(), tr.strict(k), m + " strict");
            assertNum(num(e.get("ema")), tr.ema(k), TOL, m + " ema");
            assertNum(num(e.get("last_h")), tr.lastH(k), TOL, m + " last_h");
            assertNum(num(e.get("last_l")), tr.lastL(k), TOL, m + " last_l");
            assertNum(num(e.get("hh")), tr.hh(k), TOL, m + " hh");
            assertNum(num(e.get("ll")), tr.ll(k), TOL, m + " ll");
            assertNum(num(e.get("atr")), tr.atr(k), TOL, m + " atr");
        }
    }
}
