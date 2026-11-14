package com.srliq.app.web;

import com.srliq.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Front embarqué : les routes du front renvoient index.html, sans masquer l'API ni actuator.
 * Le « front » de test est dans src/test/resources/spa-test/.
 */
@TestPropertySource(properties = "srliq.web.static-location=classpath:/spa-test/")
class SpaWebConfigTest extends IntegrationTest {

    @Test
    void servesIndexAtRootAndForFrontRoutes() throws Exception {
        // La racine est renvoyée vers /index.html (MockMvc ne suit pas les renvois internes).
        mvc.perform(get("/")).andExpect(forwardedUrl("/index.html"));
        for (String path : new String[]{"/index.html", "/signaux", "/paper", "/fondamental/biais"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("spa-test-index")))
                    .andExpect(header().string("Cache-Control", containsString("no-cache")));
        }
    }

    @Test
    void servesHashedAssetsWithLongCache() throws Exception {
        mvc.perform(get("/assets/app-1234.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("spa-test-asset")))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
        mvc.perform(get("/assets/absent.js")).andExpect(status().isNotFound());
        mvc.perform(get("/absent.png")).andExpect(status().isNotFound());
    }

    @Test
    void apiAndActuatorAreNotReplacedByIndex() throws Exception {
        mvc.perform(get("/api/instruments"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].symbol").exists());
        mvc.perform(get("/api/inconnu"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/inconnu")).andExpect(status().isNotFound());
        mvc.perform(post("/signaux")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void frontRouteDetection() {
        assertThat(SpaWebConfig.SpaFallbackResolver.isFrontRoute("")).isTrue();
        assertThat(SpaWebConfig.SpaFallbackResolver.isFrontRoute("signaux")).isTrue();
        assertThat(SpaWebConfig.SpaFallbackResolver.isFrontRoute("api/signals")).isFalse();
        assertThat(SpaWebConfig.SpaFallbackResolver.isFrontRoute("actuator/health")).isFalse();
        assertThat(SpaWebConfig.SpaFallbackResolver.isFrontRoute("favicon.ico")).isFalse();
        assertThat(SpaWebConfig.SpaFallbackResolver.isFrontRoute("apiculture")).isTrue();
    }
}
