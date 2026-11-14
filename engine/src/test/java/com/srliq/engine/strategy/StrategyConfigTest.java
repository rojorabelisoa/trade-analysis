package com.srliq.engine.strategy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyConfigTest {

    @Test
    void defaultsMatchPythonConfig() {
        StrategyConfig c = StrategyConfig.defaults();
        assertEquals(5, c.htfPivotLeft());
        assertEquals(5, c.htfPivotRight());
        assertEquals(0.35, c.zoneMergeAtr());
        assertEquals(1.0, c.zoneMaxWidthAtr());
        assertEquals(2, c.minTouches());
        assertEquals(300, c.zoneMaxAgeBars());
        assertEquals(0.2, c.sweepMinAtr());
        assertEquals(1.0, c.sweepMaxAtr());
        assertEquals(3, c.ltfPivotLeft());
        assertEquals(2, c.ltfPivotRight());
        assertEquals(12, c.confirmBars());
        assertEquals(0.2, c.slBufferAtr());
        assertEquals(StopMode.SWEEP, c.stopMode());
        assertEquals(TargetMode.OB, c.targetMode());
        assertEquals(20, c.tpLookback());
        assertEquals(1.0, c.minRr());
        assertEquals(2.0, c.rr());
        assertEquals(1.5, c.maxRiskHtfAtr());
        assertTrue(c.neutralAllowsBoth());
        assertEquals(TrendMode.STRUCTURE, c.trendMode());
        assertEquals(50, c.trendLen());
        assertEquals(DirectionFilter.BOTH, c.direction());
        assertNull(c.sessions());
        assertEquals(14, c.atrPeriod());
    }

    @Test
    void enumsAcceptPythonStrings() {
        StrategyConfig c = StrategyConfig.builder().trendMode("ema").stopMode("h4").targetMode("rr")
                .direction("short").sessions(List.of()).build();
        assertEquals(TrendMode.EMA, c.trendMode());
        assertEquals(StopMode.H4, c.stopMode());
        assertEquals(TargetMode.RR, c.targetMode());
        assertEquals(DirectionFilter.SHORT, c.direction());
        assertFalse(c.hasSessionFilter()); // tuple vide = pas de filtre, comme en Python
        assertEquals(TrendMode.STRICT, TrendMode.fromPython(" Strict "));
        assertEquals(TrendMode.OFF, TrendMode.fromPython("off"));
        assertThrows(IllegalArgumentException.class, () -> TrendMode.fromPython("sma"));
        assertEquals("h4", c.toBuilder().build().stopMode().pyName());
    }
}
