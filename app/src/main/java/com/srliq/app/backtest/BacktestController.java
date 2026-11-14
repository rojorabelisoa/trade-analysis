package com.srliq.app.backtest;

import com.srliq.app.config.PaperProperties;
import com.srliq.app.paper.PaperTradingService;
import com.srliq.app.web.ApiException;
import com.srliq.engine.backtest.IntradayRules;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/backtests")
public class BacktestController {

    private final OnDemandBacktestService service;
    private final PaperProperties paper;
    private final PaperTradingService paperTrading;

    public BacktestController(OnDemandBacktestService service, PaperProperties paper,
                              PaperTradingService paperTrading) {
        this.service = service;
        this.paper = paper;
        this.paperTrading = paperTrading;
    }

    /**
     * Backtest d'un export CSV TradingView, calculé en mémoire (rien n'est écrit en base).
     * Multipart : {@code file}, {@code symbol} ; facultatifs : {@code cost} (points de prix, défaut =
     * coût du paper trading pour l'instrument), {@code intraday} (défaut {@code true}, règles de
     * {@code srliq.intraday} ou leurs valeurs par défaut), {@code maxHoldBars} (défaut
     * {@code srliq.paper.max-hold-bars}), {@code useBias} / {@code useNews} (biais et annonces de la
     * base, défaut {@code true}), {@code strategy} (JSON des surcharges, clés de {@code srliq.strategy}
     * en camelCase, ex. {@code {"trendMode":"off","minRr":1.5}}).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OnDemandBacktestService.Result run(@RequestPart("file") MultipartFile file,
                                              @RequestParam("symbol") String symbol,
                                              @RequestParam(required = false) Double cost,
                                              @RequestParam(defaultValue = "true") boolean intraday,
                                              @RequestParam(required = false) Integer maxHoldBars,
                                              @RequestParam(defaultValue = "true") boolean useBias,
                                              @RequestParam(defaultValue = "true") boolean useNews,
                                              @RequestParam(required = false) String strategy) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest("Fichier vide");
        }
        IntradayRules rules = paperTrading.intradayRules() != null ? paperTrading.intradayRules()
                : IntradayRules.defaults();
        return service.run(new OnDemandBacktestService.Request(symbol, file.getBytes(),
                cost != null ? cost : paper.costFor(symbol), intraday, rules,
                maxHoldBars != null ? maxHoldBars : paper.getMaxHoldBars(), useBias, useNews, strategy));
    }
}
