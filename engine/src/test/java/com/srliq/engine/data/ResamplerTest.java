package com.srliq.engine.data;

import com.srliq.engine.model.CandleSeries;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResamplerTest {

    @Test
    void bucketsAlignedOnMidnightLabelledByOpenAndEmptyBucketsDropped() {
        // 23:30, 23:45, 00:15 puis un trou jusqu'à 05:00 (seaux 4h vides supprimés)
        long d = Instant.parse("2026-01-05T00:00:00Z").getEpochSecond();
        String csv = "time,open,high,low,close,volume\n"
                + (d - 1800) + ",1,5,0.5,2,1\n"
                + (d - 900) + ",2,3,1,2.5,2\n"
                + (d + 900) + ",2.5,4,2,3,3\n"
                + (d + 5 * 3600) + ",3,3.5,2.5,3.2,4\n";
        CandleSeries s = TradingViewCsv.parse(csv);
        CandleSeries h4 = Resampler.resample(s, "4h");
        assertEquals(3, h4.size());
        assertEquals(Instant.parse("2026-01-04T20:00:00Z"), h4.time(0));
        assertEquals(1.0, h4.open(0));
        assertEquals(5.0, h4.high(0));
        assertEquals(0.5, h4.low(0));
        assertEquals(2.5, h4.close(0));
        assertEquals(3.0, h4.volume(0));
        assertEquals(Instant.parse("2026-01-05T00:00:00Z"), h4.time(1));
        assertEquals(Instant.parse("2026-01-05T04:00:00Z"), h4.time(2));
    }
}
