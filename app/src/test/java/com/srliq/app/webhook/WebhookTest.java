package com.srliq.app.webhook;

import com.srliq.app.signal.SignalRecordedEvent;
import com.srliq.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
@TestPropertySource(properties = "srliq.webhook.token=" + WebhookTest.TOKEN)
class WebhookTest extends IntegrationTest {

    static final String TOKEN = "test-secret-0123456789abcdef";

    /** Alerte telle qu'émise par le script Pine. */
    static final String VALID = """
            {"symbol":"GC1!","direction":"long","entry":1997.8,"stop":1994.1,"target":2012.9,"rr":4.04,\
            "zone":"1995.4-1996.4","contacts":2,"sweep":1994.5,"bias":"neutral","session":"londres","tf":"5/60/240"}""";

    @Autowired
    ApplicationEvents events;

    private ResultActions send(String token, String body) throws Exception {
        return mvc.perform(post("/api/webhooks/tradingview/" + token).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void validAlertIsRecordedOnceAndReplayIsIdempotent() throws Exception {
        clock.set(Instant.parse("2026-11-13T08:50:01.250Z")); // alerte reçue juste après la clôture 08:50

        MvcResult first = send(TOKEN, VALID)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("GC"))
                .andExpect(jsonPath("$.source").value("TRADINGVIEW"))
                .andExpect(jsonPath("$.direction").value("long"))
                .andExpect(jsonPath("$.time").value("2026-11-13T08:50:00Z"))
                .andExpect(jsonPath("$.receivedAt").value("2026-11-13T08:50:01.250Z"))
                .andExpect(jsonPath("$.entry").value(1997.8))
                .andExpect(jsonPath("$.stop").value(1994.1))
                .andExpect(jsonPath("$.target").value(2012.9))
                .andExpect(jsonPath("$.rr").value(4.04))
                .andExpect(jsonPath("$.contacts").value(2))
                .andExpect(jsonPath("$.sweepExtreme").value(1994.5))
                .andExpect(jsonPath("$.timeframe").value("5/60/240"))
                .andReturn();
        assertThat(first.getResponse().getContentAsString()).doesNotContain(TOKEN);

        clock.set(Instant.parse("2026-11-13T08:50:07Z")); // TradingView rejoue l'alerte
        send(TOKEN, VALID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedAt").value("2026-11-13T08:50:01.250Z"));

        assertThat(count("signal")).isEqualTo(1);
        assertThat(events.stream(SignalRecordedEvent.class)).hasSize(1);

        // Bougie suivante : nouveau signal.
        clock.set(Instant.parse("2026-11-13T08:55:00.400Z"));
        send(TOKEN, VALID).andExpect(status().isCreated())
                .andExpect(jsonPath("$.time").value("2026-11-13T08:55:00Z"));
        assertThat(count("signal")).isEqualTo(2);
        assertThat(jdbc.sql("SELECT DISTINCT source FROM signal").query(String.class).single())
                .isEqualTo("TRADINGVIEW");
    }

    @Test
    void textPlainBodyIsAccepted() throws Exception {
        // TradingView envoie text/plain selon le message saisi dans l'alerte.
        mvc.perform(post("/api/webhooks/tradingview/" + TOKEN).contentType(MediaType.TEXT_PLAIN).content(VALID))
                .andExpect(status().isCreated());
    }

    @Test
    void wrongTokenReturns404WithoutLeakingAnything() throws Exception {
        MvcResult r = send("wrong-secret-0123456789abcdef", VALID)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("wrong-secret").doesNotContain(TOKEN);
        send(TOKEN.substring(0, TOKEN.length() - 1), VALID).andExpect(status().isNotFound());
        send(TOKEN + "x", VALID).andExpect(status().isNotFound());
        assertThat(count("signal")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // sens inconnu
            "{\"symbol\":\"GC1!\",\"direction\":\"buy\",\"entry\":1997.8,\"stop\":1994.1,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // stop au-dessus de l'entrée pour un long
            "{\"symbol\":\"GC1!\",\"direction\":\"long\",\"entry\":1997.8,\"stop\":1999.1,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // objectif sous l'entrée pour un long
            "{\"symbol\":\"GC1!\",\"direction\":\"long\",\"entry\":1997.8,\"stop\":1994.1,\"target\":1990.0,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // short avec stop sous l'entrée
            "{\"symbol\":\"GC1!\",\"direction\":\"short\",\"entry\":1997.8,\"stop\":1994.1,\"target\":1990.0,\"rr\":2,\"zone\":\"z\",\"contacts\":2,\"sweep\":1999.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // short avec objectif au-dessus
            "{\"symbol\":\"GC1!\",\"direction\":\"short\",\"entry\":1997.8,\"stop\":2001.0,\"target\":2005.0,\"rr\":2,\"zone\":\"z\",\"contacts\":2,\"sweep\":1999.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // prix non fini (1e400 = Infinity)
            "{\"symbol\":\"GC1!\",\"direction\":\"long\",\"entry\":1e400,\"stop\":1994.1,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // NaN (JSON invalide)
            "{\"symbol\":\"GC1!\",\"direction\":\"long\",\"entry\":NaN,\"stop\":1994.1,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // prix en chaîne
            "{\"symbol\":\"GC1!\",\"direction\":\"long\",\"entry\":\"1997.8\",\"stop\":1994.1,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // champ manquant (stop)
            "{\"symbol\":\"GC1!\",\"direction\":\"long\",\"entry\":1997.8,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // champ inconnu
            "{\"symbol\":\"GC1!\",\"direction\":\"long\",\"entry\":1997.8,\"stop\":1994.1,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\",\"qty\":3}",
            // instrument inconnu
            "{\"symbol\":\"ES1!\",\"direction\":\"long\",\"entry\":1997.8,\"stop\":1994.1,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // biais inconnu
            "{\"symbol\":\"GC1!\",\"direction\":\"long\",\"entry\":1997.8,\"stop\":1994.1,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"bullish\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // timeframe illisible
            "{\"symbol\":\"GC1!\",\"direction\":\"long\",\"entry\":1997.8,\"stop\":1994.1,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"abc\"}",
            // contacts décimal
            "{\"symbol\":\"GC1!\",\"direction\":\"long\",\"entry\":1997.8,\"stop\":1994.1,\"target\":2012.9,\"rr\":4.04,\"zone\":\"z\",\"contacts\":2.5,\"sweep\":1994.5,\"bias\":\"neutral\",\"session\":\"londres\",\"tf\":\"5/60/240\"}",
            // JSON tronqué
            "{\"symbol\":\"GC1!\",\"direction\":\"long\"",
            // pas un objet
            "[1,2,3]",
            "null",
            "   "
    })
    void invalidBodyReturns400(String body) throws Exception {
        MvcResult r = send(TOKEN, body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain(TOKEN);
        assertThat(count("signal")).isZero();
    }

    @Test
    void shortAlertIsAccepted() throws Exception {
        send(TOKEN, """
                {"symbol":"MGC1!","direction":"short","entry":1997.8,"stop":2001.0,"target":1990.0,"rr":2.44,\
                "zone":"1999.0-2000.1","contacts":3,"sweep":2000.6,"bias":"short","session":"new_york","tf":"15/60/240"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("MGC"))
                .andExpect(jsonPath("$.direction").value("short"));
    }
}
