package com.serifsystemworks.darkstone.engine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Darkstone PSX PSM container.
 *
 * <p>On-disk layout (little-endian):
 * <pre>
 *   uint32  tableWordCount          // number of following 32-bit words
 *   uint32  words[tableWordCount]   // interleaved (id/hash, offset) pairs
 *                                   // even indices = asset id/hash
 *                                   // odd  indices = file offset
 *   ... blob data at the recorded offsets ...
 * </pre>
 *
 * <p>Only odd-index words that fall inside the file are real blob starts.
 * Even-index words must be preserved exactly — the old repack that set
 * {@code count = number of .bin files} dropped every id/hash slot and black-screened
 * the game before logos.
 *
 * <p>Preferred edit path is {@link #patchBlobInPlace}: same-size rewrite of a blob
 * inside the original PSM so the header never changes.
 */
public final class PsmArchive {

    public static final int MAX_ENTRIES = 25_000;
    public static final int MAX_BLOB_BYTES = 20_000_000;
    public static final long NESTED_MIN_BYTES = 5_000;
    public static final String META_FILE = "_psm_meta.txt";
    public static final String SOURCE_COPY = "_source.psm";

    private PsmArchive() {}

    /** One extractable blob (valid odd-index offset). */
    public static final class Entry {
        public final int tableIndex;
        public final int idWordIndex;
        public final long idHash;
        public final int offset;
        public final int length;

        Entry(int tableIndex, int idWordIndex, long idHash, int offset, int length) {
            this.tableIndex = tableIndex;
            this.idWordIndex = idWordIndex;
            this.idHash = idHash;
            this.offset = offset;
            this.length = length;
        }
    }

    /** Parsed archive: full TOC + entry list. */
    public static final class Parsed {
        public final int tableWordCount;
        public final int[] words;
        public final List<Entry> entries;
        public final int fileSize;

        Parsed(int tableWordCount, int[] words, List<Entry> entries, int fileSize) {
            this.tableWordCount = tableWordCount;
            this.words = words;
            this.entries = entries;
            this.fileSize = fileSize;
        }
    }

    public static Parsed parse(byte[] data) {
        if (data == null || data.length < 8) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int count = bb.getInt(0);
        if (count <= 0 || count > MAX_ENTRIES) {
            int maxPossible = (data.length - 4) / 4;
            if (count > maxPossible) {
                count = maxPossible;
            }
            if (count <= 0 || count > MAX_ENTRIES) {
                return null;
            }
        }
        if (4L + count * 4L > data.length) {
            count = (data.length - 4) / 4;
        }

        int[] words = new int[count];
        for (int i = 0; i < count; i++) {
            words[i] = bb.getInt(4 + i * 4);
        }

        List<int[]> valid = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int off = words[i];
            if ((i & 1) == 1 && off > 0 && off < data.length) {
                valid.add(new int[]{i, off});
            }
        }

        int[] sorted = valid.stream().mapToInt(a -> a[1]).distinct().sorted().toArray();

        List<Entry> entries = new ArrayList<>();
        for (int[] pair : valid) {
            int idx = pair[0];
            int start = pair[1];
            int pos = Arrays.binarySearch(sorted, start);
            if (pos < 0) {
                continue;
            }
            int end = (pos + 1 < sorted.length) ? sorted[pos + 1] : data.length;
            int len = end - start;
            if (len <= 0 || len > MAX_BLOB_BYTES) {
                continue;
            }
            int idIdx = idx - 1;
            long idHash = (idIdx >= 0) ? (words[idIdx] & 0xFFFFFFFFL) : 0L;
            entries.add(new Entry(idx, idIdx, idHash, start, len));
        }

        return new Parsed(count, words, entries, data.length);
    }

    public static Parsed parse(Path psmFile) throws IOException {
        return parse(Files.readAllBytes(psmFile));
    }

    public static boolean unpack(Path psmFile, Path outDir) {
        return unpack(psmFile, outDir, LogSink.NULL);
    }

    public static boolean unpack(Path psmFile, Path outDir, LogSink log) {
        try {
            byte[] data = Files.readAllBytes(psmFile);
            Parsed parsed = parse(data);
            if (parsed == null || parsed.entries.isEmpty()) {
                return false;
            }

            Files.createDirectories(outDir);

            Path sourceCopy = outDir.resolve(SOURCE_COPY);
            Files.copy(psmFile, sourceCopy, StandardCopyOption.REPLACE_EXISTING);

            for (Entry e : parsed.entries) {
                Path target = outDir.resolve(String.format("%04d.bin", e.tableIndex));
                Files.write(target, Arrays.copyOfRange(data, e.offset, e.offset + e.length));
            }

            writeMeta(outDir, psmFile, parsed);
            return true;
        } catch (Exception e) {
            log.log("FAIL " + psmFile.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    private static void writeMeta(Path outDir, Path psmFile, Parsed parsed) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Darkstone PSM meta — do not edit by hand\n");
        sb.append("source=").append(psmFile.getFileName().toString()).append('\n');
        sb.append("fileSize=").append(parsed.fileSize).append('\n');
        sb.append("tableWordCount=").append(parsed.tableWordCount).append('\n');
        sb.append("entryCount=").append(parsed.entries.size()).append('\n');
        sb.append("# index,idHash,offset,length\n");
        for (Entry e : parsed.entries) {
            sb.append(e.tableIndex).append(',')
                    .append(Long.toUnsignedString(e.idHash)).append(',')
                    .append(e.offset).append(',')
                    .append(e.length).append('\n');
        }
        sb.append("# words\n");
        for (int i = 0; i < parsed.words.length; i++) {
            sb.append(i).append('=').append(Integer.toUnsignedString(parsed.words[i])).append('\n');
        }
        Files.writeString(outDir.resolve(META_FILE), sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Same-size rewrite of a blob inside {@code _source.psm}. Preferred randomization path.
     */
    public static boolean patchBlobInPlace(Path binFile, byte[] newBytes, LogSink log) {
        try {
            Path outDir = binFile.getParent();
            if (outDir == null) {
                return false;
            }
            Path source = outDir.resolve(SOURCE_COPY);
            Path metaPath = outDir.resolve(META_FILE);
            if (!Files.isRegularFile(source) || !Files.isRegularFile(metaPath)) {
                log.log("[!] Missing " + SOURCE_COPY + " or " + META_FILE + " next to " + binFile.getFileName());
                return false;
            }

            int tableIndex = Integer.parseInt(binFile.getFileName().toString().replaceAll("[^0-9]", ""));
            MetaEntry me = readMetaEntry(metaPath, tableIndex);
            if (me == null) {
                log.log("[!] No meta entry for index " + tableIndex);
                return false;
            }
            if (newBytes.length != me.length) {
                log.log("[!] Size change not supported in-place for " + binFile.getFileName()
                        + " (was " + me.length + ", now " + newBytes.length + ")");
                return false;
            }

            byte[] psm = Files.readAllBytes(source);
            if (me.offset + me.length > psm.length) {
                log.log("[!] Meta offset out of range for " + binFile.getFileName());
                return false;
            }
            System.arraycopy(newBytes, 0, psm, me.offset, newBytes.length);
            Files.write(source, psm);
            Files.write(binFile, newBytes);
            return true;
        } catch (Exception e) {
            log.log("[!] patchBlobInPlace failed: " + e.getMessage());
            return false;
        }
    }

    /** Copy each folder's patched {@code _source.psm} back onto the CD tree. */
    public static int installPatchedSources(Path outputRoot, Path cdRoot, LogSink log) throws IOException {
        log.log("=== Installing in-place patched PSM sources to CD folder ===");
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(outputRoot)) {
            sources = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> SOURCE_COPY.equals(p.getFileName().toString()))
                    .collect(Collectors.toList());
        }

        int copied = 0;
        for (Path src : sources) {
            Path folder = src.getParent();
            String folderName = folder.getFileName().toString();
            if (!folderName.endsWith("_unpacked")) {
                continue;
            }
            String psmName = folderName.substring(0, folderName.length() - "_unpacked".length()) + ".PSM";
            Path dest = resolveCdDestination(cdRoot, psmName);
            if (dest == null) {
                log.log("Skip (cannot map to CD): " + folderName);
                continue;
            }

            Path backup = dest.resolveSibling(psmName + ".bak");
            if (Files.exists(dest) && !Files.exists(backup)) {
                Files.copy(dest, backup, StandardCopyOption.REPLACE_EXISTING);
                log.log("Backup: " + backup.getFileName());
            }
            Files.createDirectories(dest.getParent());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            copied++;
            log.log("INSTALLED: " + cdRoot.relativize(dest));
        }
        log.log("Copied " + copied + " patched archives.");
        return copied;
    }

    private static Path resolveCdDestination(Path cdRoot, String psmName) {
        try (Stream<Path> walk = Files.walk(cdRoot, 6)) {
            List<Path> hits = walk
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(psmName))
                    .collect(Collectors.toList());
            if (!hits.isEmpty()) {
                return hits.get(0);
            }
        } catch (IOException ignored) {
        }
        return cdRoot.resolve(psmName);
    }

    private static final class MetaEntry {
        final int offset;
        final int length;

        MetaEntry(int offset, int length) {
            this.offset = offset;
            this.length = length;
        }
    }

    private static MetaEntry readMetaEntry(Path metaPath, int tableIndex) throws IOException {
        for (String line : Files.readAllLines(metaPath, StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            if (!line.contains(",")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 4) {
                continue;
            }
            try {
                int idx = Integer.parseInt(parts[0].trim());
                if (idx == tableIndex) {
                    int off = Integer.parseInt(parts[2].trim());
                    int len = Integer.parseInt(parts[3].trim());
                    return new MetaEntry(off, len);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * Rebuild from meta + bins. Refuses size-changing edits (returns false).
     * When {@code _source.psm} exists and sizes match, copies the patched source.
     */
    public static boolean repack(Path srcDir, Path outputPsm) {
        try {
            Path metaPath = srcDir.resolve(META_FILE);
            Path sourceCopy = srcDir.resolve(SOURCE_COPY);
            if (!Files.isRegularFile(metaPath) || !Files.isRegularFile(sourceCopy)) {
                return false;
            }
            if (!binsMatchMeta(srcDir, metaPath)) {
                return false;
            }

            Map<Integer, Path> bins = listBins(srcDir);
            Map<Integer, int[]> entryMeta = readAllMetaEntries(metaPath);
            byte[] out = Files.readAllBytes(sourceCopy);
            for (Map.Entry<Integer, int[]> e : entryMeta.entrySet()) {
                Path bin = bins.get(e.getKey());
                if (bin == null) {
                    return false;
                }
                byte[] blob = Files.readAllBytes(bin);
                int off = e.getValue()[0];
                if (off + blob.length > out.length) {
                    return false;
                }
                System.arraycopy(blob, 0, out, off, blob.length);
            }
            Files.createDirectories(outputPsm.getParent());
            Files.write(outputPsm, out);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<Integer, int[]> readAllMetaEntries(Path metaPath) throws IOException {
        Map<Integer, int[]> map = new LinkedHashMap<>();
        for (String line : Files.readAllLines(metaPath, StandardCharsets.UTF_8)) {
            if (!line.contains(",") || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split(",");
            if (p.length < 4) {
                continue;
            }
            try {
                int idx = Integer.parseInt(p[0].trim());
                int off = Integer.parseInt(p[2].trim());
                int len = Integer.parseInt(p[3].trim());
                map.put(idx, new int[]{off, len});
            } catch (NumberFormatException ignored) {
            }
        }
        return map;
    }

    private static boolean binsMatchMeta(Path srcDir, Path metaPath) throws IOException {
        Map<Integer, Path> bins = listBins(srcDir);
        for (String line : Files.readAllLines(metaPath, StandardCharsets.UTF_8)) {
            if (!line.contains(",") || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split(",");
            if (p.length < 4) {
                continue;
            }
            try {
                int idx = Integer.parseInt(p[0].trim());
                int len = Integer.parseInt(p[3].trim());
                Path bin = bins.get(idx);
                if (bin == null || Files.size(bin) != len) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return true;
    }

    private static Map<Integer, Path> listBins(Path srcDir) throws IOException {
        Map<Integer, Path> map = new LinkedHashMap<>();
        try (Stream<Path> list = Files.list(srcDir)) {
            list.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bin"))
                    .sorted(binIndexOrder())
                    .forEach(f -> {
                        try {
                            int n = Integer.parseInt(f.getFileName().toString().replaceAll("[^0-9]", ""));
                            map.put(n, f);
                        } catch (Exception ignored) {
                        }
                    });
        }
        return map;
    }

    public static int unpackTree(Path cdRoot, Path outputRoot, boolean skipExisting, LogSink log) throws IOException {
        if (cdRoot == null || outputRoot == null) {
            throw new IllegalArgumentException("CD folder and output directory are required.");
        }

        long t0 = System.currentTimeMillis();
        log.log("=== Starting batch unpack (metadata-preserving) ===");
        Files.createDirectories(outputRoot);

        List<Path> psms;
        try (Stream<Path> walk = Files.walk(cdRoot)) {
            psms = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toUpperCase(Locale.ROOT).endsWith(".PSM"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        log.log("Found " + psms.size() + " PSM files.");
        int unpacked = 0;
        int skipped = 0;
        int failed = 0;

        for (Path p : psms) {
            Path relative = cdRoot.relativize(p);
            String folderName = stripExtension(relative.getFileName().toString()) + "_unpacked";
            Path parent = relative.getParent();
            Path out = (parent == null)
                    ? outputRoot.resolve(folderName)
                    : outputRoot.resolve(parent).resolve(folderName);

            if (skipExisting && directoryHasFiles(out)) {
                log.log("Skip: " + describe(out, outputRoot));
                skipped++;
                continue;
            }

            long startMs = System.currentTimeMillis();
            boolean ok = unpack(p, out, log);
            long elapsed = System.currentTimeMillis() - startMs;
            if (ok) {
                unpacked++;
                log.log("[OK] " + relative + " (" + elapsed + " ms)");
            } else {
                failed++;
                log.log("[FAIL] " + relative + " (" + elapsed + " ms)");
            }
        }

        log.log("--- Checking nested archives (>" + NESTED_MIN_BYTES + " bytes) ---");
        List<Path> candidates;
        try (Stream<Path> walk = Files.walk(outputRoot)) {
            candidates = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bin"))
                    .filter(p -> {
                        try {
                            return Files.size(p) > NESTED_MIN_BYTES;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        }

        int nestedExtracted = 0;
        for (Path bin : candidates) {
            Path nestedOut = Path.of(bin.toString() + "_unpacked");
            if (skipExisting && Files.exists(nestedOut) && directoryHasFiles(nestedOut)) {
                continue;
            }
            try {
                byte[] head = Files.readAllBytes(bin);
                Parsed nested = parse(head);
                if (nested != null && nested.entries.size() >= 2) {
                    if (unpack(bin, nestedOut, LogSink.NULL)) {
                        nestedExtracted++;
                    }
                } else if (Files.exists(nestedOut)) {
                    deleteEmptyDir(nestedOut);
                }
            } catch (Exception e) {
                if (Files.exists(nestedOut)) {
                    try {
                        deleteEmptyDir(nestedOut);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        log.log("Nested archives unpacked: " + nestedExtracted);

        long totalTime = System.currentTimeMillis() - t0;
        log.log("=== Unpack complete in " + (totalTime / 1000.0) + " seconds ===");
        log.log("Unpacked=" + unpacked + " skipped=" + skipped + " failed=" + failed);
        return unpacked;
    }

    public static int unpackSelected(List<Path> psmFiles, Path cdRoot, Path outputRoot, LogSink log) {
        int okCount = 0;
        for (Path p : psmFiles) {
            Path relative = (cdRoot != null && p.startsWith(cdRoot)) ? cdRoot.relativize(p) : p.getFileName();
            String folderName = stripExtension(p.getFileName().toString()) + "_unpacked";
            Path parent = relative.getParent();
            Path out = (parent == null)
                    ? outputRoot.resolve(folderName)
                    : outputRoot.resolve(parent).resolve(folderName);

            log.log("Unpacking: " + p.getFileName() + " -> " + out);
            if (unpack(p, out, log)) {
                okCount++;
                log.log("Done.");
            } else {
                log.log("[FAIL] " + p.getFileName());
            }
        }
        return okCount;
    }

    public static int repackAll(Path outputRoot, LogSink log) throws IOException {
        log.log("=== Repacking (metadata-preserving) ===");
        List<Path> folders;
        try (Stream<Path> walk = Files.walk(outputRoot, 8)) {
            folders = walk
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().endsWith("_unpacked"))
                    .filter(p -> Files.isRegularFile(p.resolve(META_FILE)))
                    .collect(Collectors.toList());
        }

        int packed = 0;
        for (Path src : folders) {
            String base = src.getFileName().toString();
            if (base.toLowerCase(java.util.Locale.ROOT).endsWith("_unpacked")) {
                base = base.substring(0, base.length() - "_unpacked".length());
            }
            // Clean .PSM name (no .repacked suffix)
            String targetName = base.toUpperCase(java.util.Locale.ROOT).endsWith(".PSM")
                    ? base : base + ".PSM";
            Path dest = src.getParent().resolve(targetName);
            if (repack(src, dest)) {
                packed++;
                log.log("REPACKED: " + outputRoot.relativize(dest));
            } else {
                log.log("Failed to repack " + src.getFileName()
                        + " (need matching sizes + _source.psm / meta)");
            }
        }
        log.log("=== Repack finished (" + packed + " archives) ===");
        return packed;
    }

    public static Comparator<Path> binIndexOrder() {
        return (a, b) -> {
            try {
                int na = Integer.parseInt(a.getFileName().toString().replaceAll("[^0-9]", ""));
                int nb = Integer.parseInt(b.getFileName().toString().replaceAll("[^0-9]", ""));
                return Integer.compare(na, nb);
            } catch (Exception e) {
                return a.compareTo(b);
            }
        };
    }

    public static boolean directoryHasFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteEmptyDir(Path dir) throws IOException {
        if (Files.isDirectory(dir) && !directoryHasFiles(dir)) {
            Files.deleteIfExists(dir);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String describe(Path out, Path outputRoot) {
        try {
            return outputRoot.relativize(out).toString();
        } catch (Exception e) {
            return out.toString();
        }
    }
}
