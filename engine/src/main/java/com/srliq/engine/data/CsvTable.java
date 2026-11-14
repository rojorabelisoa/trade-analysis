package com.srliq.engine.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lecture minimale d'un CSV (séparateur virgule, guillemets RFC 4180, lignes vides
 * ignorées). Les noms de colonnes sont normalisés comme dans la référence :
 * {@code strip().lower()}. Les valeurs sont gardées telles quelles.
 */
public final class CsvTable {

    private final List<String> columns;
    private final List<List<String>> rows;

    private CsvTable(List<String> columns, List<List<String>> rows) {
        this.columns = Collections.unmodifiableList(columns);
        this.rows = Collections.unmodifiableList(rows);
    }

    public static CsvTable read(Path path) {
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(r);
        } catch (IOException e) {
            throw new UncheckedIOException(path + ": lecture impossible", e);
        }
    }

    public static CsvTable parse(String text) {
        return read(new StringReader(text));
    }

    public static CsvTable read(Reader reader) {
        List<List<String>> records = new ArrayList<>();
        try {
            BufferedReader br = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
            StringBuilder all = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) > 0) {
                all.append(buf, 0, n);
            }
            parseRecords(all, records);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (records.isEmpty()) {
            throw new IllegalArgumentException("CSV vide (aucune ligne d'en-tête)");
        }
        List<String> header = new ArrayList<>();
        for (String c : records.get(0)) {
            header.add(c.strip().toLowerCase(Locale.ROOT));
        }
        List<List<String>> body = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> row = new ArrayList<>(records.get(i));
            while (row.size() < header.size()) {
                row.add("");
            }
            body.add(row);
        }
        return new CsvTable(header, body);
    }

    private static void parseRecords(CharSequence s, List<List<String>> out) {
        int i = 0;
        int len = s.length();
        if (len > 0 && s.charAt(0) == '﻿') {
            i = 1;
        }
        List<String> rec = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean any = false;
        for (; i < len; i++) {
            char ch = s.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && s.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"') {
                inQuotes = true;
                any = true;
            } else if (ch == ',') {
                rec.add(field.toString());
                field.setLength(0);
                any = true;
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && i + 1 < len && s.charAt(i + 1) == '\n') {
                    i++;
                }
                endRecord(rec, field, any, out);
                rec = new ArrayList<>();
                field.setLength(0);
                any = false;
            } else {
                field.append(ch);
                any = true;
            }
        }
        endRecord(rec, field, any, out);
    }

    private static void endRecord(List<String> rec, StringBuilder field, boolean any, List<List<String>> out) {
        if (!any && rec.isEmpty()) {
            return; // ligne vide
        }
        rec.add(field.toString());
        boolean blank = rec.stream().allMatch(String::isBlank);
        if (!blank) {
            out.add(rec);
        }
    }

    public List<String> columns() {
        return columns;
    }

    public boolean hasColumn(String name) {
        return columns.contains(name);
    }

    public int indexOf(String name) {
        return columns.indexOf(name);
    }

    public List<List<String>> rows() {
        return rows;
    }

    public int size() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** Valeur d'une cellule ("" si la ligne est plus courte). */
    public String get(int row, String column) {
        int c = indexOf(column);
        if (c < 0) {
            throw new IllegalArgumentException("Colonne absente : " + column);
        }
        List<String> r = rows.get(row);
        return c < r.size() ? r.get(c) : "";
    }
}
