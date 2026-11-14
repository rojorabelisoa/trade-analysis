package com.srliq.app.config;

import com.srliq.app.marketdata.Timeframes;
import com.srliq.engine.strategy.DirectionFilter;
import com.srliq.engine.strategy.StrategyConfig;
import com.srliq.engine.strategy.TrendMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyPropertiesTest {

    @Test
    void defaultsAreTheEngineDefaults() {
        assertThat(new StrategyProperties().toConfig()).isEqualTo(StrategyConfig.defaults());
    }

    @Test
    void bindsReferenceStrings() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "srliq.strategy.trend-mode", "ema",
                "srliq.strategy.direction", "long",
                "srliq.strategy.min-rr", "1.5",
                "srliq.strategy.sessions", "londres,new_york")));
        StrategyConfig c = binder.bind("srliq.strategy", StrategyProperties.class).get().toConfig();
        assertThat(c.trendMode()).isEqualTo(TrendMode.EMA);
        assertThat(c.direction()).isEqualTo(DirectionFilter.LONG);
        assertThat(c.minRr()).isEqualTo(1.5);
        assertThat(c.sessions()).isEqualTo(List.of("londres", "new_york"));
        assertThat(c.atrPeriod()).isEqualTo(StrategyConfig.defaults().atrPeriod());
    }

    @Test
    void invalidValuesAreRejected() {
        StrategyProperties p = new StrategyProperties();
        p.setHtfPivotLeft(0);
        assertThatThrownBy(p::toConfig).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeframes() {
        assertThat(Timeframes.label(Duration.ofMinutes(5))).isEqualTo("5m");
        assertThat(Timeframes.label(Duration.ofHours(4))).isEqualTo("4h");
        assertThat(Timeframes.label(Duration.ofDays(1))).isEqualTo("1d");
        assertThat(Timeframes.label(Duration.ofSeconds(90))).isEqualTo("90s");
        assertThat(Timeframes.isEntryTimeframe(Duration.ofMinutes(5))).isTrue();
        assertThat(Timeframes.isEntryTimeframe(Duration.ofMinutes(15))).isTrue();
        assertThat(Timeframes.isEntryTimeframe(Duration.ofMinutes(7))).isFalse();
        assertThat(Timeframes.isEntryTimeframe(Duration.ofHours(1))).isFalse();
    }
}
