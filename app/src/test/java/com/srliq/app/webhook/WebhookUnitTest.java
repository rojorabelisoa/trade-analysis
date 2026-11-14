package com.srliq.app.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookUnitTest {

    private static WebhookTokenVerifier verifier(String token) {
        WebhookProperties p = new WebhookProperties();
        p.setToken(token);
        return new WebhookTokenVerifier(p);
    }

    @Test
    void tokenVerification() {
        assertThat(verifier(null).isConfigured()).isFalse();
        assertThat(verifier("  ").isConfigured()).isFalse();
        assertThat(verifier("short-token").isConfigured()).as("< 16 caractères").isFalse();
        assertThat(verifier("short-token").matches("short-token")).isFalse();

        WebhookTokenVerifier v = verifier("0123456789abcdef-secret");
        assertThat(v.isConfigured()).isTrue();
        assertThat(v.matches("0123456789abcdef-secret")).isTrue();
        assertThat(v.matches("0123456789abcdef-secreT")).isFalse();
        assertThat(v.matches("")).isFalse();
        assertThat(v.matches(null)).isFalse();
    }

    @Test
    void propertiesNeverPrintTheToken() {
        WebhookProperties p = new WebhookProperties();
        p.setToken("0123456789abcdef-secret");
        assertThat(p.toString()).doesNotContain("secret").contains("masqué");
    }

    @Test
    void entryTimeframeFromPinePeriod() {
        assertThat(TradingViewAlertValidator.entryTimeframe("5/60/240")).contains(Duration.ofMinutes(5));
        assertThat(TradingViewAlertValidator.entryTimeframe("15")).contains(Duration.ofMinutes(15));
        assertThat(TradingViewAlertValidator.entryTimeframe("30S/60/240")).contains(Duration.ofSeconds(30));
        assertThat(TradingViewAlertValidator.entryTimeframe("1D/1W/1M")).contains(Duration.ofDays(1));
        assertThat(TradingViewAlertValidator.entryTimeframe("D")).contains(Duration.ofDays(1));
        assertThat(TradingViewAlertValidator.entryTimeframe("1W")).isEqualTo(Optional.empty());
        assertThat(TradingViewAlertValidator.entryTimeframe("abc")).isEmpty();
        assertThat(TradingViewAlertValidator.entryTimeframe("")).isEmpty();
        assertThat(TradingViewAlertValidator.entryTimeframe("0/60")).isEmpty();
        assertThat(TradingViewAlertValidator.entryTimeframe("99999999999999999999")).isEmpty();
    }

    @Test
    void barCloseIsTheLastMultipleOfTheTimeframe() {
        Duration m5 = Duration.ofMinutes(5);
        assertThat(TradingViewWebhookController.barClose(Instant.parse("2026-01-17T08:50:00Z"), m5))
                .isEqualTo(Instant.parse("2026-01-17T08:50:00Z"));
        assertThat(TradingViewWebhookController.barClose(Instant.parse("2026-01-17T08:54:59.999Z"), m5))
                .isEqualTo(Instant.parse("2026-01-17T08:50:00Z"));
        assertThat(TradingViewWebhookController.barClose(Instant.parse("2026-01-17T08:52:10Z"),
                Duration.ofMinutes(15))).isEqualTo(Instant.parse("2026-01-17T08:45:00Z"));
    }
}
