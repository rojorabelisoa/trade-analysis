package com.srliq.app.backtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srliq.app.support.Fixtures;
import com.srliq.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /api/backtests} : mêmes chiffres que la fixture Python s1, rien en base. */
class BacktestApiTest extends IntegrationTest {

    @Autowired
    ObjectMapper json;

    MockMultipartHttpServletRequestBuilder request() {
        return multipart("/api/backtests")
                .file(new MockMultipartFile("file", "demo.csv", "text/csv", bytes(parity("candles/demo_5m.csv"))));
    }

    void assertMatchesFixture(JsonNode r, JsonNode bt) {
        JsonNode exp = bt.get("trades");
        JsonNode got = r.get("trades");
        assertThat(got).hasSize(exp.size());
        for (int k = 0; k < exp.size(); k++) {
            JsonNode e = exp.get(k);
            JsonNode a = got.get(k);
            assertThat(a.get("signalTime").asText()).isEqualTo(e.get("signal_time").asText());
            assertThat(a.get("entryTime").asText()).isEqualTo(e.get("entry_time").asText());
            assertThat(a.get("exitTime").asText()).isEqualTo(e.get("exit_time").asText());
            assertThat(a.get("outcome").asText()).isEqualTo(e.get("outcome").asText());
            assertThat(a.get("entry").asDouble()).isCloseTo(e.get("entry").asDouble(), within(1e-6));
            assertThat(a.get("exit").asDouble()).isCloseTo(e.get("exit").asDouble(), within(1e-6));
            assertThat(a.get("r").asDouble()).isCloseTo(e.get("r").asDouble(), within(1e-6));
            assertThat(a.get("openAtEnd").asBoolean()).isFalse();
        }
        JsonNode s = bt.get("summary");
        JsonNode sum = r.get("summary");
        assertThat(sum.get("trades").asInt()).isEqualTo(s.get("trades").asInt());
        assertThat(sum.get("winRatePct").asDouble()).isEqualTo(s.get("taux_reussite_%").asDouble());
        assertThat(sum.get("expectancyR").asDouble()).isEqualTo(s.get("esperance_R").asDouble());
        assertThat(sum.get("totalR").asDouble()).isEqualTo(s.get("total_R").asDouble());
        assertThat(sum.get("profitFactor").asDouble()).isEqualTo(s.get("profit_factor").asDouble());
        assertThat(sum.get("maxDrawdownR").asDouble()).isEqualTo(s.get("drawdown_max_R").asDouble());
        assertThat(sum.get("worstLosingStreak").asInt()).isEqualTo(s.get("pire_serie_pertes").asInt());
        assertThat(sum.get("exitsTpSlEod").asText()).isEqualTo(s.get("sorties_tp_sl_fin").asText());
        assertThat(sum.get("avgDurationMin").asDouble()).isEqualTo(s.get("duree_moyenne_min").asDouble());
        assertThat(r.get("equityCurve")).hasSize(exp.size());
    }

    @Test
    void intradayBacktestMatchesFixtureAndWritesNothing() throws Exception {
        String body = mvc.perform(request().param("symbol", "GC").param("cost", "0.3").param("intraday", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("GC"))
                .andExpect(jsonPath("$.timeframe").value("5m"))
                .andExpect(jsonPath("$.candles").value(8000))
                .andExpect(jsonPath("$.parameters.intraday").value(true))
                .andExpect(jsonPath("$.parameters.maxHoldBars").value(288))
                .andExpect(jsonPath("$.parameters.strategy.trendMode").value("structure"))
                .andExpect(jsonPath("$.signals.length()").value(33))
                .andExpect(jsonPath("$.funnel").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertMatchesFixture(json.readTree(body), Fixtures.backtest("s1_defaut_h4_h1_5m.json", true));
        assertThat(count("candle") + count("signal") + count("paper_trade") + count("notification")).isZero();
    }

    @Test
    void nonIntradayBacktestMatchesFixture() throws Exception {
        String body = mvc.perform(request().param("symbol", "GC").param("intraday", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameters.cost").value(0.3))
                .andExpect(jsonPath("$.parameters.intradayRules").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode r = json.readTree(body);
        assertMatchesFixture(r, Fixtures.backtest("s1_defaut_h4_h1_5m.json", false));
        assertThat(r.get("byOutcome").toString()).contains("\"key\":\"timeout\"");
    }

    @Test
    void strategyOverridesAreApplied() throws Exception {
        // Scénario s2 de la référence : tendance off, objectif à R:R fixe.
        JsonNode s2 = Fixtures.fixture("s2_sans_tendance_rr_fixe.json");
        JsonNode cfg = s2.get("config");
        assertThat(cfg.get("trend_mode").asText()).isEqualTo("off");
        String overrides = "{\"trendMode\":\"" + cfg.get("trend_mode").asText() + "\",\"targetMode\":\""
                + cfg.get("target_mode").asText() + "\",\"rr\":" + cfg.get("rr").asDouble() + "}";
        mvc.perform(request().param("symbol", "GC").param("strategy", overrides))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameters.strategy.trendMode").value("off"))
                .andExpect(jsonPath("$.signals.length()").value(s2.get("signals").size()));
        mvc.perform(request().param("symbol", "GC").param("strategy", "{\"unknownKey\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(request().param("symbol", "GC").param("strategy", "{\"trendMode\":\"sideways\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(request().param("symbol", "GC").param("cost", "-1")).andExpect(status().isBadRequest());
        mvc.perform(request().param("symbol", "XX")).andExpect(status().isBadRequest());
    }
}
