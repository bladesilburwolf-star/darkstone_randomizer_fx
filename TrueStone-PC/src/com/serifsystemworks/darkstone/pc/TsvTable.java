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
 * <p>
 * Same-size friendly for DATA.MTF:
 * <ul>
 *   <li>After load, numeric columns are normalized to the <b>column max width</b>
 *       by borrowing trailing spaces from the key field (keys are fixed-width 32).</li>
 *   <li>That lets shuffled stats use the full digit range seen anywhere in the table
 *       (e.g. LMIN up to 4 digits) instead of being stuck at a 1-digit cell.</li>
 *   <li>Line length and total byte size stay identical to retail.</li>
 * </ul>
 */
public final class TsvTable {

    public static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    public final List<String> headers = new ArrayList<>();
    public final List<Row> rows = new ArrayList<>();
    private String headerLine = "";
    private String lineEnding = "\n";
    private int originalSize = -1;
    private final List<String> rawLines = new ArrayList<>();
    private final List<Integer> lineToRow = new ArrayList<>();
    /** Max cell width per column after normalization. */
    public final Map<String, Integer> columnMaxWidth = new LinkedHashMap<>();

    public static final class Row {
        public String keyRaw;
        public final Map<String, String> cols = new LinkedHashMap<>();
        public final Map<String, Integer> widths = new LinkedHashMap<>();
        public final Map<String, String> rawCells = new LinkedHashMap<>();

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

        /**
         * Write value into col, clamped to the (normalized) cell width.
         */
        public void setInt(String col, int value) {
            Integer w = widths.get(col);
            int v = value;
            if (w != null && w > 0) {
                v = clampToWidth(v, w);
            }
            String num = Integer.toString(v);
            String cell;
            if (w != null && w > 0) {
                cell = String.format(Locale.ROOT, "%" + w + "s", num);
                if (cell.length() > w) {
                    cell = num.substring(Math.max(0, num.length() - w));
                }
            } else {
                cell = num;
            }
            cols.put(col, num);
            rawCells.put(col, cell);
        }

        private static int clampToWidth(int value, int width) {
            if (width <= 0) return value;
            if (value >= 0) {
                int max = 0;
                for (int i = 0; i < width; i++) max = max * 10 + 9;
                if (value > max) return max;
                return value;
            }
            if (width == 1) return 0;
            int maxMag = 0;
            for (int i = 0; i < width - 1; i++) maxMag = maxMag * 10 + 9;
            if (-value > maxMag) return -maxMag;
            return value;
        }

        public String get(String col) {
            return cols.getOrDefault(col, "");
        }

        public void set(String col, String value) {
            cols.put(col, value == null ? "" : value.trim());
            Integer w = widths.get(col);
            String cell = value == null ? "" : value;
            if (w != null && w > 0) {
                if (cell.length() < w) {
                    cell = String.format(Locale.ROOT, "%-" + w + "s", cell);
                } else if (cell.length() > w) {
                    cell = cell.substring(0, w);
                }
            }
            rawCells.put(col, cell);
        }

        String cellForSave(String col) {
            if (rawCells.containsKey(col)) {
                return rawCells.get(col);
            }
            return cols.getOrDefault(col, "");
        }
    }

    public int getOriginalSize() {
        return originalSize;
    }

    public static TsvTable load(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        TsvTable t = new TsvTable();
        t.originalSize = bytes.length;

        String asLatin = new String(bytes, CHARSET);
        t.lineEnding = asLatin.contains("\r\n") ? "\r\n" : "\n";

        String[] lines;
        if ("\r\n".equals(t.lineEnding)) {
            lines = asLatin.split("\\r\\n", -1);
        } else {
            lines = asLatin.split("\\n", -1);
        }
        boolean endedWithNewline = lines.length > 0 && lines[lines.length - 1].isEmpty();
        if (endedWithNewline) {
            String[] trimmed = new String[lines.length - 1];
            System.arraycopy(lines, 0, trimmed, 0, trimmed.length);
            lines = trimmed;
        }

        if (lines.length == 0) {
            return t;
        }

        t.headerLine = lines[0];
        String[] hdr = lines[0].split("\t", -1);
        for (String h : hdr) {
            t.headers.add(h.trim());
        }

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            t.rawLines.add(line);
            if (line.isBlank()) {
                t.lineToRow.add(-1);
                continue;
            }
            String[] parts = line.split("\t", -1);
            Row row = new Row();
            row.keyRaw = parts.length > 0 ? parts[0] : "";
            for (int c = 0; c < t.headers.size(); c++) {
                String name = t.headers.get(c);
                String raw = c < parts.length ? parts[c] : "";
                row.rawCells.put(name, raw);
                row.widths.put(name, raw.length());
                if (c == 0) {
                    row.cols.put(name, row.key());
                } else {
                    row.cols.put(name, raw.trim());
                }
            }
            t.lineToRow.add(t.rows.size());
            t.rows.add(row);
        }

        t.normalizeColumnWidths();
        return t;
    }

    /**
     * Expand each numeric cell's stored width up to the column max, borrowing
     * trailing spaces from the key field so the line byte length is unchanged.
     */
    private void normalizeColumnWidths() {
        if (headers.isEmpty()) return;
        String keyHeader = headers.get(0);

        // column max over data rows
        for (String h : headers) {
            int mx = 0;
            for (Row r : rows) {
                Integer w = r.widths.get(h);
                if (w != null && w > mx) mx = w;
            }
            columnMaxWidth.put(h, mx);
        }

        for (Row r : rows) {
            int need = 0;
            for (int c = 1; c < headers.size(); c++) {
                String h = headers.get(c);
                int mx = columnMaxWidth.getOrDefault(h, 0);
                int cur = r.widths.getOrDefault(h, 0);
                if (mx > cur) need += (mx - cur);
            }
            if (need <= 0) {
                // still pad cells to max for consistent setInt
                for (int c = 1; c < headers.size(); c++) {
                    String h = headers.get(c);
                    int mx = columnMaxWidth.getOrDefault(h, 0);
                    int cur = r.widths.getOrDefault(h, 0);
                    if (mx > cur) {
                        applyWidth(r, h, mx);
                    }
                }
                continue;
            }

            String key = r.keyRaw != null ? r.keyRaw : "";
            String trimmed = key.trim();
            int slack = key.length() - trimmed.length(); // trailing (and leading) pad
            // only use trailing space after name
            int trail = 0;
            for (int i = key.length() - 1; i >= 0 && key.charAt(i) == ' '; i--) trail++;
            int steal = Math.min(need, trail);
            if (steal > 0) {
                r.keyRaw = key.substring(0, key.length() - steal);
                r.widths.put(keyHeader, r.keyRaw.length());
                r.rawCells.put(keyHeader, r.keyRaw);
            }

            int remaining = need - steal;
            for (int c = 1; c < headers.size(); c++) {
                String h = headers.get(c);
                int mx = columnMaxWidth.getOrDefault(h, 0);
                int cur = r.widths.getOrDefault(h, 0);
                if (mx <= cur) continue;
                int grow = mx - cur;
                if (remaining < 0) {
                    // shouldn't happen
                    applyWidth(r, h, cur);
                    continue;
                }
                // Prefer full column max when we had enough key slack overall;
                // if not enough for all, grow as much as we still can.
                if (steal >= need) {
                    applyWidth(r, h, mx);
                } else {
                    // partial: only grow columns while budget remains
                    int g = Math.min(grow, Math.max(0, remaining));
                    // Actually we already consumed steal from key for `need`.
                    // Assign growth in header order using stolen budget.
                    applyWidth(r, h, cur + g);
                    remaining -= g;
                }
            }
            // Second pass: if full steal covered need, all should be max
            if (steal >= need) {
                for (int c = 1; c < headers.size(); c++) {
                    String h = headers.get(c);
                    int mx = columnMaxWidth.getOrDefault(h, 0);
                    applyWidth(r, h, mx);
                }
            }
        }
    }

    private static void applyWidth(Row r, String h, int newW) {
        String cell = r.rawCells.getOrDefault(h, r.cols.getOrDefault(h, ""));
        String trimmed = cell.trim();
        if (newW <= 0) {
            r.widths.put(h, 0);
            r.rawCells.put(h, trimmed);
            return;
        }
        // right-align numbers, left-align text
        boolean numeric = true;
        try {
            if (!trimmed.isEmpty()) Double.parseDouble(trimmed);
            else numeric = true;
        } catch (Exception e) {
            numeric = false;
        }
        String formatted;
        if (numeric) {
            formatted = String.format(Locale.ROOT, "%" + newW + "s", trimmed);
        } else {
            formatted = String.format(Locale.ROOT, "%-" + newW + "s", trimmed);
        }
        if (formatted.length() > newW) {
            formatted = formatted.substring(0, newW);
        }
        r.widths.put(h, newW);
        r.rawCells.put(h, formatted);
        r.cols.put(h, trimmed);
    }

    public void save(Path path, boolean requireSameSize) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(headerLine);

        boolean sourceEndedWithNewline = !rawLines.isEmpty()
                && rawLines.get(rawLines.size() - 1).isEmpty();
        int limit = sourceEndedWithNewline ? rawLines.size() - 1 : rawLines.size();

        if (rawLines.isEmpty()) {
            for (Row row : rows) {
                sb.append(lineEnding);
                for (int c = 0; c < headers.size(); c++) {
                    if (c > 0) sb.append('\t');
                    String name = headers.get(c);
                    if (c == 0) {
                        sb.append(formatKey(row));
                    } else {
                        sb.append(row.cellForSave(name));
                    }
                }
            }
            sb.append(lineEnding);
        } else {
            for (int i = 0; i < limit; i++) {
                sb.append(lineEnding);
                int rowIdx = lineToRow.get(i);
                if (rowIdx < 0) {
                    sb.append(rawLines.get(i));
                    continue;
                }
                Row row = rows.get(rowIdx);
                for (int c = 0; c < headers.size(); c++) {
                    if (c > 0) sb.append('\t');
                    String name = headers.get(c);
                    if (c == 0) {
                        sb.append(formatKey(row));
                    } else {
                        sb.append(row.cellForSave(name));
                    }
                }
            }
            if (sourceEndedWithNewline || limit > 0) {
                sb.append(lineEnding);
            }
        }

        byte[] out = sb.toString().getBytes(CHARSET);
        if (requireSameSize && originalSize >= 0 && out.length != originalSize) {
            throw new IOException("TsvTable save size mismatch: original "
                    + originalSize + " bytes, wrote " + out.length
                    + " (" + String.format(Locale.ROOT, "%+.1f%%",
                    (out.length - originalSize) * 100.0 / Math.max(1, originalSize))
                    + "). A numeric field exceeded its original cell width — "
                    + "narrow ranges or use loose PCLASS files, do not reinsert into DATA.MTF.");
        }
        Files.write(path, out);
    }

    private static String formatKey(Row row) {
        if (row.keyRaw != null) {
            return row.keyRaw;
        }
        return row.key();
    }
}
