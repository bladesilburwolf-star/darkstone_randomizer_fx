package com.serifsystemworks.darkstone.mtf;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Darkstone MTF archive reader (little-endian TOC).
 * Pack/write requires a real compressor — not implemented here yet.
 */
public final class MtfArchive implements Closeable {

    public static final class Entry {
        public final String path;
        public final long offset;
        public final int decompSize;
        public final boolean compressed;
        public final int storedSize;

        public Entry(String path, long offset, int decompSize, boolean compressed, int storedSize) {
            this.path = path;
            this.offset = offset;
            this.decompSize = decompSize;
            this.compressed = compressed;
            this.storedSize = storedSize;
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

        // Pass 1: read entire TOC sequentially (do not seek into payloads mid-TOC)
        String[] paths = new String[numFiles];
        long[] offsets = new long[numFiles];
        int[] decompSizes = new int[numFiles];
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
            decompSizes[i] = readIntLE();
        }

        // Pass 2: stored size from next offset; probe compression magic
        Integer[] order = new Integer[numFiles];
        for (int i = 0; i < numFiles; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Long.compare(offsets[a], offsets[b]));

        int[] storedSizes = new int[numFiles];
        boolean[] compressed = new boolean[numFiles];
        for (int oi = 0; oi < numFiles; oi++) {
            int idx = order[oi];
            long next = (oi + 1 < numFiles) ? offsets[order[oi + 1]] : fileLength;
            storedSizes[idx] = (int) Math.max(0, Math.min(Integer.MAX_VALUE, next - offsets[idx]));
            if (storedSizes[idx] >= 2 && offsets[idx] + 2 <= fileLength) {
                file.seek(offsets[idx]);
                int m1 = file.read() & 0xFF;
                int m2 = file.read() & 0xFF;
                compressed[idx] = MtfCompression.isCompressedMagic(m1, m2);
            }
        }

        for (int i = 0; i < numFiles; i++) {
            Entry e = new Entry(paths[i], offsets[i], decompSizes[i], compressed[i], storedSizes[i]);
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
}
