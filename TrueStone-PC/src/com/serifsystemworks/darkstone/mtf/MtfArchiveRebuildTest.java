import com.serifsystemworks.darkstone.mtf.MtfArchive;
import com.serifsystemworks.darkstone.mtf.MtfCompression;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class MtfArchiveRebuildTest {

    public static void main(String[] args) throws Exception {
        Path original = Path.of("/tmp/synthetic_original.mtf");
        Path rebuilt = Path.of("/tmp/synthetic_rebuilt.mtf");
        Path rebuilt2 = Path.of("/tmp/synthetic_rebuilt2.mtf");

        // ---- build a synthetic archive with 5 entries, some compressed, some not ----
        LinkedHashMap<String, byte[]> content = new LinkedHashMap<>();
        Random rnd = new Random(7);

        content.put("SCRIPTS/QUEST1.SPT", repeatingText("Quest one dialogue and triggers. ", 40));
        content.put("LAND/LAND01.DAT", randomBytes(rnd, 3000));
        content.put("TOWN/TOWN.TXT", repeatingText("SOL 0 0 0\nGAZON5 1 0 90\n", 30));
        content.put("MONSTERCLASS.DAT", randomBytes(rnd, 8000));
        content.put("PIECES/SOL.O3D", Files.readAllBytes(Path.of("PC/TOWN/PIECES/SOL.O3D")));

        writeSyntheticArchive(original, content);
        System.out.println("Built synthetic archive: " + Files.size(original) + " bytes, " + content.size() + " entries");

        // ---- open + verify every entry extracts back correctly ----
        MtfArchive archive = new MtfArchive();
        archive.open(original);
        int ok = 0, bad = 0;
        for (Map.Entry<String, byte[]> e : content.entrySet()) {
            byte[] extracted = archive.extract(e.getKey());
            if (Arrays.equals(extracted, e.getValue())) {
                ok++;
            } else {
                bad++;
                System.out.println("  MISMATCH on initial open: " + e.getKey());
            }
        }
        System.out.println("Initial extract: " + ok + " ok, " + bad + " bad");

        // ---- rebuild, changing exactly one entry ----
        byte[] newQuestText = repeatingText("REBUILT quest text, now longer than before! ", 60);
        Map<String, byte[]> changes = new HashMap<>();
        changes.put("SCRIPTS/QUEST1.SPT", newQuestText);

        archive.rebuild(rebuilt, changes);
        System.out.println("Rebuilt archive: " + Files.size(rebuilt) + " bytes (original was " + Files.size(original) + ")");

        // ---- reopen rebuilt archive, verify changed entry + all untouched entries ----
        MtfArchive archive2 = new MtfArchive();
        archive2.open(rebuilt);

        byte[] gotQuest = archive2.extract("SCRIPTS/QUEST1.SPT");
        boolean questOk = Arrays.equals(gotQuest, newQuestText);
        System.out.println((questOk ? "  OK   " : "  FAIL ") + "edited entry SCRIPTS/QUEST1.SPT round-trips to new content");

        int untouchedOk = 0, untouchedBad = 0;
        for (String path : new String[]{"LAND/LAND01.DAT", "TOWN/TOWN.TXT", "MONSTERCLASS.DAT", "PIECES/SOL.O3D"}) {
            byte[] got = archive2.extract(path);
            byte[] expected = content.get(path);
            if (Arrays.equals(got, expected)) {
                untouchedOk++;
            } else {
                untouchedBad++;
                System.out.println("  FAIL  untouched entry changed: " + path);
            }
        }
        System.out.println("Untouched entries after rebuild: " + untouchedOk + " ok, " + untouchedBad + " bad");

        // also verify the STORED (still-compressed) bytes of an untouched entry are byte-identical
        // to the original archive's stored bytes, proving true passthrough (not just decompress+recompress)
        byte[] storedOriginal = readStoredBytes(original, "MONSTERCLASS.DAT");
        byte[] storedRebuilt = readStoredBytes(rebuilt, "MONSTERCLASS.DAT");
        boolean storedIdentical = Arrays.equals(storedOriginal, storedRebuilt);
        System.out.println((storedIdentical ? "  OK   " : "  FAIL ")
                + "untouched entry's STORED bytes are byte-identical between original and rebuilt (true passthrough)");

        // ---- rebuild again from the rebuilt archive with no changes at all: should be stable ----
        archive2.rebuild(rebuilt2, Map.of());
        MtfArchive archive3 = new MtfArchive();
        archive3.open(rebuilt2);
        boolean stableOk = true;
        for (String path : new String[]{"SCRIPTS/QUEST1.SPT", "LAND/LAND01.DAT", "TOWN/TOWN.TXT", "MONSTERCLASS.DAT", "PIECES/SOL.O3D"}) {
            byte[] got = archive3.extract(path);
            byte[] expected = path.equals("SCRIPTS/QUEST1.SPT") ? newQuestText : content.get(path);
            if (!Arrays.equals(got, expected)) {
                stableOk = false;
                System.out.println("  FAIL  no-op rebuild changed: " + path);
            }
        }
        System.out.println((stableOk ? "  OK   " : "  FAIL ") + "no-op rebuild (zero changes) preserves everything");

        archive.close();
        archive2.close();
        archive3.close();

        boolean allGood = bad == 0 && questOk && untouchedBad == 0 && storedIdentical && stableOk;
        System.out.println("\n==============================");
        System.out.println(allGood ? "ALL CHECKS PASSED" : "SOME CHECKS FAILED");
        if (!allGood) System.exit(1);
    }

    static byte[] readStoredBytes(Path archivePath, String wantPath) throws Exception {
        MtfArchive a = new MtfArchive();
        a.open(archivePath);
        MtfArchive.Entry e = a.getEntry(wantPath);
        try (RandomAccessFile raf = new RandomAccessFile(archivePath.toFile(), "r")) {
            raf.seek(e.offset);
            byte[] b = new byte[e.storedSize];
            raf.readFully(b);
            a.close();
            return b;
        }
    }

    static byte[] repeatingText(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    static byte[] randomBytes(Random rnd, int n) {
        byte[] b = new byte[n];
        rnd.nextBytes(b);
        return b;
    }

    /** Writes a minimal MTF-shaped archive matching MtfArchive.open()'s expected layout. */
    static void writeSyntheticArchive(Path out, LinkedHashMap<String, byte[]> content) throws Exception {
        byte[][] pathBytes = new byte[content.size()][];
        byte[][] stored = new byte[content.size()][];
        int[] decompSizes = new int[content.size()];

        int i = 0;
        for (Map.Entry<String, byte[]> e : content.entrySet()) {
            pathBytes[i] = (e.getKey() + "\0").getBytes(StandardCharsets.ISO_8859_1);
            stored[i] = MtfCompression.compress(e.getValue());
            decompSizes[i] = e.getValue().length;
            i++;
        }

        long tocSize = 4;
        for (byte[] pb : pathBytes) tocSize += 4 + pb.length + 4 + 4;

        long[] offsets = new long[content.size()];
        long cursor = tocSize;
        for (int j = 0; j < content.size(); j++) {
            offsets[j] = cursor;
            cursor += stored[j].length;
        }

        try (RandomAccessFile raf = new RandomAccessFile(out.toFile(), "rw")) {
            raf.setLength(0);
            raf.write(le(content.size()));
            for (int j = 0; j < content.size(); j++) {
                raf.write(le(pathBytes[j].length));
                raf.write(pathBytes[j]);
                raf.write(le((int) offsets[j]));
                raf.write(le(decompSizes[j]));
            }
            for (int j = 0; j < content.size(); j++) {
                raf.write(stored[j]);
            }
        }
    }

    static byte[] le(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }
}
