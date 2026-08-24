package com.serifsystemworks.darkstone.pc;

import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Options for the Darkstone PC randomizer. */
public final class PcOptions {

    public Path gameRoot;          // folder containing PCLASS/ and *.DAT
    public Path outputRoot;        // where randomized copies are written (null = in-place under gameRoot)

    public boolean monsters = true;
    public boolean items = true;
    public boolean playerClasses = true;
    public boolean shuffleMonsterStats = true;
    public boolean shuffleItemStats = true;
    public boolean rangeRollMonsters = false;
    public boolean rangeRollItems = false;

    /** Patch runtime MONSTERCLASS.DAT / ITEMOBJECT.DAT (needed for actual combat). */
    public boolean patchDat = true;

    /** Shuffle same-size LAND/*.O3D prop meshes (visual land clutter). */
    public boolean landProps = false;

    /** Shuffle LAND {n} among side-quest SPT files (not FINAL/TOWN/ENTREE). */
    public boolean questScripts = false;
    /** Reassign non-key OBJECT PARENT item types inside SPT quests. */
    public boolean questRewards = false;

    public int dmgMin = 1;
    public int dmgMax = 80;
    public int acMin = 0;
    public int acMax = 150;
    public int levelMin = 1;
    public int levelMax = 200;

    public String seedText = "";
    public String preset = "General";

    public static String randomSeedString() {
        return Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE, 36).toUpperCase();
    }

    public static long seedFromString(String text) {
        if (text == null || text.isBlank()) {
            return ThreadLocalRandom.current().nextLong();
        }
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            h ^= text.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    public Random random() {
        return new Random(seedFromString(seedText));
    }

    public int randomIn(Random rnd, int min, int max) {
        if (max < min) {
            int t = min;
            min = max;
            max = t;
        }
        if (min == max) return min;
        return min + rnd.nextInt(max - min + 1);
    }
}
