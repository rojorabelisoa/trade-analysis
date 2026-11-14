package com.srliq.engine.parity;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.srliq.engine.data.CsvTable;
import com.srliq.engine.data.Resampler;
import com.srliq.engine.data.TradingViewCsv;
import com.srliq.engine.filter.BiasBook;
import com.srliq.engine.filter.NewsCalendar;
import com.srliq.engine.model.CandleSeries;
import com.srliq.engine.strategy.StrategyConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accès aux fixtures de parité ({@code src/test/resources/parity}, produites par
 * {@code tools/parity/generate_fixtures.py}) et reconstruction des entrées comme le script.
 */
final class ParityFixtures {

    static final ObjectMapper JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS) // "Infinity" (profit_factor sans perte)
            .build();

    private static final Map<String, CandleSeries> SERIES = new ConcurrentHashMap<>();

    private ParityFixtures() {
    }

    static Path path(String relative) {
        URL url = ParityFixtures.class.getResource("/parity/" + relative);
        if (url == null) {
            throw new IllegalStateException("Fixture absente : parity/" + relative);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    static JsonNode json(String relative) {
        try (InputStream in = ParityFixtures.class.getResourceAsStream("/parity/" + relative)) {
            if (in == null) {
                throw new IllegalStateException("Fixture absente : parity/" + relative);
            }
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> scenarioNames() {
        List<String> out = new ArrayList<>();
        json("index.json").forEach(n -> out.add(n.asText()));
        return out;
    }

    /** Série LTF de la fixture (le script de génération calcule tout à partir de ce CSV relu). */
    static CandleSeries ltf(String name) {
        return SERIES.computeIfAbsent(name, n -> TradingViewCsv.read(path("candles/" + n + ".csv")));
    }

    static CandleSeries resampled(String ltfName, String rule) {
        return Resampler.resample(ltf(ltfName), rule);
    }

    /** Config Python (dict asdict) -> StrategyConfig. */
    static StrategyConfig config(JsonNode c) {
        StrategyConfig.Builder b = StrategyConfig.builder()
                .htfPivotLeft(c.get("htf_pivot_left").asInt())
                .htfPivotRight(c.get("htf_pivot_right").asInt())
                .zoneMergeAtr(c.get("zone_merge_atr").asDouble())
                .zoneMaxWidthAtr(c.get("zone_max_width_atr").asDouble())
                .minTouches(c.get("min_touches").asInt())
                .zoneMaxAgeBars(c.get("zone_max_age_bars").asInt())
                .sweepMinAtr(c.get("sweep_min_atr").asDouble())
                .sweepMaxAtr(c.get("sweep_max_atr").asDouble())
                .ltfPivotLeft(c.get("ltf_pivot_left").asInt())
                .ltfPivotRight(c.get("ltf_pivot_right").asInt())
                .confirmBars(c.get("confirm_bars").asInt())
                .slBufferAtr(c.get("sl_buffer_atr").asDouble())
                .stopMode(c.get("stop_mode").asText())
                .targetMode(c.get("target_mode").asText())
                .tpLookback(c.get("tp_lookback").asInt())
                .minRr(c.get("min_rr").asDouble())
                .rr(c.get("rr").asDouble())
                .maxRiskHtfAtr(c.get("max_risk_htf_atr").asDouble())
                .neutralAllowsBoth(c.get("neutral_allows_both").asBoolean())
                .trendMode(c.get("trend_mode").asText())
                .trendLen(c.get("trend_len").asInt())
                .direction(c.get("direction").asText())
                .atrPeriod(c.get("atr_period").asInt());
        JsonNode s = c.get("sessions");
        if (s != null && !s.isNull()) {
            List<String> sessions = new ArrayList<>();
            s.forEach(x -> sessions.add(x.asText()));
            b.sessions(sessions);
        }
        return b.build();
    }

    /** Entrées d'un scénario, reconstruites comme dans {@code generate_fixtures.main()}. */
    record ScenarioInputs(String name, JsonNode doc, CandleSeries ltf, CandleSeries zones, CandleSeries trend,
                          StrategyConfig config, BiasBook bias, NewsCalendar news, String symbol) {
    }

    static ScenarioInputs scenario(String name) {
        JsonNode doc = json(name + ".json");
        String ltfName = doc.get("ltf").asText();
        CandleSeries ltf = ltf(ltfName);
        CandleSeries zones = Resampler.resample(ltf, doc.get("zone_rule").asText());
        JsonNode tr = doc.get("trend_rule");
        CandleSeries trend = tr == null || tr.isNull() ? null : Resampler.resample(ltf, tr.asText());
        String symbol = doc.get("symbol").asText();
        BiasBook bias = BiasBook.fromCsv(doc.get("use_bias").asBoolean() ? path("inputs/bias.csv") : null, symbol);
        NewsCalendar news = NewsCalendar.fromCsv(doc.get("use_news").asBoolean() ? path("inputs/news.csv") : null,
                doc.get("news_before_min").asInt(), doc.get("news_after_min").asInt());
        return new ScenarioInputs(name, doc, ltf, zones, trend, config(doc.get("config")), bias, news, symbol);
    }

    static Instant instant(JsonNode n) {
        return Instant.parse(n.asText());
    }
}
