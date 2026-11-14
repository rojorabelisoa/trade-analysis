package com.srliq.app.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srliq.app.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Notifications Telegram contre un faux serveur HTTP local. */
@ExtendWith(OutputCaptureExtension.class)
class TelegramNotificationTest extends IntegrationTest {

    static final String TOKEN = "123456789:FAKE-test-token_DoNotLeak";
    static final FakeTelegramServer TELEGRAM = new FakeTelegramServer();

    /** Premier signal de la fixture s1 : 2026-01-17 08:50 (achat). */
    static final Instant FIRST_SIGNAL = Instant.parse("2026-01-17T08:50:00Z");

    @DynamicPropertySource
    static void telegram(DynamicPropertyRegistry r) {
        r.add("srliq.telegram.bot-token", () -> TOKEN);
        r.add("srliq.telegram.chat-id", () -> "-1001234");
        r.add("srliq.telegram.api-base-url", TELEGRAM::baseUrl);
    }

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void resetServer() {
        TELEGRAM.reset();
    }

    byte[] demo() {
        return bytes(parity("candles/demo_5m.csv"));
    }

    JsonNode notifications() throws Exception {
        String body = mvc.perform(get("/api/notifications").param("size", "500"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(TOKEN).doesNotContain("FAKE-test-token");
        return json.readTree(body);
    }

    @Test
    void recentSignalIsSent(CapturedOutput output) throws Exception {
        // Il est 08:55 : seules les bougies clôturées sont stockées, le signal de 08:50 a 5 minutes.
        clock.set(FIRST_SIGNAL.plusSeconds(300));
        mvc.perform(post("/api/news").contentType(MediaType.APPLICATION_JSON)
                .content("{\"time\":\"2026-01-17T13:30:00Z\",\"currency\":\"USD\",\"impact\":\"high\","
                        + "\"event\":\"Emplois US\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/news").contentType(MediaType.APPLICATION_JSON)
                .content("{\"time\":\"2026-01-17T14:00:00Z\",\"currency\":\"USD\",\"impact\":\"low\","
                        + "\"event\":\"Mineure\"}")).andExpect(status().isCreated());
        importCsv("GC", demo()).andExpect(status().isOk());

        assertThat(TELEGRAM.requests()).hasSize(1);
        FakeTelegramServer.Request req = TELEGRAM.requests().get(0);
        assertThat(req.path()).isEqualTo("/bot" + TOKEN + "/sendMessage");
        JsonNode body = json.readTree(req.body());
        assertThat(body.get("chat_id").asText()).isEqualTo("-1001234");
        String text = body.get("text").asText();
        assertThat(text)
                .contains("SIGNAL GC - ACHAT (long)")
                .contains("Or (COMEX Gold)")
                .contains("moteur SR-Liq, timeframe 5m")
                .contains("2026-01-17 08:50 UTC (session londres)")
                .contains("Entrée réf. :").contains("Stop loss").contains("Objectif").contains("R:R 1:")
                .contains("Zone S/R :")
                .contains("Tendance H4 : haussière")
                .contains("Biais fondamental : neutral")
                .contains("2026-01-17 13:30 UTC USD Emplois US")
                .doesNotContain("Mineure")
                .contains("Coupe-circuit : inactif.")
                .contains("pas un conseil financier");

        JsonNode n = notifications();
        assertThat(n.get("total").asInt()).isEqualTo(1);
        assertThat(n.get("items").get(0).get("kind").asText()).isEqualTo("SIGNAL");
        assertThat(n.get("items").get(0).get("status").asText()).isEqualTo("SENT");
        assertThat(n.get("items").get(0).get("attempts").asInt()).isEqualTo(1);

        // Rejeu et recalcul : aucun nouvel envoi (une ligne par signal et par canal).
        mvc.perform(post("/api/signals/recompute").param("symbol", "GC")).andExpect(status().isOk());
        mvc.perform(post("/api/paper/replay").param("symbol", "GC")).andExpect(status().isOk());
        assertThat(TELEGRAM.requests()).hasSize(1);
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("FAKE-test-token");
    }

    @Test
    void historyImportSendsNothing(CapturedOutput output) throws Exception {
        importCsv("GC", demo()).andExpect(status().isOk()); // il est 2026-11-14 : tout est ancien
        assertThat(TELEGRAM.requests()).isEmpty();
        JsonNode n = notifications();
        assertThat(n.get("total").asInt()).isEqualTo(33 + 12); // 33 signaux + 12 trades clôturés
        n.get("items").forEach(item -> {
            assertThat(item.get("status").asText()).isEqualTo("SKIPPED");
            assertThat(item.get("reason").asText()).contains("trop ancien");
        });
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void failureIsRecordedThenRetried(CapturedOutput output) throws Exception {
        TELEGRAM.mode(FakeTelegramServer.Mode.ERROR_500);
        clock.set(FIRST_SIGNAL.plusSeconds(300));
        importCsv("GC", demo()).andExpect(status().isOk());
        // L'échec n'empêche ni l'enregistrement du signal ni le rejeu.
        assertThat(count("signal")).isEqualTo(1);
        assertThat(count("paper_trade")).isEqualTo(1);

        JsonNode item = notifications().get("items").get(0);
        assertThat(item.get("status").asText()).isEqualTo("FAILED");
        assertThat(item.get("reason").asText()).contains("500");
        long id = item.get("id").asLong();
        assertThat(TELEGRAM.requests()).hasSize(1); // pas de réessai automatique

        // Connexion coupée : le message d'erreur (qui contient l'URL) est nettoyé.
        TELEGRAM.mode(FakeTelegramServer.Mode.DROP);
        String body = mvc.perform(post("/api/notifications/" + id + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(TOKEN).doesNotContain("FAKE-test-token");

        TELEGRAM.mode(FakeTelegramServer.Mode.OK);
        mvc.perform(post("/api/notifications/" + id + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.attempts").value(3));
        assertThat(TELEGRAM.requests()).hasSize(3);
        mvc.perform(post("/api/notifications/" + id + "/retry")).andExpect(status().isConflict());
        mvc.perform(post("/api/notifications/999999/retry")).andExpect(status().isNotFound());
        notifications();
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("FAKE-test-token");
    }

    @Test
    void recentTradeClosureAndKillSwitchAreSent() throws Exception {
        // Il est 2026-01-30 20:50 : le trade de 07:55 vient de sortir en fin de journée (bougie 20:40).
        clock.set(Instant.parse("2026-01-30T20:50:00Z"));
        importCsv("GC", demo()).andExpect(status().isOk());
        assertThat(TELEGRAM.requests()).extracting(r -> json.readTree(r.body()).get("text").asText())
                .anySatisfy(t -> assertThat(t).contains("TRADE SIMULÉ CLÔTURÉ - GC 5m - VENTE (short)")
                        .contains("clôture de fin de journée").contains("+0.83 R")
                        .contains("aucun ordre réel"));

        TELEGRAM.reset();
        mvc.perform(post("/api/trading/halt").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Volatilité\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/trading/resume")).andExpect(status().isOk());
        assertThat(TELEGRAM.requests()).hasSize(2);
        assertThat(json.readTree(TELEGRAM.requests().get(0).body()).get("text").asText())
                .contains("COUPE-CIRCUIT ACTIVÉ").contains("Volatilité");
        assertThat(json.readTree(TELEGRAM.requests().get(1).body()).get("text").asText())
                .contains("COUPE-CIRCUIT DÉSACTIVÉ");
    }
}
