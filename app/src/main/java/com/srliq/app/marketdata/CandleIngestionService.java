package com.srliq.app.marketdata;

import com.srliq.app.instrument.Instrument;
import com.srliq.app.instrument.InstrumentRepository;
import com.srliq.app.signal.RecomputeResult;
import com.srliq.app.signal.SignalComputationService;
import com.srliq.engine.model.CandleSeries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/**
 * Puits commun à toutes les sources : écarte les bougies non clôturées, stocke le reste
 * (upsert idempotent) puis relance le calcul des signaux de l'instrument sur ce timeframe.
 */
@Service
public class CandleIngestionService implements CandleSink {

    private static final Logger log = LoggerFactory.getLogger(CandleIngestionService.class);

    private final InstrumentRepository instruments;
    private final CandleRepository candles;
    private final SignalComputationService signals;
    private final Clock clock;

    public CandleIngestionService(InstrumentRepository instruments, CandleRepository candles,
                                  SignalComputationService signals, Clock clock) {
        this.instruments = instruments;
        this.candles = candles;
        this.signals = signals;
        this.clock = clock;
    }

    @Override
    public IngestResult accept(CandleBatch batch) {
        Instrument ins = instruments.require(batch.symbol());
        Duration tf = batch.timeframe();
        CandleSeries closed = closedOnly(batch.candles(), tf);
        int skipped = batch.candles().size() - closed.size();

        CandleRepository.UpsertCount count = candles.upsert(ins.id(), tf, closed);
        log.info("Bougies {} {} ({}) : {} reçues, {} non clôturées écartées, {} nouvelles, {} modifiées",
                ins.symbol(), Timeframes.label(tf), batch.source(), batch.candles().size(), skipped,
                count.inserted(), count.updated());

        RecomputeResult recompute = signals.recompute(ins.symbol(), tf);
        return new IngestResult(ins.symbol(), Timeframes.label(tf), batch.candles().size(), skipped,
                count.inserted(), count.updated(), count.unchanged(), recompute);
    }

    /** Bougies dont la clôture (ouverture + durée) est passée ; la série étant triée, on coupe la fin. */
    private CandleSeries closedOnly(CandleSeries s, Duration tf) {
        long now = clock.millis();
        long step = tf.toMillis();
        int n = s.size();
        while (n > 0 && s.timeMillis(n - 1) + step > now) {
            n--;
        }
        return n == s.size() ? s : s.slice(0, n);
    }
}
