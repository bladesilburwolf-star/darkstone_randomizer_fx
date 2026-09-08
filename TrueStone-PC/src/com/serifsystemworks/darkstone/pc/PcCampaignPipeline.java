package com.serifsystemworks.darkstone.pc;

import com.serifsystemworks.darkstone.engine.LogSink;
import com.serifsystemworks.darkstone.mtf.MtfArchive;
import com.serifsystemworks.darkstone.mtf.MtfBackupManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Campaign workflow: DATA.MTF → extract work tree → randomize → seeded DATA_&lt;seed&gt;.MTF.
 * <p>
 * Main menu seed injection is not possible without EXE hooks; this produces
 * discrete archives you can swap into the game folder or {@code quest/}.
 */
public final class PcCampaignPipeline {

    private final LogSink log;

    public PcCampaignPipeline(LogSink log) {
        this.log = log;
    }

    /**
     * Run one or more seeds. Each seed gets
     * {@code outputRoot/seeds/&lt;seed&gt;/} with PCLASS, SCRIPT, DAT, and {@code DATA_&lt;seed&gt;.MTF}.
     */
    public void run(PcOptions base) throws IOException {
        if (base.dataMtfPath == null || !Files.isRegularFile(base.dataMtfPath)) {
            throw new IllegalStateException("Select a DATA.MTF for the campaign pipeline.");
        }
        Path outRoot = base.outputRoot != null ? base.outputRoot : base.gameRoot;
        if (outRoot == null) {
            outRoot = base.dataMtfPath.getParent();
        }
        Files.createDirectories(outRoot);

        int count = Math.max(1, Math.min(32, base.multiSeedCount));
        String primarySeed = base.seedText == null || base.seedText.isBlank()
                ? PcOptions.randomSeedString() : base.seedText.trim();

        for (int n = 0; n < count; n++) {
            String seed = (n == 0) ? primarySeed : PcOptions.randomSeedString();
            log.log("---------- seed " + (n + 1) + "/" + count + " : " + seed + " ----------");
            runOne(base, outRoot, seed);
        }
        log.log("Campaign pipeline finished (" + count + " seed(s)).");
        log.log("Install: copy chosen DATA_<seed>.MTF over the game DATA.MTF (keep a backup).");
        log.log("Main-menu seed entry requires an EXE patch — not available yet.");
    }

    private void runOne(PcOptions base, Path outRoot, String seed) throws IOException {
        Path seedDir = outRoot.resolve("seeds").resolve(sanitize(seed));
        Path work = seedDir.resolve("work");
        Path pclassDir = work.resolve("PCLASS");
        Path scriptDir = work.resolve("SCRIPT");
        Files.createDirectories(pclassDir);
        Files.createDirectories(scriptDir);

        MtfArchive archive = new MtfArchive();
        archive.open(base.dataMtfPath);
        try {
            extractMatching(archive, pclassDir, "PCLASS", ".TXT");
            extractMatching(archive, scriptDir, "SCRIPT", ".SPT");
        } finally {
            archive.close();
        }

        PcOptions o = copyOptions(base);
        o.seedText = seed;
        o.gameRoot = work;
        o.outputRoot = seedDir;
        // DAT files live in the real game install, not the extract work tree
        o.datSourceRoot = base.gameRoot != null ? base.gameRoot : base.dataMtfPath.getParent();
        o.injectMtf = false;
        o.dataMtfPath = base.dataMtfPath;
        o.campaignPipeline = false;

        new PcRandomizerEngine(log).run(o);

        // Ensure DAT outputs sit in seedDir for install
        for (String dat : new String[]{"MONSTERCLASS.DAT", "ITEMOBJECT.DAT"}) {
            Path produced = seedDir.resolve(dat);
            if (!Files.isRegularFile(produced) && o.datSourceRoot != null) {
                Path fromOut = seedDir.resolve(dat);
                // engine writes next to outputRoot
            }
            if (Files.isRegularFile(seedDir.resolve(dat))) {
                log.log("[i] DAT ready: " + seedDir.resolve(dat));
            }
        }

        // Build replacement map from seedDir outputs
        Map<String, byte[]> changes = new HashMap<>();
        Path outPclass = seedDir.resolve("PCLASS");
        if (!Files.isDirectory(outPclass)) outPclass = pclassDir;
        stageTxt(changes, base.dataMtfPath, outPclass, "MONSTER.TXT");
        stageTxt(changes, base.dataMtfPath, outPclass, "OBJECT.TXT");
        stageTxt(changes, base.dataMtfPath, outPclass, "PCLASS.TXT");

        Path outScript = seedDir.resolve("SCRIPT");
        if (Files.isDirectory(outScript)) {
            try (var stream = Files.list(outScript)) {
                for (Path spt : stream.filter(p -> p.getFileName().toString().toUpperCase(Locale.ROOT).endsWith(".SPT")).toList()) {
                    stageScript(changes, base.dataMtfPath, spt);
                }
            }
        }

        if (changes.isEmpty()) {
            log.log("[!] Seed " + seed + ": no same-size entries to pack — skipped MTF write.");
            return;
        }

        Path outMtf = seedDir.resolve("DATA_" + sanitize(seed) + ".MTF");
        MtfArchive src = new MtfArchive();
        src.open(base.dataMtfPath);
        try {
            MtfArchive.RebuildReport report = src.rebuildSameSize(outMtf, changes);
            log.log("[+] Wrote " + outMtf.getFileName() + " — " + report);
            for (String w : report.warnings) log.log("    warn: " + w);
        } finally {
            src.close();
        }

        if (base.copyToQuestFolder && base.gameRoot != null) {
            Path quest = base.gameRoot.resolve("quest");
            Files.createDirectories(quest);
            Path dest = quest.resolve("DATA_" + sanitize(seed) + ".MTF");
            Files.copy(outMtf, dest, StandardCopyOption.REPLACE_EXISTING);
            log.log("[+] Quest folder copy: " + dest);
        }
    }

    private void extractMatching(MtfArchive archive, Path destDir, String folderToken, String suffix)
            throws IOException {
        String token = folderToken.toUpperCase(Locale.ROOT);
        String suf = suffix.toUpperCase(Locale.ROOT);
        int n = 0;
        for (MtfArchive.Entry e : archive.getEntries()) {
            String up = e.path.toUpperCase(Locale.ROOT);
            if (!up.contains(token) || !up.endsWith(suf)) continue;
            // only top-level files under that folder (avoid CLASSES sub-xlsx etc for TXT we want main tables)
            String name = e.path.substring(e.path.lastIndexOf('\\') + 1);
            if (name.indexOf('\\') >= 0) continue;
            byte[] data = archive.extract(e.path);
            Files.write(destDir.resolve(name), data);
            n++;
        }
        log.log("[+] Extracted " + n + " " + folderToken + " file(s) → " + destDir);
    }

    private void stageTxt(Map<String, byte[]> changes, Path mtfPath, Path dir, String name)
            throws IOException {
        Path f = dir.resolve(name);
        if (!Files.isRegularFile(f)) return;
        MtfArchive a = new MtfArchive();
        a.open(mtfPath);
        try {
            String path = "DATA\\PCLASS\\" + name;
            MtfArchive.Entry e = a.getEntry(path);
            if (e == null) return;
            byte[] data = Files.readAllBytes(f);
            if (data.length != e.decompSize) {
                log.log("[!] " + name + " " + data.length + " != " + e.decompSize + " — not packed");
                return;
            }
            changes.put(e.path, data);
        } finally {
            a.close();
        }
    }

    private void stageScript(Map<String, byte[]> changes, Path mtfPath, Path spt) throws IOException {
        MtfArchive a = new MtfArchive();
        a.open(mtfPath);
        try {
            String name = spt.getFileName().toString();
            MtfArchive.Entry ent = a.getEntry("DATA\\SCRIPT\\" + name);
            if (ent == null) {
                for (MtfArchive.Entry e : a.getEntries()) {
                    String up = e.path.toUpperCase(Locale.ROOT);
                    if (up.contains("SCRIPT") && up.endsWith("\\" + name.toUpperCase(Locale.ROOT))) {
                        ent = e;
                        break;
                    }
                }
            }
            if (ent == null) return;
            byte[] data = Files.readAllBytes(spt);
            if (data.length != ent.decompSize) {
                log.log("[!] " + name + " size mismatch — not packed");
                return;
            }
            changes.put(ent.path, data);
        } finally {
            a.close();
        }
    }

    private static PcOptions copyOptions(PcOptions base) {
        PcOptions o = new PcOptions();
        o.monsters = base.monsters;
        o.items = base.items;
        o.playerClasses = base.playerClasses;
        o.shuffleMonsterStats = base.shuffleMonsterStats;
        o.shuffleItemStats = base.shuffleItemStats;
        o.rangeRollMonsters = base.rangeRollMonsters;
        o.rangeRollItems = base.rangeRollItems;
        o.shuffleEnemyTypes = base.shuffleEnemyTypes;
        o.landEnemyShuffle = base.landEnemyShuffle;
        o.randomizeSpeeds = base.randomizeSpeeds;
        o.randomizeSpawnCounts = base.randomizeSpawnCounts;
        o.spawnCountMin = base.spawnCountMin;
        o.spawnCountMax = base.spawnCountMax;
        o.spawnChanceMin = base.spawnChanceMin;
        o.spawnChanceMax = base.spawnChanceMax;
        o.questSpawnDensity = base.questSpawnDensity;
        o.questSpawnMinPct = base.questSpawnMinPct;
        o.questSpawnMaxPct = base.questSpawnMaxPct;
        o.shopEarlyMaxLevel = base.shopEarlyMaxLevel;
        o.shopEarlyMinPct = base.shopEarlyMinPct;
        o.shopEarlyMaxPct = base.shopEarlyMaxPct;
        o.shopMidMaxLevel = base.shopMidMaxLevel;
        o.shopMidMinPct = base.shopMidMinPct;
        o.shopMidMaxPct = base.shopMidMaxPct;
        o.speedMin = base.speedMin;
        o.speedMax = base.speedMax;
        o.attSpdMin = base.attSpdMin;
        o.attSpdMax = base.attSpdMax;
        o.patchDat = base.patchDat;
        o.datSourceRoot = base.datSourceRoot;
        o.landProps = base.landProps;
        o.questScripts = base.questScripts;
        o.questRewards = base.questRewards;
        o.shuffleShops = base.shuffleShops;
        o.lootTierBands = base.lootTierBands;
        o.startKits = base.startKits;
        o.questLandLogic = base.questLandLogic;
        o.progressionRedesign = base.progressionRedesign;
        o.itemPowerLag = base.itemPowerLag;
        o.earlyGameCaps = base.earlyGameCaps;
        o.earlyDmgCap = base.earlyDmgCap;
        o.earlyAcCap = base.earlyAcCap;
        o.earlyLevelThreshold = base.earlyLevelThreshold;
        o.itemLevelUnlock = base.itemLevelUnlock;
        o.itemLevelReduction = base.itemLevelReduction;
        o.earlyMonsterScale = base.earlyMonsterScale;
        o.balanceShopEconomy = base.balanceShopEconomy;
        o.improveClasses = base.improveClasses;
        o.randomizeMonsterPower = base.randomizeMonsterPower;
        o.startingGoldCap = base.startingGoldCap;
        o.startingGoldAbsoluteMin = base.startingGoldAbsoluteMin;
        o.startingGoldAbsoluteMax = base.startingGoldAbsoluteMax;
        o.randomizeStartingGold = base.randomizeStartingGold;
        o.weaponKindFloor = base.weaponKindFloor;
        o.weaponKindCeil = base.weaponKindCeil;
        o.dmgMin = base.dmgMin;
        o.dmgMax = base.dmgMax;
        o.acMin = base.acMin;
        o.acMax = base.acMax;
        o.levelMin = base.levelMin;
        o.levelMax = base.levelMax;
        o.preset = base.preset;
        return o;
    }

    private static String sanitize(String seed) {
        if (seed == null || seed.isBlank()) return "seed";
        String s = seed.replaceAll("[^A-Za-z0-9_-]", "");
        return s.isEmpty() ? "seed" : (s.length() > 32 ? s.substring(0, 32) : s);
    }
}
