package com.serifsystemworks.darkstone.pc;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tab-separated Darkstone PC export table (MONSTER.TXT, OBJECT.TXT, …).
 * Preserves header line and key column padding where possible.
 */
public final class TsvTable {

    public static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    public final List<String> headers = new ArrayList<>();
    public final List<Row> rows = new ArrayList<>();
    private String headerLine = "";

    public static final class Row {
        public String keyRaw;          // original key cell (may be space-padded)
        public final Map<String, String> cols = new LinkedHashMap<>();

        public String key() {
            return keyRaw == null ? "" : keyRaw.trim();
        }

        public int getInt(String col, int fallback) {
            String v = cols.get(col);
            if (v == null) return fallback;
            try {
                return (int) Math.round(Double.parseDouble(v.trim()));
            } catch (Exception e) {
                return fallback;
            }
        }

        public void setInt(String col, int value) {
            cols.put(col, Integer.toString(value));
        }

        public String get(String col) {
            return cols.getOrDefault(col, "");
        }

        public void set(String col, String value) {
            cols.put(col, value);
        }
    }

    public static TsvTable load(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, CHARSET);
        TsvTable t = new TsvTable();
        if (lines.isEmpty()) return t;
        t.headerLine = lines.get(0);
        String[] hdr = lines.get(0).split("\t", -1);
        for (String h : hdr) {
            t.headers.add(h.trim());
        }
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] parts = line.split("\t", -1);
            Row row = new Row();
            row.keyRaw = parts.length > 0 ? parts[0] : "";
            for (int c = 0; c < t.headers.size(); c++) {
                String name = t.headers.get(c);
                String val = c < parts.length ? parts[c].trim() : "";
                if (c == 0) {
                    row.cols.put(name, row.key());
                } else {
                    row.cols.put(name, val);
                }
            }
            t.rows.add(row);
        }
        return t;
    }

    public void save(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(headerLine).append('\n');
        int keyWidth = 32;
        for (Row row : rows) {
            if (row.keyRaw != null && row.keyRaw.length() > keyWidth) {
                keyWidth = row.keyRaw.length();
            }
        }
        for (Row row : rows) {
            for (int c = 0; c < headers.size(); c++) {
                if (c > 0) sb.append('\t');
                String name = headers.get(c);
                if (c == 0) {
                    String key = row.key();
                    // pad like original exports
                    String padded = String.format(Locale.ROOT, "%-" + keyWidth + "s", key);
                    sb.append(padded);
                } else {
                    String val = row.cols.getOrDefault(name, "");
                    // numeric columns: right-ish pad with spaces for readability
                    if (isNumericHeader(name) && !val.isEmpty()) {
                        sb.append(String.format(Locale.ROOT, "%6s", val));
                    } else {
                        sb.append(val);
                    }
                }
            }
            sb.append('\n');
        }
        Files.writeString(path, sb.toString(), CHARSET);
    }

    private static boolean isNumericHeader(String h) {
        String u = h.toUpperCase(Locale.ROOT);
        return u.equals("LEVEL") || u.equals("LMIN") || u.equals("LMAX")
                || u.equals("DMIN") || u.equals("DMAX") || u.equals("AC")
                || u.equals("TOHIT") || u.equals("SPEED") || u.equals("ATTFRE")
                || u.equals("CHAAPP") || u.equals("CNTAPP") || u.equals("ATTSPD")
                || u.equals("STR") || u.equals("DEX") || u.equals("MAG") || u.equals("VIT")
                || u.equals("DUR");
    }
}
