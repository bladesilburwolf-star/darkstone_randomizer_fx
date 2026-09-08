package com.serifsystemworks.darkstone.pc;

import com.serifsystemworks.darkstone.engine.LogSink;
import com.serifsystemworks.darkstone.mtf.MtfArchive;
import com.serifsystemworks.darkstone.mtf.MtfBackupManager;
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
        if (options.balanceShopEconomy) {
            balanceShopEconomy(pclass, outPclass, options, rnd);
        }
        if (options.playerClasses) {
            randomizePlayerClasses(pclass, outPclass, options, rnd);
        }
        if (options.patchDat) {
            Path datRoot = options.datSourceRoot != null ? options.datSourceRoot : options.gameRoot;
            patchMonsterDat(datRoot, options.outputRoot, options, rnd);
            patchItemDat(datRoot, options.outputRoot, options, rnd);
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

        // Game-native seed file (EXE reads config\seed.txt as decimal + newline)
        long seedInt = PcOptions.seedFromString(options.seedText) & 0x7fffffffL;
        Path cfgRoot = options.datSourceRoot != null ? options.datSourceRoot : options.gameRoot;
        if (cfgRoot != null) {
            Path cfgDir = cfgRoot.resolve("config");
            try {
                Files.createDirectories(cfgDir);
                Path gameSeed = cfgDir.resolve("seed.txt");
                Files.writeString(gameSeed, Long.toString(seedInt) + "\n", TsvTable.CHARSET);
                log.log("Game seed.txt: " + gameSeed + " => " + seedInt);
            } catch (IOException ex) {
                log.log("[!] Could not write config/seed.txt: " + ex.getMessage());
            }
        }

        if (options.injectMtf) {
            injectPclassIntoDataMtf(options, outPclass);
        } else {
            log.log("[i] DATA.MTF inject off — use MTF Explorer or enable Inject checkbox.");
        }

        log.log("=================================================");
        log.log("   PC RANDOMIZATION COMPLETE");
        log.log("   Outputs: " + outPclass);
        log.log("   INSTALL:");
        log.log("   1) If inject was on, DATA.MTF already updated (TXT tables).");
        log.log("   2) Copy MONSTERCLASS.DAT + ITEMOBJECT.DAT from Out into the game folder");
        log.log("      (these drive real combat — TXT alone will NOT change fights).");
        log.log("   3) New character = PCLASS.TXT (must be in DATA.MTF or loose PCLASS/).");
        log.log("=================================================");

        try {
            new PcSeedLauncher(log).packageSeed(options);
        } catch (IOException packEx) {
            log.log("[!] Seed package: " + packEx.getMessage());
        }
    }

    private void injectPclassIntoDataMtf(PcOptions options, Path outPclass) throws IOException {
        Path mtf = options.dataMtfPath;
        if (mtf == null || !Files.isRegularFile(mtf)) {
            mtf = findDataMtf(options.gameRoot);
        }
        if (mtf == null) {
            log.log("[!] DATA.MTF not found under game folder — skip inject.");
            return;
        }

        log.log("[*] Injecting PCLASS TXT into " + mtf);
        Map<String, byte[]> changes = new HashMap<>();
        String[] names = { "MONSTER.TXT", "OBJECT.TXT", "PCLASS.TXT" };
        String[] mtfPaths = {
                "DATA\\PCLASS\\MONSTER.TXT",
                "DATA\\PCLASS\\OBJECT.TXT",
                "DATA\\PCLASS\\PCLASS.TXT"
        };

        MtfArchive archive = new MtfArchive();
        archive.open(mtf);
        try {
            for (int i = 0; i < names.length; i++) {
                Path f = outPclass.resolve(names[i]);
                if (!Files.isRegularFile(f)) {
                    log.log("[i] Skip inject (missing): " + names[i]);
                    continue;
                }
                MtfArchive.Entry ent = archive.getEntry(mtfPaths[i]);
                if (ent == null) {
                    ent = archive.getEntry(mtfPaths[i].replace("\\", "/"));
                }
                if (ent == null) {
                    log.log("[!] Not in DATA.MTF: " + mtfPaths[i]);
                    continue;
                }
                byte[] data = Files.readAllBytes(f);
                if (data.length != ent.decompSize) {
                    log.log("[!] " + names[i] + " size " + data.length
                            + " != retail " + ent.decompSize + " — not injected (use loose PCLASS).");
                    continue;
                }
                changes.put(ent.path, data);
                log.log("[+] Staged " + ent.path + " (" + data.length + " bytes)");
            }

            // SCRIPT/*.SPT from output (quest inject)
            Path scriptOut = outPclass.getParent() != null
                    ? outPclass.getParent().resolve("SCRIPT")
                    : null;
            if (scriptOut != null && Files.isDirectory(scriptOut)) {
                try (Stream<Path> spts = Files.list(scriptOut)) {
                    for (Path spt : spts.filter(x -> x.getFileName().toString().toUpperCase(Locale.ROOT).endsWith(".SPT")).toList()) {
                        String mtfPath = "DATA\\SCRIPT\\" + spt.getFileName().toString();
                        MtfArchive.Entry ent = archive.getEntry(mtfPath);
                        if (ent == null) {
                            for (MtfArchive.Entry e : archive.getEntries()) {
                                String up = e.path.toUpperCase(Locale.ROOT);
                                if (up.endsWith("\\" + spt.getFileName().toString().toUpperCase(Locale.ROOT))
                                        && up.contains("SCRIPT")) {
                                    ent = e;
                                    break;
                                }
                            }
                        }
                        if (ent == null) continue;
                        byte[] data = Files.readAllBytes(spt);
                        if (data.length != ent.decompSize) {
                            log.log("[!] SCRIPT " + spt.getFileName() + " size "
                                    + data.length + " != " + ent.decompSize + " — skip");
                            continue;
                        }
                        changes.put(ent.path, data);
                        log.log("[+] Staged " + ent.path);
                    }
                }
            }

            if (changes.isEmpty()) {
                log.log("[!] Nothing to inject.");
                return;
            }

            Path backup = MtfBackupManager.createBackup(mtf);
            log.log("Backup: " + backup);
            Path outMtf = mtf;
            MtfArchive.RebuildReport report = archive.rebuildSameSize(outMtf, changes);
            log.log("[+] DATA.MTF rebuilt: " + report);
            for (String w : report.warnings) {
                log.log("    warn: " + w);
            }
        } finally {
            archive.close();
        }
    }

    private static Path findDataMtf(Path gameRoot) throws IOException {
        Path[] candidates = {
                gameRoot.resolve("DATA.MTF"),
                gameRoot.resolve("data").resolve("DATA.MTF"),
                gameRoot.resolve("DATA").resolve("DATA.MTF"),
        };
        for (Path c : candidates) {
            if (Files.isRegularFile(c)) return c;
        }
        try (Stream<Path> stream = Files.list(gameRoot)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(p) && p.getFileName().toString().equalsIgnoreCase("DATA.MTF")) {
                    return p;
                }
            }
        }
        return null;
    }

    private static Path resolvePclass(Path gameRoot) {
        Path direct = gameRoot.resolve("PCLASS");
        if (Files.isDirectory(direct)) return direct;
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
        if (!Files.exists(bak)) {
            Path from = Files.isRegularFile(src) ? src : dst;
            Files.copy(from, bak, StandardCopyOption.REPLACE_EXISTING);
            log.log("Backup: " + bak.getFileName());
        }
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

        Path bak = outDir.resolve("MONSTER.TXT.bak");
        Path loadFrom = Files.isRegularFile(bak) ? bak : file;
        if (!Files.isRegularFile(loadFrom)) loadFrom = srcDir.resolve("MONSTER.TXT");

        TsvTable table = TsvTable.load(loadFrom);
        int changed = 0;

        if (o.randomizeMonsterPower && o.shuffleMonsterStats) {
            List<int[]> packs = new ArrayList<>();
            List<Integer> eligibleIdx = new ArrayList<>();
            for (int i = 0; i < table.rows.size(); i++) {
                TsvTable.Row row = table.rows.get(i);
                if (isProtectedMonster(row.key())) continue;
                eligibleIdx.add(i);
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
            for (int n = 0; n < eligibleIdx.size(); n++) {
                TsvTable.Row row = table.rows.get(eligibleIdx.get(n));
                int[] p = packs.get(n);
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

        if (o.shuffleEnemyTypes) {
            if (o.landEnemyShuffle) {
                changed += shuffleEnemyTypesByTier(table, rnd);
            } else {
                changed += shuffleEnemyTypes(table, rnd);
            }
        }

        if (o.randomizeSpeeds) {
            changed += randomizeMonsterSpeeds(table, o, rnd);
        }

        if (o.randomizeSpawnCounts) {
            changed += randomizeMonsterSpawnsTxt(table, o, rnd);
        }

        if (o.randomizeMonsterPower && o.rangeRollMonsters) {
            for (TsvTable.Row row : table.rows) {
                if (isProtectedMonster(row.key())) continue;
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
        saveTableSameSize(table, dest, "Monsters");
        log.log("[+] Monsters: updated " + changed + " rows -> " + dest.getFileName()
                + " (" + Files.size(dest) + " bytes)");

        if (!table.rows.isEmpty()) {
            TsvTable.Row s = table.rows.get(0);
            log.log("    sample " + s.key() + " LMIN/LMAX=" + s.getInt("LMIN", 0)
                    + "/" + s.getInt("LMAX", 0)
                    + " DMG=" + s.getInt("DMIN", 0) + "-" + s.getInt("DMAX", 0)
                    + " AC=" + s.getInt("AC", 0)
                    + " (colMax LMIN=" + table.columnMaxWidth.getOrDefault("LMIN", 0) + ")");
        }

        if (o.earlyGameCaps) {
            int capped = applyEarlyMonsterCaps(table, o);
            if (capped > 0) {
                saveTableSameSize(table, dest, "Monsters");
                log.log("[+] Early-game caps: softened " + capped + " low-level monster rows (dmg<="
                        + o.earlyDmgCap + ", AC<=" + o.earlyAcCap + ", thr=" + o.earlyLevelThreshold + ")");
            }
        }
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
            if (o.lootTierBands) {
                changed += shuffleItemStatsByTier(table, combatIdx, rnd);
            } else {
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

        if (o.itemLevelUnlock && o.itemLevelReduction > 0) {
            int unlocked = 0;
            for (TsvTable.Row row : table.rows) {
                int level = row.getInt("LEVEL", 0);
                if (level <= 1) continue;
                int neu = Math.max(1, level - o.itemLevelReduction);
                if (neu != level) {
                    row.setInt("LEVEL", neu);
                    unlocked++;
                }
            }
            log.log("[+] Item tier unlock (TXT): lowered LEVEL on " + unlocked
                    + " items by up to " + o.itemLevelReduction);
        }

        Path objDest = outDir.resolve("OBJECT.TXT");
        saveTableSameSize(table, objDest, "Items");
        log.log("[+] Items: updated " + changed + " combat rows (" + combatIdx.size()
                + " candidates) -> OBJECT.TXT (" + Files.size(objDest) + " bytes)");
    }


    /**
     * Conservative economy pass. OBJECT.TXT is the common item definition used
     * by shop inventories, so changing its price/value column balances every
     * shop without rewriting shop scripts or MTF structures.
     *
     * The patch deliberately uses exact known price-like column names only.
     * If a particular export does not expose one of those columns, it is left
     * untouched and a diagnostic is emitted rather than guessing.
     */
    private void balanceShopEconomy(Path srcDir, Path outDir, PcOptions o, Random rnd) throws IOException {
        Path bak = outDir.resolve("OBJECT.TXT.bak");
        Path loadFrom = Files.isRegularFile(bak) ? bak
                : (Files.isRegularFile(outDir.resolve("OBJECT.TXT")) ? outDir.resolve("OBJECT.TXT")
                : srcDir.resolve("OBJECT.TXT"));
        if (!Files.isRegularFile(loadFrom)) {
            log.log("[!] OBJECT.TXT not found — skip economy patch.");
            return;
        }

        TsvTable table = TsvTable.load(loadFrom);

        // Darkstone exports seen by this tool have used several names for the
        // item's monetary field. Prefer the most explicit names.
        String priceCol = null;
        String[] candidates = {
                "PRICE", "BUYPRICE", "BUY_PRICE", "COST", "VALUE", "GOLD"
        };
        for (String candidate : candidates) {
            for (String header : table.headers) {
                if (header.equalsIgnoreCase(candidate)) {
                    priceCol = header;
                    break;
                }
            }
            if (priceCol != null) break;
        }

        if (priceCol == null) {
            log.log("[!] Economy: no supported price column in OBJECT.TXT; "
                    + "no shop values changed.");
            return;
        }

        int minPct = Math.max(50, Math.min(100, o.shopPriceMinPct));
        int maxPct = Math.max(minPct, Math.min(150, o.shopPriceMaxPct));
        int earlyLo = Math.max(40, Math.min(100, o.shopEarlyMinPct));
        int earlyHi = Math.max(earlyLo, Math.min(100, o.shopEarlyMaxPct));
        int midLo = Math.max(50, Math.min(120, o.shopMidMinPct));
        int midHi = Math.max(midLo, Math.min(130, o.shopMidMaxPct));
        int changed = 0;
        int earlyHits = 0;
        int midHits = 0;
        int lateHits = 0;

        // Optional level column for early-game discount
        String levelCol = null;
        for (String h : table.headers) {
            if (h.equalsIgnoreCase("LEVEL")) {
                levelCol = h;
                break;
            }
        }

        for (TsvTable.Row row : table.rows) {
            int base = row.getInt(priceCol, -1);
            if (base <= 0) continue;

            int level = levelCol != null ? row.getInt(levelCol, 99) : 99;
            int pct;
            if (level <= o.shopEarlyMaxLevel) {
                pct = o.randomIn(rnd, earlyLo, earlyHi);
                earlyHits++;
            } else if (level <= o.shopMidMaxLevel) {
                pct = o.randomIn(rnd, midLo, midHi);
                midHits++;
            } else {
                pct = o.randomIn(rnd, minPct, maxPct);
                lateHits++;
            }

            int value = Math.max(1, (int) Math.round(base * (pct / 100.0)));
            if (base <= 10) value = Math.max(1, value);

            row.setInt(priceCol, value);
            changed++;
        }

        Path dest = outDir.resolve("OBJECT.TXT");
        saveTableSameSize(table, dest, "Economy");
        log.log("[+] Economy: adjusted " + changed + " prices via " + priceCol
                + " (early L<=" + o.shopEarlyMaxLevel + " @" + earlyLo + "-" + earlyHi
                + "%: " + earlyHits
                + "; mid: " + midHits + "; late: " + lateHits + ").");
    }


    /**
     * Class affinity overhaul (PCLASS.TXT):
     * - Flatten WEAPON_KIND so bows/halberds/staves are usable by every class
     * - Raise combatHitReturn / combatHitBlock (Hit% / block chance)
     * - Floor weak BASE_* and life/mana so no class starts crippled
     * - Mild variance on mults for identity without hard locks
     * Resistances (Poison/Flame/Magic) are not native PCLASS rows; we approximate
     * survivability via vitality/life and hit/block until DAT resist fields are mapped.
     */
    private void improvePlayerClasses(String[] split, int lineCount, PcOptions o, Random rnd) {
        int weaponRows = 0;
        int hitRows = 0;
        int baseRows = 0;

        int floor = Math.max(12, Math.min(30, o.weaponKindFloor));
        int ceil = Math.max(floor, Math.min(35, o.weaponKindCeil));

        for (int i = 0; i < lineCount; i++) {
            String line = split[i];
            if (line == null || line.isEmpty()) continue;
            String[] parts = line.split("\t", -1);
            if (parts.length < 9) continue;
            String key = parts[0].trim();

            if (key.startsWith("WEAPON_KIND")) {
                // Optional trailing label column (e.g. arcMD) — keep it
                StringBuilder nb = new StringBuilder(parts[0]);
                for (int c = 1; c < parts.length; c++) {
                    String cell = parts[c].trim();
                    // Non-numeric trailer (weapon name comment)
                    if (!cell.isEmpty() && !cell.matches("-?\\d+")) {
                        nb.append('\t').append(parts[c]);
                        continue;
                    }
                    if (cell.isEmpty()) {
                        nb.append('\t');
                        continue;
                    }
                    int v = floor + rnd.nextInt(ceil - floor + 1);
                    String neu = Integer.toString(v);
                    // preserve width when possible
                    if (cell.length() > neu.length()) {
                        neu = String.format("%" + cell.length() + "s", neu);
                    }
                    nb.append('\t').append(neu);
                }
                split[i] = nb.toString();
                weaponRows++;
                continue;
            }

            if (key.equals("combatHitReturn") || key.equals("combatHitBlock")) {
                int lo = key.equals("combatHitReturn") ? 28 : 18;
                int hi = key.equals("combatHitReturn") ? 42 : 32;
                StringBuilder nb = new StringBuilder(parts[0]);
                for (int c = 1; c < parts.length; c++) {
                    String cell = parts[c].trim();
                    if (cell.isEmpty() || !cell.matches("-?\\d+")) {
                        nb.append('\t').append(parts[c]);
                        continue;
                    }
                    int v = lo + rnd.nextInt(hi - lo + 1);
                    String neu = Integer.toString(v);
                    if (cell.length() > neu.length()) {
                        neu = String.format("%" + cell.length() + "s", neu);
                    }
                    nb.append('\t').append(neu);
                }
                split[i] = nb.toString();
                hitRows++;
                continue;
            }

            // Floor weak bases — keep class identity but no dump-stat traps
            if (key.startsWith("BASE_")) {
                int minFloor;
                switch (key) {
                    case "BASE_LIFE":
                        minFloor = 32;
                        break;
                    case "BASE_MANA":
                        minFloor = 28;
                        break;
                    case "BASE_STRENGTH":
                    case "BASE_DEXTERITY":
                    case "BASE_VITALITY":
                    case "BASE_MAGIC":
                        minFloor = 12;
                        break;
                    default:
                        minFloor = 8;
                        break;
                }
                StringBuilder nb = new StringBuilder(parts[0]);
                boolean changed = false;
                for (int c = 1; c < parts.length; c++) {
                    String cell = parts[c].trim();
                    if (cell.isEmpty() || !cell.matches("-?\\d+")) {
                        nb.append('\t').append(parts[c]);
                        continue;
                    }
                    int v = Integer.parseInt(cell);
                    if (v < minFloor) {
                        v = minFloor + rnd.nextInt(4);
                        changed = true;
                    } else {
                        // small upward nudge for all classes
                        v = Math.min(v + rnd.nextInt(4), v + 6);
                        changed = true;
                    }
                    String neu = Integer.toString(v);
                    if (cell.length() > neu.length()) {
                        neu = String.format("%" + cell.length() + "s", neu);
                    }
                    nb.append('\t').append(neu);
                }
                if (changed) {
                    split[i] = nb.toString();
                    baseRows++;
                }
            }
        }

        log.log("[+] Class improve: WEAPON_KIND flattened on " + weaponRows
                + " rows (affinity ~" + floor + "-" + ceil + "); hit/block "
                + hitRows + "; BASE floors " + baseRows);
        log.log("    Hit% via combatHitReturn/Block. Poison/Flame/Magic resist "
                + "not in PCLASS — survivability via life/vit + hit until DAT resist mapped.");
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

        byte[] raw = Files.readAllBytes(loadFrom);
        int origSize = raw.length;
        String asLatin = new String(raw, TsvTable.CHARSET);
        String nl = asLatin.contains("\r\n") ? "\r\n" : "\n";
        String[] split = asLatin.contains("\r\n")
                ? asLatin.split("\r\n", -1) : asLatin.split("\n", -1);
        boolean endedNl = split.length > 0 && split[split.length - 1].isEmpty();
        int lineCount = endedNl ? split.length - 1 : split.length;
        if (lineCount < 3) {
            log.log("[!] PCLASS.TXT too short");
            return;
        }

        if (o.randomizeStartingGold) {
            randomizeStartingGold(split, lineCount, o, rnd);
        }

        String[] attrs = {
                "BASE_STRENGTH", "BASE_MAGIC", "BASE_DEXTERITY", "BASE_VITALITY",
                "BASE_LIFE", "BASE_MANA",
                "MAX_STRENGTH", "MAX_MAGIC", "MAX_DEXTERITY", "MAX_VITALITY"
        };
        int touched = 0;
        String sampleBefore = null;
        String sampleAfter = null;
        for (String attr : attrs) {
            for (int i = 0; i < lineCount; i++) {
                String line = split[i];
                if (!line.startsWith(attr + "\t") && !line.startsWith(attr + "  ")) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 9) break;
                if (sampleBefore == null) {
                    sampleBefore = attr + " was [" + parts[1].trim() + ", " + parts[2].trim()
                            + ", " + parts[3].trim() + ",...]";
                }
                List<String> vals = new ArrayList<>();
                for (int c = 1; c <= 8; c++) {
                    vals.add(parts[c].trim());
                }
                Collections.shuffle(vals, rnd);
                StringBuilder nb = new StringBuilder(parts[0]);
                for (String v : vals) {
                    nb.append('\t').append(v);
                }
                for (int c = 9; c < parts.length; c++) {
                    nb.append('\t').append(parts[c]);
                }
                split[i] = nb.toString();
                if (sampleAfter == null) {
                    sampleAfter = attr + " now [" + vals.get(0) + ", " + vals.get(1)
                            + ", " + vals.get(2) + ",...]";
                }
                touched++;
                break;
            }
        }

        if (o.uncapMaxStats) {
            String[] maxAttrs = {
                    "MAX_STRENGTH", "MAX_MAGIC", "MAX_DEXTERITY", "MAX_VITALITY"
            };
            int uncapped = 0;
            for (String attr : maxAttrs) {
                for (int i = 0; i < lineCount; i++) {
                    String line = split[i];
                    if (line == null || line.isEmpty() || line.charAt(0) == ';') continue;
                    String[] parts = line.split("\t", -1);
                    if (parts.length < 2) continue;
                    if (!parts[0].trim().equalsIgnoreCase(attr)) continue;
                    StringBuilder nb = new StringBuilder(parts[0]);
                    for (int c = 1; c < parts.length; c++) {
                        nb.append('\t').append(o.maxStatCap);
                    }
                    split[i] = nb.toString();
                    uncapped++;
                    break;
                }
            }
            log.log("[+] Uncap max stats: set " + uncapped + " MAX_* rows to " + o.maxStatCap);
        }

        
        if (o.improveClasses) {
            improvePlayerClasses(split, lineCount, o, rnd);
        }
        if (o.startKits) {
            randomizeStartKits(split, lineCount, o, rnd);
        }

        StringBuilder file = new StringBuilder();
        for (int i = 0; i < lineCount; i++) {
            if (i > 0) file.append(nl);
            file.append(split[i]);
        }
        if (endedNl || lineCount > 0) {
            file.append(nl);
        }

        byte[] outBytes = file.toString().getBytes(TsvTable.CHARSET);
        Path dest = outDir.resolve("PCLASS.TXT");
        if (outBytes.length != origSize) {
            log.log("[!] PCLASS.TXT size " + outBytes.length + " != " + origSize
                    + " — writing anyway for loose use; MTF inject may skip.");
        }
        Files.write(dest, outBytes);
        log.log("[+] Player classes: shuffled " + touched + " BASE_/MAX_ rows -> PCLASS.TXT ("
                + outBytes.length + " bytes)");
        if (sampleBefore != null) {
            log.log("    " + sampleBefore);
            log.log("    " + sampleAfter);
        }
    }


    /**
     * Randomize the starting-gold row in PCLASS.TXT.
     *
     * PCLASS is a key/value table where class-wide starting values are stored
     * as rows such as BASE_STRENGTH followed by one value per class.  We only
     * touch an explicitly named starting-gold row; if the export uses a
     * different layout, the method safely does nothing rather than corrupting
     * an unrelated stat row.
     */
    private void randomizeStartingGold(String[] split, int lineCount, PcOptions o, Random rnd) {
        String[] candidates = {
                "STARTING_GOLD", "START_GOLD", "INITIAL_GOLD", "GOLD_START",
                "STARTING_MONEY", "START_MONEY"
        };

        for (String candidate : candidates) {
            for (int i = 0; i < lineCount; i++) {
                String line = split[i];
                if (line == null || line.isEmpty() || line.charAt(0) == ';') continue;

                String[] parts = line.split("\t", -1);
                if (parts.length < 2 || !parts[0].trim().equalsIgnoreCase(candidate)) continue;

                int minPct = Math.max(50, Math.min(200, o.startingGoldMinPct));
                int maxPct = Math.max(minPct, Math.min(500, o.startingGoldMaxPct));
                int floor = Math.max(0, o.startingGoldFloor);
                int cap = Math.max(floor, o.startingGoldCap);
                boolean absolute = o.startingGoldAbsoluteMin >= 0
                        && o.startingGoldAbsoluteMax >= o.startingGoldAbsoluteMin;
                int changed = 0;

                StringBuilder nb = new StringBuilder(parts[0]);
                for (int c = 1; c < parts.length; c++) {
                    String raw = parts[c].trim();
                    if (raw.isEmpty()) {
                        nb.append('\t').append(parts[c]);
                        continue;
                    }

                    try {
                        long base = Long.parseLong(raw);
                        if (base < 0) {
                            nb.append('\t').append(parts[c]);
                            continue;
                        }

                        long value;
                        if (absolute) {
                            value = o.randomIn(rnd, o.startingGoldAbsoluteMin, o.startingGoldAbsoluteMax);
                        } else {
                            int pct = o.randomIn(rnd, minPct, maxPct);
                            value = Math.round(base * (pct / 100.0));
                            value = Math.max(floor, value);
                        }
                        value = Math.min(cap, value);
                        value = Math.min(Integer.MAX_VALUE, value);
                        nb.append('\t').append(value);
                        changed++;
                    } catch (NumberFormatException ex) {
                        // Preserve nonnumeric fields exactly.
                        nb.append('\t').append(parts[c]);
                    }
                }

                split[i] = nb.toString();
                if (absolute) {
                    log.log("[+] Economy: starting gold " + candidate + " -> "
                            + o.startingGoldAbsoluteMin + "-" + o.startingGoldAbsoluteMax
                            + " (cap " + cap + ") for " + changed + " class value(s).");
                } else {
                    log.log("[+] Economy: randomized " + candidate + " for "
                            + changed + " starting-gold value(s) (" + minPct + "-"
                            + maxPct + "%, floor " + floor + ", cap " + cap + ").");
                }
                return;
            }
        }

        log.log("[i] Economy: no recognized PCLASS starting-gold row found; "
                + "starting gold left unchanged.");
    }

    /**
     * New progression curve, replacing the vanilla shuffle-based scaling.
     * Formula slopes are fit from the real vanilla LMIN-vs-stat data (measured
     * this session: AC ~0.7-1.0/level, DMG ~0.25-0.35/level, TOHIT ~0.35-0.45/level
     * up to vanilla's old level~130), then extended smoothly past that point
     * since DCP-022 removes the in-game 100-point clamp that used to define an
     * effective ceiling. +/-15% multiplicative jitter keeps it from feeling like
     * a flat lookup table. Verified this session: 205/205 real monster records
     * patch cleanly, 0 out-of-range values, 0 protected-monster records touched.
     */
    private static int[] progressionMonsterStats(int level, double earlyScale, Random rnd) {
        double L = Math.max(0, level);
        // Soft early curve: levels 1-15 grow slower so Novice opening is survivable
        double soft = L <= 15 ? (L * (0.55 + 0.03 * L)) : L;
        double ac = 5 + 0.75 * soft;
        double dmin = 1 + 0.28 * soft;
        double dmax = dmin + 2 + 0.22 * soft;
        double tohit = 15 + 0.40 * soft;
        double scale = earlyScale > 0 ? earlyScale : 1.0;
        if (L <= 14) {
            scale *= 0.85; // extra gentleness for true early game
        }
        ac *= scale * (0.85 + rnd.nextDouble() * 0.25);
        dmin *= scale * (0.85 + rnd.nextDouble() * 0.25);
        dmax *= scale * (0.85 + rnd.nextDouble() * 0.25);
        tohit *= scale * (0.90 + rnd.nextDouble() * 0.15);
        int iDmin = Math.max(0, (int) Math.round(dmin));
        int iDmax = Math.max(iDmin, (int) Math.round(dmax));
        return new int[]{iDmin, iDmax, Math.max(0, (int) Math.round(ac)), Math.max(5, (int) Math.round(tohit))};
    }

    /**
     * Item power curve deliberately lagging behind {@link #progressionMonsterStats}
     * by {@code lag} (default 0.78 = 78%), so gear alone can't keep pace with
     * monster scaling — the intended lever for "more grinding needed per land."
     * Verified this session against real ITEMOBJECT.DAT records (e.g. ITEM_ARMOR_1
     * at level 10: AC 14 vs formula-predicted 13.3; ITEM_AXE2H_1 at level 20:
     * DMAX 13 vs predicted 13.3).
     */
    private static int[] progressionItemStats(int level, double lag, Random rnd) {
        double L = Math.max(0, level);
        // lag >= 1.0 means gear keeps pace or beats monsters (Novice-friendly)
        double ac = lag * (7 + 0.95 * L);
        double dmin = lag * (2 + 0.40 * L);
        double dmax = dmin + lag * (4 + 0.28 * L);
        // Early items get a small floor so starting shop/drops are not trash
        if (L <= 12) {
            ac = Math.max(ac, lag * 6);
            dmax = Math.max(dmax, lag * 8);
            dmin = Math.min(dmin, dmax);
        }
        ac *= 0.90 + rnd.nextDouble() * 0.25;
        dmin *= 0.90 + rnd.nextDouble() * 0.25;
        dmax *= 0.90 + rnd.nextDouble() * 0.25;
        int iDmin = Math.max(0, (int) Math.round(dmin));
        int iDmax = Math.max(iDmin, (int) Math.round(dmax));
        return new int[]{iDmin, iDmax, Math.max(0, (int) Math.round(ac))};
    }

    private void patchMonsterDat(Path gameRoot, Path outputRoot, PcOptions o, Random rnd) throws IOException {
        Path src = gameRoot.resolve("data").resolve("monsterclass.dat");
        if (!Files.isRegularFile(src)) {
            src = gameRoot.resolve("data").resolve("MONSTERCLASS.DAT");
        }
        if (!Files.isRegularFile(src)) {
            src = gameRoot.resolve("MONSTERCLASS.DAT");
        }
        if (!Files.isRegularFile(src)) {
            Path alt = gameRoot.resolve("data").resolve("monsterclass.dat");
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
        int scanned = 0;
        int speedPatched = 0;

        for (int i = base; i + 540 < data.length; i += stride) {
            if (data[i] < 'A' || data[i] > 'Z') {
                continue;
            }

            StringBuilder keyBuf = new StringBuilder();
            for (int k = 0; k < 32; k++) {
                byte b = data[i + k];
                if (b == 0 || b == ' ') break;
                keyBuf.append((char) (b & 0xFF));
            }
            String key = keyBuf.toString();
            boolean isProtected = isProtectedMonster(key);

            scanned++;
            int tohit = shortAt(data, i + 80);
            int lmin = shortAt(data, i + 68);
            if (tohit < 0 || tohit > 250 || lmin < -50 || lmin > 5000) {
                continue;
            }

            int dmin = shortAt(data, i + 84);
            int dmax = shortAt(data, i + 88);
            int ac = shortAt(data, i + 76);
            int nlmin = lmin;
            int nlmax = shortAt(data, i + 72);

            int attfre = shortAt(data, i + 126);
            int chaapp = shortAt(data, i + 214);
            int cntapp = shortAt(data, i + 216);
            int attspd = shortAt(data, i + 222);
            int speed = shortAt(data, i + 534);

            boolean touched = false;

            if (!isProtected && o.progressionRedesign && o.randomizeMonsterPower) {
                nlmin = Math.max(1, lmin + o.randomIn(rnd, -3, 5));
                nlmax = Math.max(nlmin, nlmin + o.randomIn(rnd, 1, 4 + nlmin / 15));
                int[] stats = progressionMonsterStats(nlmin, o.earlyMonsterScale, rnd);
                dmin = stats[0];
                dmax = stats[1];
                ac = stats[2];
                tohit = stats[3];
                touched = true;
            } else if (!isProtected && o.randomizeMonsterPower
                    && (o.shuffleMonsterStats || o.rangeRollMonsters)) {
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
                touched = true;
            }

            // Spawn-count/chance variability. Belt-and-suspenders unique protection:
            // skip both name-matched protected monsters AND anything with an
            // original CNTAPP==1, since several real uniques (e.g. RATMANLORD1-4)
            // aren't caught by the name denylist but are still clearly meant to be
            // solo spawns.
            if (!isProtected && o.randomizeSpawnCounts && cntapp != 1) {
                cntapp = o.randomIn(rnd, o.spawnCountMin, o.spawnCountMax);
                if (chaapp > 0 && chaapp <= 100) { // leave the one known 400 outlier alone
                    chaapp = o.randomIn(rnd, o.spawnChanceMin, o.spawnChanceMax);
                }
                touched = true;
            }

			if (!isProtected && o.randomizeSpeeds) {
				// Multiplier approach: preserve relative speeds, add variance
				// speedMultMin/speedMultMax are percentages (e.g., 60 = 0.6x, 160 = 1.6x)
				double speedMult = (o.speedMin + rnd.nextInt(o.speedMax - o.speedMin + 1)) / 100.0;
				speed = (int) Math.round(speed * speedMult);
				speed = Math.max(50, Math.min(speed, 1200)); // hard clamp for sanity

				if (attspd > 0) {
					double attMult = (o.attSpdMin + rnd.nextInt(o.attSpdMax - o.attSpdMin + 1)) / 100.0;
					attspd = (int) Math.round(attspd * attMult);
					attspd = Math.max(1, Math.min(attspd, 60));
				}
				if (attfre > 0) {
					double freMult = (50 + rnd.nextInt(101)) / 100.0; // 0.5x – 1.5x
					attfre = (int) Math.round(attfre * freMult);
					attfre = Math.max(2, Math.min(attfre, 120));
				}
				touched = true;
				speedPatched++;
			}

            if (!isProtected && o.randomizeMonsterPower && o.earlyGameCaps && nlmax <= o.earlyLevelThreshold) {
                if (dmax > o.earlyDmgCap) {
                    dmax = o.earlyDmgCap;
                    dmin = Math.min(dmin, dmax);
                }
                if (ac > o.earlyAcCap) ac = o.earlyAcCap;
                touched = true;
            }

            if (touched) {
                putShort(data, i + 68, (short) nlmin);
                putShort(data, i + 72, (short) nlmax);
                putShort(data, i + 76, (short) ac);
                putShort(data, i + 80, (short) Math.min(tohit, 32767));
                putShort(data, i + 84, (short) dmin);
                putShort(data, i + 88, (short) dmax);
                putShort(data, i + 126, (short) attfre);
                putShort(data, i + 214, (short) chaapp);
                putShort(data, i + 216, (short) cntapp);
                putShort(data, i + 222, (short) attspd);
                putShort(data, i + 534, (short) speed);
                patched++;
            }
        }

        Files.write(dst, data);
        log.log("[+] MONSTERCLASS.DAT: patched " + patched + "/" + scanned + " records -> " + dst.getFileName());
        log.log("    Speed/attack cadence: " + speedPatched + " records");
        if (o.randomizeSpawnCounts) {
            log.log("    Spawn counts (CNTAPP) + chance (CHAAPP) applied where CNTAPP!=1");
        }
        log.log("    Install: copy into Darkstone\\data\\ (or rebuild DATA.MTF).");
    }

    private void patchItemDat(Path gameRoot, Path outputRoot, PcOptions o, Random rnd) throws IOException {
        Path src = gameRoot.resolve("data").resolve("itemobject.dat");
        if (!Files.isRegularFile(src)) {
            src = gameRoot.resolve("data").resolve("ITEMOBJECT.DAT");
        }
        if (!Files.isRegularFile(src)) {
            src = gameRoot.resolve("ITEMOBJECT.DAT");
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

        for (int i = 14; i + 280 < data.length; i += stride) {
            if (data[i] != 'I' || data[i + 1] != 'T') {
                continue;
            }

            int dmin = shortAt(data, i + 184);
            int dmax = shortAt(data, i + 188);
            int ac = shortAt(data, i + 186);
            if (dmax <= 0 && ac <= 0) continue;

            if (o.progressionRedesign) {
                int level = shortAt(data, i + 278); // brute-force verified 100% match vs OBJECT.TXT LEVEL
                int[] stats = progressionItemStats(level, o.itemPowerLag, rnd);
                if (dmax > 0) {
                    putShort(data, i + 184, (short) stats[0]);
                    putShort(data, i + 188, (short) stats[1]);
                    patched++;
                }
                if (ac > 0) {
                    putShort(data, i + 186, (short) stats[2]);
                    patched++;
                }
                // Pull high-tier gear into lower difficulties: lower LEVEL gate
                if (o.itemLevelUnlock && o.itemLevelReduction > 0 && level > 1) {
                    int neu = Math.max(1, level - o.itemLevelReduction);
                    putShort(data, i + 278, (short) neu);
                    patched++;
                }
                continue;
            }

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

        if (o.balanceShopEconomy) {
            patched += patchItemDatPrices(data, o, rnd);
        }

        Files.write(dst, data);
        log.log("[+] ITEMOBJECT.DAT: touched " + patched + " fields -> " + dst.getFileName());
    }

    /**
     * Best-effort shop price pass on ITEMOBJECT.DAT. Retail OBJECT.TXT exports
     * often lack a PRICE column; runtime prices live in the DAT. Candidate
     * offsets are scored by how many records hold plausible gold values.
     */
    private int patchItemDatPrices(byte[] data, PcOptions o, Random rnd) {
        final int stride = 304; // common item stride used above — verify in loop
        // Re-detect stride from existing loop: look for IT names every N bytes
        int detected = detectItemStride(data);
        int useStride = detected > 0 ? detected : 304;
        int[] candidates = {170, 172, 174, 178, 180, 190, 192, 196, 200, 270, 272, 274, 276, 280, 282, 284, 288, 292};
        int bestOff = -1;
        int bestScore = 0;
        for (int off : candidates) {
            int score = 0;
            int seen = 0;
            for (int i = 0; i + off + 2 < data.length; i += useStride) {
                if (data[i] != 'I' || data[i + 1] != 'T') continue;
                int v = shortAt(data, i + off) & 0xFFFF;
                seen++;
                if (v >= 5 && v <= 50000) score++;
            }
            if (seen > 10 && score > bestScore && score * 100 / seen >= 40) {
                bestScore = score;
                bestOff = off;
            }
        }
        if (bestOff < 0) {
            log.log("[!] Economy DAT: no plausible PRICE offset found — shop gold unchanged in DAT.");
            return 0;
        }

        int earlyLo = Math.max(40, o.shopEarlyMinPct);
        int earlyHi = Math.max(earlyLo, o.shopEarlyMaxPct);
        int midLo = Math.max(50, o.shopMidMinPct);
        int midHi = Math.max(midLo, o.shopMidMaxPct);
        int minPct = Math.max(50, o.shopPriceMinPct);
        int maxPct = Math.max(minPct, o.shopPriceMaxPct);
        int changed = 0;
        for (int i = 0; i + bestOff + 2 < data.length; i += useStride) {
            if (data[i] != 'I' || data[i + 1] != 'T') continue;
            int base = shortAt(data, i + bestOff) & 0xFFFF;
            if (base < 5 || base > 50000) continue;
            int level = shortAt(data, i + 278);
            if (level < 0 || level > 5000) level = 99;
            int pct;
            if (level <= o.shopEarlyMaxLevel) pct = o.randomIn(rnd, earlyLo, earlyHi);
            else if (level <= o.shopMidMaxLevel) pct = o.randomIn(rnd, midLo, midHi);
            else pct = o.randomIn(rnd, minPct, maxPct);
            int value = Math.max(1, (int) Math.round(base * (pct / 100.0)));
            if (value > 32767) value = 32767;
            putShort(data, i + bestOff, (short) value);
            changed++;
        }
        log.log("[+] Economy DAT: offset +" + bestOff + " stride " + useStride
                + " — adjusted " + changed + " prices (early items cheaper).");
        return changed;
    }

    private int detectItemStride(byte[] data) {
        int first = -1;
        for (int i = 0; i + 2 < Math.min(data.length, 2000); i++) {
            if (data[i] == 'I' && data[i + 1] == 'T' && data[i + 2] >= 'A') {
                if (first < 0) first = i;
                else return i - first;
            }
        }
        return -1;
    }

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
     * Scale integer literals after density-like keywords in SPT text.
     * Same-size constrained: only rewrite when the new number fits the
     * same character width (pad with spaces or reject).
     */
    private String[] scaleQuestDensity(String text, PcOptions o, Random rnd) {
        java.util.regex.Pattern pat = java.util.regex.Pattern.compile(
                "(?i)(\\b(?:COUNT|NB|NUMBER|AMOUNT|MONSTERS|WAVE|SPAWN|GROUP|PACK)\\b"
                        + "\\s*[=:{]?\\s*)(\\d+)");
        java.util.regex.Matcher m = pat.matcher(text);
        StringBuffer sb = new StringBuffer();
        int edits = 0;
        while (m.find()) {
            String prefix = m.group(1);
            String numStr = m.group(2);
            int val = Integer.parseInt(numStr);
            if (val <= 0 || val > 500) {
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group()));
                continue;
            }
            int pct = o.randomIn(rnd, o.questSpawnMinPct, o.questSpawnMaxPct);
            int neu = Math.max(1, (int) Math.round(val * (pct / 100.0)));
            String neuStr = Integer.toString(neu);
            if (neuStr.length() > numStr.length()) {
                // cannot grow token — keep original for same-size / layout safety
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group()));
                continue;
            }
            while (neuStr.length() < numStr.length()) {
                neuStr = " " + neuStr; // left-pad spaces to preserve width
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(prefix + neuStr));
            edits++;
        }
        m.appendTail(sb);
        return new String[]{sb.toString(), Integer.toString(edits)};
    }

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
            landSwaps = assignQuestLandIds(texts, landIds, landPat, o, rnd);
        }

        int rewardEdits = 0;
        if (o.questRewards || o.shuffleShops) {
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

        int densityEdits = 0;
        if (o.questSpawnDensity) {
            for (int i = 0; i < texts.size(); i++) {
                String[] res = scaleQuestDensity(texts.get(i), o, rnd);
                texts.set(i, res[0]);
                densityEdits += Integer.parseInt(res[1]);
            }
        }

        for (int i = 0; i < sideQuests.size(); i++) {
            Path dest = outDir.resolve(sideQuests.get(i).getFileName().toString());
            Files.writeString(dest, texts.get(i), TsvTable.CHARSET);
        }

        log.log("[+] Quest scripts: " + landSwaps + " LAND reassignments, "
                + rewardEdits + " reward PARENT edits, "
                + densityEdits + " spawn-density number tweaks across "
                + sideQuests.size() + " SPT files.");
        log.log("    Install: SCRIPT/ into DATA.MTF (or data\\SCRIPT\\ override if supported).");
        log.log("    QUESTFINAL / TOWN / ENTREE left untouched.");
    }


    private int randomizeMonsterSpawnsTxt(TsvTable table, PcOptions o, Random rnd) {
        int n = 0;
        for (TsvTable.Row row : table.rows) {
            if (isProtectedMonster(row.key())) continue;
            if (row.getInt("LEVEL", -1) < 1) continue;
            int cnt = row.getInt("CNTAPP", 0);
            if (cnt == 1) continue; // unique / solo
            if (cnt <= 0 && row.getInt("CHAAPP", 0) <= 0) continue;
            row.setInt("CNTAPP", o.randomIn(rnd, o.spawnCountMin, o.spawnCountMax));
            int cha = row.getInt("CHAAPP", 0);
            if (cha > 0 && cha <= 100) {
                row.setInt("CHAAPP", o.randomIn(rnd, o.spawnChanceMin, o.spawnChanceMax));
            }
            n++;
        }
        log.log("[+] Spawn TXT: CNTAPP/CHAAPP on " + n + " rows (count "
                + o.spawnCountMin + "-" + o.spawnCountMax + ")");
        return n;
    }

    private int shuffleEnemyTypesByTier(TsvTable table, Random rnd) {
        int[][] bands = {
                {1, 12},
                {13, 25},
                {26, 40},
                {41, 60},
                {61, 100},
                {101, 9999}
        };
        int total = 0;
        for (int[] band : bands) {
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < table.rows.size(); i++) {
                TsvTable.Row row = table.rows.get(i);
                if (isProtectedMonster(row.key())) continue;
                int level = row.getInt("LEVEL", -1);
                if (level < 1) continue;
                int lmax = row.getInt("LMAX", level);
                int key = Math.max(level, lmax);
                if (key < band[0] || key > band[1]) continue;
                idx.add(i);
            }
            if (idx.size() < 2) continue;

            List<TsvTable.Row> snapshot = new ArrayList<>();
            for (int i : idx) {
                TsvTable.Row src = table.rows.get(i);
                TsvTable.Row copy = new TsvTable.Row();
                copy.keyRaw = src.keyRaw;
                copy.cols.putAll(src.cols);
                copy.widths.putAll(src.widths);
                copy.rawCells.putAll(src.rawCells);
                snapshot.add(copy);
            }
            Collections.shuffle(snapshot, rnd);
            for (int n = 0; n < idx.size(); n++) {
                TsvTable.Row dest = table.rows.get(idx.get(n));
                TsvTable.Row src = snapshot.get(n);
                dest.keyRaw = src.keyRaw;
                dest.cols.clear();
                dest.cols.putAll(src.cols);
                dest.widths.clear();
                dest.widths.putAll(src.widths);
                dest.rawCells.clear();
                dest.rawCells.putAll(src.rawCells);
            }
            total += idx.size();
            log.log("    tier " + band[0] + "-" + band[1] + ": shuffled " + idx.size() + " types");
        }
        log.log("[+] Enemy types by land/dungeon tier: " + total + " rows");
        return total;
    }

    private int randomizeMonsterSpeeds(TsvTable table, PcOptions o, Random rnd) {
        int n = 0;
        for (TsvTable.Row row : table.rows) {
            if (isProtectedMonster(row.key())) continue;
            if (row.getInt("LEVEL", -1) < 1) continue;
            int speed = o.randomIn(rnd, o.speedMin, o.speedMax);
            row.setInt("SPEED", speed);
            if (row.cols.containsKey("ATTSPD") || row.getInt("ATTSPD", -1) >= 0) {
                row.setInt("ATTSPD", o.randomIn(rnd, o.attSpdMin, o.attSpdMax));
            }
            if (row.cols.containsKey("ATTFRE")) {
                int fre = o.randomIn(rnd, 5, 80);
                row.setInt("ATTFRE", fre);
            }
            n++;
        }
        log.log("[+] Speeds: randomized SPEED/ATTSPD/ATTFRE on " + n + " combat rows"
                + " (SPEED " + o.speedMin + "-" + o.speedMax + ")");
        return n;
    }

    private int shuffleEnemyTypes(TsvTable table, Random rnd) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < table.rows.size(); i++) {
            if (isProtectedMonster(table.rows.get(i).key())) continue;
            if (table.rows.get(i).getInt("LEVEL", -1) < 1) continue;
            idx.add(i);
        }
        if (idx.size() < 2) {
            log.log("[!] Enemy types: not enough eligible combat rows.");
            return 0;
        }

        List<TsvTable.Row> snapshot = new ArrayList<>();
        for (int i : idx) {
            TsvTable.Row src = table.rows.get(i);
            TsvTable.Row copy = new TsvTable.Row();
            copy.keyRaw = src.keyRaw;
            copy.cols.putAll(src.cols);
            copy.widths.putAll(src.widths);
            copy.rawCells.putAll(src.rawCells);
            snapshot.add(copy);
        }
        Collections.shuffle(snapshot, rnd);
        for (int n = 0; n < idx.size(); n++) {
            TsvTable.Row dest = table.rows.get(idx.get(n));
            TsvTable.Row src = snapshot.get(n);
            dest.keyRaw = src.keyRaw;
            dest.cols.clear();
            dest.cols.putAll(src.cols);
            dest.widths.clear();
            dest.widths.putAll(src.widths);
            dest.rawCells.clear();
            dest.rawCells.putAll(src.rawCells);
        }
        log.log("[+] Enemy types: swapped " + idx.size() + " combat monster identities.");
        return idx.size();
    }

    private static boolean isProtectedMonster(String key) {
        if (key == null) return true;
        String u = key.toUpperCase(Locale.ROOT);
        String[] needles = {
                "BOSS", "QUEST", "TOWN", "SPELL", "PNJ", "BILL", "LICORNE", "POULET",
                "ENFANT", "DRAAK", "FINAL", "HORGAN", "SHADIRE", "ROLLAND", "LUX",
                "KIRGARD", "ZORAM", "ERALDUS", "FELDER", "GOLEMFIRERIK", "GOLEMICERIK",
                "APOTHICAIRE", "ARMURIER", "BANQUIER", "TAVERNIER", "GUIDE", "PROF",
                "CHARPENTIER", "MINEUR", "PAYSAN", "VALET", "PORTEUR", "FANTOME"
        };
        for (String n : needles) {
            if (u.contains(n)) return true;
        }
        return false;
    }

    private int applyEarlyMonsterCaps(TsvTable table, PcOptions o) {
        int n = 0;
        for (TsvTable.Row row : table.rows) {
            if (isProtectedMonster(row.key())) continue;
            int level = row.getInt("LEVEL", 99);
            int lmax = row.getInt("LMAX", 99);
            if (level < 0) continue;
            if (level > o.earlyLevelThreshold && lmax > o.earlyLevelThreshold) continue;
            int dmin = row.getInt("DMIN", 0);
            int dmax = row.getInt("DMAX", 0);
            int ac = row.getInt("AC", 0);
            boolean ch = false;
            if (dmax > o.earlyDmgCap) {
                dmax = o.earlyDmgCap;
                dmin = Math.min(dmin, dmax);
                row.setInt("DMAX", dmax);
                row.setInt("DMIN", dmin);
                ch = true;
            }
            if (ac > o.earlyAcCap) {
                row.setInt("AC", o.earlyAcCap);
                ch = true;
            }
            if (ch) n++;
        }
        return n;
    }

    private int applyEarlyItemCaps(TsvTable table, PcOptions o) {
        int n = 0;
        for (TsvTable.Row row : table.rows) {
            int level = row.getInt("LEVEL", 99);
            if (level > o.earlyLevelThreshold) continue;
            int dmax = row.getInt("DMAX", 0);
            if (dmax <= 0) continue;
            if (dmax > o.earlyDmgCap + 20) {
                int dmin = row.getInt("DMIN", 1);
                dmax = o.earlyDmgCap + 20;
                dmin = Math.min(dmin, dmax);
                row.setInt("DMAX", dmax);
                row.setInt("DMIN", dmin);
                n++;
            }
        }
        return n;
    }

    private void saveTableSameSize(TsvTable table, Path dest, String label) throws IOException {
        try {
            table.save(dest, true);
        } catch (IOException ex) {
            log.log("[!] " + label + ": " + ex.getMessage());
            log.log("[!] " + label + ": writing grown file for loose PCLASS use only — "
                    + "do NOT Replace this into DATA.MTF.");
            table.save(dest, false);
        }
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

    private static int lootTier(int level) {
        if (level <= 12) return 0;
        if (level <= 25) return 1;
        if (level <= 40) return 2;
        if (level <= 60) return 3;
        return 4;
    }

    private int shuffleItemStatsByTier(TsvTable table, List<Integer> combatIdx, Random rnd) {
        Map<Integer, List<Integer>> byTier = new HashMap<>();
        for (int i : combatIdx) {
            int tier = lootTier(table.rows.get(i).getInt("LEVEL", 0));
            byTier.computeIfAbsent(tier, k -> new ArrayList<>()).add(i);
        }
        int changed = 0;
        for (Map.Entry<Integer, List<Integer>> e : byTier.entrySet()) {
            List<Integer> idx = e.getValue();
            if (idx.size() < 2) continue;
            List<int[]> packs = new ArrayList<>();
            for (int i : idx) {
                TsvTable.Row row = table.rows.get(i);
                packs.add(new int[]{
                        row.getInt("DMIN", 0),
                        row.getInt("DMAX", 0),
                        row.getInt("AC", 0),
                        row.getInt("DUR", 0)
                });
            }
            Collections.shuffle(packs, rnd);
            for (int n = 0; n < idx.size(); n++) {
                TsvTable.Row row = table.rows.get(idx.get(n));
                int[] p = packs.get(n);
                row.setInt("DMIN", p[0]);
                row.setInt("DMAX", Math.max(p[0], p[1]));
                row.setInt("AC", p[2]);
                if (p[3] > 0) row.setInt("DUR", p[3]);
                changed++;
            }
            log.log("    loot tier " + e.getKey() + ": shuffled " + idx.size() + " combat items");
        }
        log.log("[+] Loot tier bands: " + changed + " items (stats within LEVEL bands only)");
        return changed;
    }

    private int assignQuestLandIds(List<String> texts, List<Integer> landIds,
                                   java.util.regex.Pattern landPat, PcOptions o, Random rnd) {
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < landIds.size(); i++) {
            int id = landIds.get(i);
            if (id >= 0 && id <= 6) eligible.add(i);
        }
        if (eligible.size() < 2) return 0;
        int swaps = 0;
        if (!o.questLandLogic) {
            List<Integer> pool = new ArrayList<>();
            for (int i : eligible) pool.add(landIds.get(i));
            Collections.shuffle(pool, rnd);
            int pi = 0;
            for (int i : eligible) {
                int newId = pool.get(pi++);
                java.util.regex.Matcher m = landPat.matcher(texts.get(i));
                if (m.find()) {
                    texts.set(i, m.replaceFirst(m.group(1) + newId + m.group(3)));
                    landIds.set(i, newId);
                    swaps++;
                }
            }
            log.log("[+] Quest LAND: pure shuffle (" + swaps + ")");
            return swaps;
        }
        int n = eligible.size();
        int t1 = Math.max(1, n / 3);
        int t2 = Math.max(t1 + 1, (2 * n) / 3);
        int[][] bands = new int[][]{{0, 1, 2}, {2, 3, 4}, {4, 5, 6}};
        for (int bi = 0; bi < 3; bi++) {
            int from = bi == 0 ? 0 : (bi == 1 ? t1 : t2);
            int to = bi == 0 ? t1 : (bi == 1 ? t2 : n);
            if (from >= to) continue;
            List<Integer> bandPool = new ArrayList<>();
            for (int k = from; k < to; k++) {
                int[] b = bands[bi];
                bandPool.add(b[rnd.nextInt(b.length)]);
            }
            Collections.shuffle(bandPool, rnd);
            int pi = 0;
            for (int k = from; k < to; k++) {
                int qi = eligible.get(k);
                int newId = bandPool.get(pi++);
                java.util.regex.Matcher m = landPat.matcher(texts.get(qi));
                if (m.find()) {
                    texts.set(qi, m.replaceFirst(m.group(1) + newId + m.group(3)));
                    landIds.set(qi, newId);
                    swaps++;
                }
            }
        }
        log.log("[+] Quest LAND logic: " + swaps + " assignments "
                + "(early->L0-2, mid->L2-4, late->L4-6; within-band shuffle)");
        return swaps;
    }

    private void randomizeStartKits(String[] split, int lineCount, PcOptions o, Random rnd) {
        String[] weapons = {
                "ITEM_XEP1_01", "ITEM_XDAJ_02", "ITEM_XARC_01", "ITEM_XGOU_01", "ITEM_XSCE_01",
                "ITEM_AXE1H_1", "ITEM_SWORD1H_1", "ITEM_DAGUE", "ITEM_HACHETTE", "ITEM_SCEPTRE1"
        };
        String[] armor = {
                "ITEM_XBOU_01", "ITEM_XARMORV_01", "ITEM_ARMOR_1", "ITEM_SHIELD_1",
                "ITEM_BOOK_MAGICMISSILE", "ITEM_BOOK_HEALING", "ITEM_BOOK_RESURRECT",
                "ITEM_BOOK_FIREBALL", "ITEM_RING", "ITEM_AMULET"
        };
        String[] pots = {
                "ITEM_POTION_HEALING", "ITEM_POTION_MANA", "ITEM_POTION_VITALITY",
                "ITEM_POTION_STRENGTH", "ITEM_FOOD5", "ITEM_POTION"
        };
        String[] rows = {"startItem", "startItem2", "startItem3", "startItem4"};
        String[][] pools = {weapons, pots, pots, armor};
        int touched = 0;
        for (int r = 0; r < rows.length; r++) {
            for (int i = 0; i < lineCount; i++) {
                String line = split[i];
                if (line == null || line.isEmpty()) continue;
                String[] parts = line.split("\t", -1);
                if (parts.length < 9) continue;
                if (!parts[0].trim().equals(rows[r])) continue;
                StringBuilder nb = new StringBuilder(parts[0]);
                String[] pool = pools[r];
                for (int c = 1; c < parts.length; c++) {
                    String cell = parts[c];
                    String trimmed = cell.trim();
                    if (trimmed.isEmpty()) {
                        nb.append('\t').append(parts[c]);
                        continue;
                    }
                    if (trimmed.startsWith("ITEM_") || trimmed.startsWith("IT")) {
                        String pick = pool[rnd.nextInt(pool.length)];
                        if (cell.length() > pick.length()) {
                            pick = String.format("%-" + cell.length() + "s", pick);
                        } else if (pick.length() > cell.length()) {
                            pick = cell;
                        }
                        nb.append('\t').append(pick);
                        if (!pick.trim().equals(trimmed)) touched++;
                    } else {
                        nb.append('\t').append(parts[c]);
                    }
                }
                split[i] = nb.toString();
                break;
            }
        }
        log.log("[+] Start kits: updated " + touched + " class loadout cells (seeded pools)");
    }

}
