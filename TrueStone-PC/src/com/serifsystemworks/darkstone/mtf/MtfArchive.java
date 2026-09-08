package com.serifsystemworks.darkstone.mtf;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Darkstone MTF archive reader/writer (little-endian TOC).
 * <p>
 * Rebuild prefers <b>size-stable</b> storage so DATA.MTF stays boot-safe:
 * <ul>
 *   <li>Unchanged entries are copied byte-for-byte (true passthrough).</li>
 *   <li>Replacements keep the original compressed/uncompressed choice when possible.</li>
 *   <li>If recompress grows past the original stored size, fall back to uncompressed
 *       store (or the smaller of the two) and log the delta.</li>
 *   <li>{@link #rebuildSameSize} refuses any entry whose decompressed size changes —
 *       required for fixed-record binaries like MONSTERCLASS.DAT.</li>
 * </ul>
 */
public final class MtfArchive implements Closeable {

    public static final class Entry {
        public final String path;
        public final long offset;
        public final int decompSize;
        public final boolean compressed;
        public final int storedSize;
        /** MAGIC_AE or MAGIC_AF when compressed; 0 otherwise. */
        public final int magic1;
        /** File offset of the little-endian decompSize field in the TOC. */
        public final long tocDecompSizeOffset;

        public Entry(String path, long offset, int decompSize, boolean compressed, int storedSize) {
            this(path, offset, decompSize, compressed, storedSize, compressed ? MtfCompression.MAGIC_AE : 0, -1);
        }

        public Entry(String path, long offset, int decompSize, boolean compressed, int storedSize, int magic1) {
            this(path, offset, decompSize, compressed, storedSize, magic1, -1);
        }

        public Entry(String path, long offset, int decompSize, boolean compressed, int storedSize,
                     int magic1, long tocDecompSizeOffset) {
            this.path = path;
            this.offset = offset;
            this.decompSize = decompSize;
            this.compressed = compressed;
            this.storedSize = storedSize;
            this.magic1 = magic1;
            this.tocDecompSizeOffset = tocDecompSizeOffset;
        }
    }

    /** Result summary for UI / logs. */
    public static final class RebuildReport {
        public long originalBytes;
        public long rebuiltBytes;
        public int replaced;
        public int storedUncompressedFallback;
        public int decompSizeChanged;
        public final List<String> warnings = new ArrayList<>();

        @Override
        public String toString() {
            return "rebuilt " + rebuiltBytes + " bytes (was " + originalBytes + "), replaced="
                    + replaced + ", uncompressedFallback=" + storedUncompressedFallback
                    + ", decompSizeChanged=" + decompSizeChanged
                    + (warnings.isEmpty() ? "" : ", warnings=" + warnings.size());
        }
    }

    private RandomAccessFile file;
    private long fileLength;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Entry> entryMap = new HashMap<>();

    public void open(Path path) throws IOException {
        close();
        file = new RandomAccessFile(path.toFile(), "r");
        fileLength = file.length();
        entries.clear();
        entryMap.clear();

        int numFiles = readIntLE();
        if (numFiles < 0 || numFiles > 500_000) {
            throw new IOException("Unrealistic MTF entry count: " + numFiles);
        }

        String[] paths = new String[numFiles];
        long[] offsets = new long[numFiles];
        int[] decompSizes = new int[numFiles];
        long[] tocDecompOff = new long[numFiles];
        for (int i = 0; i < numFiles; i++) {
            int pathLen = readIntLE();
            if (pathLen <= 0 || pathLen > 1024) {
                throw new IOException("Bad pathLen at entry " + i + ": " + pathLen);
            }
            byte[] pathBytes = new byte[pathLen];
            file.readFully(pathBytes);
            int strLen = pathLen > 0 && pathBytes[pathLen - 1] == 0 ? pathLen - 1 : pathLen;
            paths[i] = new String(pathBytes, 0, strLen, StandardCharsets.ISO_8859_1);
            offsets[i] = readIntLE() & 0xFFFFFFFFL;
            tocDecompOff[i] = file.getFilePointer();
            decompSizes[i] = readIntLE();
        }

        Integer[] order = new Integer[numFiles];
        for (int i = 0; i < numFiles; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Long.compare(offsets[a], offsets[b]));

        int[] storedSizes = new int[numFiles];
        boolean[] compressed = new boolean[numFiles];
        int[] magic1 = new int[numFiles];
        for (int oi = 0; oi < numFiles; oi++) {
            int idx = order[oi];
            // Skip alias slots that share the previous offset (stored size 0 in TOC-order math)
            long next = fileLength;
            for (int j = oi + 1; j < numFiles; j++) {
                if (offsets[order[j]] > offsets[idx]) {
                    next = offsets[order[j]];
                    break;
                }
            }
            storedSizes[idx] = (int) Math.max(0, Math.min(Integer.MAX_VALUE, next - offsets[idx]));
            if (storedSizes[idx] >= 2 && offsets[idx] + 2 <= fileLength) {
                file.seek(offsets[idx]);
                int m1 = file.read() & 0xFF;
                int m2 = file.read() & 0xFF;
                compressed[idx] = MtfCompression.isCompressedMagic(m1, m2);
                if (compressed[idx]) {
                    magic1[idx] = m1;
                }
            }
        }
        // Propagate stored size / compression flags to aliases sharing the same offset
        for (int oi = 0; oi < numFiles; oi++) {
            int idx = order[oi];
            if (storedSizes[idx] > 0) continue;
            // find primary with same offset
            for (int j = 0; j < numFiles; j++) {
                if (offsets[j] == offsets[idx] && storedSizes[j] > 0) {
                    storedSizes[idx] = storedSizes[j];
                    compressed[idx] = compressed[j];
                    magic1[idx] = magic1[j];
                    break;
                }
            }
        }

        for (int i = 0; i < numFiles; i++) {
            Entry e = new Entry(paths[i], offsets[i], decompSizes[i], compressed[i], storedSizes[i], magic1[i], tocDecompOff[i]);
            entries.add(e);
            entryMap.put(paths[i].toUpperCase(Locale.ROOT), e);
        }
    }

    public List<String> listFiles() {
        List<String> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            out.add(e.path);
        }
        return out;
    }

    public List<Entry> getEntries() {
        return List.copyOf(entries);
    }

    public byte[] extract(String path) throws IOException {
        Entry entry = entryMap.get(path.toUpperCase(Locale.ROOT));
        if (entry == null) {
            throw new FileNotFoundException(path);
        }
        file.seek(entry.offset);
        int toRead = (int) Math.min(entry.storedSize, Math.max(0, fileLength - entry.offset));
        byte[] stored = new byte[toRead];
        file.readFully(stored);
        if (entry.compressed) {
            return MtfCompression.decompress(stored, entry.decompSize);
        }
        if (stored.length == entry.decompSize) {
            return stored;
        }
        byte[] raw = new byte[entry.decompSize];
        System.arraycopy(stored, 0, raw, 0, Math.min(stored.length, entry.decompSize));
        return raw;
    }

    public boolean containsFile(String path) {
        return entryMap.containsKey(path.toUpperCase(Locale.ROOT));
    }

    public int getFileSize(String path) {
        Entry e = entryMap.get(path.toUpperCase(Locale.ROOT));
        return e != null ? e.decompSize : -1;
    }

    public Entry getEntry(String path) {
        return entryMap.get(path.toUpperCase(Locale.ROOT));
    }

    public long getFileLength() {
        return fileLength;
    }

    /**
     * Rebuild with size-aware storage. Allows decompressed size changes but tries
     * not to balloon stored payloads.
     */
    public RebuildReport rebuild(Path outputPath, Map<String, byte[]> newDecompressedContent) throws IOException {
        return rebuild(outputPath, newDecompressedContent, false);
    }

    /**
     * Same as {@link #rebuild(Path, Map)} but refuses replacements whose
     * decompressed length differs from the original (safe for fixed-record DATs).
     */
    public RebuildReport rebuildSameSize(Path outputPath, Map<String, byte[]> newDecompressedContent) throws IOException {
        // Prefer in-place (exact retail layout) when every replacement compresses
        // into its original stored slot. Randomized TXT is often less compressible
        // and will not fit — fall back to the shared-offset-aware full rebuild,
        // still requiring identical decompressed sizes.
        try {
            return rebuildInPlace(outputPath, newDecompressedContent);
        } catch (IllegalArgumentException | IOException inPlaceFail) {
            RebuildReport report = rebuild(outputPath, newDecompressedContent, true);
            report.warnings.add(0, "In-place layout could not be preserved ("
                    + inPlaceFail.getMessage() + ") — used full rebuild with same decomp sizes.");
            return report;
        }
    }

    /**
     * Layout-preserving rebuild: copy the entire original archive, then overwrite
     * only the stored payload of each replaced entry (padded to the original
     * stored size). TOC offsets and total file size stay identical to retail —
     * this is what keeps GOG Darkstone booting after DATA.MTF edits.
     * <p>
     * Requires each replacement to have the same decompressed length as retail,
     * and a stored encoding that fits in the original storedSize.
     */
    public RebuildReport rebuildInPlace(Path outputPath, Map<String, byte[]> newDecompressedContent) throws IOException {
        if (file == null) {
            throw new IOException("Archive is not open — call open() before rebuildInPlace()");
        }
        Map<String, byte[]> replacements = new HashMap<>();
        for (Map.Entry<String, byte[]> e : newDecompressedContent.entrySet()) {
            replacements.put(e.getKey().toUpperCase(Locale.ROOT), e.getValue());
        }

        RebuildReport report = new RebuildReport();
        report.originalBytes = fileLength;

        // Full byte-for-byte copy of the retail archive
        file.seek(0);
        byte[] whole = new byte[(int) Math.min(fileLength, Integer.MAX_VALUE)];
        if (fileLength > Integer.MAX_VALUE) {
            throw new IOException("Archive too large for in-place buffer");
        }
        file.readFully(whole);

        for (Entry e : entries) {
            byte[] replacement = replacements.get(e.path.toUpperCase(Locale.ROOT));
            if (replacement == null) {
                continue;
            }
            report.replaced++;
            if (replacement.length != e.decompSize) {
                report.decompSizeChanged++;
                throw new IOException("In-place rebuild requires same decompressed size: "
                        + e.path + " retail " + e.decompSize + " vs " + replacement.length);
            }

            byte[] stored = encodeForInPlace(e, replacement, report);
            if (stored.length > e.storedSize) {
                throw new IOException("In-place rebuild: stored payload for " + e.path
                        + " is " + stored.length + " bytes but slot is only " + e.storedSize
                        + ". Cannot preserve layout.");
            }
            // Pad to exact original stored size so the next entry's offset stays valid
            byte[] padded = new byte[e.storedSize];
            System.arraycopy(stored, 0, padded, 0, stored.length);
            // remaining bytes stay 0 — game stops at compressed stream end / decomp size

            if (e.offset + e.storedSize > whole.length) {
                throw new IOException("Entry extends past file: " + e.path);
            }
            System.arraycopy(padded, 0, whole, (int) e.offset, e.storedSize);

            // decomp size unchanged — TOC field already correct
        }

        Files.write(outputPath, whole);
        report.rebuiltBytes = whole.length;
        return report;
    }

    private static byte[] encodeForInPlace(Entry original, byte[] raw, RebuildReport report) {
        if (!original.compressed) {
            if (raw.length > original.storedSize) {
                throw new IllegalArgumentException(original.path + " uncompressed raw exceeds slot");
            }
            return raw;
        }
        int magic = original.magic1 != 0 ? original.magic1 : MtfCompression.MAGIC_AE;
        byte[] compressed = MtfCompression.compress(raw, magic);
        if (compressed.length <= original.storedSize) {
            return compressed;
        }
        // Try the other magic in case it packs tighter
        int alt = magic == MtfCompression.MAGIC_AE ? MtfCompression.MAGIC_AF : MtfCompression.MAGIC_AE;
        byte[] altComp = MtfCompression.compress(raw, alt);
        if (altComp.length <= original.storedSize) {
            report.warnings.add(original.path + ": used alternate magic 0x"
                    + Integer.toHexString(alt) + " to fit slot");
            return altComp;
        }
        // Last resort: store uncompressed if it fits (and original allowed? still try)
        if (raw.length <= original.storedSize) {
            report.storedUncompressedFallback++;
            report.warnings.add(original.path + ": recompress "
                    + compressed.length + " > slot " + original.storedSize
                    + " — storing uncompressed in-place");
            return raw;
        }
        throw new IllegalArgumentException(original.path + ": cannot fit in stored slot "
                + original.storedSize + " (compress=" + compressed.length
                + ", raw=" + raw.length + ")");
    }


    public RebuildReport rebuild(Path outputPath, Map<String, byte[]> newDecompressedContent,
                                 boolean requireSameDecompSize) throws IOException {
        if (file == null) {
            throw new IOException("Archive is not open — call open() before rebuild()");
        }
        Map<String, byte[]> replacements = new HashMap<>();
        for (Map.Entry<String, byte[]> e : newDecompressedContent.entrySet()) {
            replacements.put(e.getKey().toUpperCase(Locale.ROOT), e.getValue());
        }

        RebuildReport report = new RebuildReport();
        report.originalBytes = fileLength;
        int numFiles = entries.size();

        // Retail DATA.MTF has many TOC aliases: several entries share one offset.
        // Group by original offset so we store each payload blob once.
        Map<Long, List<Integer>> byOffset = new LinkedHashMap<>();
        for (int i = 0; i < numFiles; i++) {
            byOffset.computeIfAbsent(entries.get(i).offset, k -> new ArrayList<>()).add(i);
        }

        // Resolve stored payload per unique original offset
        Map<Long, byte[]> payloadByOldOffset = new HashMap<>();
        Map<Long, Integer> decompByOldOffset = new HashMap<>();

        for (Map.Entry<Long, List<Integer>> group : byOffset.entrySet()) {
            long oldOff = group.getKey();
            List<Integer> idxs = group.getValue();
            Entry primary = entries.get(idxs.get(0));

            // Prefer a replacement for any alias in the group
            byte[] replacement = null;
            Entry replacedEntry = null;
            for (int idx : idxs) {
                Entry e = entries.get(idx);
                byte[] r = replacements.get(e.path.toUpperCase(Locale.ROOT));
                if (r != null) {
                    replacement = r;
                    replacedEntry = e;
                    report.replaced++;
                    if (r.length != e.decompSize) {
                        report.decompSizeChanged++;
                        String msg = e.path + ": decomp " + e.decompSize + " -> " + r.length;
                        report.warnings.add(msg);
                        if (requireSameDecompSize) {
                            throw new IOException("Same-size rebuild refused: " + msg);
                        }
                    }
                    break;
                }
            }

            if (replacement != null) {
                byte[] stored = encodeReplacement(replacedEntry, replacement, report);
                payloadByOldOffset.put(oldOff, stored);
                decompByOldOffset.put(oldOff, replacement.length);
            } else {
                // Shared retail blob: size = gap to next distinct offset
                int storedSize = primary.storedSize;
                if (storedSize <= 0) {
                    // recompute from sorted distinct offsets
                    storedSize = 0; // filled below if needed
                }
                // Use max storedSize among aliases (some may be 0 due to same-offset sort)
                for (int idx : idxs) {
                    storedSize = Math.max(storedSize, entries.get(idx).storedSize);
                }
                if (storedSize <= 0) {
                    // find next higher offset
                    long next = fileLength;
                    for (long off : byOffset.keySet()) {
                        if (off > oldOff && off < next) next = off;
                    }
                    storedSize = (int) Math.max(0, next - oldOff);
                }
                file.seek(oldOff);
                int toRead = (int) Math.min(storedSize, Math.max(0, fileLength - oldOff));
                byte[] stored = new byte[toRead];
                if (toRead > 0) {
                    file.readFully(stored);
                }
                payloadByOldOffset.put(oldOff, stored);
                decompByOldOffset.put(oldOff, primary.decompSize);
            }
        }

        // TOC paths
        byte[][] pathBytesArr = new byte[numFiles][];
        for (int i = 0; i < numFiles; i++) {
            pathBytesArr[i] = (entries.get(i).path + "\0").getBytes(StandardCharsets.ISO_8859_1);
        }

        long tocSize = 4L;
        for (int i = 0; i < numFiles; i++) {
            tocSize += 4L + pathBytesArr[i].length + 4L + 4L;
        }

        // Assign new offsets per unique old offset, packing in original offset order
        List<Long> orderedOld = new ArrayList<>(byOffset.keySet());
        Collections.sort(orderedOld);
        Map<Long, Long> newOffsetByOld = new HashMap<>();
        long cursor = tocSize;
        for (long oldOff : orderedOld) {
            newOffsetByOld.put(oldOff, cursor);
            cursor += payloadByOldOffset.get(oldOff).length;
        }
        report.rebuiltBytes = cursor;

        try (RandomAccessFile out = new RandomAccessFile(outputPath.toFile(), "rw")) {
            out.setLength(0);
            writeIntLE(out, numFiles);
            for (int i = 0; i < numFiles; i++) {
                Entry e = entries.get(i);
                writeIntLE(out, pathBytesArr[i].length);
                out.write(pathBytesArr[i]);
                writeIntLE(out, (int) (long) newOffsetByOld.get(e.offset));
                writeIntLE(out, decompByOldOffset.get(e.offset));
            }
            for (long oldOff : orderedOld) {
                out.write(payloadByOldOffset.get(oldOff));
            }
        }
        return report;
    }

    private static byte[] encodeReplacement(Entry original, byte[] raw, RebuildReport report) {
        // Original was stored raw — keep raw (game accepted this shape)
        if (!original.compressed) {
            return raw;
        }

        int magic = original.magic1 != 0 ? original.magic1 : MtfCompression.MAGIC_AE;
        byte[] compressed = MtfCompression.compress(raw, magic);

        // Recompress grew past original stored size: prefer the smaller encoding.
        // Expansion here is the usual reason DATA.MTF stops booting after a "simple" replace.
        if (compressed.length > original.storedSize) {
            if (raw.length <= compressed.length) {
                report.storedUncompressedFallback++;
                report.warnings.add(original.path + ": recompress " + compressed.length
                        + " > original stored " + original.storedSize
                        + " — storing uncompressed (" + raw.length + " bytes)");
                return raw;
            }
            report.warnings.add(original.path + ": recompress " + compressed.length
                    + " > original stored " + original.storedSize
                    + " (using compress anyway; archive will grow)");
        }
        return compressed;
    }

    private static String pct(int neu, int old) {
        if (old <= 0) return "n/a";
        double p = (neu - old) * 100.0 / old;
        return String.format(Locale.ROOT, "%+.1f%%", p);
    }

    @Override
    public void close() throws IOException {
        if (file != null) {
            file.close();
            file = null;
        }
    }

    private int readIntLE() throws IOException {
        byte[] b = new byte[4];
        file.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static void writeIntLE(RandomAccessFile out, int v) throws IOException {
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array());
    }
}
