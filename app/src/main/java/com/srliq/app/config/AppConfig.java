package com.srliq.app.config;

import com.srliq.engine.strategy.StrategyConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

    /** Horloge UTC (remplaçable dans les tests). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Configuration du moteur, validée au démarrage : une valeur invalide empêche l'application de démarrer. */
    @Bean
    public StrategyConfig strategyConfig(StrategyProperties properties) {
        return properties.toConfig();
    }
}
