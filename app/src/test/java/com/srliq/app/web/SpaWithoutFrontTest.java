package com.srliq.app.web;

import com.srliq.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Build sans -PwithWeb : pas d'index.html, les routes du front restent des 404 (comportement d'avant). */
@TestPropertySource(properties = "srliq.web.static-location=classpath:/aucun-front/")
class SpaWithoutFrontTest extends IntegrationTest {

    @Test
    void frontRoutesAre404WithoutEmbeddedFront() throws Exception {
        mvc.perform(get("/signaux"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/api/instruments")).andExpect(status().isOk());
    }
}
