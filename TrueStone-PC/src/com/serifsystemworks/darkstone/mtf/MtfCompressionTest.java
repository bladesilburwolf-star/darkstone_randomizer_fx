import com.serifsystemworks.darkstone.mtf.MtfCompression;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

public class MtfCompressionTest {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) throws Exception {
        // ---- synthetic edge cases ----
        check("empty", new byte[0]);
        check("single byte", new byte[]{0x42});
        check("two bytes", new byte[]{0x01, 0x02});
        check("three bytes (min match len)", new byte[]{0x07, 0x07, 0x07});

        byte[] allZeros = new byte[5000];
        check("all zeros 5000 bytes", allZeros);

        byte[] repeatingPattern = new byte[10000];
        for (int i = 0; i < repeatingPattern.length; i++) repeatingPattern[i] = (byte) (i % 7);
        check("repeating 7-byte pattern x10000", repeatingPattern);

        byte[] runLength = new byte[2000];
        Arrays.fill(runLength, (byte) 0xAB);
        check("single-byte run 2000 (tests overlapping copy / offset=1)", runLength);

        Random rnd = new Random(42);
        byte[] random1k = new byte[1024];
        rnd.nextBytes(random1k);
        check("random 1024 bytes (near-incompressible)", random1k);

        byte[] random64k = new byte[65536];
        rnd.nextBytes(random64k);
        check("random 64KB (incompressible, stresses control-byte boundaries)", random64k);

        // long-distance match right at the 1023-offset boundary
        byte[] boundary = new byte[2100];
        rnd.nextBytes(boundary);
        System.arraycopy(boundary, 0, boundary, 1023, 50); // exact match at offset 1023
        check("match exactly at max offset (1023)", boundary);

        byte[] longRun = new byte[500];
        Arrays.fill(longRun, (byte) 0x5A);
        check("run longer than MAX_MATCH(66) forcing multiple backrefs", longRun);

        // ---- real repo data ----
        checkFile("PC/PCLASS/MONSTER.TXT");
        checkFile("PC/PCLASS/OBJECT.TXT");
        checkFile("PC/ITEMOBJECT.DAT");
        checkFile("PC/MONSTERCLASS.DAT");
        checkFile("PC/OBJ3D.DAT");
        checkFile("PC/SND.DAT");
        checkFile("PC/TRISPRITE.DAT");
        checkFile("PC/TOWN/TOWN.TXT");
        checkFile("PC/TOWN/TOWN.B3D");
        checkFile("PC/TOWN/TOWN.BRM");
        checkFile("PC/TOWN/TOWN.CLD");

        File pieces = new File("PC/TOWN/PIECES");
        File[] o3dFiles = pieces.listFiles((d, name) -> name.toUpperCase().endsWith(".O3D"));
        if (o3dFiles != null) {
            for (File f : o3dFiles) {
                checkFile(f.getPath());
            }
        }

        System.out.println("\n==============================");
        System.out.println("TOTAL: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void checkFile(String path) throws IOException {
        File f = new File(path);
        if (!f.exists()) {
            System.out.println("  SKIP   " + path + " (not found)");
            return;
        }
        byte[] data = Files.readAllBytes(f.toPath());
        check(path + " (" + data.length + " bytes)", data);
    }

    static void check(String label, byte[] original) {
        try {
            byte[] compressed = MtfCompression.compress(original);
            byte[] restored = MtfCompression.decompress(compressed, original.length);
            boolean ok = Arrays.equals(original, restored);
            double ratio = original.length == 0 ? 1.0 : (double) compressed.length / original.length;
            if (ok) {
                passed++;
                System.out.printf("  OK     %-55s  %7d -> %7d bytes (%.1f%%)%n",
                        label, original.length, compressed.length, ratio * 100);
            } else {
                failed++;
                int firstDiff = -1;
                int n = Math.min(original.length, restored.length);
                for (int i = 0; i < n; i++) {
                    if (original[i] != restored[i]) { firstDiff = i; break; }
                }
                System.out.printf("  FAIL   %-55s  orig=%d restored=%d firstDiff=%d%n",
                        label, original.length, restored.length, firstDiff);
            }
        } catch (Exception e) {
            failed++;
            System.out.println("  ERROR  " + label + "  " + e);
        }
    }
}
