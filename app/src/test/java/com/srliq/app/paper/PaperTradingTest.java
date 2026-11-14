package com.srliq.app.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srliq.app.support.Csv;
import com.srliq.app.support.Fixtures;
import com.srliq.app.support.IntegrationTest;
import com.srliq.engine.model.Direction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paper trading = rejeu du backtest du moteur. Parité avec la fixture Python s1 (variante
 * {@code intraday: true}, coût 0.3, 288 bougies) : ce sont les valeurs par défaut de l'application.
 */
class PaperTradingTest extends IntegrationTest {

    static final String FIXTURE = "s1_defaut_h4_h1_5m.json";
    static final double TOL = 1e-6;

    @Autowired
    PaperTradeRepository trades;

    @Autowired
    ObjectMapper json;

    static byte[] demo() {
        return bytes(parity("candles/demo_5m.csv"));
    }

    List<PaperTrade> all(String symbol) {
        return trades.findAll(new PaperTradeRepository.Filter(symbol, null, null, null, null));
    }

    static void assertSameTrades(JsonNode expected, List<PaperTrade> actual) {
        List<JsonNode> exp = new ArrayList<>();
        expected.forEach(exp::add);
        assertThat(actual).as("nombre de trades").hasSize(exp.size());
        for (int k = 0; k < exp.size(); k++) {
            JsonNode e = exp.get(k);
            PaperTrade a = actual.get(k);
            String where = "trade #" + k + " " + e.get("signal_time").asText();
            assertThat(a.status()).as(where + " statut").isEqualTo(PaperTradeStatus.CLOSED);
            assertThat(a.signalTime().toString()).as(where + " signal").isEqualTo(e.get("signal_time").asText());
            assertThat(a.entryTime().toString()).as(where + " entrée (heure)").isEqualTo(e.get("entry_time").asText());
            assertThat(a.exitTime().toString()).as(where + " sortie (heure)").isEqualTo(e.get("exit_time").asText());
            assertThat(a.outcome().pyName()).as(where + " type de sortie").isEqualTo(e.get("outcome").asText());
            assertThat(a.entry()).as(where + " entrée").isCloseTo(e.get("entry").asDouble(), within(TOL));
            assertThat(a.exitPrice()).as(where + " sortie").isCloseTo(e.get("exit").asDouble(), within(TOL));
            assertThat(a.r()).as(where + " R").isCloseTo(e.get("r").asDouble(), within(TOL));
            assertThat(a.cost()).isEqualTo(0.3);
        }
    }

    @Test
    void closedTradesMatchPythonIntradayBacktest() throws Exception {
        importCsv("GC", demo()).andExpect(status().isOk());

        JsonNode bt = Fixtures.backtest(FIXTURE, true);
        assertThat(bt.get("cost").asDouble()).isEqualTo(0.3);
        assertThat(bt.get("max_hold_bars").asInt()).isEqualTo(288);
        // La fixture ne contient pas de trade resté ouvert en fin de données : son dernier trade
        // (2026-01-31 14:35) sort au stop à 15:25 ; ensuite la limite de -2 R du jour bloque 16:45
        // et aucun autre signal n'est pris jusqu'à la fin des bougies (2026-02-01 18:35).
        List<PaperTrade> stored = all("GC");
        assertThat(stored).allMatch(t -> t.status() == PaperTradeStatus.CLOSED);
        assertSameTrades(bt.get("trades"), stored);

        // Résumé de l'API = résumé de la référence.
        JsonNode s = bt.get("summary");
        String body = mvc.perform(get("/api/paper/summary").param("symbol", "GC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedTrades").value(12))
                .andExpect(jsonPath("$.openTrades").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode sum = json.readTree(body).get("summary");
        assertThat(sum.get("trades").asInt()).isEqualTo(s.get("trades").asInt());
        assertThat(sum.get("winRatePct").asDouble()).isEqualTo(s.get("taux_reussite_%").asDouble());
        assertThat(sum.get("expectancyR").asDouble()).isEqualTo(s.get("esperance_R").asDouble());
        assertThat(sum.get("totalR").asDouble()).isEqualTo(s.get("total_R").asDouble());
        assertThat(sum.get("profitFactor").asDouble()).isEqualTo(s.get("profit_factor").asDouble());
        assertThat(sum.get("maxDrawdownR").asDouble()).isEqualTo(s.get("drawdown_max_R").asDouble());
        assertThat(sum.get("worstLosingStreak").asInt()).isEqualTo(s.get("pire_serie_pertes").asInt());
        assertThat(sum.get("exitsTpSlEod").asText()).isEqualTo(s.get("sorties_tp_sl_fin").asText());
        assertThat(sum.get("avgDurationMin").asDouble()).isEqualTo(s.get("duree_moyenne_min").asDouble());
        JsonNode curve = json.readTree(body).get("equityCurve");
        assertThat(curve).hasSize(12);
        assertThat(curve.get(11).get("r").asDouble()).isCloseTo(-10.87, within(0.005));
        assertThat(json.readTree(body).get("byOutcome").get(0).get("key").asText()).isEqualTo("eod");
    }

    @Test
    void replayIsIdempotent() throws Exception {
        importCsv("GC", demo()).andExpect(status().isOk());
        List<PaperTrade> before = all("GC");

        mvc.perform(post("/api/paper/replay").param("symbol", "GC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeframes[0].timeframe").value("5m"))
                .andExpect(jsonPath("$.timeframes[0].signals").value(33))
                .andExpect(jsonPath("$.timeframes[0].trades").value(12))
                .andExpect(jsonPath("$.timeframes[0].inserted").value(0))
                .andExpect(jsonPath("$.timeframes[0].updated").value(0))
                .andExpect(jsonPath("$.timeframes[0].deleted").value(0))
                .andExpect(jsonPath("$.timeframes[0].unchanged").value(12));
        // Réimporter le même fichier ne change rien non plus.
        importCsv("GC", demo()).andExpect(status().isOk());
        assertThat(all("GC")).isEqualTo(before);
        assertThat(count("paper_trade")).isEqualTo(12);
    }

    @Test
    void openTradeIsClosedWhenNextCandlesArrive() throws Exception {
        // Trade de 2026-01-30 07:55 (vente) : sort en fin de journée à 20:40. Coupé à 12:00, il est OPEN.
        Instant cut = Instant.parse("2026-01-30T12:00:00Z");
        importCsv("GC", Csv.before(demo(), cut)).andExpect(status().isOk());

        List<PaperTrade> first = all("GC");
        PaperTrade open = first.get(first.size() - 1);
        assertThat(open.status()).isEqualTo(PaperTradeStatus.OPEN);
        assertThat(open.signalTime()).isEqualTo(Instant.parse("2026-01-30T07:55:00Z"));
        assertThat(open.direction()).isEqualTo(Direction.SHORT);
        assertThat(open.exitTime()).isNull();
        assertThat(open.exitPrice()).isNull();
        assertThat(open.outcome()).isNull();
        Instant lastBar = Instant.parse("2026-01-30T11:55:00Z");
        double lastClose = Csv.close(demo(), lastBar);
        assertThat(open.lastTime()).isEqualTo(lastBar);
        assertThat(open.lastPrice()).isEqualTo(lastClose);
        assertThat(open.r()).as("R latent, coût déduit")
                .isCloseTo((open.entry() - lastClose - 0.3) / (open.stop() - open.entry()), within(1e-9));
        // Les trades terminés avant la coupure sont ceux de la référence.
        JsonNode exp = Fixtures.backtest(FIXTURE, true).get("trades");
        List<JsonNode> expBefore = new ArrayList<>();
        exp.forEach(n -> {
            if (Instant.parse(n.get("signal_time").asText()).isBefore(open.signalTime())) {
                expBefore.add(n);
            }
        });
        assertSameTrades(new ObjectMapper().valueToTree(expBefore), first.subList(0, first.size() - 1));
        mvc.perform(get("/api/paper/trades").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].direction").value("short"));

        // Les bougies suivantes arrivent : le même trade (même ligne) est terminé.
        importCsv("GC", demo()).andExpect(status().isOk());
        List<PaperTrade> after = all("GC");
        assertSameTrades(exp, after);
        PaperTrade closed = trades.findById(open.id()).orElseThrow();
        assertThat(closed.status()).isEqualTo(PaperTradeStatus.CLOSED);
        assertThat(closed.outcome().pyName()).isEqualTo("eod");
        assertThat(closed.lastPrice()).isEqualTo(closed.exitPrice());
        // Clôture d'un trade ancien : notification non envoyée (Telegram non configuré ici).
        assertThat(jdbc.sql("SELECT count(*) FROM notification WHERE kind = 'TRADE_CLOSED' AND subject_id = :id")
                .param("id", open.id()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void sizingUsesInstrumentPointValue() throws Exception {
        importCsv("GC", demo()).andExpect(status().isOk());
        importCsv("MGC", demo()).andExpect(status().isOk());
        List<PaperTrade> gc = all("GC");
        List<PaperTrade> mgc = all("MGC");
        assertThat(gc).hasSameSizeAs(mgc);
        for (int k = 0; k < gc.size(); k++) {
            PaperTrade g = gc.get(k);
            PaperTrade m = mgc.get(k);
            assertThat(g.pointValue()).isEqualTo(100.0);
            assertThat(m.pointValue()).isEqualTo(10.0);
            assertThat(g.quantity()).isEqualTo((int) Math.floor(1000.0 / (g.riskPoints() * 100.0) + 1e-9));
            assertThat(m.quantity()).isEqualTo((int) Math.floor(1000.0 / (m.riskPoints() * 10.0) + 1e-9));
            assertThat(g.riskTooLarge()).isEqualTo(g.quantity() == 0);
            assertThat(g.pnlCurrency()).isCloseTo(g.r() * g.riskPoints() * 100.0 * g.quantity(), within(1e-6));
            assertThat(m.pnlCurrency()).isCloseTo(m.r() * m.riskPoints() * 10.0 * m.quantity(), within(1e-6));
            assertThat(m.r()).isEqualTo(g.r());
        }
        // Premier trade : risque 3.73 points -> 2 contrats GC (746 $), 26 contrats MGC (970 $).
        assertThat(gc.get(0).riskPoints()).isCloseTo(3.7332621, within(1e-6));
        assertThat(gc.get(0).quantity()).isEqualTo(2);
        assertThat(mgc.get(0).quantity()).isEqualTo(26);
        mvc.perform(get("/api/paper/trades").param("symbol", "mgc1!").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].symbol").value("MGC"));
        mvc.perform(get("/api/paper/trades").param("from", "2026-01-31T00:00:00Z").param("to", "2026-02-01T00:00:00Z")
                        .param("symbol", "GC"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/paper/trades").param("from", "2026-02-01T00:00:00Z").param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/paper/summary"))
                .andExpect(jsonPath("$.closedTrades").value(24))
                .andExpect(jsonPath("$.symbol").doesNotExist());
    }

    @Test
    void tradingViewSignalsDoNotCreateTrades() throws Exception {
        mvc.perform(post("/api/paper/replay").param("symbol", "GC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeframes").isEmpty());
        mvc.perform(post("/api/paper/replay").param("symbol", "XX")).andExpect(status().isBadRequest());
        assertThat(count("paper_trade")).isZero();
    }
}
