package com.serifsystemworks.darkstone.pc;

import com.serifsystemworks.darkstone.engine.LogSink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

/**
 * PC Darkstone randomizer — edits designer TXT exports under PCLASS/.
 * Writes to outputRoot (or in-place under gameRoot/PCLASS when output is null).
 */
public final class PcRandomizerEngine {

    private final LogSink log;

    public PcRandomizerEngine(LogSink log) {
        this.log = log;
    }

    public void run(PcOptions options) throws IOException {
        if (options.gameRoot == null || !Files.isDirectory(options.gameRoot)) {
            throw new IllegalStateException("Select the Darkstone PC game folder (contains PCLASS/).");
        }
        Path pclass = resolvePclass(options.gameRoot);
        Path outPclass = options.outputRoot != null
                ? options.outputRoot.resolve("PCLASS")
                : pclass;
        Files.createDirectories(outPclass);

        Random rnd = options.random();
        log.log("=================================================");
        log.log("   DARKSTONE PC RANDOMIZER");
        log.log("   Seed : " + options.seedText);
        log.log("   Hash : " + PcOptions.seedFromString(options.seedText));
        log.log("   Preset: " + options.preset);
        log.log("=================================================");

        // Backup originals into output once
        backupIfNeeded(pclass, outPclass, "MONSTER.TXT");
        backupIfNeeded(pclass, outPclass, "OBJECT.TXT");
        backupIfNeeded(pclass, outPclass, "PCLASS.TXT");

        if (options.monsters) {
            randomizeMonsters(pclass, outPclass, options, rnd);
        }
        if (options.items) {
            randomizeItems(pclass, outPclass, options, rnd);
        }
        if (options.playerClasses) {
            randomizePlayerClasses(pclass, outPclass, options, rnd);
        }
        if (options.patchDat) {
            patchMonsterDat(options.gameRoot, options.outputRoot, options, rnd);
            patchItemDat(options.gameRoot, options.outputRoot, options, rnd);
        }
        if (options.landProps) {
            randomizeLandProps(options.gameRoot, options.outputRoot, rnd);
        }
        if (options.questScripts || options.questRewards) {
            randomizeQuestScripts(options.gameRoot, options.outputRoot, options, rnd);
        }

        Path seedFile = (options.outputRoot != null ? options.outputRoot : options.gameRoot)
                .resolve("darkstone_pc_seed_" + sanitize(options.seedText) + ".txt");
        Files.writeString(seedFile,
                "seed=" + options.seedText + "\n"
                        + "hash=" + PcOptions.seedFromString(options.seedText) + "\n"
                        + "preset=" + options.preset + "\n"
                        + "platform=PC\n");
        log.log("Seed written: " + seedFile);
        log.log("=================================================");
        log.log("   PC RANDOMIZATION COMPLETE");
        log.log("   Copy PCLASS/*.TXT back into the game if you used an output folder.");
        log.log("=================================================");
    }

    private static Path resolvePclass(Path gameRoot) {
        Path direct = gameRoot.resolve("PCLASS");
        if (Files.isDirectory(direct)) return direct;
        // allow selecting PCLASS itself
        if (gameRoot.getFileName() != null
                && gameRoot.getFileName().toString().equalsIgnoreCase("PCLASS")) {
            return gameRoot;
        }
        return direct;
    }

    private void backupIfNeeded(Path srcDir, Path outDir, String name) throws IOException {
        Path src = srcDir.resolve(name);
        Path dst = outDir.resolve(name);
        Path bak = outDir.resolve(name + ".bak");
        if (!Files.isRegularFile(src) && !Files.isRegularFile(dst)) {
            return;
        }
        // First run: snapshot vanilla into .bak
        if (!Files.exists(bak)) {
            Path from = Files.isRegularFile(src) ? src : dst;
            Files.copy(from, bak, StandardCopyOption.REPLACE_EXISTING);
            log.log("Backup: " + bak.getFileName());
        }
        // Always re-seed working copy from .bak so each Randomize is deterministic
        Files.copy(bak, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    private void randomizeMonsters(Path srcDir, Path outDir, PcOptions o, Random rnd) throws IOException {
        Path file = outDir.resolve("MONSTER.TXT");
        if (!Files.isRegularFile(file)) {
            file = srcDir.resolve("MONSTER.TXT");
        }
        if (!Files.isRegularFile(file)) {
            log.log("[!] MONSTER.TXT not found");
            return;
        }
        // Always load from bak if present for clean re-roll
        Path bak = outDir.resolve("MONSTER.TXT.bak");
        Path loadFrom = Files.isRegularFile(bak) ? bak : file;
        if (!Files.isRegularFile(loadFrom)) loadFrom = srcDir.resolve("MONSTER.TXT");

        TsvTable table = TsvTable.load(loadFrom);
        int changed = 0;

        if (o.shuffleMonsterStats) {
            List<int[]> packs = new ArrayList<>();
            for (TsvTable.Row row : table.rows) {
                packs.add(new int[]{
                        row.getInt("LMIN", 1),
                        row.getInt("LMAX", 1),
                        row.getInt("DMIN", 1),
                        row.getInt("DMAX", 1),
                        row.getInt("AC", 0),
                        row.getInt("TOHIT", 40),
                        row.getInt("SPEED", 400)
                });
            }
            Collections.shuffle(packs, rnd);
            for (int i = 0; i < table.rows.size(); i++) {
                TsvTable.Row row = table.rows.get(i);
                int[] p = packs.get(i);
                row.setInt("LMIN", p[0]);
                row.setInt("LMAX", Math.max(p[0], p[1]));
                row.setInt("DMIN", p[2]);
                row.setInt("DMAX", Math.max(p[2], p[3]));
                row.setInt("AC", p[4]);
                row.setInt("TOHIT", p[5]);
                row.setInt("SPEED", p[6]);
                changed++;
            }
        }

        if (o.rangeRollMonsters) {
            for (TsvTable.Row row : table.rows) {
                int dmin = o.randomIn(rnd, o.dmgMin, o.dmgMax);
                int dmax = o.randomIn(rnd, dmin, o.dmgMax);
                row.setInt("DMIN", dmin);
                row.setInt("DMAX", dmax);
                row.setInt("AC", o.randomIn(rnd, o.acMin, o.acMax));
                int lmin = o.randomIn(rnd, o.levelMin, o.levelMax);
                int lmax = o.randomIn(rnd, lmin, o.levelMax);
                row.setInt("LMIN", lmin);
                row.setInt("LMAX", lmax);
                changed++;
            }
        }

        Path dest = outDir.resolve("MONSTER.TXT");
        table.save(dest);
        log.log("[+] Monsters: updated " + changed + " rows -> " + dest.getFileName());
    }

    private void randomizeItems(Path srcDir, Path outDir, PcOptions o, Random rnd) throws IOException {
        Path bak = outDir.resolve("OBJECT.TXT.bak");
        Path loadFrom = Files.isRegularFile(bak) ? bak
                : (Files.isRegularFile(outDir.resolve("OBJECT.TXT")) ? outDir.resolve("OBJECT.TXT")
                : srcDir.resolve("OBJECT.TXT"));
        if (!Files.isRegularFile(loadFrom)) {
            log.log("[!] OBJECT.TXT not found");
            return;
        }
        TsvTable table = TsvTable.load(loadFrom);
        int changed = 0;

        // Only shuffle combat-ish items (have damage or AC)
        List<Integer> combatIdx = new ArrayList<>();
        for (int i = 0; i < table.rows.size(); i++) {
            TsvTable.Row row = table.rows.get(i);
            int dmax = row.getInt("DMAX", 0);
            int ac = row.getInt("AC", 0);
            if (dmax > 0 || ac > 0) {
                combatIdx.add(i);
            }
        }

        if (o.shuffleItemStats && combatIdx.size() >= 2) {
            List<int[]> packs = new ArrayList<>();
            for (int i : combatIdx) {
                TsvTable.Row row = table.rows.get(i);
                packs.add(new int[]{
                        row.getInt("DMIN", 0),
                        row.getInt("DMAX", 0),
                        row.getInt("AC", 0),
                        row.getInt("LEVEL", 0),
                        row.getInt("DUR", 0)
                });
            }
            Collections.shuffle(packs, rnd);
            for (int n = 0; n < combatIdx.size(); n++) {
                TsvTable.Row row = table.rows.get(combatIdx.get(n));
                int[] p = packs.get(n);
                row.setInt("DMIN", p[0]);
                row.setInt("DMAX", Math.max(p[0], p[1]));
                row.setInt("AC", p[2]);
                row.setInt("LEVEL", p[3]);
                if (p[4] > 0) row.setInt("DUR", p[4]);
                changed++;
            }
        }

        if (o.rangeRollItems) {
            for (int i : combatIdx) {
                TsvTable.Row row = table.rows.get(i);
                if (row.getInt("DMAX", 0) > 0) {
                    int dmin = o.randomIn(rnd, o.dmgMin, o.dmgMax);
                    int dmax = o.randomIn(rnd, dmin, o.dmgMax);
                    row.setInt("DMIN", dmin);
                    row.setInt("DMAX", dmax);
                }
                if (row.getInt("AC", 0) > 0) {
                    row.setInt("AC", o.randomIn(rnd, Math.max(1, o.acMin), o.acMax));
                }
                changed++;
            }
        }

        table.save(outDir.resolve("OBJECT.TXT"));
        log.log("[+] Items: updated " + changed + " combat rows (" + combatIdx.size()
                + " candidates) -> OBJECT.TXT");
    }

    private void randomizePlayerClasses(Path srcDir, Path outDir, PcOptions o, Random rnd) throws IOException {
        Path bak = outDir.resolve("PCLASS.TXT.bak");
        Path loadFrom = Files.isRegularFile(bak) ? bak
                : (Files.isRegularFile(outDir.resolve("PCLASS.TXT")) ? outDir.resolve("PCLASS.TXT")
                : srcDir.resolve("PCLASS.TXT"));
        if (!Files.isRegularFile(loadFrom)) {
            log.log("[!] PCLASS.TXT not found");
            return;
        }
        // PCLASS is row-oriented attributes × 8 class columns — different layout
        List<String> lines = Files.readAllLines(loadFrom, TsvTable.CHARSET);
        if (lines.size() < 3) {
            log.log("[!] PCLASS.TXT too short");
            return;
        }
        // Find BASE_* rows and shuffle values across the 8 classes within each row pair groups
        List<String> out = new ArrayList<>(lines);
        String[] baseRows = {
                "BASE_STRENGTH", "BASE_MAGIC", "BASE_DEXTERITY", "BASE_VITALITY",
                "BASE_LIFE", "BASE_MANA"
        };
        int touched = 0;
        for (String attr : baseRows) {
            for (int i = 0; i < out.size(); i++) {
                if (!out.get(i).startsWith(attr + "\t") && !out.get(i).startsWith(attr + " ")) {
                    continue;
                }
                String[] parts = out.get(i).split("\t", -1);
                if (parts.length < 9) break;
                List<String> vals = new ArrayList<>();
                for (int c = 1; c <= 8; c++) {
                    vals.add(parts[c].trim());
                }
                Collections.shuffle(vals, rnd);
                StringBuilder nb = new StringBuilder(parts[0]);
                for (String v : vals) {
                    nb.append('\t').append(v);
                }
                // preserve trailing tabs if any
                for (int c = 9; c < parts.length; c++) {
                    nb.append('\t').append(parts[c]);
                }
                out.set(i, nb.toString());
                touched++;
                break;
            }
        }
        Files.write(outDir.resolve("PCLASS.TXT"), out, TsvTable.CHARSET);
        log.log("[+] Player classes: shuffled " + touched + " BASE_* rows across 8 classes -> PCLASS.TXT");
    }


    /**
     * Runtime monster stats live in MONSTERCLASS.DAT (not only MONSTER.TXT).
     * Relative to each record name: LMIN@68 LMAX@72 AC@76 TOHIT@80 DMIN@84 DMAX@88 SPEED@534 (s16 LE).
     */
    private void patchMonsterDat(Path gameRoot, Path outputRoot, PcOptions o, Random rnd) throws IOException {
        Path src = gameRoot.resolve("MONSTERCLASS.DAT");
        if (!Files.isRegularFile(src)) {
            // sometimes under data/
            Path alt = gameRoot.resolve("data").resolve("MONSTERCLASS.DAT");
            if (Files.isRegularFile(alt)) src = alt;
        }
        if (!Files.isRegularFile(src)) {
            log.log("[!] MONSTERCLASS.DAT not found next to PCLASS — skip DAT monster patch.");
            return;
        }
        Path outDir = outputRoot != null ? outputRoot : src.getParent();
        Files.createDirectories(outDir);
        Path bak = outDir.resolve("MONSTERCLASS.DAT.bak");
        Path dst = outDir.resolve("MONSTERCLASS.DAT");
        if (!Files.exists(bak)) {
            Files.copy(src, bak, StandardCopyOption.REPLACE_EXISTING);
            log.log("Backup: MONSTERCLASS.DAT.bak");
        }
        byte[] data = Files.readAllBytes(bak);
        final int stride = 584;
        final int base = 8;
        int patched = 0;
        for (int i = base; i + 90 < data.length; i += stride) {
            if (data[i] < 'A' || data[i] > 'Z') {
                continue;
            }
            int tohit = shortAt(data, i + 80);
            int lmin = shortAt(data, i + 68);
            if (tohit < 0 || tohit > 250 || lmin < 0 || lmin > 5000) {
                continue;
            }
            if (!(o.shuffleMonsterStats || o.rangeRollMonsters)) {
                continue;
            }
            int dmin = shortAt(data, i + 84);
            int dmax = shortAt(data, i + 88);
            int ac = shortAt(data, i + 76);
            int nlmin = lmin;
            int nlmax = shortAt(data, i + 72);
            if (o.rangeRollMonsters) {
                dmin = o.randomIn(rnd, o.dmgMin, o.dmgMax);
                dmax = o.randomIn(rnd, dmin, o.dmgMax);
                ac = o.randomIn(rnd, o.acMin, o.acMax);
                nlmin = o.randomIn(rnd, o.levelMin, o.levelMax);
                nlmax = o.randomIn(rnd, nlmin, o.levelMax);
            } else if (o.shuffleMonsterStats) {
                dmin = Math.max(0, dmin + o.randomIn(rnd, -6, 8));
                dmax = Math.max(dmin, dmax + o.randomIn(rnd, -6, 10));
                ac = Math.max(0, ac + o.randomIn(rnd, -10, 14));
                nlmin = Math.max(1, nlmin + o.randomIn(rnd, -10, 15));
                nlmax = Math.max(nlmin, nlmax + o.randomIn(rnd, -10, 15));
            }
            putShort(data, i + 68, (short) nlmin);
            putShort(data, i + 72, (short) nlmax);
            putShort(data, i + 76, (short) ac);
            putShort(data, i + 84, (short) dmin);
            putShort(data, i + 88, (short) dmax);
            patched++;
        }
        Files.write(dst, data);
        log.log("[+] MONSTERCLASS.DAT: patched " + patched + " monster records -> " + dst.getFileName());
        log.log("    Install: copy into Darkstone\\data\\ (or rebuild DATA.MTF).");
    }

    /**
     * ITEMOBJECT.DAT: relative to IT* name, DMIN@184 DMAX@188 AC@186 (s16) observed on samples.
     */
    private void patchItemDat(Path gameRoot, Path outputRoot, PcOptions o, Random rnd) throws IOException {
        Path src = gameRoot.resolve("ITEMOBJECT.DAT");
        if (!Files.isRegularFile(src)) {
            Path alt = gameRoot.resolve("data").resolve("ITEMOBJECT.DAT");
            if (Files.isRegularFile(alt)) src = alt;
        }
        if (!Files.isRegularFile(src)) {
            log.log("[!] ITEMOBJECT.DAT not found — skip DAT item patch.");
            return;
        }
        Path outDir = outputRoot != null ? outputRoot : src.getParent();
        Files.createDirectories(outDir);
        Path bak = outDir.resolve("ITEMOBJECT.DAT.bak");
        Path dst = outDir.resolve("ITEMOBJECT.DAT");
        if (!Files.exists(bak)) {
            Files.copy(src, bak, StandardCopyOption.REPLACE_EXISTING);
            log.log("Backup: ITEMOBJECT.DAT.bak");
        }
        byte[] data = Files.readAllBytes(bak);
        int patched = 0;
        int stride = 394;
        // records start at offset 14 with IT names
        for (int i = 14; i + 200 < data.length; i += stride) {
            if (data[i] != 'I' || data[i + 1] != 'T') {
                // try scan
                continue;
            }
            int dmin = shortAt(data, i + 184);
            int dmax = shortAt(data, i + 188);
            int ac = shortAt(data, i + 186);
            if (dmax <= 0 && ac <= 0) continue;
            if (dmax > 0) {
                if (o.rangeRollItems) {
                    dmin = o.randomIn(rnd, o.dmgMin, o.dmgMax);
                    dmax = o.randomIn(rnd, dmin, o.dmgMax);
                } else if (o.shuffleItemStats) {
                    dmin = Math.max(0, dmin + o.randomIn(rnd, -4, 6));
                    dmax = Math.max(dmin, dmax + o.randomIn(rnd, -4, 8));
                }
                putShort(data, i + 184, (short) dmin);
                putShort(data, i + 188, (short) dmax);
                patched++;
            }
            if (ac > 0) {
                if (o.rangeRollItems) {
                    ac = o.randomIn(rnd, Math.max(1, o.acMin), o.acMax);
                } else if (o.shuffleItemStats) {
                    ac = Math.max(1, ac + o.randomIn(rnd, -5, 10));
                }
                putShort(data, i + 186, (short) ac);
                patched++;
            }
        }
        Files.write(dst, data);
        log.log("[+] ITEMOBJECT.DAT: touched " + patched + " fields -> " + dst.getFileName());
    }


    /**
     * LAND/*.O3D — overworld prop meshes (grass tiles, barriers, cottages…).
     * Shuffle file contents among same-size groups so filenames (references) stay valid.
     */
    private void randomizeLandProps(Path gameRoot, Path outputRoot, Random rnd) throws IOException {
        Path land = gameRoot.resolve("LAND");
        if (!Files.isDirectory(land)) {
            log.log("[!] LAND/ folder not found under game root — skip prop shuffle.");
            return;
        }
        Path outLand = outputRoot != null ? outputRoot.resolve("LAND") : land;
        Files.createDirectories(outLand);

        List<Path> sources = new ArrayList<>();
        try (Stream<Path> list = Files.list(land)) {
            list.filter(p -> p.getFileName().toString().toUpperCase(Locale.ROOT).endsWith(".O3D"))
                    .sorted()
                    .forEach(sources::add);
        }
        if (sources.size() < 2) {
            log.log("[+] LAND props: not enough .O3D files.");
            return;
        }

        // Copy vanilla to out first (and bak once)
        Path bakDir = outLand.resolve("_bak");
        if (!Files.isDirectory(bakDir)) {
            Files.createDirectories(bakDir);
            for (Path p : sources) {
                Files.copy(p, bakDir.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            }
            log.log("Backup: LAND/_bak (" + sources.size() + " O3D)");
        }

        Map<Long, List<Path>> bySize = new HashMap<>();
        for (Path p : sources) {
            Path from = bakDir.resolve(p.getFileName().toString());
            if (!Files.isRegularFile(from)) from = p;
            long sz = Files.size(from);
            bySize.computeIfAbsent(sz, k -> new ArrayList<>()).add(from);
        }

        int swapped = 0;
        for (Map.Entry<Long, List<Path>> e : bySize.entrySet()) {
            List<Path> group = e.getValue();
            if (group.size() < 2) {
                // still copy through
                for (Path from : group) {
                    Path dest = outLand.resolve(from.getFileName().toString());
                    Files.copy(from, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                continue;
            }
            List<byte[]> contents = new ArrayList<>();
            for (Path from : group) {
                contents.add(Files.readAllBytes(from));
            }
            Collections.shuffle(contents, rnd);
            for (int i = 0; i < group.size(); i++) {
                Path dest = outLand.resolve(group.get(i).getFileName().toString());
                Files.write(dest, contents.get(i));
                swapped++;
            }
        }
        log.log("[+] LAND props: shuffled " + swapped + " .O3D meshes in " + bySize.size()
                + " size groups -> " + outLand);
        log.log("    Install: merge LAND/ into DATA.MTF or Darkstone\\data\\LAND\\");
    }


    /**
     * SCRIPT/*.SPT — campaign quest definitions (PC has ~2 side quests per land 0-6 + FINAL on 7).
     * Safe ops: reshuffle LAND {n} among DP/FC side quests; optionally swap OBJECT PARENT
     * lines that are not keys/crystals.
     */
    private void randomizeQuestScripts(Path gameRoot, Path outputRoot, PcOptions o, Random rnd) throws IOException {
        Path scriptDir = gameRoot.resolve("SCRIPT");
        if (!Files.isDirectory(scriptDir)) {
            scriptDir = gameRoot.resolve("SCRIPTS");
        }
        if (!Files.isDirectory(scriptDir)) {
            log.log("[!] SCRIPT/ folder not found — skip quest scripts.");
            return;
        }
        Path outDir = outputRoot != null ? outputRoot.resolve("SCRIPT") : scriptDir;
        Files.createDirectories(outDir);
        Path bakDir = outDir.resolve("_bak");
        if (!Files.isDirectory(bakDir)) {
            Files.createDirectories(bakDir);
        }

        List<Path> sideQuests = new ArrayList<>();
        try (Stream<Path> list = Files.list(scriptDir)) {
            for (Path p : list.filter(f -> {
                String n = f.getFileName().toString().toUpperCase(Locale.ROOT);
                return n.endsWith(".SPT") && !n.equals("QUESTFINAL.SPT")
                        && !n.equals("TOWN.SPT") && !n.equals("ENTREE.SPT")
                        && !n.endsWith(".SPTT");
            }).sorted().toList()) {
                Path bak = bakDir.resolve(p.getFileName().toString());
                if (!Files.exists(bak)) {
                    Files.copy(p, bak, StandardCopyOption.REPLACE_EXISTING);
                }
                sideQuests.add(bak);
            }
        }
        if (sideQuests.isEmpty()) {
            log.log("[+] Quest scripts: no side-quest SPT files.");
            return;
        }

        // Collect LAND ids
        List<String> texts = new ArrayList<>();
        List<Integer> landIds = new ArrayList<>();
        java.util.regex.Pattern landPat = java.util.regex.Pattern.compile(
                "(LAND\\s*\\{\\s*)(\\d+)(\\s*\\})", java.util.regex.Pattern.CASE_INSENSITIVE);
        for (Path bak : sideQuests) {
            String text = Files.readString(bak, TsvTable.CHARSET);
            texts.add(text);
            java.util.regex.Matcher m = landPat.matcher(text);
            if (m.find()) {
                landIds.add(Integer.parseInt(m.group(2)));
            } else {
                landIds.add(-1);
            }
        }

        int landSwaps = 0;
        if (o.questScripts) {
            List<Integer> pool = new ArrayList<>();
            for (int id : landIds) {
                if (id >= 0 && id <= 6) pool.add(id); // keep FINAL land 7 out of side pool
            }
            if (pool.size() >= 2) {
                Collections.shuffle(pool, rnd);
                int pi = 0;
                for (int i = 0; i < texts.size(); i++) {
                    if (landIds.get(i) < 0 || landIds.get(i) > 6) continue;
                    int newId = pool.get(pi++);
                    java.util.regex.Matcher m = landPat.matcher(texts.get(i));
                    if (m.find()) {
                        texts.set(i, m.replaceFirst(m.group(1) + newId + m.group(3)));
                        landSwaps++;
                    }
                }
            }
        }

        int rewardEdits = 0;
        if (o.questRewards) {
            // Pool of safe PARENT targets from OBJECT.TXT-like names used in scripts
            List<String> parents = List.of(
                    "ITEM_POTION", "ITEM_POTION_MANA", "ITEM_POTION_VITALITY", "ITEM_POTION_STRENGTH",
                    "ITEM_SCROLL", "ITEM_RING", "ITEM_AMULET", "ITEM_DAGUE", "ITEM_SWORD1H_2",
                    "ITEM_SWORD1H_3", "ITEM_SHIELD_2", "ITEM_ARMOR_4", "ITEM_FOOD5", "ITRING",
                    "ITPOTION", "ITEM_BOOK_POISONCLOUD", "ITEM_TORCHE", "ITEM_HACHETTE");
            java.util.regex.Pattern parentPat = java.util.regex.Pattern.compile(
                    "(PARENT\\s*\\{\\s*)([A-Za-z0-9_]+)(\\s*\\})");
            java.util.regex.Pattern keyPat = java.util.regex.Pattern.compile(
                    "KEY\\s*\\{\\s*(ITEM_[A-Za-z0-9_]+)\\s*\\}");
            for (int i = 0; i < texts.size(); i++) {
                String text = texts.get(i);
                // Skip OBJECT blocks whose KEY looks like KEY/CLEF/CRISTAL
                StringBuilder out = new StringBuilder();
                int pos = 0;
                java.util.regex.Matcher om = java.util.regex.Pattern.compile(
                        "(?s)OBJECT\\s*\\{.*?\\n\\s*\\}").matcher(text);
                boolean any = false;
                while (om.find()) {
                    out.append(text, pos, om.start());
                    String block = om.group();
                    java.util.regex.Matcher km = keyPat.matcher(block);
                    String key = km.find() ? km.group(1).toUpperCase(Locale.ROOT) : "";
                    boolean protect = key.contains("KEY") || key.contains("CLEF")
                            || key.contains("CRISTAL") || key.contains("VIRTUAL")
                            || key.contains("FALSEKEY");
                    if (!protect) {
                        java.util.regex.Matcher pm = parentPat.matcher(block);
                        if (pm.find()) {
                            String pick = parents.get(rnd.nextInt(parents.size()));
                            block = pm.replaceFirst(pm.group(1) + pick + pm.group(3));
                            rewardEdits++;
                            any = true;
                        }
                    }
                    out.append(block);
                    pos = om.end();
                }
                out.append(text, pos, text.length());
                if (any) texts.set(i, out.toString());
            }
        }

        for (int i = 0; i < sideQuests.size(); i++) {
            Path dest = outDir.resolve(sideQuests.get(i).getFileName().toString());
            Files.writeString(dest, texts.get(i), TsvTable.CHARSET);
        }
        log.log("[+] Quest scripts: " + landSwaps + " LAND reassignments, "
                + rewardEdits + " reward PARENT edits across " + sideQuests.size() + " SPT files.");
        log.log("    Install: SCRIPT/ into DATA.MTF (or data\\SCRIPT\\ override if supported).");
        log.log("    QUESTFINAL / TOWN / ENTREE left untouched.");
    }

    private static int shortAt(byte[] data, int off) {
        int v = (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
        if (v >= 0x8000) {
            v -= 0x10000;
        }
        return v;
    }

    private static void putShort(byte[] data, int off, short value) {
        data[off] = (byte) (value & 0xFF);
        data[off + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static String sanitize(String seed) {
        if (seed == null || seed.isBlank()) return "seed";
        String s = seed.replaceAll("[^A-Za-z0-9_-]", "");
        return s.isEmpty() ? "seed" : (s.length() > 32 ? s.substring(0, 32) : s);
    }
}
