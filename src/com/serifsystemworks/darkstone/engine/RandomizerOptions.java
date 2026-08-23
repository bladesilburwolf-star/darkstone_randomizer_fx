package com.serifsystemworks.darkstone.engine;

public final class RandomizerOptions {
    // Core
    public boolean loot = true;
    public boolean enemies = false;
    public boolean heroes = true;
    public boolean shops = false;
    public boolean maps = false;
    public boolean dungeons = true;
    public boolean dungeonsCrossLand = false;
    public boolean quests = false;

    // Character start
    public boolean startingGear = true;
    public boolean startingGold = true;
    public boolean startingSpells = true;

    // Expanded
    public boolean weaponStats = true;
    public boolean spellLevels = true;
    public boolean skillLevels = true;
    public boolean playerLevels = true;
    public boolean enemyLevels = false;

    // QoL
    public boolean disableVideos = false;
    public boolean copyToCd = true;

    public String seedText = "";

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

    public static long seedFromString(String seedString) {
        if (seedString == null || seedString.isBlank()) {
            return System.currentTimeMillis();
        }
        long hash = seedString.hashCode();
        for (int i = 0; i < seedString.length(); i++) {
            hash = hash * 31 + seedString.charAt(i);
        }
        return hash;
    }

    public static String randomSeedString() {
        return String.valueOf(100000 + new java.util.Random().nextInt(900000));
    }
}
