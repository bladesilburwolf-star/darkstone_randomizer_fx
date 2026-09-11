package com.serifsystemworks.darkstone.engine;

public final class RandomizerOptions {
    // Core
    public boolean loot = false;
    public boolean enemies = false;
    /** Shuffle MO_* encounter name slots (enemy types per land / level). */
    public boolean enemyTypes = true;
    /** "Complete randomization" — any monster type in any slot regardless of
     *  name length, vs. the tame same-length-only swaps in enemyTypes. New
     *  option alongside the existing shuffle, not a replacement. */
    public boolean enemyTypesChaotic = false;
    public boolean heroes = true;
    public boolean shops = false;
    public boolean maps = false;

    /** Static placed props (pots, barrels, chests, book stands) only. */
    public boolean objects = false;

    /** Overworld LAND FE tiles + structural props. */
    public boolean dungeons = true;
    public boolean dungeonsCrossLand = false;
    /** Use DungeonRandomizerEnhanced's per-land difficulty weighting to decide
     *  shuffle scope per land (harder-tier lands get cross-land mixing, easier
     *  lands stay shuffled locally) instead of the single global dungeonsCrossLand flag. */
    public boolean progressiveDifficulty = true;
    /** Field-level monster stat curve on the verified 474-byte record format (see TableScanner). */
    public boolean progressionRedesign = true;
    /** FF-style player baseline stats, ported from PC (see RandomizerEngine.applyFfStylePlayerBases). */
    public boolean ffPlayerBases = false;
    public int ffPlayerBaseMinPct = 140;
    public int ffPlayerBaseMaxPct = 185;
    /** Item DMIN/AC power scale, ported from PC's itemPowerLag concept (DMAX/LEVEL unverified, left untouched). */
    public boolean itemPowerScale = false;
    public int itemPowerMinPct = 100;
    public int itemPowerMaxPct = 130;
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
    /** Vagrant Story-style cosmetic equipment appearance shuffle. */
    public boolean equipmentModels = false;
    /** Appearance plus the verified DMIN/AC bundle; equipment requirements stay put. */
    public boolean equipmentModelsConsistent = false;
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
        // The consistent mode is a superset of cosmetic mode.
        if (equipmentModelsConsistent) {
            equipmentModels = false;
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
