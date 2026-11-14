package com.srliq.app.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Base des tests d'intégration : contexte Spring complet sur un vrai PostgreSQL
 * ({@code SRLIQ_TEST_DB_URL}, schéma créé par Flyway), tables vidées avant chaque test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    /** Heure par défaut des tests : bien après les données des fixtures (janvier 2026). */
    public static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    public static class TestClockConfig {
        @Bean
        @Primary
        public MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("TRUNCATE candle, signal, fundamental_bias, news_event RESTART IDENTITY").update();
        clock.set(NOW);
    }

    /** Fichier des fixtures de parité du moteur ({@code engine/src/test/resources/parity}). */
    public static Path parity(String relative) {
        String dir = System.getProperty("srliq.parity.dir");
        if (dir == null) {
            throw new IllegalStateException("Propriété système srliq.parity.dir absente (lancer via Gradle)");
        }
        return Path.of(dir).resolve(relative);
    }

    public static byte[] bytes(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected ResultActions importCsv(String symbol, byte[] csv) throws Exception {
        return mvc.perform(multipart("/api/candles/import")
                .file(new MockMultipartFile("file", "export.csv", "text/csv", csv))
                .param("symbol", symbol));
    }

    protected int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }
}
