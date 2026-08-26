package com.serifsystemworks.psxdisc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Locate ISO9660 files inside a raw PSX track image and replace contents
 * when the new payload fits the existing extent (pad with zeros if smaller).
 * <p>
 * Supports 2048-byte logical sectors in MODE1/2048 images and MODE2/2352
 * form1 user-data at offset +24 within each sector.
 */
public final class Iso9660Patcher {

    public static final class IsoFile {
        public String path;          // e.g. DATA1.PSM or PATH/FILE.EXT
        public long extentLba;       // logical block
        public int dataLength;       // size in bytes
        public long dirRecordOffset; // absolute byte offset of directory record in image
        public int nameLen;
    }

    private final Path binPath;
    private final int rawSectorSize;   // 2048 or 2352
    private final int userDataOffset;  // 0 for 2048, 24 for MODE2/2352 form1
    private final int userDataSize;    // 2048

    public Iso9660Patcher(Path binPath, int rawSectorSize) {
        this.binPath = binPath;
        if (rawSectorSize == 2352) {
            this.rawSectorSize = 2352;
            this.userDataOffset = 24; // sync+header+subheader
            this.userDataSize = 2048;
        } else {
            this.rawSectorSize = 2048;
            this.userDataOffset = 0;
            this.userDataSize = 2048;
        }
    }

    public List<IsoFile> listFiles() throws IOException {
        List<IsoFile> out = new ArrayList<>();
        try (FileChannel ch = FileChannel.open(binPath, StandardOpenOption.READ)) {
            byte[] pvd = readLogicalSector(ch, 16);
            if (pvd[0] != 1 || pvd[1] != 'C' || pvd[2] != 'D') {
                throw new IOException("No ISO9660 PVD at LBA 16 (is this the data track?)");
            }
            int rootLba = le32(pvd, 158);
            int rootLen = le32(pvd, 166);
            walkDir(ch, rootLba, rootLen, "", out);
        }
        return out;
    }

    /**
     * Replace multiple files by ISO path (case-insensitive, with or without ;1).
     * New size must be &lt;= existing extent; smaller files are zero-padded.
     *
     * @return number of files patched
     */
    public int replaceAll(Map<String, byte[]> replacements) throws IOException {
        List<IsoFile> files = listFiles();
        Map<String, IsoFile> byName = new HashMap<>();
        for (IsoFile f : files) {
            byName.put(norm(f.path), f);
            String base = f.path;
            int slash = base.lastIndexOf('/');
            if (slash >= 0) {
                base = base.substring(slash + 1);
            }
            byName.putIfAbsent(norm(base), f);
        }

        int patched = 0;
        try (FileChannel ch = FileChannel.open(binPath,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            for (Map.Entry<String, byte[]> e : replacements.entrySet()) {
                IsoFile target = byName.get(norm(e.getKey()));
                if (target == null) {
                    System.err.println("[!] Not on disc: " + e.getKey());
                    continue;
                }
                byte[] data = e.getValue();
                if (data.length > target.dataLength) {
                    System.err.println("[!] Too large (" + data.length + " > " + target.dataLength
                            + "): " + e.getKey() + " — skip (same-size/smaller only)");
                    continue;
                }
                byte[] payload = new byte[target.dataLength];
                System.arraycopy(data, 0, payload, 0, data.length);
                // rest already zero
                writeExtent(ch, target.extentLba, payload);
                patched++;
                System.out.println("[+] Patched " + target.path + " (" + data.length
                        + " bytes into " + target.dataLength + " extent)");
            }
        }
        return patched;
    }

    private void walkDir(FileChannel ch, int lba, int length, String prefix, List<IsoFile> out)
            throws IOException {
        int sectors = (length + userDataSize - 1) / userDataSize;
        ByteBuffer dir = ByteBuffer.allocate(sectors * userDataSize);
        for (int i = 0; i < sectors; i++) {
            dir.put(readLogicalSector(ch, lba + i));
        }
        dir.flip();
        byte[] all = new byte[dir.remaining()];
        dir.get(all);

        int pos = 0;
        while (pos < all.length) {
            int recLen = all[pos] & 0xFF;
            if (recLen == 0) {
                // advance to next sector boundary in buffer
                int sectorOff = (pos / userDataSize) * userDataSize;
                pos = sectorOff + userDataSize;
                continue;
            }
            if (pos + recLen > all.length) {
                break;
            }
            int extent = le32(all, pos + 2);
            int dataLen = le32(all, pos + 10);
            int flags = all[pos + 25] & 0xFF;
            int nameLen = all[pos + 32] & 0xFF;
            String name = new String(all, pos + 33, nameLen, StandardCharsets.ISO_8859_1);
            if (!name.equals("\u0000") && !name.equals("\u0001")) {
                String clean = name;
                int semi = clean.indexOf(';');
                if (semi >= 0) {
                    clean = clean.substring(0, semi);
                }
                String full = prefix.isEmpty() ? clean : prefix + "/" + clean;
                if ((flags & 0x02) != 0) {
                    // directory — skip . and recurse
                    if (!clean.isEmpty()) {
                        walkDir(ch, extent, dataLen, full, out);
                    }
                } else {
                    IsoFile f = new IsoFile();
                    f.path = full;
                    f.extentLba = extent & 0xFFFFFFFFL;
                    f.dataLength = dataLen;
                    out.add(f);
                }
            }
            pos += recLen;
        }
    }

    private void writeExtent(FileChannel ch, long startLba, byte[] data) throws IOException {
        int remaining = data.length;
        int off = 0;
        long lba = startLba;
        while (remaining > 0) {
            int chunk = Math.min(userDataSize, remaining);
            long rawOff = lba * (long) rawSectorSize + userDataOffset;
            ByteBuffer buf = ByteBuffer.wrap(data, off, chunk);
            ch.position(rawOff);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
            // if chunk < 2048, zero-fill rest of user data
            if (chunk < userDataSize) {
                byte[] pad = new byte[userDataSize - chunk];
                ch.write(ByteBuffer.wrap(pad));
            }
            off += chunk;
            remaining -= chunk;
            lba++;
        }
    }

    private byte[] readLogicalSector(FileChannel ch, long lba) throws IOException {
        long rawOff = lba * (long) rawSectorSize + userDataOffset;
        ByteBuffer buf = ByteBuffer.allocate(userDataSize);
        ch.position(rawOff);
        int n = ch.read(buf);
        if (n < userDataSize) {
            throw new IOException("Short read at LBA " + lba);
        }
        return buf.array();
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xFF)
                | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16)
                | ((b[off + 3] & 0xFF) << 24);
    }

    private static String norm(String s) {
        return s.replace('\\', '/').toUpperCase(Locale.ROOT).replace(";1", "");
    }

    /** Convenience: map filenames in a folder to ISO basenames. */
    public static Map<String, byte[]> loadFolder(Path folder) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        try (var stream = Files.list(folder)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                map.put(p.getFileName().toString(), Files.readAllBytes(p));
            }
        }
        return map;
    }
}
