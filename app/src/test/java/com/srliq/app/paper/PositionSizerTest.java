package com.srliq.app.paper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionSizerTest {

    @Test
    void goldContracts() {
        // 100 000 x 1 % = 1 000 $ ; GC : 4 points x 100 $ = 400 $ par contrat -> 2 contrats.
        assertThat(PositionSizer.size(100_000, 1.0, 4.0, 100)).isEqualTo(new PositionSizer.Size(2, false));
        // Exactement 1 000 $ : 1 contrat (pas d'erreur d'arrondi).
        assertThat(PositionSizer.size(100_000, 1.0, 10.0, 100).quantity()).isEqualTo(1);
        assertThat(PositionSizer.size(100_000, 1.0, 0.1, 100).quantity()).isEqualTo(100);
    }

    @Test
    void microGoldContracts() {
        // MGC : 4 points x 10 $ = 40 $ -> 25 contrats.
        assertThat(PositionSizer.size(100_000, 1.0, 4.0, 10)).isEqualTo(new PositionSizer.Size(25, false));
        assertThat(PositionSizer.size(100_000, 0.5, 3.0, 10).quantity()).isEqualTo(16);
    }

    @Test
    void riskTooLargeForCapital() {
        // GC : 15 points x 100 $ = 1 500 $ > 1 000 $ -> 0 contrat, signalé.
        assertThat(PositionSizer.size(100_000, 1.0, 15.0, 100)).isEqualTo(new PositionSizer.Size(0, true));
        assertThat(PositionSizer.size(100_000, 1.0, 15.0, 10)).isEqualTo(new PositionSizer.Size(6, false));
    }

    @Test
    void rejectsInvalidRisk() {
        assertThatThrownBy(() -> PositionSizer.size(100_000, 1.0, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
