package com.serifsystemworks.darkstone.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Self-check for PSM unpack / in-place patch / repack.
 * Run: java -cp out com.serifsystemworks.darkstone.engine.PsmRoundtripTest [TOWN.PSM]
 */
public final class PsmRoundtripTest {
    public static void main(String[] args) throws Exception {
        Path sample = args.length > 0
                ? Path.of(args[0])
                : Path.of("PSM/TOWN.PSM");
        if (!Files.isRegularFile(sample)) {
            // Synthetic mini archive: count=4 words = 2 pairs (id, offset)
            Path tmp = Files.createTempDirectory("darkstone-psm-test");
            byte[] blob0 = "HELLO-PSM-0".getBytes();
            byte[] blob1 = new byte[64];
            for (int i = 0; i < blob1.length; i++) {
                blob1[i] = (byte) (i & 0x0F);
            }
            int count = 4; // id0, off0, id1, off1
            int header = 4 + count * 4;
            int off0 = header;
            int off1 = off0 + blob0.length;
            int total = off1 + blob1.length;
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(total).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            bb.putInt(count);
            bb.putInt(0x11111111); // id0
            bb.putInt(off0);
            bb.putInt(0x22222222); // id1
            bb.putInt(off1);
            bb.put(blob0);
            bb.put(blob1);
            sample = tmp.resolve("TEST.PSM");
            Files.write(sample, bb.array());
            System.out.println("Using synthetic PSM at " + sample);
        } else {
            System.out.println("Using real PSM: " + sample);
        }

        byte[] original = Files.readAllBytes(sample);
        Path unpacked = Files.createTempDirectory("psm-unpacked");
        check(PsmArchive.unpack(sample, unpacked), "unpack failed");

        PsmArchive.Parsed parsed = PsmArchive.parse(original);
        check(parsed != null, "parse null");
        check(!parsed.entries.isEmpty(), "no entries");
        System.out.println("  tableWordCount=" + parsed.tableWordCount + " entries=" + parsed.entries.size());

        // Zero-edit repack must be byte-identical
        Path repacked = unpacked.resolveSibling("roundtrip.PSM");
        check(PsmArchive.repack(unpacked, repacked), "repack failed");
        byte[] after = Files.readAllBytes(repacked);
        check(Arrays.equals(original, after), "roundtrip bytes differ (len " + original.length + " vs " + after.length + ")");
        System.out.println("  zero-edit roundtrip: IDENTICAL (" + original.length + " bytes)");

        // In-place patch of first blob with same bytes must keep identity
        EntryInfo first = firstBin(unpacked);
        check(first != null, "no bin");
        byte[] blob = Files.readAllBytes(first.path);
        check(PsmArchive.patchBlobInPlace(first.path, blob, System.out::println), "patch identity failed");
        byte[] sourceAfter = Files.readAllBytes(unpacked.resolve(PsmArchive.SOURCE_COPY));
        check(Arrays.equals(original, sourceAfter), "in-place identity changed source");
        System.out.println("  in-place identity patch: OK");

        // Mutate first byte and confirm source changes only there
        blob[0] = (byte) (blob[0] ^ 0x5A);
        check(PsmArchive.patchBlobInPlace(first.path, blob, System.out::println), "patch mutate failed");
        sourceAfter = Files.readAllBytes(unpacked.resolve(PsmArchive.SOURCE_COPY));
        check(sourceAfter[first.offset] == blob[0], "mutated byte not written at offset");
        // restore
        blob[0] = (byte) (blob[0] ^ 0x5A);
        PsmArchive.patchBlobInPlace(first.path, blob, System.out::println);

        long seed = RandomizerOptions.seedFromString("DARKSTONE");
        check(seed == RandomizerOptions.seedFromString("DARKSTONE"), "seed must be deterministic");

        System.out.println("PsmRoundtripTest OK");
    }

    private static final class EntryInfo {
        final Path path;
        final int offset;
        EntryInfo(Path path, int offset) {
            this.path = path;
            this.offset = offset;
        }
    }

    private static EntryInfo firstBin(Path unpacked) throws Exception {
        Path meta = unpacked.resolve(PsmArchive.META_FILE);
        if (!Files.isRegularFile(meta)) {
            return null;
        }
        for (String line : Files.readAllLines(meta)) {
            if (line.startsWith("#") || !line.contains(",")) {
                continue;
            }
            String[] p = line.split(",");
            if (p.length < 4) {
                continue;
            }
            int idx = Integer.parseInt(p[0].trim());
            int off = Integer.parseInt(p[2].trim());
            Path bin = unpacked.resolve(String.format("%04d.bin", idx));
            if (Files.isRegularFile(bin)) {
                return new EntryInfo(bin, off);
            }
        }
        return null;
    }

    private static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
