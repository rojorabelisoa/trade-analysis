package com.srliq.app.webhook;

/**
 * Corps JSON émis par {@code alert()} du script Pine {@code sr_liquidity_entry.pine} :
 * <pre>{"symbol":"GC1!","direction":"long","entry":1997.8,"stop":1994.0,"target":2012.8,"rr":4.04,
 * "zone":"1995.4-1996.3","contacts":2,"sweep":1994.5,"bias":"neutral","session":"londres","tf":"5/60/240"}</pre>
 * Types en objets pour distinguer un champ absent ; la validation est faite par
 * {@link TradingViewAlertValidator}.
 */
public record TradingViewAlert(
        String symbol,
        String direction,
        Double entry,
        Double stop,
        Double target,
        Double rr,
        String zone,
        Integer contacts,
        Double sweep,
        String bias,
        String session,
        String tf) {
}
