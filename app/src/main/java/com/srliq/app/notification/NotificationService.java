package com.srliq.app.notification;

import com.srliq.app.instrument.Instrument;
import com.srliq.app.instrument.InstrumentRepository;
import com.srliq.app.news.NewsEvent;
import com.srliq.app.news.NewsRepository;
import com.srliq.app.paper.PaperTrade;
import com.srliq.app.paper.PaperTradeClosedEvent;
import com.srliq.app.signal.SignalRecord;
import com.srliq.app.signal.SignalRecordedEvent;
import com.srliq.app.trading.TradingHalt;
import com.srliq.app.trading.TradingHaltChangedEvent;
import com.srliq.app.trading.TradingHaltRepository;
import com.srliq.app.web.ApiException;
import com.srliq.engine.strategy.StrategyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Notifications Telegram des nouveaux signaux, des trades simulés clôturés et du coupe-circuit.
 *
 * <ul>
 *   <li>Écoute après commit : un signal n'est notifié qu'une fois enregistré.</li>
 *   <li>Pas de rafale sur l'historique : seul un événement de moins de
 *       {@code srliq.telegram.max-signal-age-minutes} est envoyé ; les autres sont SKIPPED.</li>
 *   <li>Une ligne par sujet et par canal (idempotent) ; un échec donne FAILED, sans réessai
 *       automatique ({@code POST /api/notifications/{id}/retry}).</li>
 *   <li>Aucune exception ne remonte : un échec n'empêche ni l'enregistrement du signal ni le rejeu.</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final TelegramClient telegram;
    private final TelegramProperties props;
    private final InstrumentRepository instruments;
    private final NewsRepository news;
    private final TradingHaltRepository halts;
    private final StrategyConfig strategy;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, TelegramClient telegram,
                               TelegramProperties props, InstrumentRepository instruments, NewsRepository news,
                               TradingHaltRepository halts, StrategyConfig strategy,
                               PlatformTransactionManager txManager, Clock clock) {
        this.notifications = notifications;
        this.telegram = telegram;
        this.props = props;
        this.instruments = instruments;
        this.news = news;
        this.halts = halts;
        this.strategy = strategy;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        log.info("Notifications Telegram {}", props.enabled() ? "activées" : "désactivées (jeton ou discussion absent)");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignalRecorded(SignalRecordedEvent e) {
        SignalRecord s = e.signal();
        safely(NotificationKind.SIGNAL, s.id(), () -> process(NotificationKind.SIGNAL, s.id(), s.time(),
                () -> signalMessage(s)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaperTradeClosed(PaperTradeClosedEvent e) {
        PaperTrade t = e.trade();
        safely(NotificationKind.TRADE_CLOSED, t.id(), () -> process(NotificationKind.TRADE_CLOSED, t.id(),
                t.exitTime(), () -> MessageFormatter.tradeClosed(t)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHaltChanged(TradingHaltChangedEvent e) {
        TradingHalt h = e.halt();
        NotificationKind kind = e.halted() ? NotificationKind.HALT : NotificationKind.RESUME;
        Instant at = e.halted() ? h.startedAt() : h.endedAt();
        safely(kind, h.id(), () -> process(kind, h.id(), at, () -> MessageFormatter.haltChanged(h, e.halted())));
    }

    /** Nouvel essai d'une notification FAILED (renvoie le texte enregistré). */
    public Notification retry(long id) {
        Notification n = notifications.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification " + id + " introuvable"));
        if (n.status() != NotificationStatus.FAILED) {
            throw ApiException.conflict("Seule une notification FAILED peut être renvoyée (statut : " + n.status() + ")");
        }
        if (!telegram.enabled()) {
            throw ApiException.conflict("Telegram non configuré");
        }
        Instant now = clock.instant();
        if (!inTx(() -> notifications.claim(id, NotificationStatus.FAILED, now))) {
            throw ApiException.conflict("Notification déjà en cours de renvoi");
        }
        deliver(id, n.kind(), n.subjectId(), n.message());
        return notifications.findById(id).orElseThrow();
    }

    private void process(NotificationKind kind, long subjectId, Instant eventTime, Supplier<String> message) {
        Instant now = clock.instant();
        if (notifications.find(kind, subjectId, Notification.TELEGRAM).isPresent()) {
            return; // déjà traitée
        }
        if (!telegram.enabled()) {
            record(kind, subjectId, NotificationStatus.SKIPPED, "Telegram non configuré", null, now);
            return;
        }
        Duration maxAge = Duration.ofMinutes(props.getMaxSignalAgeMinutes());
        if (eventTime == null || eventTime.isBefore(now.minus(maxAge))) {
            record(kind, subjectId, NotificationStatus.SKIPPED,
                    "trop ancien (plus de " + props.getMaxSignalAgeMinutes() + " min) : historique", null, now);
            return;
        }
        String text = message.get();
        Optional<Long> id = record(kind, subjectId, NotificationStatus.PENDING, null, text, now);
        id.ifPresent(i -> deliver(i, kind, subjectId, text));
    }

    private void deliver(long id, NotificationKind kind, long subjectId, String text) {
        try {
            telegram.send(text);
            inTx(() -> {
                notifications.markAttempt(id, NotificationStatus.SENT, null, clock.instant());
                return null;
            });
            log.info("Notification Telegram {} #{} envoyée", kind, subjectId);
        } catch (TelegramClient.SendException ex) {
            inTx(() -> {
                notifications.markAttempt(id, NotificationStatus.FAILED, ex.getMessage(), clock.instant());
                return null;
            });
            log.warn("Notification Telegram {} #{} en échec : {}", kind, subjectId, ex.getMessage());
        }
    }

    private Optional<Long> record(NotificationKind kind, long subjectId, NotificationStatus status, String reason,
                                  String message, Instant now) {
        return inTx(() -> notifications.insertIfAbsent(kind, subjectId, Notification.TELEGRAM, status, reason,
                message, now));
    }

    private String signalMessage(SignalRecord s) {
        String name = instruments.findBySymbol(s.symbol()).map(Instrument::name).orElse(null);
        List<NewsEvent> upcoming = news.find(s.time(), s.time().plus(Duration.ofHours(24)).plusMillis(1)).stream()
                .filter(n -> "high".equalsIgnoreCase(n.impact()))
                .toList();
        return MessageFormatter.signal(s, name, strategy.trendMode(), upcoming, halts.findActive());
    }

    /** Écritures dans une transaction neuve : on est après le commit de la transaction d'origine. */
    private <T> T inTx(Supplier<T> work) {
        return requiresNew.execute(status -> work.get());
    }

    private void safely(NotificationKind kind, long subjectId, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            // Message seul (pas de trace) : rien ici ne doit pouvoir journaliser l'URL de l'API.
            log.error("Notification {} #{} : erreur inattendue ({})", kind, subjectId,
                    TelegramClient.redact(ex.toString(), props.getBotToken()));
        }
    }
}
