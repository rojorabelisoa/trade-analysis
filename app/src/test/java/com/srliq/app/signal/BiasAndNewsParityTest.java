package com.srliq.app.signal;

import com.srliq.app.support.IntegrationTest;
import com.srliq.engine.data.CsvTable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scénario s4 de la référence (tendance EMA, longs seuls, sessions filtrées, biais et annonces) :
 * configuration par {@code srliq.strategy.*}, biais et annonces saisis par l'API.
 */
@TestPropertySource(properties = {
        "srliq.strategy.trend-mode=ema",
        "srliq.strategy.trend-len=20",
        "srliq.strategy.direction=long",
        "srliq.strategy.neutral-allows-both=false",
        "srliq.strategy.sessions=londres,overlap_londres_ny,new_york",
        "srliq.news.before-minutes=30",
        "srliq.news.after-minutes=30"})
class BiasAndNewsParityTest extends IntegrationTest {

    @Autowired
    SignalRepository signals;

    @Test
    void signalsMatchPythonReferenceWithBiasAndNews() throws Exception {
        CsvTable bias = CsvTable.read(parity("inputs/bias.csv"));
        for (int r = 0; r < bias.size(); r++) {
            create("/api/bias", "{\"date\":\"%s\",\"symbol\":\"%s\",\"bias\":\"%s\",\"note\":\"%s\"}".formatted(
                    bias.get(r, "date"), bias.get(r, "symbol"), bias.get(r, "bias"), bias.get(r, "note")));
        }
        CsvTable news = CsvTable.read(parity("inputs/news.csv"));
        for (int r = 0; r < news.size(); r++) {
            String time = news.get(r, "time_utc").replace(' ', 'T') + ":00Z";
            create("/api/news", "{\"time\":\"%s\",\"currency\":\"%s\",\"impact\":\"%s\",\"event\":\"%s\"}".formatted(
                    time, news.get(r, "currency"), news.get(r, "impact"), news.get(r, "event")));
        }

        importCsv("GC", bytes(parity("candles/demo_5m.csv"))).andExpect(status().isOk());

        ParityAssertions.assertSameSignals(ParityAssertions.fixture("s4_ema_long_biais_annonces.json").get("signals"),
                ParityAssertions.engineSignals(signals, "GC"));
    }

    private void create(String url, String json) throws Exception {
        mvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }
}
