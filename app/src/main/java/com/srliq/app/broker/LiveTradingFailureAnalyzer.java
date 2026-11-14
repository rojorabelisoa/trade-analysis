package com.srliq.app.broker;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Message de démarrage lisible quand {@code trading.live.enabled=true} est refusé. */
public class LiveTradingFailureAnalyzer extends AbstractFailureAnalyzer<IllegalStateException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, IllegalStateException cause) {
        if (!LiveTradingGuard.MESSAGE.equals(cause.getMessage())) {
            return null;
        }
        return new FailureAnalysis(LiveTradingGuard.MESSAGE,
                "Supprimez trading.live.enabled (ou TRADING_LIVE_ENABLED) ou mettez-la à false : seul le paper "
                        + "trading est disponible.", cause);
    }
}
