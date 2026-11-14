package com.srliq.app.marketdata;

import com.srliq.app.web.ApiException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/candles")
public class CandleImportController {

    private final TradingViewCsvSource csv;

    public CandleImportController(TradingViewCsvSource csv) {
        this.csv = csv;
    }

    /**
     * Import d'un export CSV TradingView (multipart : {@code file}, {@code symbol}). Réimporter le
     * même fichier ne duplique rien. Les signaux de l'instrument sont recalculés ensuite.
     */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestResult importCsv(@RequestPart("file") MultipartFile file, @RequestParam("symbol") String symbol)
            throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest("Fichier vide");
        }
        return csv.importCsv(symbol, file.getBytes());
    }
}
