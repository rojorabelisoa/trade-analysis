package com.srliq.engine.structure;

import java.time.Instant;

/**
 * Order block. {@code dir} = 1 (acheteur) ou -1 (vendeur) ; connu à {@code knownAt}
 * (clôture de la bougie d'englobement).
 */
public record OrderBlock(int dir, double top, double bot, Instant knownAt) {

    /** {@code find_order_blocks} : bougie opposée juste avant une bougie qui l'englobe en clôture. */
    public static java.util.List<OrderBlock> find(com.srliq.engine.model.CandleSeries df, java.time.Duration dur) {
        java.util.List<OrderBlock> out = new java.util.ArrayList<>();
        for (int i = 1; i < df.size(); i++) {
            double o1 = df.open(i - 1);
            double c1 = df.close(i - 1);
            double h1 = df.high(i - 1);
            double l1 = df.low(i - 1);
            double c = df.close(i);
            Instant known = df.time(i).plus(dur);
            if (c1 < o1 && c > h1) {
                out.add(new OrderBlock(1, h1, l1, known));
            }
            if (c1 > o1 && c < l1) {
                out.add(new OrderBlock(-1, h1, l1, known));
            }
        }
        return out;
    }
}
