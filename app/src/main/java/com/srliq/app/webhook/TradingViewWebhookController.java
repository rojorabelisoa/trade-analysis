package com.srliq.app.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.srliq.app.instrument.Instrument;
import com.srliq.app.instrument.InstrumentRepository;
import com.srliq.app.signal.SignalRecord;
import com.srliq.app.signal.SignalRecorder;
import com.srliq.app.signal.SignalSource;
import com.srliq.app.web.ApiExceptionHandler;
import com.srliq.engine.model.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Réception des alertes TradingView. TradingView ne permet pas d'en-tête personnalisé : le secret
 * est dans l'URL ({@code /api/webhooks/tradingview/{token}}). Le secret n'est jamais journalisé ni
 * renvoyé (le champ {@code instance} des erreurs est masqué).
 *
 * <ul>
 *   <li>secret non configuré : 503 ; mauvais secret : 404 (l'endpoint ne se révèle pas) ;</li>
 *   <li>corps invalide : 400 ; nouveau signal : 201 ; alerte déjà reçue : 200.</li>
 * </ul>
 *
 * <p>Idempotence : l'heure du signal est l'heure de réception ramenée à la clôture de la bougie
 * d'entrée (multiple du timeframe lu dans "tf", alerte émise à la clôture de bougie). Une alerte
 * rejouée pendant la même bougie a donc la même clé (instrument, source, heure, sens) et n'est
 * pas dupliquée ; {@code received_at} garde l'heure exacte de réception.
 */
@RestController
public class TradingViewWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TradingViewWebhookController.class);
    private static final URI MASKED_INSTANCE = URI.create("/api/webhooks/tradingview/***");

    private final WebhookTokenVerifier verifier;
    private final InstrumentRepository instruments;
    private final SignalRecorder recorder;
    private final Clock clock;
    private final ObjectReader reader;

    public TradingViewWebhookController(WebhookTokenVerifier verifier, InstrumentRepository instruments,
                                        SignalRecorder recorder, Clock clock) {
        this.verifier = verifier;
        this.instruments = instruments;
        this.recorder = recorder;
        this.clock = clock;
        // Lecture stricte : champ inconnu, type approximatif ("12" pour un nombre, 2.5 pour un entier)
        // ou contenu après le JSON = 400.
        ObjectMapper strict = JsonMapper.builder()
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.reader = strict.readerFor(TradingViewAlert.class);
    }

    @PostMapping(path = "/api/webhooks/tradingview/{token}")
    public ResponseEntity<?> receive(@PathVariable("token") String token,
                                     @RequestBody(required = false) String body) {
        if (!verifier.isConfigured()) {
            return masked(HttpStatus.SERVICE_UNAVAILABLE, "Webhook TradingView non configuré");
        }
        if (!verifier.matches(token)) {
            log.warn("Alerte TradingView refusée : secret invalide");
            return masked(HttpStatus.NOT_FOUND, "Ressource introuvable");
        }
        if (body == null || body.isBlank()) {
            return masked(HttpStatus.BAD_REQUEST, "Corps vide");
        }
        TradingViewAlert alert;
        try {
            alert = reader.readValue(body);
        } catch (JsonProcessingException e) {
            return masked(HttpStatus.BAD_REQUEST, "JSON invalide : " + e.getOriginalMessage());
        }
        if (alert == null) {
            return masked(HttpStatus.BAD_REQUEST, "Corps vide");
        }
        List<String> errors = TradingViewAlertValidator.validate(alert);
        Instrument ins = errors.isEmpty() ? instruments.findBySymbol(alert.symbol()).orElse(null) : null;
        if (errors.isEmpty() && ins == null) {
            errors = List.of("symbol : instrument inconnu '" + alert.symbol() + "'");
        }
        if (!errors.isEmpty()) {
            ResponseEntity<ProblemDetail> r = masked(HttpStatus.BAD_REQUEST, "Alerte invalide");
            r.getBody().setProperty("errors", errors);
            return r;
        }

        Instant received = clock.instant();
        Duration tf = TradingViewAlertValidator.entryTimeframe(alert.tf()).orElseThrow();
        SignalRecord record = new SignalRecord(null, ins.symbol(), SignalSource.TRADINGVIEW, alert.tf().strip(),
                barClose(received, tf), Direction.fromPython(alert.direction()), alert.entry(), alert.stop(),
                alert.target(), alert.rr(), alert.zone().strip(), alert.contacts(), null, alert.sweep(),
                alert.bias(), alert.session(), null, received);
        SignalRecorder.Recorded result = recorder.record(record);
        log.info("Alerte TradingView {} {} {} : {}", ins.symbol(), alert.direction(), record.time(),
                result.created() ? "enregistrée" : "déjà reçue");
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.signal());
    }

    /** Clôture de la bougie d'entrée qui vient de se terminer : instant ramené au multiple de {@code tf}. */
    static Instant barClose(Instant received, Duration tf) {
        long step = tf.toMillis();
        if (step <= 0) {
            return received.truncatedTo(ChronoUnit.MINUTES);
        }
        return Instant.ofEpochMilli(Math.floorDiv(received.toEpochMilli(), step) * step);
    }

    private static ResponseEntity<ProblemDetail> masked(HttpStatus status, String detail) {
        ResponseEntity<ProblemDetail> r = ApiExceptionHandler.problem(status, detail);
        r.getBody().setInstance(MASKED_INSTANCE);
        return r;
    }
}
