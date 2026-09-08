package com.serifsystemworks.darkstone.pc;

import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Options for the Darkstone PC randomizer. */
public final class PcOptions {

    public Path gameRoot;
    public Path outputRoot;
    /** Where to find MONSTERCLASS.DAT / ITEMOBJECT.DAT (defaults to gameRoot). */
    public Path datSourceRoot;

    public boolean monsters = true;
    public boolean items = true;
    public boolean playerClasses = true;
    public boolean shuffleMonsterStats = true;
    public boolean shuffleItemStats = true;
    public boolean rangeRollMonsters = false;
    public boolean rangeRollItems = false;

    /** Swap entire combat monster identities (key + stats) among safe trash/elite rows. */
    public boolean shuffleEnemyTypes = false;
    /** Shuffle enemy identities within level bands (per-land / dungeon tiers). */
    public boolean landEnemyShuffle = true;
    /** Randomize SPEED / ATTSPD / ATTFRE (movement & attack cadence). */
    public boolean randomizeSpeeds = true;
    public int speedMin = 60;    // 0.6x vanilla (a bit slower)
	public int speedMax = 160;   // 1.6x vanilla (noticeably faster)
	public int attSpdMin = 70;   // 0.7x (slightly slower swings)
	public int attSpdMax = 140;  // 1.4x (snappier attacks)
	
	public boolean randomizeSpawnCounts = true;
	public int spawnCountMin = 1;
	public int spawnCountMax = 8;
	public int spawnChanceMin = 50;
	public int spawnChanceMax = 100;

	/** Formula-driven progression redesign (replaces vanilla-shuffle-based scaling). */
	public boolean progressionRedesign = true;
    /** When false, leave enemy combat stats vanilla (speeds/spawns may still run). */
    public boolean randomizeMonsterPower = true;
	/** How far item power lags behind monster power at the same level (0.78 = 78%), forcing more grinding. */
	public double itemPowerLag = 1.05; // >=1 = items keep up / beat monster curve

    /** Patch runtime MONSTERCLASS.DAT / ITEMOBJECT.DAT. */
    public boolean patchDat = true;

    public boolean landProps = false;
    public boolean questScripts = false;
    public boolean questRewards = false;
    /** Scale numeric spawn/count fields found in side-quest SPT scripts. */
    public boolean questSpawnDensity = true;
    public int questSpawnMinPct = 70;
    public int questSpawnMaxPct = 130;

    /** Shuffle non-key item PARENT refs in shop-like SPT blocks / combat item pool. */
    public boolean shuffleShops = false;

    /** Shuffle item combat stats only within LEVEL tier bands (DS2-style pools). */
    public boolean lootTierBands = true;
    /** Seeded start kits (startItem..startItem4) from early-tier pools. */
    public boolean startKits = true;
    /** Quest LAND assignment with early/mid/late logic (not pure shuffle). */
    public boolean questLandLogic = true;

    /** Balance shop prices while preserving the retail table layout. */
    public boolean balanceShopEconomy = true;
    /** Lower/upper bounds for deterministic per-seed shop-price variation. */
    public int shopPriceMinPct = 85;
    public int shopPriceMaxPct = 115;
    /** Items at or below this LEVEL get an extra early-game discount. */
    public int shopEarlyMaxLevel = 12;
    public int shopEarlyMinPct = 55;
    public int shopEarlyMaxPct = 85;
    /** Mid-tier price band (between early and late). */
    public int shopMidMaxLevel = 28;
    public int shopMidMinPct = 75;
    public int shopMidMaxPct = 100;
    /** Randomize each class\' starting gold around its retail value. */
    public boolean randomizeStartingGold = true;
    public int startingGoldMinPct = 80;
    public int startingGoldMaxPct = 120;
    /** Absolute safety floor for generated starting gold. */
    public int startingGoldFloor = 50;
    /** Hard ceiling for starting gold (TrueStone default 10000). */
    public int startingGoldCap = 10000;
    /** If both >= 0, roll absolute gold instead of % of retail. */
    public int startingGoldAbsoluteMin = -1;
    public int startingGoldAbsoluteMax = -1;

    /** Soft-cap trash-mob / low-level item damage so early game stays playable. */
    public boolean earlyGameCaps = true;
    public int earlyDmgCap = 18;
    public int earlyAcCap = 32;
    public int earlyLevelThreshold = 14;

    /** Pull higher-difficulty-tier items into lower play by cutting LEVEL reqs. */
    public boolean itemLevelUnlock = true;
    /** Subtract this many levels from item LEVEL (min 1). Novice=20, Legend=0. */
    public int itemLevelReduction = 18;
    /** Scale factor for low-level monster power (Novice ~0.65, Legend ~1.15). */
    public double earlyMonsterScale = 0.65;

    /** Force MAX_STRENGTH/MAGIC/DEX/VIT rows to 999 (pair with DCP-022 EXE clamps). */
    public boolean uncapMaxStats = true;
    /** Flatten weapon affinity + boost hit/block/base stats so every class can use every weapon. */
    public boolean improveClasses = true;
    /** Target WEAPON_KIND proficiency (lower = easier to use that weapon type). */
    public int weaponKindFloor = 18;
    public int weaponKindCeil = 22;
    public int maxStatCap = 999;

    public boolean injectMtf = false;
    public Path dataMtfPath = null;

    /**
     * Full campaign pipeline: extract from DATA.MTF → randomize → write
     * DATA_&lt;seed&gt;.MTF (and optional quest/ copy).
     */
    public boolean campaignPipeline = false;
    /** How many different seeds to generate when pipeline is on (1 = single). */
    public int multiSeedCount = 1;
    /** Also copy seeded MTF under gameRoot/quest/ for pack-style installs. */
    public boolean copyToQuestFolder = false;

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
