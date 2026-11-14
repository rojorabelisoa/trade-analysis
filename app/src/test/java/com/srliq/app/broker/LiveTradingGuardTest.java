package com.srliq.app.broker;

import com.srliq.app.SrLiqApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code trading.live.enabled=true} empêche le démarrage : aucun adaptateur réel n'existe. */
@ExtendWith(OutputCaptureExtension.class)
class LiveTradingGuardTest {

    @Test
    void applicationRefusesToStartWithLiveTradingEnabled(CapturedOutput output) {
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(SrLiqApplication.class)
                    .web(WebApplicationType.NONE)
                    .profiles("test")
                    .run("--trading.live.enabled=true")) {
                // ne doit pas arriver ici
            }
        }).hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("trading.live.enabled=true est refusé")
                .hasMessageContaining("aucun adaptateur de courtier réel");
        assertThat(output.getAll()).contains("APPLICATION FAILED TO START")
                .contains("seul le paper trading est disponible");
    }

    @Test
    void guardAndPaperAdapter() {
        assertThat(new LiveTradingGuard(false).liveEnabled()).isFalse();
        assertThatThrownBy(() -> new LiveTradingGuard(true)).isInstanceOf(IllegalStateException.class);
        PaperBrokerAdapter paper = new PaperBrokerAdapter();
        BrokerAdapter.OrderAck ack = paper.submitEntry(new BrokerAdapter.EntryOrder("c1", "GC",
                com.srliq.engine.model.Direction.LONG, 1, null, 1990.0, 2010.0));
        assertThat(ack.accepted()).isTrue();
        assertThat(ack.orderId()).startsWith("paper-");
        assertThat(paper.positions()).isEmpty();
    }
}
