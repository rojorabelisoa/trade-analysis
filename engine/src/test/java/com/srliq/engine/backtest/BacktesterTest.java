package com.srliq.engine.backtest;

import com.srliq.engine.model.CandleSeries;
import com.srliq.engine.model.Direction;
import com.srliq.engine.model.Outcome;
import com.srliq.engine.model.Signal;
import com.srliq.engine.model.Trade;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Portage des tests de backtest de {@code tests/test_core.py}. */
class BacktesterTest {

    private static CandleSeries bars(double[][] rows, String start) {
        int n = rows.length;
        long t0 = Instant.parse(start).toEpochMilli();
        long[] t = new long[n];
        double[] o = new double[n];
        double[] h = new double[n];
        double[] l = new double[n];
        double[] c = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = t0 + i * 300_000L;
            o[i] = rows[i][0];
            h[i] = rows[i][1];
            l[i] = rows[i][2];
            c[i] = rows[i][3];
        }
        return CandleSeries.ofArrays(t, o, h, l, c, new double[n]);
    }

    private static double[][] repeat(double[] row, int n) {
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            out[i] = row;
        }
        return out;
    }

    private static Signal sig(Instant t) {
        return new Signal("GC", Direction.LONG, t, 100.0, 95.0, 110.0, 2.0, "z", t, 96.0, "long", "londres", null);
    }

    @Test
    void stopCountsFirstWhenStopAndTargetInSameBar() {
        CandleSeries ltf = bars(new double[][]{{100, 101, 99, 100}, {100, 111, 94, 100}, {100, 101, 99, 100}},
                "2026-01-05T10:00:00Z");
        List<Trade> trades = Backtester.run(ltf, List.of(sig(ltf.time(1))), 0.0, 288, null);
        assertEquals(Outcome.SL, trades.get(0).outcome());
        assertEquals(-1.0, trades.get(0).r(), 1e-12);
    }

    @Test
    void targetAndCost() {
        CandleSeries ltf = bars(new double[][]{{100, 101, 99, 100}, {100, 111, 99, 110}, {110, 111, 109, 110}},
                "2026-01-05T10:00:00Z");
        List<Trade> trades = Backtester.run(ltf, List.of(sig(ltf.time(1))), 0.5, 288, null);
        assertEquals(Outcome.TP, trades.get(0).outcome());
        assertEquals((10 - 0.5) / 5, trades.get(0).r(), 1e-12);
        assertEquals(1, BacktestSummary.of(trades).trades());
    }

    @Test
    void timeoutExitsAtCloseAfterMaxHoldBars() {
        CandleSeries ltf = bars(repeat(new double[]{100, 101, 99, 100.5}, 10), "2026-01-05T10:00:00Z");
        List<Trade> trades = Backtester.run(ltf, List.of(sig(ltf.time(1))), 0.0, 3, null);
        assertEquals(Outcome.TIMEOUT, trades.get(0).outcome());
        assertEquals(ltf.time(4), trades.get(0).exitTime());
        assertEquals(100.5, trades.get(0).exit());
    }

    @Test
    void intradayFlatAtEndOfDayAndEntryWindow() {
        IntradayRules rules = IntradayRules.defaults();
        CandleSeries ltf = bars(repeat(new double[]{100, 101, 99, 100}, 30), "2026-01-05T18:45:00Z");
        List<Trade> trades = Backtester.run(ltf, List.of(sig(ltf.time(2))), 0.0, 288, rules);
        assertEquals(Outcome.EOD, trades.get(0).outcome());
        assertTrue(!trades.get(0).exitTime().plus(Duration.ofMinutes(5)).isBefore(Instant.parse("2026-01-05T20:45:00Z")));
        assertEquals(Instant.parse("2026-01-05T20:40:00Z"), trades.get(0).exitTime());

        CandleSeries ltf2 = bars(repeat(new double[]{100, 101, 99, 100}, 5), "2026-01-05T19:20:00Z");
        assertTrue(Backtester.run(ltf2, List.of(sig(ltf2.time(2))), 0.0, 288, rules).isEmpty());
    }

    @Test
    void intradayDailyLossLimit() {
        IntradayRules rules = IntradayRules.defaults().withMaxLossDayR(1.0);
        double[][] rows = new double[12][];
        for (int i = 0; i < 12; i++) {
            rows[i] = i % 2 == 0 ? new double[]{100, 101, 99, 100} : new double[]{100, 100.5, 94, 95};
        }
        CandleSeries ltf = bars(rows, "2026-01-05T10:00:00Z");
        List<Trade> trades = Backtester.run(ltf, List.of(sig(ltf.time(1)), sig(ltf.time(5))), 0.0, 288, rules);
        assertEquals(1, trades.size());
        assertEquals(Outcome.SL, trades.get(0).outcome());
    }

    @Test
    void intradayMaxTradesPerDay() {
        IntradayRules rules = IntradayRules.defaults().withMaxTradesDay(1);
        CandleSeries ltf = bars(new double[][]{{100, 101, 99, 100}, {100, 111, 99, 110}, {110, 111, 109, 110},
                {100, 101, 99, 100}, {100, 111, 99, 110}}, "2026-01-05T10:00:00Z");
        List<Trade> trades = Backtester.run(ltf, List.of(sig(ltf.time(1)), sig(ltf.time(4))), 0.0, 288, rules);
        assertEquals(1, trades.size());
    }

    @Test
    void oneTradeAtATimeAndGapBeyondStopCancels() {
        CandleSeries ltf = bars(new double[][]{{100, 101, 99, 100}, {100, 101, 99, 100}, {100, 101, 99, 100},
                {94, 95, 93, 94}, {100, 111, 99, 110}}, "2026-01-05T10:00:00Z");
        // 2e signal pendant le 1er trade : ignoré ; 3e signal : ouverture 94 sous le stop 95 -> annulé
        List<Trade> trades = Backtester.run(ltf,
                List.of(sig(ltf.time(1)), sig(ltf.time(2)), sig(ltf.time(3))), 0.0, 288, null);
        assertEquals(1, trades.size());
        assertEquals(ltf.time(3), trades.get(0).exitTime());
    }

    @Test
    void breakdownBySessionDirectionOutcome() {
        CandleSeries ltf = bars(new double[][]{{100, 101, 99, 100}, {100, 111, 94, 100}, {100, 111, 99, 110},
                {110, 111, 109, 110}}, "2026-01-05T10:00:00Z");
        List<Trade> trades = Backtester.run(ltf, List.of(sig(ltf.time(1)), sig(ltf.time(2))), 0.0, 288, null);
        assertEquals(2, trades.size());
        List<Breakdown.Row> byOutcome = Breakdown.byOutcome(trades);
        assertEquals("sl", byOutcome.get(0).key());
        assertEquals(0.0, byOutcome.get(0).winRatePct());
        assertEquals("tp", byOutcome.get(1).key());
        assertEquals(2.0, byOutcome.get(1).totalR());
        List<Breakdown.Row> bySession = Breakdown.bySession(trades);
        assertEquals(1, bySession.size());
        assertEquals(50.0, bySession.get(0).winRatePct());
        assertEquals(0.5, bySession.get(0).expectancyR());
    }

    @Test
    void emptySummary() {
        assertEquals(java.util.Map.of("trades", 0), BacktestSummary.of(List.of()).toMap());
    }
}
