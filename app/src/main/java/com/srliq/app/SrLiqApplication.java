package com.srliq.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application SR-Liq : stockage des bougies, calcul des signaux avec le moteur ({@code :engine}),
 * réception des alertes TradingView. Aucun envoi d'ordre.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SrLiqApplication {

    public static void main(String[] args) {
        SpringApplication.run(SrLiqApplication.class, args);
    }
}
