package com.srliq.app.webhook;

import com.srliq.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profil de test : srliq.webhook.token vide. */
class WebhookDisabledTest extends IntegrationTest {

    @Test
    void returns503WhenTokenIsNotConfigured() throws Exception {
        mvc.perform(post("/api/webhooks/tradingview/anything-at-all-1234").contentType(MediaType.APPLICATION_JSON)
                        .content(WebhookTest.VALID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/webhooks/tradingview/").contentType(MediaType.APPLICATION_JSON)
                        .content(WebhookTest.VALID))
                .andExpect(status().isNotFound());
        assertThat(count("signal")).isZero();
    }
}
