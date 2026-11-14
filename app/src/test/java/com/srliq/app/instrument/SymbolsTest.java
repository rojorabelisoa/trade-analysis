package com.srliq.app.instrument;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolsTest {

    @Test
    void normalizesTradingViewSymbols() {
        assertThat(Symbols.normalize("GC1!")).isEqualTo("GC");
        assertThat(Symbols.normalize("MGC1!")).isEqualTo("MGC");
        assertThat(Symbols.normalize("CL2!")).isEqualTo("CL");
        assertThat(Symbols.normalize("COMEX:SI1!")).isEqualTo("SI");
        assertThat(Symbols.normalize(" hg ")).isEqualTo("HG");
        assertThat(Symbols.normalize("GC")).isEqualTo("GC");
        assertThat(Symbols.normalize("GCZ2026")).isEqualTo("GCZ2026");
        assertThat(Symbols.normalize(null)).isEmpty();
    }
}
