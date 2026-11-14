package com.srliq.app.trading;

import com.srliq.app.paper.PaperTrade;
import com.srliq.app.paper.PaperTradeRepository;
import com.srliq.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Coupe-circuit : aucune entrée pendant un arrêt, les trades ouverts continuent ; reprise ; statut. */
class KillSwitchTest extends IntegrationTest {

    @Autowired
    PaperTradeRepository trades;

    List<String> signalTimes() {
        return trades.findAll(new PaperTradeRepository.Filter("GC", null, null, null, null)).stream()
                .map(t -> t.signalTime().toString()).toList();
    }

    void halt(String reason) throws Exception {
        mvc.perform(post("/api/trading/halt").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.halted").value(true))
                .andExpect(jsonPath("$.reason").value(reason));
    }

    @Test
    void statusHaltAndResume() throws Exception {
        mvc.perform(get("/api/trading/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.halted").value(false))
                .andExpect(jsonPath("$.paperTrading").value(true))
                .andExpect(jsonPath("$.liveTradingEnabled").value(false))
                .andExpect(jsonPath("$.broker").value("paper"))
                .andExpect(jsonPath("$.history").isEmpty());
        mvc.perform(post("/api/trading/resume")).andExpect(status().isConflict());
        mvc.perform(post("/api/trading/halt").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());

        halt("Perte journalière");
        mvc.perform(get("/api/trading/status"))
                .andExpect(jsonPath("$.halted").value(true))
                .andExpect(jsonPath("$.since").value(NOW.toString()))
                .andExpect(jsonPath("$.reason").value("Perte journalière"));
        mvc.perform(post("/api/trading/halt").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict());

        clock.set(NOW.plusSeconds(3600));
        mvc.perform(post("/api/trading/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.halted").value(false))
                .andExpect(jsonPath("$.history[0].startedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.history[0].endedAt").value(NOW.plusSeconds(3600).toString()));
        // Activation / désactivation : une notification chacune (non envoyée, Telegram non configuré).
        assertThat(jdbc.sql("SELECT string_agg(kind || ':' || status, ',' ORDER BY id) FROM notification")
                .query(String.class).single()).isEqualTo("HALT:SKIPPED,RESUME:SKIPPED");
    }

    @Test
    void signalsDuringHaltOpenNoTrade() throws Exception {
        // Arrêt du 2026-01-20 00:00 au 2026-01-24 00:00 : les trades de référence des 20 (08:55) et
        // 23 (14:40) disparaissent.
        clock.set(Instant.parse("2026-01-20T00:00:00Z"));
        halt("Test");
        clock.set(Instant.parse("2026-01-24T00:00:00Z"));
        mvc.perform(post("/api/trading/resume")).andExpect(status().isOk());
        clock.set(NOW);

        importCsv("GC", bytes(parity("candles/demo_5m.csv"))).andExpect(status().isOk());
        assertThat(signalTimes()).doesNotContain("2026-01-20T08:55:00Z", "2026-01-23T14:40:00Z")
                .contains("2026-01-18T09:55:00Z", "2026-01-25T09:35:00Z")
                .allMatch(t -> t.compareTo("2026-01-20") < 0 || t.compareTo("2026-01-24") >= 0);
        assertThat(signalTimes()).hasSize(10);
        mvc.perform(post("/api/paper/replay").param("symbol", "GC"))
                .andExpect(jsonPath("$.timeframes[0].excludedByHalt").value(
                        jdbc.sql("SELECT count(*) FROM signal WHERE signal_time >= '2026-01-20' "
                                + "AND signal_time < '2026-01-24'").query(Integer.class).single()))
                .andExpect(jsonPath("$.timeframes[0].unchanged").value(10));
    }

    @Test
    void openTradeKeepsBeingManagedDuringHalt() throws Exception {
        importCsv("GC", bytes(parity("candles/demo_5m.csv"))).andExpect(status().isOk());
        PaperTrade before = trades.findAll(new PaperTradeRepository.Filter("GC", null, null, null, null)).stream()
                .filter(t -> t.signalTime().equals(Instant.parse("2026-01-20T08:55:00Z"))).findFirst().orElseThrow();

        // Arrêt ouvert pendant ce trade (08:55 -> 14:55), jamais levé : il est géré jusqu'à sa sortie,
        // mais plus aucune entrée n'a lieu ensuite.
        clock.set(Instant.parse("2026-01-20T10:00:00Z"));
        halt("Arrêt manuel");
        clock.set(NOW);
        mvc.perform(post("/api/paper/replay").param("symbol", "GC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeframes[0].deleted").value(9));
        List<PaperTrade> after = trades.findAll(new PaperTradeRepository.Filter("GC", null, null, null, null));
        assertThat(after).hasSize(3);
        PaperTrade last = after.get(2);
        assertThat(last.id()).isEqualTo(before.id());
        assertThat(last.exitTime()).isEqualTo(before.exitTime());
        assertThat(last.r()).isEqualTo(before.r());
        assertThat(last.outcome()).isEqualTo(before.outcome());
    }
}
