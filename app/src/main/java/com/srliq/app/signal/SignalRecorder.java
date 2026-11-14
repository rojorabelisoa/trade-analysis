package com.srliq.app.signal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enregistre les signaux sans doublon et publie {@link SignalRecordedEvent} pour chaque nouveau. */
@Service
public class SignalRecorder {

    /** Résultat : le signal en base, et s'il vient d'être créé. */
    public record Recorded(SignalRecord signal, boolean created) {
    }

    private final SignalRepository signals;
    private final ApplicationEventPublisher events;

    public SignalRecorder(SignalRepository signals, ApplicationEventPublisher events) {
        this.signals = signals;
        this.events = events;
    }

    @Transactional
    public Recorded record(SignalRecord s) {
        return signals.insertIfAbsent(s)
                .map(id -> {
                    SignalRecord stored = signals.findById(id).orElseThrow();
                    events.publishEvent(new SignalRecordedEvent(stored));
                    return new Recorded(stored, true);
                })
                .orElseGet(() -> new Recorded(
                        signals.findByKey(s.symbol(), s.source(), s.timeframe(), s.time(), s.direction()).orElseThrow(), false));
    }
}
