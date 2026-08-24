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
        log.log("=================================================");

        if (options.loot) {
            randomizeLoot(rnd);
        }
        if (options.enemies) {
            randomizeEnemies(rnd);
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
        if (options.shops) {
            randomizeShops(rnd);
        }
        if (options.maps) {
            randomizeMaps(rnd);
        }
        if (options.dungeons) {
            randomizeDungeons(rnd, options);
        }
        if (options.palettes) {
            randomizePalettes(rnd, options);
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
                // Protect keys / critical quest gates by default
                String u = name.toUpperCase(Locale.ROOT);
                if (u.contains("KEY") || u.contains("CLEF") || u.contains("FALSEKEY")) {
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
            for (Path p : enemyFiles) {
                byte[] b = Files.readAllBytes(p);
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
                    + " (monster-filtered=" + monsters.size() + ", spell/other=" + others.size() + ").");
            return count;
        } catch (Exception e) {
            log.log("[!] Enemy randomization failed: " + e.getMessage());
            return 0;
        }
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
        try {
            List<Path> gearFiles = findMatching(p -> {
                byte[] b = Files.readAllBytes(p);
                if (b.length < 500 || b.length > 8000) {
                    return false;
                }
                String t = TableScanner.latin1(b);
                return t.contains("ITEM_POTION_HEALING") && t.contains("ASPRITE_WARRIOR");
            });

            int changed = 0;
            for (Path p : gearFiles) {
                byte[] data = Files.readAllBytes(p);
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                // Walk 344-byte class strides; gold-like field often at +48 within block
                int localChanges = 0;
                for (int base = 0; base + 52 < data.length; base += 344) {
                    int off = base + 48;
                    if (off + 4 > data.length) {
                        break;
                    }
                    int val = bb.getInt(off);
                    if (val >= 10 && val <= 200) {
                        bb.putInt(off, options.randomGold(rnd));
                        localChanges++;
                    }
                }
                if (localChanges > 0 && writePatched(p, data)) {
                    changed += localChanges;
                    log.log("    gold in " + p.getFileName() + ": " + localChanges + " class(es) -> "
                            + options.goldMin + "-" + options.goldMax);
                }
            }
            log.log("[+] Starting gold: updated " + changed + " class entries.");
            return changed;
        } catch (Exception e) {
            log.log("[!] Starting gold failed: " + e.getMessage());
            return 0;
        }
    }

    public int randomizeShops(Random rnd) {
        try {
            List<Path> shopFiles = findMatching(p -> TableScanner.isShop(p, Files.readAllBytes(p)));
            int updatedShops = 0;
            for (Path p : shopFiles) {
                byte[] data = Files.readAllBytes(p);
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i + 4 <= data.length; i += 4) {
                    int itemOrPrice = bb.getInt(i);
                    if (itemOrPrice > 10 && itemOrPrice < 50_000) {
                        double multiplier = 0.5 + (rnd.nextDouble() * 1.7);
                        int newPrice = Math.max(1, (int) (itemOrPrice * multiplier));
                        bb.putInt(i, newPrice);
                    }
                }
                if (writePatched(p, data)) {
                    updatedShops++;
                }
            }
            log.log("[+] Shop & price randomization: rebalanced " + updatedShops + " town tables.");
            return updatedShops;
        } catch (Exception e) {
            log.log("[!] Shop randomization failed: " + e.getMessage());
            return 0;
        }
    }

    public int randomizeMaps(Random rnd) {
        try {
            List<Path> mapFiles = findMatching(p -> TableScanner.isMap(Files.readAllBytes(p)));
            if (mapFiles.size() < 2) {
                log.log("[+] Map randomization: not enough map headers (" + mapFiles.size() + ").");
                return 0;
            }
            List<byte[]> contents = new ArrayList<>();
            for (Path p : mapFiles) {
                contents.add(Files.readAllBytes(p));
            }
            Collections.shuffle(contents, rnd);
            int n = 0;
            for (int i = 0; i < mapFiles.size(); i++) {
                if (writePatched(mapFiles.get(i), contents.get(i))) {
                    n++;
                }
            }
            log.log("[+] Map randomization: reordered " + n + " level layout headers.");
            return n;
        } catch (Exception e) {
            log.log("[!] Map randomization failed: " + e.getMessage());
            return 0;
        }
    }

    public int randomizeQuests(Random rnd) {
        try {
            List<Path> questFiles = findMatching(p -> TableScanner.isQuest(Files.readAllBytes(p)));
            if (questFiles.size() < 2) {
                log.log("[+] Quest randomization: not enough targets (" + questFiles.size() + ").");
                return 0;
            }
            List<byte[]> contents = new ArrayList<>();
            for (Path p : questFiles) {
                contents.add(Files.readAllBytes(p));
            }
            Collections.shuffle(contents, rnd);
            int n = 0;
            for (int i = 0; i < questFiles.size(); i++) {
                if (writePatched(questFiles.get(i), contents.get(i))) {
                    n++;
                }
            }
            log.log("[+] Quest item randomization: swapped " + n + " quest blobs.");
            return n;
        } catch (Exception e) {
            log.log("[!] Quest randomization failed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * QoL: on the extracted CD folder, rename common intro/movie streams so the game
     * cannot open them. User still rebuilds the ISO with CDImg afterward.
     */
    public int disableVideos(Path cdRoot) {
        if (cdRoot == null || !Files.isDirectory(cdRoot)) {
            log.log("[!] disableVideos: CD folder not set.");
            return 0;
        }
        try {
            List<Path> hits = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(cdRoot, 6)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String n = p.getFileName().toString().toUpperCase(Locale.ROOT);
                    if (n.endsWith(".STR") || n.endsWith(".XA")
                            || n.contains("MOVIE") || n.contains("INTRO")
                            || n.contains("FMV") || n.contains("CINE")) {
                        hits.add(p);
                    }
                });
            }
            int n = 0;
            for (Path p : hits) {
                Path bak = p.resolveSibling(p.getFileName().toString() + ".vidbak");
                if (!Files.exists(bak)) {
                    Files.move(p, bak);
                    n++;
                    log.log("    disabled video: " + cdRoot.relativize(p));
                }
            }
            log.log("[+] Disable videos: " + n + " file(s) renamed to *.vidbak on CD folder.");
            if (n == 0) {
                log.log("    (No .STR/.XA/MOVIE/INTRO files found — intros may be audio tracks only.)");
            }
            return n;
        } catch (Exception e) {
            log.log("[!] disableVideos failed: " + e.getMessage());
            return 0;
        }
    }


    /**
     * Weapon damage-like pairs: consecutive u16 (min,max) with 1 <= min <= max <= 80
     * inside item string tables. Rewritten into weaponMin–weaponMax (min <= max).
     */
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
     * Dungeon / land shuffle for LAND* (overworld) and LEVEL* (QUEST0/1/2 interiors).
     * <ul>
     *   <li>Within each archive: shuffle same-size FE blobs (56-byte headers + larger objects)</li>
     *   <li>Optional cross-land: pool all 56-byte FE headers across LAND* and reshuffle</li>
     * </ul>
     * Size is always preserved so PSM in-place patch stays valid.
     */
    public int randomizeDungeons(Random rnd, RandomizerOptions options) {
        try {
            List<Path> landFolders = findLandFolders();
            if (landFolders.isEmpty()) {
                // fall back: any unpacked folder with many FE-56 blobs
                landFolders = findFoldersWithFeMaps(8);
            }
            if (landFolders.isEmpty()) {
                log.log("[+] Dungeons: no LAND*/LEVEL* folders found (unpack LANDS + QUEST0/1/2 first).");
                return 0;
            }

            int total = 0;
            List<Path> allFe56 = new ArrayList<>();

            for (Path folder : landFolders) {
                Map<Long, List<Path>> bySize = new HashMap<>();
                try (Stream<Path> list = Files.list(folder)) {
                    for (Path p : list.filter(Files::isRegularFile)
                            .filter(f -> f.getFileName().toString().endsWith(".bin"))
                            .toList()) {
                        byte[] b = Files.readAllBytes(p);
                        if (b.length < 56 || b.length > 200_000) {
                            continue; // skip tiny noise and huge mesh/texture blobs
                        }
                        boolean isFe = b[0] == (byte) 0xFE;
                        // Interior room/prop templates common in QUEST LEVEL packs
                        boolean isInteriorTemplate = b.length == 664 || b.length == 948
                                || b.length == 1332 || b.length == 1562
                                || b.length == 304 || b.length == 1252;
                        if (isFe || isInteriorTemplate) {
                            bySize.computeIfAbsent((long) b.length, k -> new ArrayList<>()).add(p);
                            if (isFe && b.length == 56) {
                                allFe56.add(p);
                            }
                        }
                    }
                }

                int folderCount = 0;
                for (Map.Entry<Long, List<Path>> e : bySize.entrySet()) {
                    List<Path> group = e.getValue();
                    if (group.size() < 2) {
                        continue;
                    }
                    // When cross-land is on, skip local 56-byte shuffle (done globally later)
                    if (options.dungeonsCrossLand && e.getKey() == 56L) {
                        continue;
                    }
                    List<byte[]> contents = new ArrayList<>();
                    for (Path p : group) {
                        contents.add(Files.readAllBytes(p));
                    }
                    Collections.shuffle(contents, rnd);
                    for (int i = 0; i < group.size(); i++) {
                        if (writePatched(group.get(i), contents.get(i))) {
                            folderCount++;
                        }
                    }
                }
                if (folderCount > 0) {
                    log.log("    " + folder.getFileName() + ": shuffled " + folderCount + " FE objects");
                }
                total += folderCount;
            }

            if (options.dungeonsCrossLand && allFe56.size() >= 2) {
                List<byte[]> contents = new ArrayList<>();
                for (Path p : allFe56) {
                    contents.add(Files.readAllBytes(p));
                }
                Collections.shuffle(contents, rnd);
                int n = 0;
                for (int i = 0; i < allFe56.size(); i++) {
                    if (writePatched(allFe56.get(i), contents.get(i))) {
                        n++;
                    }
                }
                log.log("    cross-land: shuffled " + n + " x 56-byte FE headers across lands");
                total += n;
            }

            long levelFolders = landFolders.stream()
                    .filter(p -> p.getFileName().toString().toUpperCase(Locale.ROOT).startsWith("LEVEL"))
                    .count();
            long landOnly = landFolders.size() - levelFolders;
            log.log("[+] Dungeons: " + total + " map objects across " + landFolders.size()
                    + " folder(s) (lands=" + landOnly + ", interiors=" + levelFolders + ")"
                    + (options.dungeonsCrossLand ? " (cross-pack FE56 on)" : " (per-pack)") + ".");
            return total;
        } catch (Exception ex) {
            log.log("[!] Dungeon randomization failed: " + ex.getMessage());
            return 0;
        }
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
