package com.srliq.engine.data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

/**
 * Conversion de dates texte en {@link Instant} UTC, comme {@code pd.to_datetime(..., utc=True)} :
 * ISO 8601 avec ou sans {@code T}, avec ou sans fuseau ({@code Z}, {@code +01:00}) ; sans fuseau,
 * l'heure est lue comme UTC ; une date seule vaut minuit UTC.
 */
public final class TimeParser {

    private static final DateTimeFormatter LOCAL = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart().appendLiteral('T').optionalEnd()
            .optionalStart().appendLiteral(' ').optionalEnd()
            .appendPattern("HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .toFormatter();

    private static final DateTimeFormatter OFFSET = new DateTimeFormatterBuilder()
            .append(LOCAL)
            .optionalStart().appendLiteral(' ').optionalEnd()
            .appendOffset("+HH:MM", "Z")
            .toFormatter();

    private static final DateTimeFormatter OFFSET_COMPACT = new DateTimeFormatterBuilder()
            .append(LOCAL)
            .optionalStart().appendLiteral(' ').optionalEnd()
            .appendOffset("+HHMM", "Z")
            .toFormatter();

    private TimeParser() {
    }

    public static Instant parse(String text) {
        String s = text.strip();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Date vide");
        }
        try {
            return OffsetDateTime.parse(s, OFFSET).toInstant();
        } catch (DateTimeParseException ignored) {
            // essai suivant
        }
        try {
            return OffsetDateTime.parse(s, OFFSET_COMPACT).toInstant();
        } catch (DateTimeParseException ignored) {
            // essai suivant
        }
        try {
            return LocalDateTime.parse(s, LOCAL).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // essai suivant
        }
        try {
            return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date illisible : '" + text + "'", e);
        }
    }
}
