package com.srliq.app.paper;

/**
 * Dimensionnement à risque fixe : contrats = partie entière de
 * (capital x risque %) / (risque en points x valeur du point). Le capital est fixe (pas de
 * capitalisation des gains). 0 contrat : le risque d'un seul contrat dépasse le budget.
 */
public final class PositionSizer {

    /** Résultat : nombre de contrats et indicateur « risque trop grand pour le capital ». */
    public record Size(int quantity, boolean riskTooLarge) {
    }

    private PositionSizer() {
    }

    public static Size size(double capital, double riskPercent, double riskPoints, double pointValue) {
        if (riskPoints <= 0 || pointValue <= 0) {
            throw new IllegalArgumentException("Risque et valeur du point doivent être > 0");
        }
        double budget = capital * riskPercent / 100.0;
        double perContract = riskPoints * pointValue;
        // Petite tolérance : 1000 / (4 x 100) doit donner 2.5 -> 2, et 1000 / (10 x 100) exactement 1.
        long q = (long) Math.floor(budget / perContract + 1e-9);
        int quantity = (int) Math.min(Math.max(q, 0), Integer.MAX_VALUE);
        return new Size(quantity, quantity == 0);
    }
}
