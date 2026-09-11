package com.serifsystemworks.darkstone.engine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RandomizerEngine {

    private static final Pattern ITEM_NAME = Pattern.compile("ITEM_[A-Z0-9_]+");
    private static final int GEAR_SLOT_SIZE = 32;
    // PSX DATA1 item-record layout (394 bytes). These are the PSX equivalents
    // of PC ITEMOBJECT.DAT's appearance fields, relocated around the name area.
    private static final int OFF_ITEM_EQUIP_SFX = 126;
    private static final int LEN_ITEM_EQUIP_SFX = 16;
    private static final int OFF_ITEM_SPRITE = 316;
    private static final int LEN_ITEM_SPRITE = 32;
    private static final int OFF_ITEM_MESH = 350;
    private static final int LEN_ITEM_MESH = 16;

    private final Path outputRoot;
    private final LogSink log;

    public RandomizerEngine(Path outputRoot, LogSink log) {
        this.outputRoot = outputRoot;
        this.log = log == null ? LogSink.NULL : log;
    }

    public void runMaster(RandomizerOptions options) throws IOException {
        if (outputRoot == null) {
            log.log("[!] Error: Set output directory first.");
            return;
        }

        String seedText = options.seedText == null || options.seedText.isBlank() ? "12345" : options.seedText.trim();
        long seed = RandomizerOptions.seedFromString(seedText);
        Random rnd = new Random(seed);

        log.log("=================================================");
        log.log("   STARTING MASTER RANDOMIZATION (in-place patch)");
        log.log("   Seed text : " + seedText);
        log.log("   Seed hash : " + seed);
        log.log("   Stats " + options.statMin + "-" + options.statMax
                + "  Gold " + options.goldMin + "-" + options.goldMax);
        log.log("   Levels " + options.levelMin + "-" + options.levelMax
                + "  Skills " + options.skillMin + "-" + options.skillMax
                + "  Weapon " + options.weaponMin + "-" + options.weaponMax);
        options.resolveConflicts();
        if (options.loot && !options.startingGear) {
            log.log("Note: loot on → starting gear forced OFF (crash if both).");
        }
        log.log("=================================================");

        if (options.loot) {
            randomizeLoot(rnd);
        }
        if (options.enemies) {
            randomizeEnemies(rnd);
        }
        if (options.progressionRedesign) {
            patchMonsterTemplates474(rnd, options);
        }
        if (options.ffPlayerBases) {
            applyFfStylePlayerBases(rnd, options.ffPlayerBaseMinPct, options.ffPlayerBaseMaxPct);
        }
        if (options.itemPowerScale) {
            applyItemPowerScale(rnd, options.itemPowerMinPct, options.itemPowerMaxPct);
        }
        if (options.enemyTypes) {
            randomizeEnemyTypes(rnd);
        }
        if (options.enemyTypesChaotic) {
            randomizeEnemyTypesChaotic(rnd);
        }
        if (options.heroes) {
            randomizeHeroes(rnd, options);
        }
        if (options.startingGear || options.startingSpells) {
            randomizeStartingGear(rnd, options);
        }
        if (options.startingGold) {
            randomizeStartingGold(rnd, options);
        }
        if (options.weaponStats) {
            randomizeWeaponStats(rnd, options);
        }
        if (options.equipmentModels || options.equipmentModelsConsistent) {
            randomizeEquipmentModels(rnd, options.equipmentModelsConsistent);
        }
        if (options.spellLevels) {
            randomizeSpellLevels(rnd, options);
        }
        if (options.skillLevels) {
            randomizeSkillLevels(rnd, options);
        }
        if (options.playerLevels) {
            randomizePlayerLevels(rnd, options);
        }
        if (options.enemyLevels) {
            randomizeEnemyLevels(rnd, options);
        }
        if (options.combatExtras) {
            randomizeCombatExtras(rnd, options);
        }
        if (options.shops) {
            randomizeShops(rnd);
        }
        if (options.maps) {
            randomizeMaps(rnd);
        }
        if (options.objects) {
            randomizeStaticObjects(rnd, options);
        }
        if (options.dungeons) {
            randomizeDungeons(rnd, options);
        }
        if (options.palettes) {
            randomizePalettes(rnd, options);
        }
        if (options.music) {
            randomizeMusic(rnd, options);
        }
        if (options.videos) {
            randomizeVideos(rnd, options);
        }
        if (options.quests) {
            randomizeQuests(rnd);
        }

        log.log("--- Syncing patched blobs into _source.psm archives ---");
        PsmArchive.repackAll(outputRoot, log);
        log.log("=================================================");
        log.log("      MASTER RANDOMIZATION COMPLETE               ");
        log.log("=================================================");
    }

    /**
     * Real loot lives in QUEST$ (AL*_Q*.PSM): ITEM_* name slots for quest rewards,
     * chest contents, and pickups. DATA1 64-byte pools were almost empty noise.
     * <p>
     * Strategy: collect ITEM_* slots (except DROP/PICK/USE), keep KEY/CLEF protected
     * by default, and reassign names from the pool into slots they fit.
     */

    /**
     * Progression / softlock items — shared naming with PC SCRIPT exports.
     * Never shuffle these as generic loot.
     */
    static boolean isProgressionItem(String name) {
        return com.serifsystemworks.darkstone.config.RandomizerConstants.isProtectedItem(name)
                || (name != null && name.toUpperCase(java.util.Locale.ROOT).contains("CRYSTAL"));
    }

    public int randomizeLoot(Random rnd) {
        try {
            int questSlots = randomizeQuestItemLoot(rnd);
            int pools = 0;
            List<Path> lootFiles = findMatching(p -> TableScanner.isLoot(Files.readAllBytes(p)));
            for (Path p : lootFiles) {
                byte[] data = Files.readAllBytes(p);
                shuffleBytes(data, rnd);
                if (writePatched(p, data)) {
                    pools++;
                }
            }
            if (pools > 0) {
                log.log("[+] Loot (legacy pools): shuffled " + pools + " DATA-style 64-byte pools.");
            }
            log.log("[+] Loot: " + questSlots + " QUEST$ ITEM slots reassigned"
                    + (pools > 0 ? " + " + pools + " legacy pools" : "") + ".");
            return questSlots + pools;
        } catch (Exception e) {
            log.log("[!] Loot randomization failed: " + e.getMessage());
            return 0;
        }
    }

    private int randomizeQuestItemLoot(Random rnd) throws Exception {
        // Prefer bins under QUEST$-style packs: AL*_Q*, AQFINAL, or any bin with many ITEM_
        List<Path> candidates = findMatching(p -> {
            String name = p.getFileName().toString().toUpperCase(Locale.ROOT);
            String parent = p.getParent() != null
                    ? p.getParent().getFileName().toString().toUpperCase(Locale.ROOT) : "";
            if (parent.contains("QUEST") || parent.startsWith("AL") || parent.startsWith("AQ")) {
                return true;
            }
            byte[] b = Files.readAllBytes(p);
            if (b.length > 200_000) return false;
            String t = TableScanner.latin1(b);
            int c = 0;
            int i = 0;
            while ((i = t.indexOf("ITEM_", i)) >= 0) {
                c++;
                i += 5;
                if (c >= 3) return true;
            }
            return false;
        });

        final String[] SYSTEM = {"ITEM_DROP", "ITEM_PICK", "ITEM_USE"};
        java.util.regex.Pattern itemPat = java.util.regex.Pattern.compile("ITEM_[A-Z0-9_]+");

        class Slot {
            final Path file;
            final int offset;
            final String name;
            final int capacity; // max bytes for name + nulls we can overwrite

            Slot(Path file, int offset, String name, int capacity) {
                this.file = file;
                this.offset = offset;
                this.name = name;
                this.capacity = capacity;
            }
        }

        List<Slot> slots = new ArrayList<>();
        Map<Path, byte[]> fileData = new HashMap<>();

        for (Path p : candidates) {
            byte[] data = Files.readAllBytes(p);
            String text = new String(data, java.nio.charset.StandardCharsets.US_ASCII);
            java.util.regex.Matcher m = itemPat.matcher(text);
            while (m.find()) {
                String name = m.group();
                boolean sys = false;
                for (String s : SYSTEM) {
                    if (s.equals(name)) {
                        sys = true;
                        break;
                    }
                }
                if (sys) {
                    continue;
                }
                // Protect progression items (PC SCRIPT crosswalk + PSX QUEST$).
                // Crystals, Draak key, virtual flags, keys/clefs must never be loot-shuffled.
                if (isProgressionItem(name)) {
                    continue;
                }
                int off = m.start();
                int end = m.end();
                int nulls = 0;
                while (end + nulls < data.length && data[end + nulls] == 0) {
                    nulls++;
                }
                // Allow writing up to name length + trailing nulls (at least name+1)
                int capacity = Math.max(name.length() + 1, name.length() + nulls);
                // Cap at 32-byte style fields common in this game
                capacity = Math.min(capacity, 32);
                if (capacity < 8) {
                    continue;
                }
                slots.add(new Slot(p, off, name, capacity));
                fileData.put(p, data);
            }
        }

        if (slots.size() < 2) {
            log.log("[+] Loot (QUEST$): not enough ITEM slots (" + slots.size() + ").");
            return 0;
        }

        // Build pool of unique names
        List<String> pool = slots.stream().map(s -> s.name).distinct().collect(Collectors.toList());
        int changed = 0;
        Map<Path, Boolean> dirty = new HashMap<>();

        for (Slot slot : slots) {
            // Prefer a different name that fits
            List<String> fits = new ArrayList<>();
            for (String n : pool) {
                if (n.length() + 1 <= slot.capacity) {
                    fits.add(n);
                }
            }
            if (fits.isEmpty()) {
                continue;
            }
            String pick = fits.get(rnd.nextInt(fits.size()));
            // mild bias: avoid always same
            if (pick.equals(slot.name) && fits.size() > 1) {
                pick = fits.get(rnd.nextInt(fits.size()));
            }
            if (pick.equals(slot.name)) {
                continue;
            }
            byte[] data = fileData.get(slot.file);
            // clear field and write
            for (int i = 0; i < slot.capacity && slot.offset + i < data.length; i++) {
                data[slot.offset + i] = 0;
            }
            byte[] raw = pick.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            int n = Math.min(raw.length, slot.capacity - 1);
            System.arraycopy(raw, 0, data, slot.offset, n);
            dirty.put(slot.file, true);
            changed++;
        }

        int files = 0;
        for (Map.Entry<Path, Boolean> e : dirty.entrySet()) {
            if (e.getValue() && writePatched(e.getKey(), fileData.get(e.getKey()))) {
                files++;
            }
        }
        log.log("[+] Loot (QUEST$): " + changed + " ITEM slots in " + files + " blobs (keys protected).");
        return changed;
    }

    public int randomizeEnemies(Random rnd) {
        try {
            List<Path> enemyFiles = findMatching(p -> TableScanner.isEnemy(Files.readAllBytes(p)));
            List<Path> monsters = new ArrayList<>();
            List<Path> others = new ArrayList<>();
            int protectedCount = 0;
            for (Path p : enemyFiles) {
                byte[] b = Files.readAllBytes(p);
                if (com.serifsystemworks.darkstone.config.RandomizerConstants.isProtectedMonster(b)) {
                    protectedCount++;
                    continue; // never enters the shuffle pool — unique bosses/NPCs stay put
                }
                if (TableScanner.looksLikeMonster(b)) {
                    monsters.add(p);
                } else {
                    others.add(p);
                }
            }
            List<Path> targets = monsters.isEmpty() ? enemyFiles : monsters;

            Map<Long, List<Path>> bySize = new HashMap<>();
            for (Path p : targets) {
                bySize.computeIfAbsent(Files.size(p), k -> new ArrayList<>()).add(p);
            }

            int count = 0;
            for (List<Path> group : bySize.values()) {
                if (group.size() < 2) {
                    continue;
                }
                List<byte[]> contents = new ArrayList<>();
                for (Path p : group) {
                    contents.add(Files.readAllBytes(p));
                }
                Collections.shuffle(contents, rnd);
                for (int i = 0; i < group.size(); i++) {
                    if (writePatched(group.get(i), contents.get(i))) {
                        count++;
                    }
                }
            }
            log.log("[+] Enemy randomization: swapped " + count + " templates"
                    + " (monster-filtered=" + monsters.size() + ", spell/other=" + others.size()
                    + ", protected/skipped=" + protectedCount + ").");
            return count;
        } catch (Exception e) {
            log.log("[!] Enemy randomization failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Field-level stat curve on the verified 474-byte monster/NPC record
     * format (see TableScanner.MONSTER474_SIZE and OFF474_* offsets).
     * Same formula shape as the PC-side progressionMonsterStats, adapted for
     * this session's finding that PSX rosters are distributed per-land
     * (N records packed per file) rather than centralized in one archive.
     * Same-size in place only — never changes a blob's total length, so it's
     * always safe for PsmArchive.patchBlobInPlace.
     */
    public int patchMonsterTemplates474(Random rnd, RandomizerOptions options) {
        int patched = 0, recordsSeen = 0, recordsProtected = 0;
        try {
            List<Path> candidates = findMatching(p -> {
                try {
                    return TableScanner.isMonsterRosterBlob(Files.readAllBytes(p));
                } catch (IOException e) {
                    return false;
                }
            });
            for (Path p : candidates) {
                byte[] data = Files.readAllBytes(p);
                int n = data.length / TableScanner.MONSTER474_SIZE;
                boolean anyChange = false;
                for (int i = 0; i < n; i++) {
                    int base = i * TableScanner.MONSTER474_SIZE;
                    recordsSeen++;
                    int end = base;
                    while (end < base + 32 && data[end] != 0) end++;
                    String name = new String(data, base, end - base, java.nio.charset.StandardCharsets.ISO_8859_1);

                    if (com.serifsystemworks.darkstone.config.RandomizerConstants.isProtectedMonster(
                            java.util.Arrays.copyOfRange(data, base, base + TableScanner.MONSTER474_SIZE))) {
                        recordsProtected++;
                        continue;
                    }

                    int lmin = readU16(data, base + TableScanner.OFF474_LMIN);
                    int ac = readU16(data, base + TableScanner.OFF474_AC);
                    int dmin = readU16(data, base + TableScanner.OFF474_DMIN);
                    // skip pure decorative/non-combat entries (all-zero stats, e.g. POULET)
                    if (ac == 0 && dmin == 0) {
                        continue;
                    }

                    int newLmin = Math.max(1, lmin + options.randomIn(rnd, -3, 5));
                    int newLmax = newLmin + options.randomIn(rnd, 1, 4 + newLmin / 15);
                    double L = newLmin;
                    double acVal = (8 + 0.9 * L) * (0.85 + rnd.nextDouble() * 0.30);
                    double dminVal = (2 + 0.35 * L) * (0.85 + rnd.nextDouble() * 0.30);
                    double dmaxVal = (dminVal + 3 + 0.25 * L) * (0.85 + rnd.nextDouble() * 0.30);
                    double tohitVal = (20 + 0.45 * L) * (0.90 + rnd.nextDouble() * 0.20);
                    int iDmin = Math.max(0, (int) Math.round(dminVal));
                    int iDmax = Math.max(iDmin, (int) Math.round(dmaxVal));
                    // Fit from real data this session (BAT1 4/2, GOBELIN4 16/4,
                    // BAT3 51/17, BAT4 73/22 -> averages ~LMIN/3), clamped to
                    // Darkstone's documented level cap of 99. Keeps XP reward
                    // in sync with the new combat power instead of staying at
                    // whatever tiny vanilla LEVEL this record happened to have.
                    int newLevel = Math.max(1, Math.min(99, (int) Math.round(newLmin / 3.0)));

                    writeU16(data, base + TableScanner.OFF474_LMIN, newLmin);
                    writeU16(data, base + TableScanner.OFF474_LMAX, newLmax);
                    writeU16(data, base + TableScanner.OFF474_AC, Math.max(0, (int) Math.round(acVal)));
                    writeU16(data, base + TableScanner.OFF474_TOHIT, Math.max(5, (int) Math.round(tohitVal)));
                    writeU16(data, base + TableScanner.OFF474_DMIN, iDmin);
                    writeU16(data, base + TableScanner.OFF474_DMAX, iDmax);
                    writeU16(data, base + TableScanner.OFF474_LEVEL, newLevel);
                    anyChange = true;
                    patched++;
                }
                if (anyChange) {
                    writePatched(p, data);
                }
            }
            log.log("[+] Monster templates (474-byte records): " + patched + " patched, "
                    + recordsProtected + " protected/skipped, " + recordsSeen + " scanned across "
                    + candidates.size() + " files.");
        } catch (Exception e) {
            log.log("[!] patchMonsterTemplates474 failed: " + e.getMessage());
        }
        return patched;
    }

    /**
     * FF-style player baseline stats, ported from the PC engine's
     * applyFFStylePlayerBases: each class's BASE_STRENGTH/MAGIC/DEXTERITY/
     * VITALITY/LIFE/MANA gets an independently-rolled percentage boost in
     * [minPct, maxPct] (LIFE/MANA capped tighter to avoid runaway
     * survivability), applied to the verified 344-byte-per-class record
     * table (see TableScanner.isPlayerClassTable). Same-size in place only.
     */
    public int applyFfStylePlayerBases(Random rnd, int minPct, int maxPct) {
        int clampedMin = Math.max(100, Math.min(300, minPct));
        int clampedMax = Math.max(clampedMin, Math.min(400, maxPct));
        int touched = 0;
        try {
            List<Path> candidates = findMatching(p -> {
                try {
                    return TableScanner.isPlayerClassTable(Files.readAllBytes(p));
                } catch (IOException e) {
                    return false;
                }
            });
            if (candidates.isEmpty()) {
                log.log("[!] FF-style player bases: no player-class table found.");
                return 0;
            }
            int[] baseOffs = {
                    TableScanner.OFF_PC_BASE_STR, TableScanner.OFF_PC_BASE_MAG,
                    TableScanner.OFF_PC_BASE_DEX, TableScanner.OFF_PC_BASE_VIT,
                    TableScanner.OFF_PC_BASE_LIFE, TableScanner.OFF_PC_BASE_MANA
            };
            for (Path p : candidates) {
                byte[] data = Files.readAllBytes(p);
                for (int i = 0; i < TableScanner.PLAYERCLASS_COUNT; i++) {
                    int rec = TableScanner.PLAYERCLASS_HEADER_SIZE + i * TableScanner.PLAYERCLASS_RECORD_SIZE;
                    for (int off : baseOffs) {
                        int base = readU16(data, rec + off);
                        if (base <= 0) continue;
                        int pct = clampedMin + rnd.nextInt(clampedMax - clampedMin + 1);
                        boolean isSurvival = (off == TableScanner.OFF_PC_BASE_LIFE || off == TableScanner.OFF_PC_BASE_MANA);
                        if (isSurvival) {
                            pct = Math.min(pct, clampedMin + 25);
                        }
                        int neu = Math.max(1, (int) Math.round(base * pct / 100.0));
                        writeU16(data, rec + off, neu);
                        touched++;
                    }
                }
                writePatched(p, data);
            }
            log.log("[+] FF-style player bases: " + touched + " cells randomized at "
                    + clampedMin + "-" + clampedMax + "% across " + candidates.size() + " table(s).");
        } catch (Exception e) {
            log.log("[!] FF-style player bases failed: " + e.getMessage());
        }
        return touched;
    }


    /**
     * Equipment/item power scaling on the verified 394-byte item record
     * (see TableScanner.OFF_ITEM_DMIN/OFF_ITEM_AC). Only DMIN and AC are
     * touched — DMAX and LEVEL were exhaustively searched for this session
     * and never located in this record, so they're left completely
     * untouched rather than guessed at. Mirrors the spirit of the PC
     * itemPowerLag option as a direct percentage scale on the two fields
     * that are actually confirmed safe to write.
     */
    public int applyItemPowerScale(Random rnd, int minPct, int maxPct) {
        int clampedMin = Math.max(50, Math.min(300, minPct));
        int clampedMax = Math.max(clampedMin, Math.min(400, maxPct));
        int touched = 0;
        int recordsSeen = 0;
        try {
            List<Path> candidates = findMatching(p -> {
                try {
                    byte[] b = Files.readAllBytes(p);
                    return b.length > 500 && TableScanner.latin1(b).contains("ITEM_");
                } catch (IOException e) {
                    return false;
                }
            });
            java.util.regex.Pattern itemName = java.util.regex.Pattern.compile("IT[A-Z0-9_]{3,30}");
            for (Path p : candidates) {
                byte[] data = Files.readAllBytes(p);
                String text = new String(data, java.nio.charset.StandardCharsets.US_ASCII);
                java.util.regex.Matcher m = itemName.matcher(text);
                boolean anyChange = false;
                while (m.find()) {
                    int nameOff = m.start();
                    int rec = nameOff - TableScanner.OFF_ITEM_NAME;
                    if (rec < 0 || rec + TableScanner.ITEM_RECORD_SIZE > data.length) {
                        continue;
                    }
                    // confirm this really is a record boundary: name must be null-terminated
                    int end = m.end();
                    if (end >= data.length || data[end] != 0) {
                        continue;
                    }
                    recordsSeen++;

                    int dmin = readU16(data, rec + TableScanner.OFF_ITEM_DMIN);
                    int ac = data[rec + TableScanner.OFF_ITEM_AC] & 0xFF;

                    if (dmin > 0) {
                        int pct = clampedMin + rnd.nextInt(clampedMax - clampedMin + 1);
                        int neu = Math.max(1, (int) Math.round(dmin * pct / 100.0));
                        neu = Math.min(neu, 32767);
                        writeU16(data, rec + TableScanner.OFF_ITEM_DMIN, neu);
                        anyChange = true;
                        touched++;
                    }
                    if (ac > 0) {
                        int pct = clampedMin + rnd.nextInt(clampedMax - clampedMin + 1);
                        int neu = Math.max(1, Math.min(255, (int) Math.round(ac * pct / 100.0)));
                        data[rec + TableScanner.OFF_ITEM_AC] = (byte) neu;
                        anyChange = true;
                        touched++;
                    }
                }
                if (anyChange) {
                    writePatched(p, data);
                }
            }
            log.log("[+] Item power (DMIN/AC only — DMAX/LEVEL unverified, left untouched): "
                    + touched + " fields patched across " + recordsSeen + " records in "
                    + candidates.size() + " files, " + clampedMin + "-" + clampedMax + "%.");
        } catch (Exception e) {
            log.log("[!] Item power scale failed: " + e.getMessage());
        }
        return touched;
    }


    private static int readU16(byte[] data, int off) {
        return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
    }

    private static void writeU16(byte[] data, int off, int value) {
        data[off] = (byte) (value & 0xFF);
        data[off + 1] = (byte) ((value >> 8) & 0xFF);
    }

    public int randomizeHeroes(Random rnd, RandomizerOptions options) {
        try {
            List<Path> heroFiles = findMatching(p -> TableScanner.isHero(Files.readAllBytes(p)));
            int randomizedCount = 0;
            for (Path p : heroFiles) {
                byte[] data = Files.readAllBytes(p);
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                int changes = 0;
                for (int offset = 0; offset + 2 <= data.length; offset += 2) {
                    int val = bb.getShort(offset) & 0xFFFF;
                    if (val >= 8 && val <= 45) {
                        int prev = offset > 0 ? (data[offset - 1] & 0xFF) : 0;
                        int next = offset + 2 < data.length ? (data[offset + 2] & 0xFF) : 0;
                        if ((prev >= 0x20 && prev < 0x7F) || (next >= 0x20 && next < 0x7F)) {
                            continue;
                        }
                        bb.putShort(offset, (short) options.randomStat(rnd));
                        changes++;
                    }
                }
                if (changes > 0 && writePatched(p, data)) {
                    randomizedCount++;
                    log.log("    hero blob " + p.getFileName() + ": " + changes + " stats -> range "
                            + options.statMin + "-" + options.statMax);
                }
            }
            // Optional: raise class MAX caps to 999 when PC-style patterns are found
            int capFiles = 0;
            List<Path> large = findMatching(p -> {
                long sz = Files.size(p);
                if (sz < 2000 || sz > 12_000) return false;
                byte[] b = Files.readAllBytes(p);
                return !TableScanner.isUiStringTable(b);
            });
            StatCapRemover caps = new StatCapRemover(log);
            for (Path p : large) {
                byte[] data = Files.readAllBytes(p);
                if (caps.removeStatCaps(data) && writePatched(p, data)) {
                    capFiles++;
                }
            }
            if (capFiles > 0) {
                log.log("[+] Stat caps raised on " + capFiles + " blob(s).");
            }
            log.log("[+] Hero randomization: patched " + randomizedCount + " class data blobs.");
            return randomizedCount;
        } catch (Exception e) {
            log.log("[!] Hero randomization failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Starting gear lives in small DATA1 tables (e.g. 0025.bin): eight class blocks,
     * each with four 32-byte ITEM_* slots. We reshuffle names from the global pool
     * (and optionally bias toward ITEM_BOOK_* when startingSpells is on).
     */
    public int randomizeStartingGear(Random rnd, RandomizerOptions options) {
        try {
            List<Path> gearFiles = findMatching(p -> {
                byte[] b = Files.readAllBytes(p);
                if (b.length < 500 || b.length > 8000) {
                    return false;
                }
                String t = TableScanner.latin1(b);
                return t.contains("ITEM_POTION_HEALING") && t.contains("ASPRITE_WARRIOR");
            });

            int filesPatched = 0;
            int slotsChanged = 0;
            for (Path p : gearFiles) {
                byte[] data = Files.readAllBytes(p);
                List<GearSlot> slots = findGearSlots(data);
                if (slots.size() < 4) {
                    continue;
                }
                List<String> pool = slots.stream().map(s -> s.name).distinct().collect(Collectors.toList());
                if (options.startingSpells) {
                    for (String n : List.of(
                            "ITEM_BOOK_MAGICMISSILE", "ITEM_BOOK_RESURRECT",
                            "ITEM_BOOK_FIREWALL", "ITEM_BOOK_HEALING",
                            "ITEM_BOOK_TELEPORT", "ITEM_BOOK_IDENTIFY")) {
                        if (!pool.contains(n)) {
                            // only add if name fits 31 chars
                            if (n.length() < GEAR_SLOT_SIZE) {
                                pool.add(n);
                            }
                        }
                    }
                }
                if (pool.isEmpty()) {
                    continue;
                }

                byte[] copy = data.clone();
                for (GearSlot slot : slots) {
                    String pick = pool.get(rnd.nextInt(pool.size()));
                    if (options.startingSpells && rnd.nextInt(100) < 35) {
                        List<String> books = pool.stream().filter(n -> n.contains("BOOK")).toList();
                        if (!books.isEmpty()) {
                            pick = books.get(rnd.nextInt(books.size()));
                        }
                    }
                    writeFixedName(copy, slot.offset, pick, GEAR_SLOT_SIZE);
                    if (!pick.equals(slot.name)) {
                        slotsChanged++;
                    }
                }
                if (writePatched(p, copy)) {
                    filesPatched++;
                }
            }
            log.log("[+] Starting gear: " + slotsChanged + " item slots across " + filesPatched + " tables"
                    + (options.startingSpells ? " (spell books biased)" : "") + ".");
            return slotsChanged;
        } catch (Exception e) {
            log.log("[!] Starting gear failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Class starter table stores a recurring u32 near the head of each ~344-byte block
     * (observed values 22/30). Reroll those into goldMin–goldMax.
     */
    public int randomizeStartingGold(Random rnd, RandomizerOptions options) {
        // No stable gold field map yet; prior heuristics could touch wrong ints.
        log.log("[+] Starting gold: skipped (no effect / unsafe without field map).");
        return 0;
    }

    public int randomizeShops(Random rnd) {
        // Disabled: previous implementation rewrote every u32 in 10..50000 range inside
        // TOWN blobs, which corrupts string pointers/lengths and freezes the shop UI.
        log.log("[+] Shops: skipped (unsafe price spray disabled — caused freezes / long strings).");
        return 0;
    }


    public int randomizeMaps(Random rnd) {
        log.log("[+] Maps: skipped (tile shuffle disabled).");
        return 0;
    }

    public int randomizeQuests(Random rnd) {
        log.log("[+] Quests: skipped (use loot only).");
        return 0;
    }

    /**
     * Vagrant Story-style equipment identity shuffle for the PSX DATA1 item
     * table. Cosmetic mode swaps only sprite, OBJ3D mesh key, and equip SFX.
     * Consistent mode moves the same look together with the verified DMIN/AC
     * bundle. Names, attack animations, level gates, class/stat requirements,
     * prices, and every other record field remain in their original row.
     */
    public int randomizeEquipmentModels(Random rnd, boolean withStats) {
        try {
            List<Path> files = findMatching(p -> {
                try {
                    byte[] data = Files.readAllBytes(p);
                    return data.length >= TableScanner.ITEM_RECORD_SIZE
                            && TableScanner.latin1(data).contains("ITEM_");
                } catch (IOException ex) {
                    return false;
                }
            });
            int rowsChanged = 0;
            int filesChanged = 0;
            for (Path file : files) {
                byte[] data = Files.readAllBytes(file);
                List<Integer> records = findEquipmentRecords(data);
                if (records.size() < 2) continue;

                List<byte[]> looks = new ArrayList<>(records.size());
                for (int base : records) {
                    looks.add(copyEquipmentLook(data, base, withStats));
                }
                Collections.shuffle(looks, rnd);
                int changedHere = 0;
                for (int i = 0; i < records.size(); i++) {
                    int base = records.get(i);
                    byte[] before = copyEquipmentLook(data, base, withStats);
                    byte[] replacement = looks.get(i);
                    if (!Arrays.equals(before, replacement)) {
                        writeEquipmentLook(data, base, replacement, withStats);
                        changedHere++;
                    }
                }
                if (changedHere > 0 && writePatched(file, data)) {
                    rowsChanged += changedHere;
                    filesChanged++;
                }
            }
            log.log("[+] Equipment " + (withStats ? "consistent" : "cosmetic")
                    + ": remapped " + rowsChanged + " item models in " + filesChanged
                    + " DATA1 table(s)"
                    + (withStats ? " (DMIN/AC moved; requirements preserved)." : "."));
            return rowsChanged;
        } catch (Exception ex) {
            log.log("[!] Equipment model randomization failed: " + ex.getMessage());
            return 0;
        }
    }

    private static List<Integer> findEquipmentRecords(byte[] data) {
        List<Integer> records = new ArrayList<>();
        String text = new String(data, StandardCharsets.US_ASCII);
        Matcher matcher = ITEM_NAME.matcher(text);
        Set<Integer> seen = new HashSet<>();
        while (matcher.find()) {
            int base = matcher.start() - TableScanner.OFF_ITEM_NAME;
            if (base < 0 || base + TableScanner.ITEM_RECORD_SIZE > data.length || !seen.add(base)) continue;
            String name = matcher.group();
            if (com.serifsystemworks.darkstone.config.RandomizerConstants.isProtectedItem(name)) continue;
            String sprite = readFixedCString(data, base + OFF_ITEM_SPRITE, LEN_ITEM_SPRITE);
            String mesh = readFixedCString(data, base + OFF_ITEM_MESH, LEN_ITEM_MESH);
            if (isEquipmentModel(mesh, name, sprite)) records.add(base);
        }
        return records;
    }

    private static boolean isEquipmentModel(String mesh, String itemName, String sprite) {
        String m = mesh.toUpperCase(Locale.ROOT);
        String n = itemName.toUpperCase(Locale.ROOT);
        // A sprite alone is not enough: potions, food, gold, and quest items
        // have inventory sprites too. Match the verified equip mesh families,
        // then retain the name fallback for unusual weapon/armor variants.
        if (m.startsWith("W") || m.startsWith("A0") || m.startsWith("XBOU")
                || m.equals("STAFF") || m.equals("AMULET") || m.equals("RING")
                || m.equals("BOOK") || m.equals("SCROLL")) return true;
        return n.contains("SWORD") || n.contains("AXE") || n.contains("BOW")
                || n.contains("ARMOR") || n.contains("HELMET") || n.contains("SHIELD")
                || n.contains("HAMMER") || n.contains("STAFF") || n.contains("DAGUE")
                || n.contains("LANCE") || n.contains("HALLE") || n.contains("FAUX")
                || n.contains("MARTEAU") || n.contains("SCEPTRE") || n.contains("RING")
                || n.contains("AMULET") || n.contains("RAGS") || n.contains("BOTTE")
                || n.contains("GANT") || n.contains("MAIL") || n.contains("ROBE");
    }

    private static byte[] copyEquipmentLook(byte[] data, int base, boolean withStats) {
        int length = LEN_ITEM_EQUIP_SFX + LEN_ITEM_SPRITE + LEN_ITEM_MESH + (withStats ? 3 : 0);
        byte[] out = new byte[length];
        int at = 0;
        System.arraycopy(data, base + OFF_ITEM_EQUIP_SFX, out, at, LEN_ITEM_EQUIP_SFX);
        at += LEN_ITEM_EQUIP_SFX;
        System.arraycopy(data, base + OFF_ITEM_SPRITE, out, at, LEN_ITEM_SPRITE);
        at += LEN_ITEM_SPRITE;
        System.arraycopy(data, base + OFF_ITEM_MESH, out, at, LEN_ITEM_MESH);
        if (withStats) {
            System.arraycopy(data, base + TableScanner.OFF_ITEM_DMIN, out,
                    LEN_ITEM_EQUIP_SFX + LEN_ITEM_SPRITE + LEN_ITEM_MESH, 3);
        }
        return out;
    }

    private static void writeEquipmentLook(byte[] data, int base, byte[] source, boolean withStats) {
        int at = 0;
        System.arraycopy(source, at, data, base + OFF_ITEM_EQUIP_SFX, LEN_ITEM_EQUIP_SFX);
        at += LEN_ITEM_EQUIP_SFX;
        System.arraycopy(source, at, data, base + OFF_ITEM_SPRITE, LEN_ITEM_SPRITE);
        at += LEN_ITEM_SPRITE;
        System.arraycopy(source, at, data, base + OFF_ITEM_MESH, LEN_ITEM_MESH);
        if (withStats) {
            System.arraycopy(source, LEN_ITEM_EQUIP_SFX + LEN_ITEM_SPRITE + LEN_ITEM_MESH,
                    data, base + TableScanner.OFF_ITEM_DMIN, 3);
        }
    }

    private static String readFixedCString(byte[] data, int offset, int length) {
        int end = offset;
        int limit = Math.min(data.length, offset + length);
        while (end < limit && data[end] != 0) end++;
        return new String(data, offset, end - offset, StandardCharsets.US_ASCII);
    }

    public int randomizeWeaponStats(Random rnd, RandomizerOptions options) {
        try {
            List<Path> files = findMatching(p -> {
                byte[] b = Files.readAllBytes(p);
                if (b.length < 1000 || b.length > 80_000) return false;
                String t = TableScanner.latin1(b);
                return t.contains("ITBASTARDSWORD") || t.contains("ITARMOR")
                        || t.contains("SWORD") || t.contains("FIREBOW");
            });
            int pairs = 0;
            for (Path p : files) {
                byte[] data = Files.readAllBytes(p);
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                int local = 0;
                for (int off = 0; off + 4 <= data.length; off += 2) {
                    int min = bb.getShort(off) & 0xFFFF;
                    int max = bb.getShort(off + 2) & 0xFFFF;
                    if (min >= 1 && max >= min && max <= 80) {
                        // skip if either side sits in ASCII
                        if (isAsciiNeighborhood(data, off) || isAsciiNeighborhood(data, off + 2)) {
                            continue;
                        }
                        int nmin = options.randomWeapon(rnd);
                        int nmax = options.randomWeapon(rnd);
                        if (nmax < nmin) {
                            int tmp = nmin;
                            nmin = nmax;
                            nmax = tmp;
                        }
                        bb.putShort(off, (short) nmin);
                        bb.putShort(off + 2, (short) nmax);
                        local++;
                        off += 2; // advance past the pair
                    }
                }
                if (local > 0 && writePatched(p, data)) {
                    pairs += local;
                    log.log("    weapons in " + p.getFileName() + ": " + local + " min/max pairs");
                }
            }
            log.log("[+] Weapon stats: " + pairs + " damage pairs -> "
                    + options.weaponMin + "-" + options.weaponMax);
            return pairs;
        } catch (Exception e) {
            log.log("[!] Weapon stats failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Spell rank-like u16 values (1–15) in the spell name table (0021-style) and
     * effect templates, rewritten into skillMin–skillMax (reuses skill range for ranks).
     */
    public int randomizeSpellLevels(Random rnd, RandomizerOptions options) {
        try {
            List<Path> files = findMatching(p -> {
                byte[] b = Files.readAllBytes(p);
                String t = TableScanner.latin1(b);
                return t.contains("SPELLMAGICMISSILE") || t.contains("SPELLFIREBALL")
                        || t.contains("SPELLHEALING");
            });
            int count = 0;
            for (Path p : files) {
                byte[] data = Files.readAllBytes(p);
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                int local = 0;
                // Prefer bytes immediately after null-terminated SPELL* names
                String text = new String(data, java.nio.charset.StandardCharsets.US_ASCII);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("SPELL[A-Z][A-Z]+").matcher(text);
                while (m.find()) {
                    int end = m.end();
                    while (end < data.length && data[end] == 0) {
                        end++;
                    }
                    // single u8 rank often follows padding
                    if (end < data.length) {
                        int v = data[end] & 0xFF;
                        if (v >= 1 && v <= 15) {
                            data[end] = (byte) options.randomSkill(rnd);
                            local++;
                        }
                    }
                    // also a following u16 if small
                    if (end + 2 <= data.length) {
                        int v = bb.getShort(end) & 0xFFFF;
                        if (v >= 1 && v <= 20) {
                            bb.putShort(end, (short) options.randomSkill(rnd));
                            local++;
                        }
                    }
                }
                if (local > 0 && writePatched(p, data)) {
                    count += local;
                    log.log("    spell ranks in " + p.getFileName() + ": " + local);
                }
            }
            log.log("[+] Spell levels: " + count + " ranks -> "
                    + options.skillMin + "-" + options.skillMax);
            return count;
        } catch (Exception e) {
            log.log("[!] Spell levels failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Skill ranks: isolated u8 0–10 in hero class blobs (outside ASCII runs).
     */
    public int randomizeSkillLevels(Random rnd, RandomizerOptions options) {
        try {
            List<Path> files = findMatching(p -> TableScanner.isHero(Files.readAllBytes(p)));
            int count = 0;
            for (Path p : files) {
                byte[] data = Files.readAllBytes(p);
                int local = 0;
                for (int i = 0; i < data.length; i++) {
                    int v = data[i] & 0xFF;
                    if (v < 1 || v > 10) {
                        continue;
                    }
                    if (isAsciiNeighborhood(data, i)) {
                        continue;
                    }
                    // require quiet neighbors (not mid-string, not high binary)
                    int prev = i > 0 ? (data[i - 1] & 0xFF) : 0;
                    int next = i + 1 < data.length ? (data[i + 1] & 0xFF) : 0;
                    if (prev > 32 && prev < 127) continue;
                    if (next > 32 && next < 127) continue;
                    if (prev > 10 && next > 10) continue;
                    data[i] = (byte) options.randomSkill(rnd);
                    local++;
                }
                // cap per file so we don't thrash
                if (local > 200) {
                    // too aggressive — skip write
                    log.log("    skip skills in " + p.getFileName() + " (too many candidates: " + local + ")");
                    continue;
                }
                if (local > 0 && writePatched(p, data)) {
                    count += local;
                    log.log("    skills in " + p.getFileName() + ": " + local);
                }
            }
            log.log("[+] Skill levels: " + count + " ranks -> "
                    + options.skillMin + "-" + options.skillMax);
            return count;
        } catch (Exception e) {
            log.log("[!] Skill levels failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Player starting level fields in class starter table (u16 values 1–10 at block +0 / small headers).
     */
    public int randomizePlayerLevels(Random rnd, RandomizerOptions options) {
        try {
            List<Path> files = findMatching(p -> {
                byte[] b = Files.readAllBytes(p);
                if (b.length < 500 || b.length > 8000) return false;
                String t = TableScanner.latin1(b);
                return t.contains("ASPRITE_WARRIOR") && t.contains("ITEM_POTION_HEALING");
            });
            int count = 0;
            for (Path p : files) {
                byte[] data = Files.readAllBytes(p);
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                int local = 0;
                // class stride ~344; level-like u16 often near start of each block (values 1–8)
                for (int base = 0; base + 8 < data.length; base += 344) {
                    for (int rel : new int[]{0, 4, 8, 12}) {
                        int off = base + rel;
                        if (off + 2 > data.length) continue;
                        int v = bb.getShort(off) & 0xFFFF;
                        if (v >= 1 && v <= 10) {
                            bb.putShort(off, (short) options.randomLevel(rnd));
                            local++;
                        }
                    }
                }
                if (local > 0 && writePatched(p, data)) {
                    count += local;
                    log.log("    player levels in " + p.getFileName() + ": " + local);
                }
            }
            log.log("[+] Player levels: " + count + " fields -> "
                    + options.levelMin + "-" + options.levelMax);
            return count;
        } catch (Exception e) {
            log.log("[!] Player levels failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Enemy power: within size-matched templates, reroll small u16 combat fields (1–50)
     * outside the structural header (skip first 8 bytes).
     */
    public int randomizeEnemyLevels(Random rnd, RandomizerOptions options) {
        try {
            List<Path> files = findMatching(p -> TableScanner.isEnemy(Files.readAllBytes(p)));
            int count = 0;
            for (Path p : files) {
                byte[] data = Files.readAllBytes(p);
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                int local = 0;
                for (int off = 8; off + 2 <= Math.min(data.length, 128); off += 2) {
                    int v = bb.getShort(off) & 0xFFFF;
                    if (v >= 1 && v <= 50 && !isAsciiNeighborhood(data, off)) {
                        bb.putShort(off, (short) options.randomLevel(rnd));
                        local++;
                    }
                }
                if (local > 0 && writePatched(p, data)) {
                    count += local;
                }
            }
            log.log("[+] Enemy levels/power: " + count + " fields in " + files.size()
                    + " templates -> " + options.levelMin + "-" + options.levelMax);
            return count;
        } catch (Exception e) {
            log.log("[!] Enemy levels failed: " + e.getMessage());
            return 0;
        }
    }

    private static boolean isAsciiNeighborhood(byte[] data, int offset) {
        for (int i = Math.max(0, offset - 1); i <= Math.min(data.length - 1, offset + 2); i++) {
            int b = data[i] & 0xFF;
            if (b >= 0x41 && b <= 0x7A) {
                return true;
            }
        }
        return false;
    }


    /**
     * Tightened dungeon / land shuffle.
     * <ul>
     *   <li>LAND* overworld: per-pack same-size FE (56 + known prop sizes); optional cross-land FE56</li>
     *   <li>LEVEL* interiors: per-pack FE + room templates; optional cross within QUEST0/1/2 tier only</li>
     *   <li>LEVEL29/30 and DRAAK excluded unless dungeonsFinal</li>
     *   <li>Cross-land never mixes LAND tiles with dungeon interiors</li>
     *   <li>Only known interior template sizes are shuffled (avoids unique critical blobs)</li>
     * </ul>
     */

    /**
     * Shuffle MO_* encounter name slots across LAND / LEVEL blobs.
     * Same-capacity string slots only — changes which enemy types appear where.
     */
    /** Shared MO_* slot representation for both randomizeEnemyTypes and randomizeEnemyTypesChaotic. */
    private static final class MoSlot {
        final Path file; final int off; final String name; final int capacity;
        MoSlot(Path f, int o, String n, int c) { file = f; off = o; name = n; capacity = c; }
    }

    /**
     * Scans every MO_* dictionary-key reference across matching files. Each
     * slot's capacity is strictly its own original name length + 1 (the NUL
     * terminator) — verified safe to write across the whole capacity because
     * the scan only accepts a match whose very next byte is already NUL
     * (never eats into the following dictionary key's bytes). Quest/boss/
     * DRAAK-named references are excluded entirely — never a source, never a
     * destination — so unique fights stay unique regardless of which
     * enemy-type mode uses this list.
     */
    private List<MoSlot> collectMoSlots() throws IOException {
        List<Path> files = findMatching(p -> {
            byte[] b = Files.readAllBytes(p);
            if (b.length < 32 || b.length > 200_000) return false;
            String t = TableScanner.latin1(b);
            return t.contains("MO_");
        });
        List<MoSlot> slots = new ArrayList<>();
        java.util.regex.Pattern mo = java.util.regex.Pattern.compile("MO_[A-Z0-9_]+");
        for (Path p : files) {
            byte[] data = Files.readAllBytes(p);
            String text = new String(data, java.nio.charset.StandardCharsets.US_ASCII);
            java.util.regex.Matcher m = mo.matcher(text);
            while (m.find()) {
                String name = m.group();
                int end = m.end();
                if (end >= data.length || data[end] != 0) continue;
                int cap = name.length() + 1;
                if (cap < 6 || cap > 24) continue;
                String u = name.toUpperCase(Locale.ROOT);
                if (u.contains("QUEST") || u.contains("BOSS") || u.contains("DRAAK") || u.contains("LORD")) continue;
                slots.add(new MoSlot(p, m.start(), name, cap));
            }
        }
        return slots;
    }

    public int randomizeEnemyTypes(Random rnd) {
        try {
            List<MoSlot> slots = collectMoSlots();
            if (slots.size() < 4) {
                log.log("[+] Enemy types: not enough MO_ slots (" + slots.size() + ").");
                return 0;
            }
            Map<Path, byte[]> dataMap = new HashMap<>();
            for (MoSlot s : slots) {
                dataMap.computeIfAbsent(s.file, p -> {
                    try { return Files.readAllBytes(p); } catch (IOException e) { throw new RuntimeException(e); }
                });
            }
            // Group by capacity; shuffle names within capacity bands
            Map<Integer, List<MoSlot>> byCap = new HashMap<>();
            for (MoSlot s : slots) byCap.computeIfAbsent(s.capacity, k -> new ArrayList<>()).add(s);
            int changed = 0;
            Set<Path> dirty = new HashSet<>();
            for (List<MoSlot> group : byCap.values()) {
                if (group.size() < 2) continue;
                List<String> names = new ArrayList<>();
                for (MoSlot s : group) names.add(s.name);
                Collections.shuffle(names, rnd);
                for (int i = 0; i < group.size(); i++) {
                    MoSlot s = group.get(i);
                    String nn = names.get(i);
                    if (nn.equals(s.name)) continue;
                    if (nn.length() + 1 > s.capacity) continue;
                    byte[] data = dataMap.get(s.file);
                    byte[] nb = nn.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    Arrays.fill(data, s.off, s.off + s.capacity, (byte) 0);
                    System.arraycopy(nb, 0, data, s.off, nb.length);
                    data[s.off + nb.length] = 0;
                    dirty.add(s.file);
                    changed++;
                }
            }
            int fileCount = 0;
            for (Path p : dirty) {
                if (writePatched(p, dataMap.get(p))) fileCount++;
            }
            log.log("[+] Enemy types: " + changed + " MO_ slots in " + fileCount + " blobs.");
            return changed;
        } catch (Exception e) {
            log.log("[!] Enemy type shuffle failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * "Complete randomization" enemy-type mode: any monster type can land in
     * any slot, regardless of name length — goblins next to wizards next to
     * skeleton captains, the actual "mix everything" experience, vs. the tame
     * same-length-only swaps in {@link #randomizeEnemyTypes}. A name is only
     * ever placed into a slot whose original capacity can hold it (no partial
     * writes, no overflow into the next dictionary key) — slots are processed
     * largest-capacity-first so short names don't get starved out of fitting
     * anywhere. Reuses the exact same slot collection as the tame mode, so
     * quest/boss/DRAAK-named references are excluded identically.
     */
    public int randomizeEnemyTypesChaotic(Random rnd) {
        try {
            List<MoSlot> slots = collectMoSlots();
            if (slots.size() < 4) {
                log.log("[+] Enemy types (chaotic): not enough MO_ slots (" + slots.size() + ").");
                return 0;
            }
            Map<Path, byte[]> dataMap = new HashMap<>();
            for (MoSlot s : slots) {
                dataMap.computeIfAbsent(s.file, p -> {
                    try { return Files.readAllBytes(p); } catch (IOException e) { throw new RuntimeException(e); }
                });
            }

            List<String> pool = new ArrayList<>();
            for (MoSlot s : slots) pool.add(s.name);

            // Process slots in random order (NOT sorted by capacity) and pick a
            // RANDOM fitting candidate each time (not "first fitting"). Capacity-
            // sorted processing was tried and rejected this session: smallest-
            // first makes small slots greedily monopolize every short name
            // before large slots get a turn (verified via a controlled synthetic
            // test: 0 cross-length matches out of 11 changes even with a
            // deliberately varied-length dataset); largest-first has the mirror
            // problem, starving small slots (60/205 unplaceable in real testing).
            // True random order + random pick avoids both failure modes.
            List<MoSlot> order = new ArrayList<>(slots);
            Collections.shuffle(order, rnd);

            List<String> remaining = new ArrayList<>(pool);
            int changed = 0;
            int unplaceable = 0;
            Set<Path> dirty = new HashSet<>();

            for (MoSlot s : order) {
                List<Integer> fitting = new ArrayList<>();
                for (int idx = 0; idx < remaining.size(); idx++) {
                    if (remaining.get(idx).length() + 1 <= s.capacity) {
                        fitting.add(idx);
                    }
                }
                if (fitting.isEmpty()) {
                    unplaceable++;
                    continue; // nothing left in the pool fits — leave this slot as-is
                }
                int chosenIdx = fitting.get(rnd.nextInt(fitting.size()));
                String chosen = remaining.remove(chosenIdx);
                if (chosen.equals(s.name)) {
                    continue; // consumed from the pool, but no byte change needed
                }
                byte[] data = dataMap.get(s.file);
                byte[] nb = chosen.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                Arrays.fill(data, s.off, s.off + s.capacity, (byte) 0);
                System.arraycopy(nb, 0, data, s.off, nb.length);
                data[s.off + nb.length] = 0;
                dirty.add(s.file);
                changed++;
            }

            int fileCount = 0;
            for (Path p : dirty) {
                if (writePatched(p, dataMap.get(p))) fileCount++;
            }
            log.log("[+] Enemy types (chaotic): " + changed + " MO_ slots reassigned across all name lengths in "
                    + fileCount + " blobs" + (unplaceable > 0 ? " (" + unplaceable + " slots too small for anything left in the pool)" : "") + ".");
            return changed;
        } catch (Exception e) {
            log.log("[!] Chaotic enemy type shuffle failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Patch extra combat-ish u16 fields on hero and enemy templates:
     * bands for AC (0–80-ish), hit (20–120), speed (5–40), plus existing level range.
     */
    public int randomizeCombatExtras(Random rnd, RandomizerOptions options) {
        try {
            List<Path> files = findMatching(p -> {
                byte[] b = Files.readAllBytes(p);
                if (TableScanner.isUiStringTable(b)) return false;
                return TableScanner.isEnemy(b) || TableScanner.isHero(b) || TableScanner.looksLikeMonster(b);
            });
            int patched = 0;
            int fields = 0;
            for (Path p : files) {
                byte[] data = Files.readAllBytes(p);
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                int local = 0;
                for (int off = 0; off + 2 <= data.length; off += 2) {
                    int val = bb.getShort(off) & 0xFFFF;
                    // skip ASCII neighborhood
                    int prev = off > 0 ? (data[off - 1] & 0xFF) : 0;
                    int next = off + 2 < data.length ? (data[off + 2] & 0xFF) : 0;
                    if ((prev >= 0x20 && prev < 0x7F) || (next >= 0x20 && next < 0x7F)) continue;

                    int neu = -1;
                    // AC-like: small values often 0–60
                    if (val >= 1 && val <= 60 && options.acMax > 0) {
                        // dilute: only ~25% of candidates
                        if (rnd.nextInt(4) == 0) neu = options.randomAc(rnd);
                    }
                    // Hit / accuracy-like mid band
                    else if (val >= 40 && val <= 150) {
                        if (rnd.nextInt(5) == 0) neu = options.randomHit(rnd);
                    }
                    // Speed-like low-mid
                    else if (val >= 6 && val <= 35) {
                        if (rnd.nextInt(5) == 0) neu = options.randomSpeed(rnd);
                    }
                    if (neu >= 0 && neu != val) {
                        bb.putShort(off, (short) neu);
                        local++;
                    }
                }
                if (local > 0 && writePatched(p, data)) {
                    patched++;
                    fields += local;
                }
            }
            log.log("[+] Combat extras: " + fields + " fields in " + patched + " blobs (AC/hit/speed bands).");
            return fields;
        } catch (Exception e) {
            log.log("[!] Combat extras failed: " + e.getMessage());
            return 0;
        }
    }

    public int randomizeDungeons(Random rnd, RandomizerOptions options) {
        try {
            int total = 0;
            List<Path> landFolders = findLandFolders();
            if (landFolders.isEmpty()) {
                landFolders = findFoldersWithFeMaps(8);
            }
            List<Path> landsOnly = new ArrayList<>();
            for (Path folder : landFolders) {
                String n = folder.getFileName().toString().toUpperCase(Locale.ROOT);
                if (n.startsWith("LAND")) {
                    landsOnly.add(folder);
                }
            }

            // 1) Overworld tiles (FE56) per land pack
            if (options.dungeons || options.dungeonsPlus) {
                List<Path> landFe56 = new ArrayList<>();
                DungeonRandomizerEnhanced dre = options.progressiveDifficulty
                        ? new DungeonRandomizerEnhanced(log, rnd.nextLong())
                        : null;
                List<Integer> order = dre != null ? dre.shuffleDungeonBlocks() : null;

                for (int idx = 0; idx < landsOnly.size(); idx++) {
                    Path folder = landsOnly.get(idx);
                    // Dungeons Plus deliberately broadens structural variety across lands.
                    // It still uses the same-size FE56 safety rules and does not alter quest data.
                    boolean includeInCrossLandPool = options.dungeonsCrossLand || options.dungeonsPlus;
                    if (dre != null) {
                        DungeonRandomizerEnhanced.RoomDifficulty tier = dre.pickDifficultyForLand(idx);
                        log.log("    " + folder.getFileName() + " difficulty tier: " + tier);
                        // Harder-tier lands get cross-land structural mixing (more
                        // unpredictable prop variety); easier lands stay shuffled
                        // within themselves, closer to the original layout.
                        includeInCrossLandPool = includeInCrossLandPool
                                || tier == DungeonRandomizerEnhanced.RoomDifficulty.HARD
                                || tier == DungeonRandomizerEnhanced.RoomDifficulty.VERY_HARD
                                || tier == DungeonRandomizerEnhanced.RoomDifficulty.BOSS;
                    }
                    int n = shuffleFolderMapObjects(folder, rnd, options, true,
                            includeInCrossLandPool ? landFe56 : null, null);
                    if (n > 0) {
                        log.log("    " + folder.getFileName() + " (land tiles): " + n);
                    }
                    total += n;
                }
                if (landFe56.size() >= 2) {
                    total += shufflePathContents(landFe56, rnd);
                    log.log("    cross-land FE56: " + landFe56.size()
                            + (dre != null ? " (progressive difficulty: harder-tier lands only, unless dungeonsCrossLand forces all)" : ""));
                }
            }

            // 2) Dungeon doors = structural FE props with fixed counts across LAND packs
            //    (sizes observed in every LAND: 238,300,360,408,414,496,530,544,...)
            if (options.dungeonDoors || options.dungeonsPlus) {
                int doors = shuffleDungeonDoors(rnd, landsOnly);
                total += doors;
                log.log("[+] Dungeon doors: shuffled " + doors + " structural prop blobs across lands.");
            }

            log.log("[+] Dungeons total objects touched: " + total
                    + " (tiles=" + options.dungeons + ", doors=" + options.dungeonDoors
                    + ", cross-land=" + options.dungeonsCrossLand
                    + ", Dungeons Plus=" + options.dungeonsPlus + ").");
            return total;
        } catch (Exception ex) {
            log.log("[!] Dungeon randomization failed: " + ex.getMessage());
            return 0;
        }
    }

    /**
     * Shuffle complete, same-sized static FE map-object records inside their
     * own map packs. FE56 tile headers and records carrying animation names are
     * excluded, so container/opening and break-animation data cannot be mixed.
     * Fake containers, mimics, touch-disappear objects, and traps retain their
     * native behavior; that needs a separately mapped behavior pass.
     */
    public int randomizeStaticObjects(Random rnd, RandomizerOptions options) {
        try {
            int total = 0;
            int folders = 0;
            try (Stream<Path> walk = Files.walk(outputRoot, 8)) {
                List<Path> mapFolders = walk.filter(Files::isDirectory)
                        .filter(folder -> isStaticObjectMapFolder(folder, options.dungeonsFinal))
                        .sorted()
                        .collect(Collectors.toList());
                for (Path folder : mapFolders) {
                    int changed = shuffleFolderStaticObjects(folder, rnd);
                    if (changed > 0) {
                        folders++;
                        total += changed;
                        log.log("    " + outputRoot.relativize(folder) + ": " + changed + " static object records.");
                    }
                }
            }
            log.log("[+] Static objects: shuffled " + total + " records in " + folders
                    + " map pack(s); animation-bearing records skipped.");
            return total;
        } catch (Exception ex) {
            log.log("[!] Static object randomization failed: " + ex.getMessage());
            return 0;
        }
    }

    private boolean isStaticObjectMapFolder(Path folder, boolean includeFinal) {
        String name = folder.getFileName().toString().toUpperCase(Locale.ROOT);
        if (!name.endsWith("_UNPACKED")) return false;
        if (name.startsWith("LAND")) return true;
        if (!name.startsWith("LEVEL")) return false;
        return includeFinal || (!name.startsWith("LEVEL29_") && !name.startsWith("LEVEL30_"));
    }

    private int shuffleFolderStaticObjects(Path folder, Random rnd) throws IOException {
        Map<Long, List<Path>> bySize = new HashMap<>();
        try (Stream<Path> list = Files.list(folder)) {
            for (Path p : list.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bin"))
                    .toList()) {
                byte[] data = Files.readAllBytes(p);
                if (isStaticObjectRecord(data)) {
                    bySize.computeIfAbsent((long) data.length, ignored -> new ArrayList<>()).add(p);
                }
            }
        }
        int changed = 0;
        for (List<Path> group : bySize.values()) {
            if (group.size() >= 2) changed += shufflePathContents(group, rnd);
        }
        return changed;
    }

    private static boolean isStaticObjectRecord(byte[] data) {
        // FE56 is the common map/tile header; larger blobs may include rooms or animation data.
        if (data.length < 112 || data.length > 4096 || data[0] != (byte) 0xFE) return false;
        String ascii = new String(data, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
        return !ascii.contains("ANIM")
                && !ascii.contains("CHESTOPEN")
                && !ascii.contains("CHESTCLOSE")
                && !ascii.contains("BARRELOPEN")
                && !ascii.contains("POTR")
                && !ascii.contains("POTU");
    }

    /**
     * Cross-land shuffle of same-size structural FE (not the 48x FE56 tile headers).
     * These fixed-count props are the practical stand-in for overworld dungeon doors.
     */
    private int shuffleDungeonDoors(Random rnd, List<Path> landFolders) throws IOException {
        // Sizes that appear with the same count in every LAND sample we inventoried
        Set<Integer> doorSizes = Set.of(
                238, 300, 360, 408, 414, 496, 530, 544, 546, 604, 608, 626, 652, 704
        );
        Map<Integer, List<Path>> pool = new HashMap<>();
        for (Path folder : landFolders) {
            try (Stream<Path> list = Files.list(folder)) {
                for (Path p : list.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().endsWith(".bin"))
                        .toList()) {
                    byte[] b = Files.readAllBytes(p);
                    if (b.length < 100 || b[0] != (byte) 0xFE) continue;
                    if (!doorSizes.contains(b.length)) continue;
                    pool.computeIfAbsent(b.length, k -> new ArrayList<>()).add(p);
                }
            }
        }
        int n = 0;
        for (Map.Entry<Integer, List<Path>> e : pool.entrySet()) {
            if (e.getValue().size() < 2) continue;
            int c = shufflePathContents(e.getValue(), rnd);
            if (c > 0) {
                log.log("    door-size " + e.getKey() + ": " + c + " blobs");
            }
            n += c;
        }
        return n;
    }


    private int shuffleFolderMapObjects(Path folder, Random rnd, RandomizerOptions options,
                                        boolean landMode, List<Path> landFe56, List<Path> ignored)
            throws IOException {
        final Set<Integer> landSizes = Set.of(56, 112, 168, 224, 280, 336);
        Map<Long, List<Path>> bySize = new HashMap<>();
        try (Stream<Path> list = Files.list(folder)) {
            for (Path p : list.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".bin"))
                    .toList()) {
                byte[] b = Files.readAllBytes(p);
                if (b.length < 56 || b.length > 20_000) continue;
                boolean isFe = b[0] == (byte) 0xFE;
                if (!isFe) continue;
                if (b.length != 56 && b.length > 512) continue;
                if (!landSizes.contains(b.length) && b.length != 56) continue;
                bySize.computeIfAbsent((long) b.length, k -> new ArrayList<>()).add(p);
                if (isFe && b.length == 56 && landFe56 != null) {
                    landFe56.add(p);
                }
            }
        }
        int folderCount = 0;
        for (Map.Entry<Long, List<Path>> e : bySize.entrySet()) {
            if (e.getValue().size() < 2) continue;
            if (e.getKey() == 56L && landFe56 != null) continue;
            folderCount += shufflePathContents(e.getValue(), rnd);
        }
        return folderCount;
    }

    private int shufflePathContents(List<Path> paths, Random rnd) throws IOException {
        if (paths.size() < 2) return 0;
        List<byte[]> contents = new ArrayList<>(paths.size());
        for (Path p : paths) contents.add(Files.readAllBytes(p));
        Collections.shuffle(contents, rnd);
        int n = 0;
        for (int i = 0; i < paths.size(); i++) {
            if (writePatched(paths.get(i), contents.get(i))) n++;
        }
        return n;
    }

    private List<Path> findLandFolders() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(outputRoot, 8)) {
            walk.filter(Files::isDirectory)
                    .filter(p -> {
                        String n = p.getFileName().toString().toUpperCase(Locale.ROOT);
                        // LAND outdoor + QUEST interior LEVEL packs
                        return n.endsWith("_UNPACKED")
                                && (n.startsWith("LAND") || n.startsWith("LEVEL"));
                    })
                    .sorted()
                    .forEach(out::add);
        }
        return out;
    }

    private List<Path> findFoldersWithFeMaps(int minCount) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(outputRoot, 6)) {
            for (Path dir : walk.filter(Files::isDirectory).toList()) {
                int c = 0;
                try (Stream<Path> list = Files.list(dir)) {
                    for (Path p : list.filter(f -> f.getFileName().toString().endsWith(".bin")).toList()) {
                        byte[] b = Files.readAllBytes(p);
                        if (b.length == 56 && b[0] == (byte) 0xFE) {
                            c++;
                        }
                    }
                } catch (Exception ignored) {
                }
                if (c >= minCount) {
                    out.add(dir);
                }
            }
        }
        return out;
    }


    /**
     * Palette randomizer: find PSX TIM textures with CLUT (16 or 256 RGB555 colors)
     * and either hue-rotate or shuffle entries. Color 0 is left alone (often transparent).
     * Works on whole-bin TIMs (DATA2/TOWN) and embedded TIMs (LAND texture packs).
     */
    public int randomizePalettes(Random rnd, RandomizerOptions options) {
        try {
            int files = 0;
            int cluts = 0;
            int colors = 0;
            try (Stream<Path> walk = Files.walk(outputRoot)) {
                List<Path> bins = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".bin"))
                        .filter(p -> !p.getFileName().toString().startsWith("_"))
                        .collect(Collectors.toList());
                for (Path p : bins) {
                    byte[] data = Files.readAllBytes(p);
                    if (data.length < 32) {
                        continue;
                    }
                    List<int[]> clutRanges = findTimCluts(data);
                    if (clutRanges.isEmpty()) {
                        continue;
                    }
                    int fileColors = 0;
                    for (int[] range : clutRanges) {
                        int off = range[0];
                        int n = range[1];
                        int hue = options.randomIn(rnd, options.paletteHueMin, options.paletteHueMax);
                        if (options.paletteShuffle) {
                            fileColors += shuffleClut(data, off, n, rnd);
                        } else {
                            fileColors += hueShiftClut(data, off, n, hue);
                        }
                        cluts++;
                    }
                    if (fileColors > 0 && writePatched(p, data)) {
                        files++;
                        colors += fileColors;
                    }
                }
            }
            log.log("[+] Palettes: " + cluts + " CLUTs / " + colors + " colors in " + files
                    + " files (" + (options.paletteShuffle ? "shuffle" : "hue-shift") + ").");
            return colors;
        } catch (Exception ex) {
            log.log("[!] Palette randomization failed: " + ex.getMessage());
            return 0;
        }
    }

    /** Returns list of {clutByteOffset, colorCount} for valid TIM CLUTs in data. */
    private static List<int[]> findTimCluts(byte[] data) {
        List<int[]> out = new ArrayList<>();
        // TIM magic may appear on any alignment inside texture packs
        int i = 0;
        while (i + 20 < data.length) {
            if (data[i] == 0x10 && data[i + 1] == 0 && data[i + 2] == 0 && data[i + 3] == 0) {
                int flags = (data[i + 4] & 0xFF)
                        | ((data[i + 5] & 0xFF) << 8)
                        | ((data[i + 6] & 0xFF) << 16)
                        | ((data[i + 7] & 0xFF) << 24);
                boolean hasClut = (flags & 8) != 0;
                int bpp = flags & 7;
                if (hasClut && bpp <= 1) {
                    int pos = i + 8;
                    if (pos + 12 <= data.length) {
                        int clutLen = (data[pos] & 0xFF)
                                | ((data[pos + 1] & 0xFF) << 8)
                                | ((data[pos + 2] & 0xFF) << 16)
                                | ((data[pos + 3] & 0xFF) << 24);
                        int w = (data[pos + 8] & 0xFF) | ((data[pos + 9] & 0xFF) << 8);
                        int h = (data[pos + 10] & 0xFF) | ((data[pos + 11] & 0xFF) << 8);
                        int n = w * h;
                        int clutOff = pos + 12;
                        if (clutLen >= 12 && n >= 16 && n <= 256
                                && clutOff + n * 2 <= data.length
                                && pos + clutLen <= data.length) {
                            out.add(new int[]{clutOff, n});
                            i = Math.max(i + 1, pos + clutLen);
                            continue;
                        }
                    }
                }
            }
            i++;
        }
        return out;
    }

    private static int shuffleClut(byte[] data, int off, int n, Random rnd) {
        // Keep index 0 fixed; shuffle the rest
        List<Integer> colors = new ArrayList<>(n - 1);
        for (int i = 1; i < n; i++) {
            int c = (data[off + i * 2] & 0xFF) | ((data[off + i * 2 + 1] & 0xFF) << 8);
            colors.add(c);
        }
        Collections.shuffle(colors, rnd);
        for (int i = 1; i < n; i++) {
            int c = colors.get(i - 1);
            data[off + i * 2] = (byte) (c & 0xFF);
            data[off + i * 2 + 1] = (byte) ((c >> 8) & 0xFF);
        }
        return n - 1;
    }

    private static int hueShiftClut(byte[] data, int off, int n, int hueDegrees) {
        int changed = 0;
        for (int i = 0; i < n; i++) {
            int c = (data[off + i * 2] & 0xFF) | ((data[off + i * 2 + 1] & 0xFF) << 8);
            if (i == 0 && c == 0) {
                continue; // transparent / black key
            }
            int nc = hueShiftRgb555(c, hueDegrees);
            if (nc != c) {
                data[off + i * 2] = (byte) (nc & 0xFF);
                data[off + i * 2 + 1] = (byte) ((nc >> 8) & 0xFF);
                changed++;
            }
        }
        return changed;
    }

    /** PSX RGB555: R 0-4, G 5-9, B 10-14, STP 15. */
    private static int hueShiftRgb555(int color, int hueDegrees) {
        int r = color & 0x1F;
        int g = (color >> 5) & 0x1F;
        int b = (color >> 10) & 0x1F;
        int stp = color & 0x8000;
        if (r == 0 && g == 0 && b == 0) {
            return color;
        }
        float rf = r / 31f;
        float gf = g / 31f;
        float bf = b / 31f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float h;
        if (delta < 1e-6f) {
            h = 0f;
        } else if (max == rf) {
            h = 60f * (((gf - bf) / delta) % 6f);
        } else if (max == gf) {
            h = 60f * (((bf - rf) / delta) + 2f);
        } else {
            h = 60f * (((rf - gf) / delta) + 4f);
        }
        if (h < 0) {
            h += 360f;
        }
        float s = max <= 0 ? 0 : delta / max;
        float v = max;
        h = (h + hueDegrees) % 360f;
        if (h < 0) {
            h += 360f;
        }
        // HSV -> RGB
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;
        float rr, gg, bb;
        if (h < 60) { rr = c; gg = x; bb = 0; }
        else if (h < 120) { rr = x; gg = c; bb = 0; }
        else if (h < 180) { rr = 0; gg = c; bb = x; }
        else if (h < 240) { rr = 0; gg = x; bb = c; }
        else if (h < 300) { rr = x; gg = 0; bb = c; }
        else { rr = c; gg = 0; bb = x; }
        int nr = Math.max(0, Math.min(31, Math.round((rr + m) * 31)));
        int ng = Math.max(0, Math.min(31, Math.round((gg + m) * 31)));
        int nb = Math.max(0, Math.min(31, Math.round((bb + m) * 31)));
        return stp | (nb << 10) | (ng << 5) | nr;
    }


    /**
     * Shuffle Music/*.RAW contents under the CD root (filenames stay so the game
     * still opens 01.RAW etc., but the PCM data is permuted).
     */
    public int randomizeMusic(Random rnd, RandomizerOptions options) {
        try {
            Path root = options.cdRoot != null ? options.cdRoot : outputRoot;
            List<Path> tracks = findLooseFiles(root, ".RAW");
            // Ignore dummy / silence pads if tiny
            tracks = tracks.stream()
                    .filter(p -> {
                        try {
                            return Files.size(p) > 4096;
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .sorted()
                    .collect(Collectors.toList());
            if (tracks.size() < 2) {
                log.log("[+] Music: need >=2 .RAW tracks under CD (found " + tracks.size()
                        + "). Copy Music/ folder onto the CD path.");
                return 0;
            }
            List<byte[]> contents = new ArrayList<>();
            for (Path p : tracks) {
                contents.add(Files.readAllBytes(p));
            }
            Collections.shuffle(contents, rnd);
            int n = 0;
            for (int i = 0; i < tracks.size(); i++) {
                Files.write(tracks.get(i), contents.get(i));
                n++;
            }
            log.log("[+] Music: shuffled " + n + " RAW tracks under " + root.getFileName() + ".");
            return n;
        } catch (Exception ex) {
            log.log("[!] Music randomization failed: " + ex.getMessage());
            return 0;
        }
    }

    /**
     * "Disable videos" — zero out every .DPS FMV under the CD root so the
     * game has nothing to play back.
     * <p>
     * NOT YET VERIFIED against real hardware/emulator: this assumes the
     * game's FMV player gracefully no-ops on an empty/near-empty .DPS file
     * rather than erroring. {@link #randomizeVideos} shuffles real video
     * payloads between files (known-good data, just relocated), which is a
     * fundamentally safer operation than this — zeroing content is a
     * genuine unknown until tested. A backup of each original is kept
     * alongside (".DPS.bak") so this is reversible either way.
     */
    public int disableVideos(Path cdRoot) {
        try {
            Path root = cdRoot != null ? cdRoot : outputRoot;
            List<Path> vids = findLooseFiles(root, ".DPS");
            int n = 0;
            for (Path p : vids) {
                Path bak = p.resolveSibling(p.getFileName() + ".bak");
                if (!Files.exists(bak)) {
                    Files.copy(p, bak);
                }
                // Minimal placeholder rather than a 0-byte file, in case the
                // player expects some header before deciding to bail out.
                Files.write(p, new byte[16]);
                n++;
            }
            log.log("[+] Videos: disabled " + n + " .DPS file(s) under " + root.getFileName()
                    + " (originals backed up as .DPS.bak). UNVERIFIED — please confirm this doesn't crash FMV playback.");
            return n;
        } catch (Exception ex) {
            log.log("[!] Disable videos failed: " + ex.getMessage());
            return 0;
        }
    }

    /**
     * Shuffle CINE/*.DPS (and any .DPS) FMV payloads. Filenames stay; video data swaps.
     */
    public int randomizeVideos(Random rnd, RandomizerOptions options) {
        try {
            Path root = options.cdRoot != null ? options.cdRoot : outputRoot;
            List<Path> vids = findLooseFiles(root, ".DPS");
            vids = vids.stream()
                    .filter(p -> {
                        try {
                            return Files.size(p) > 8192;
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .sorted()
                    .collect(Collectors.toList());
            if (vids.size() < 2) {
                log.log("[+] Videos: need >=2 .DPS under CD (found " + vids.size()
                        + "). Copy CINE/ folder onto the CD path.");
                return 0;
            }
            List<byte[]> contents = new ArrayList<>();
            for (Path p : vids) {
                contents.add(Files.readAllBytes(p));
            }
            Collections.shuffle(contents, rnd);
            int n = 0;
            for (int i = 0; i < vids.size(); i++) {
                Files.write(vids.get(i), contents.get(i));
                n++;
            }
            log.log("[+] Videos: shuffled " + n + " DPS cinematics under " + root.getFileName() + ".");
            return n;
        } catch (Exception ex) {
            log.log("[!] Video randomization failed: " + ex.getMessage());
            return 0;
        }
    }

    private List<Path> findLooseFiles(Path root, String extension) throws IOException {
        String ext = extension.toUpperCase(Locale.ROOT);
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root, 6)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toUpperCase(Locale.ROOT).endsWith(ext))
                    .collect(Collectors.toList());
        }
    }

    public void exportForPc() {
        if (outputRoot == null) {
            log.log("[!] Error: Set output directory first.");
            return;
        }
        log.log("=== Exporting PC port structure ===");
        try {
            Path pcRoot = outputRoot.getParent() != null
                    ? outputRoot.getParent().resolve("DarkstonePC_Port")
                    : outputRoot.resolve("DarkstonePC_Port");
            Files.createDirectories(pcRoot);
            StringBuilder manifest = new StringBuilder("# Darkstone PSX Asset Export\n\n");
            for (String coreFolder : new String[]{"DATA1_unpacked", "DATA2_unpacked", "DRAAK_unpacked", "TOWN_unpacked"}) {
                Path src = outputRoot.resolve(coreFolder);
                if (!Files.exists(src)) {
                    continue;
                }
                Path dest = pcRoot.resolve(coreFolder);
                Files.createDirectories(dest);
                try (Stream<Path> stream = Files.list(src)) {
                    stream.filter(Files::isRegularFile)
                            .filter(f -> f.getFileName().toString().endsWith(".bin"))
                            .forEach(f -> {
                                try {
                                    Files.copy(f, dest.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                                } catch (Exception ignored) {
                                }
                            });
                }
                long fileCount;
                try (Stream<Path> listed = Files.list(dest)) {
                    fileCount = listed.count();
                }
                manifest.append(String.format("Folder: %-16s | Files: %d%n", coreFolder, fileCount));
            }
            Files.writeString(pcRoot.resolve("manifest.txt"), manifest.toString());
            log.analysis(manifest.toString());
            log.log("Export complete: " + pcRoot);
        } catch (Exception e) {
            log.log("Export error: " + e.getMessage());
        }
    }

    private static final class GearSlot {
        final int offset;
        final String name;

        GearSlot(int offset, String name) {
            this.offset = offset;
            this.name = name;
        }
    }

    private static List<GearSlot> findGearSlots(byte[] data) {
        List<GearSlot> slots = new ArrayList<>();
        String text = new String(data, StandardCharsets.US_ASCII);
        Matcher m = ITEM_NAME.matcher(text);
        while (m.find()) {
            slots.add(new GearSlot(m.start(), m.group()));
        }
        return slots;
    }

    private static void writeFixedName(byte[] data, int offset, String name, int slotSize) {
        if (offset < 0 || offset + slotSize > data.length) {
            return;
        }
        byte[] raw = name.getBytes(StandardCharsets.US_ASCII);
        int n = Math.min(raw.length, slotSize - 1);
        for (int i = 0; i < slotSize; i++) {
            data[offset + i] = 0;
        }
        System.arraycopy(raw, 0, data, offset, n);
    }

    private boolean writePatched(Path binFile, byte[] data) {
        try {
            Path meta = binFile.getParent() != null
                    ? binFile.getParent().resolve(PsmArchive.META_FILE)
                    : null;
            if (meta != null && Files.isRegularFile(meta)) {
                return PsmArchive.patchBlobInPlace(binFile, data, log);
            }
            Files.write(binFile, data);
            return true;
        } catch (Exception e) {
            log.log("[!] writePatched " + binFile.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    private interface FileMatch {
        boolean test(Path path) throws Exception;
    }

    private List<Path> findMatching(FileMatch match) throws IOException {
        try (Stream<Path> walk = Files.walk(outputRoot)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".bin") && !n.startsWith("_");
                    })
                    .filter(p -> {
                        try {
                            return match.test(p);
                        } catch (Exception e) {
                            return false;
                        }
                    }).collect(Collectors.toList());
        }
    }

    private static void shuffleBytes(byte[] data, Random rnd) {
        for (int i = data.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            byte temp = data[i];
            data[i] = data[j];
            data[j] = temp;
        }
    }
}
