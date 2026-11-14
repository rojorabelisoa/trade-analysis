package com.srliq.app.bias;

import java.time.LocalDate;

/** Biais fondamental en vigueur à partir de {@code effectiveDate} (minuit UTC). */
public record BiasEntry(long id, LocalDate effectiveDate, String symbol, String bias, String note) {
}
