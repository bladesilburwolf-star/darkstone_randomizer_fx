package com.serifsystemworks.darkstone.engine;

public final class RandomizerOptions {
    // Core
    public boolean loot = false;
    public boolean enemies = false;
    /** Shuffle MO_* encounter name slots (enemy types per land / level). */
    public boolean enemyTypes = true;
    public boolean heroes = true;
    public boolean shops = false;
    public boolean maps = false;

    /** Overworld LAND FE tiles + structural props. */
    public boolean dungeons = true;
    public boolean dungeonsCrossLand = false;
    /**
     * Dungeon doors: cross-land shuffle of fixed-count structural FE props
     * (replaces ineffective interior / cross-interior modes).
     */
    public boolean dungeonDoors = true;
    /** Include LEVEL29/30 and DRAAK final packs in door/prop pools when true. */
    public boolean dungeonsFinal = false;

    @Deprecated public boolean dungeonsInteriors = false;
    @Deprecated public boolean dungeonsCrossInterior = false;

    public boolean quests = false;

    public boolean palettes = true;
    public boolean paletteShuffle = false;
    public int paletteHueMin = 30;
    public int paletteHueMax = 330;

    public boolean startingGear = true;
    public boolean startingGold = true;
    public boolean startingSpells = true;

    public boolean weaponStats = true;
    public boolean spellLevels = true;
    public boolean skillLevels = true;
    public boolean playerLevels = true;
    public boolean enemyLevels = false;
    /**
     * Extra combat fields on hero/enemy blobs: wider u16 band (AC / hit / speed-like).
     */
    public boolean combatExtras = true;

    public boolean disableVideos = false;
    public boolean music = false;
    public boolean videos = false;
    public boolean copyToCd = false;

    public String seedText = "";
    public java.nio.file.Path cdRoot;

    public int statMin = 12;
    public int statMax = 35;
    public int goldMin = 50;
    public int goldMax = 500;
    public int levelMin = 1;
    public int levelMax = 5;
    public int skillMin = 1;
    public int skillMax = 5;
    public int weaponMin = 3;
    public int weaponMax = 25;
    /** Armor / AC-like u16 band. */
    public int acMin = 0;
    public int acMax = 80;
    /** Accuracy / to-hit-like u16 band. */
    public int hitMin = 20;
    public int hitMax = 120;
    /** Speed / agility-like u16 band. */
    public int speedMin = 5;
    public int speedMax = 40;

    public int randomIn(java.util.Random rnd, int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        lo = Math.max(0, Math.min(99999, lo));
        hi = Math.max(lo, Math.min(99999, hi));
        return lo + rnd.nextInt(hi - lo + 1);
    }

    public int randomStat(java.util.Random rnd) { return randomIn(rnd, statMin, statMax); }
    public int randomGold(java.util.Random rnd) { return randomIn(rnd, goldMin, goldMax); }
    public int randomLevel(java.util.Random rnd) { return randomIn(rnd, levelMin, levelMax); }
    public int randomSkill(java.util.Random rnd) { return randomIn(rnd, skillMin, skillMax); }
    public int randomWeapon(java.util.Random rnd) { return randomIn(rnd, weaponMin, weaponMax); }
    public int randomAc(java.util.Random rnd) { return randomIn(rnd, acMin, acMax); }
    public int randomHit(java.util.Random rnd) { return randomIn(rnd, hitMin, hitMax); }
    public int randomSpeed(java.util.Random rnd) { return randomIn(rnd, speedMin, speedMax); }

    /** Enforce gear XOR loot — loot wins if both true (safer to drop gear). */
    public void resolveConflicts() {
        if (loot && startingGear) {
            startingGear = false;
            startingSpells = false;
        }
    }

    public static long seedFromString(String seedString) {
        if (seedString == null || seedString.isBlank()) {
            return System.currentTimeMillis();
        }
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < seedString.length(); i++) {
            h ^= seedString.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    public static String randomSeedString() {
        return Long.toHexString(System.nanoTime() ^ System.currentTimeMillis()).toUpperCase();
    }
}
