package com.srliq.app.notification;

import com.srliq.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profil de test : pas de jeton Telegram -> notifications désactivées, sans erreur. */
class TelegramDisabledTest extends IntegrationTest {

    @Autowired
    TelegramClient client;

    @Test
    void disabledWithoutToken() throws Exception {
        assertThat(client.enabled()).isFalse();
        clock.set(Instant.parse("2026-01-17T08:55:00Z"));
        importCsv("GC", bytes(parity("candles/demo_5m.csv"))).andExpect(status().isOk());
        assertThat(count("signal")).isEqualTo(1);
        mvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.items[0].reason").value("Telegram non configuré"));
        mvc.perform(post("/api/notifications/1/retry")).andExpect(status().isConflict());
    }

    @Test
    void redactRemovesToken() {
        assertThat(TelegramClient.redact("I/O error on POST request for \"https://api.telegram.org/bot12:abc/sendMessage\"",
                "12:abc")).doesNotContain("12:abc").contains("/bot***/");
        assertThat(TelegramClient.redact("x https://h/botSOMETHING/sendMessage", "other"))
                .doesNotContain("SOMETHING");
    }
}
