package com.srliq.app.signal;

import com.srliq.engine.model.Direction;
import com.srliq.engine.model.Signal;

import java.time.Instant;
import java.util.Objects;

/**
 * Signal stocké : champs de {@link Signal} plus la provenance.
 *
 * @param id         identifiant en base ({@code null} avant enregistrement)
 * @param timeframe  "5m" pour le moteur ; champ {@code tf} brut pour TradingView ("5/60/240")
 * @param time       clôture de la bougie de confirmation (UTC)
 * @param contacts   nombre de contacts de la zone (alerte TradingView uniquement)
 * @param sweepTime  heure du sweep (moteur uniquement)
 * @param receivedAt heure d'enregistrement / de réception
 */
public record SignalRecord(
        Long id,
        String symbol,
        SignalSource source,
        String timeframe,
        Instant time,
        Direction direction,
        double entry,
        double stop,
        double target,
        double rr,
        String zone,
        Integer contacts,
        Instant sweepTime,
        Double sweepExtreme,
        String bias,
        String session,
        Double nextZone,
        Instant receivedAt) {

    public SignalRecord {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /** Signal au format du moteur (pour le backtest). {@code sweepExtreme} absent -> NaN. */
    public Signal toEngine() {
        return new Signal(symbol, direction, time, entry, stop, target, rr, zone, sweepTime,
                sweepExtreme == null ? Double.NaN : sweepExtreme, bias, session, nextZone);
    }

    /** Signal du moteur, prêt à être enregistré. */
    public static SignalRecord fromEngine(Signal s, String symbol, String timeframe, Instant receivedAt) {
        return new SignalRecord(null, symbol, SignalSource.ENGINE, timeframe, s.time(), s.direction(), s.entry(),
                s.stop(), s.target(), s.rr(), s.zone(), null, s.sweepTime(), s.sweepExtreme(), s.bias(),
                s.session(), s.nextZone(), receivedAt);
    }
}
