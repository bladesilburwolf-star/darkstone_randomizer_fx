package com.serifsystemworks.darkstone.pc;

import com.serifsystemworks.darkstone.engine.LogSink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Installs a generated seed package into the Darkstone game folder and optionally launches the EXE.
 * <p>
 * Seed package layout (under {@code seeds/&lt;seed&gt;/}):
 * <pre>
 *   config/seed.txt
 *   data/monsterclass.dat
 *   data/itemobject.dat
 *   data/pClass/pclass.txt   (optional)
 *   DATA_&lt;seed&gt;.MTF          (optional — replaces game DATA.MTF)
 *   PCLASS/*.TXT             (optional loose copies)
 * </pre>
 * Game-native seed file is always written as {@code config\seed.txt} with format {@code %d\n}.
 */
public final class PcSeedLauncher {

    private final LogSink log;

    public PcSeedLauncher(LogSink log) {
        this.log = log;
    }

    /** Resolve seeds/&lt;seed&gt; under outputRoot or gameRoot. */
    public static Path resolveSeedDir(Path gameRoot, Path outputRoot, String seedText) {
        String id = sanitize(seedText);
        if (outputRoot != null) {
            Path p = outputRoot.resolve("seeds").resolve(id);
            if (Files.isDirectory(p)) return p;
        }
        if (gameRoot != null) {
            Path p = gameRoot.resolve("seeds").resolve(id);
            if (Files.isDirectory(p)) return p;
        }
        if (outputRoot != null) {
            return outputRoot.resolve("seeds").resolve(id);
        }
        if (gameRoot != null) {
            return gameRoot.resolve("seeds").resolve(id);
        }
        return Path.of("seeds", id);
    }

    /**
     * Package current randomizer outputs into seeds/&lt;seed&gt;/ for later install/launch.
     * Call after {@link PcRandomizerEngine#run}.
     */
    public void packageSeed(PcOptions options) throws IOException {
        Path game = options.datSourceRoot != null ? options.datSourceRoot : options.gameRoot;
        Path out = options.outputRoot != null ? options.outputRoot : game;
        if (out == null) {
            throw new IllegalStateException("No output/game folder for seed package.");
        }
        String seedText = options.seedText == null || options.seedText.isBlank()
                ? PcOptions.randomSeedString() : options.seedText.trim();
        long seedInt = PcOptions.seedFromString(seedText) & 0x7fffffffL;
        Path seedDir;
        // If outputRoot is already .../seeds/<id>, package in place
        if (out.getFileName() != null
                && sanitize(seedText).equalsIgnoreCase(out.getFileName().toString())
                && out.getParent() != null
                && "seeds".equalsIgnoreCase(out.getParent().getFileName().toString())) {
            seedDir = out;
        } else {
            seedDir = out.resolve("seeds").resolve(sanitize(seedText));
        }
        Files.createDirectories(seedDir.resolve("config"));
        Files.createDirectories(seedDir.resolve("data").resolve("pClass"));

        Files.writeString(seedDir.resolve("config").resolve("seed.txt"),
                Long.toString(seedInt) + "\n", TsvTable.CHARSET);

        // DAT from output or game/data
        copyIfPresent(firstExisting(
                out.resolve("monsterclass.dat"),
                out.resolve("MONSTERCLASS.DAT"),
                out.resolve("data").resolve("monsterclass.dat"),
                game != null ? game.resolve("data").resolve("monsterclass.dat") : null,
                game != null ? game.resolve("MONSTERCLASS.DAT") : null
        ), seedDir.resolve("data").resolve("monsterclass.dat"));

        copyIfPresent(firstExisting(
                out.resolve("itemobject.dat"),
                out.resolve("ITEMOBJECT.DAT"),
                out.resolve("data").resolve("itemobject.dat"),
                game != null ? game.resolve("data").resolve("itemobject.dat") : null,
                game != null ? game.resolve("ITEMOBJECT.DAT") : null
        ), seedDir.resolve("data").resolve("itemobject.dat"));

        Path pclassOut = out.resolve("PCLASS");
        Files.createDirectories(seedDir.resolve("PCLASS"));
        if (Files.isDirectory(pclassOut)) {
            for (String name : List.of("PCLASS.TXT", "MONSTER.TXT", "OBJECT.TXT")) {
                Path src = pclassOut.resolve(name);
                if (Files.isRegularFile(src)) {
                    Files.copy(src, seedDir.resolve("PCLASS").resolve(name),
                            StandardCopyOption.REPLACE_EXISTING);
                    if (name.equalsIgnoreCase("PCLASS.TXT")) {
                        Files.createDirectories(seedDir.resolve("data").resolve("pClass"));
                        Files.copy(src, seedDir.resolve("data").resolve("pClass").resolve("pclass.txt"),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } else {
            Files.createDirectories(seedDir.resolve("PCLASS"));
        }

        // Optional seeded MTF already named DATA_<seed>.MTF in seedDir
        Path mtf = seedDir.resolve("DATA_" + sanitize(seedText) + ".MTF");
        if (!Files.isRegularFile(mtf) && out != null) {
            Path alt = out.resolve("DATA_" + sanitize(seedText) + ".MTF");
            if (Files.isRegularFile(alt)) {
                Files.copy(alt, mtf, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        StringBuilder man = new StringBuilder();
        man.append("seedText=").append(seedText).append('\n');
        man.append("seedInt=").append(seedInt).append('\n');
        man.append("preset=").append(options.preset).append('\n');
        man.append("platform=PC\n");
        Files.writeString(seedDir.resolve("manifest.txt"), man.toString(), TsvTable.CHARSET);

        log.log("[+] Seed package: " + seedDir);
        log.log("    seedInt=" + seedInt + "  (config/seed.txt)");
        listPack(seedDir);
    }

    /**
     * Copy seed package into the live game folder.
     *
     * @param replaceDataMtf if true and package has DATA_*.MTF, backup and replace game DATA.MTF
     */
    public void installSeed(Path gameRoot, Path seedDir, boolean replaceDataMtf) throws IOException {
        if (gameRoot == null || !Files.isDirectory(gameRoot)) {
            throw new IllegalStateException("Game folder not set.");
        }
        if (seedDir == null || !Files.isDirectory(seedDir)) {
            throw new IllegalStateException("Seed folder not found: " + seedDir);
        }

        log.log("[*] Installing seed from " + seedDir + " -> " + gameRoot);

        Path cfg = gameRoot.resolve("config");
        Files.createDirectories(cfg);
        Path seedTxt = seedDir.resolve("config").resolve("seed.txt");
        if (Files.isRegularFile(seedTxt)) {
            Files.copy(seedTxt, cfg.resolve("seed.txt"), StandardCopyOption.REPLACE_EXISTING);
            log.log("    config/seed.txt <- " + Files.readString(seedTxt, TsvTable.CHARSET).trim());
        } else {
            // derive from folder name
            long seedInt = PcOptions.seedFromString(seedDir.getFileName().toString()) & 0x7fffffffL;
            Files.writeString(cfg.resolve("seed.txt"), Long.toString(seedInt) + "\n", TsvTable.CHARSET);
            log.log("    config/seed.txt <- " + seedInt + " (from folder name)");
        }

        Path dataDir = gameRoot.resolve("data");
        Files.createDirectories(dataDir);
        copyIfPresent(seedDir.resolve("data").resolve("monsterclass.dat"),
                dataDir.resolve("monsterclass.dat"));
        copyIfPresent(seedDir.resolve("data").resolve("itemobject.dat"),
                dataDir.resolve("itemobject.dat"));
        // uppercase fallbacks some installs use
        copyIfPresent(seedDir.resolve("data").resolve("monsterclass.dat"),
                dataDir.resolve("MONSTERCLASS.DAT"));
        copyIfPresent(seedDir.resolve("data").resolve("itemobject.dat"),
                dataDir.resolve("ITEMOBJECT.DAT"));

        Path pclassGame = dataDir.resolve("pClass");
        Files.createDirectories(pclassGame);
        Path pclassSrc = seedDir.resolve("data").resolve("pClass").resolve("pclass.txt");
        if (!Files.isRegularFile(pclassSrc)) {
            pclassSrc = seedDir.resolve("PCLASS").resolve("PCLASS.TXT");
        }
        if (Files.isRegularFile(pclassSrc)) {
            Path destP = pclassGame.resolve("pclass.txt");
            if (Files.isRegularFile(destP)) {
                Path bak = pclassGame.resolve("pclass.txt.truestone_bak");
                if (!Files.exists(bak)) {
                    Files.copy(destP, bak, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.copy(pclassSrc, destP, StandardCopyOption.REPLACE_EXISTING);
            log.log("    data/pClass/pclass.txt (" + Files.size(pclassSrc) + " bytes)");
        } else {
            log.log("    [i] No pclass.txt in seed package");
        }

        if (replaceDataMtf) {
            Path packaged = null;
            try (Stream<Path> s = Files.list(seedDir)) {
                for (Path p : s.filter(x -> {
                    String n = x.getFileName().toString().toUpperCase(Locale.ROOT);
                    return n.startsWith("DATA_") && n.endsWith(".MTF");
                }).toList()) {
                    packaged = p;
                    break;
                }
            }
            if (packaged != null && Files.isRegularFile(packaged)) {
                Path dest = gameRoot.resolve("DATA.MTF");
                Path bak = gameRoot.resolve("DATA.MTF.launcher_bak");
                if (Files.isRegularFile(dest) && !Files.exists(bak)) {
                    Files.copy(dest, bak, StandardCopyOption.REPLACE_EXISTING);
                    log.log("    Backup DATA.MTF -> DATA.MTF.launcher_bak");
                }
                Files.copy(packaged, dest, StandardCopyOption.REPLACE_EXISTING);
                log.log("    DATA.MTF <- " + packaged.getFileName());
            } else {
                log.log("    [i] No DATA_*.MTF in seed package — left game DATA.MTF unchanged.");
            }
        }

        log.log("[+] Install complete.");
    }

    /** Start Darkstone.exe (or Darkstone_patched.exe) in the game folder. */
    public void launchGame(Path gameRoot) throws IOException {
        if (gameRoot == null || !Files.isDirectory(gameRoot)) {
            throw new IllegalStateException("Game folder not set.");
        }
        // Game EXE lives in the install root only (not data/, not TrueStone folder)
        Path exe = firstExisting(
                gameRoot.resolve("Darkstone.exe"),
                gameRoot.resolve("Darkstone_patched.exe"),
                gameRoot.resolve("darkstone.exe")
        );
        if (exe == null) {
            throw new IllegalStateException(
                    "Darkstone.exe not found in game folder:\n  " + gameRoot
                    + "\n(TrueStone is the randomizer — the game EXE must be named Darkstone.exe here.)");
        }
        log.log("[*] Launching " + exe.getFileName() + " in " + gameRoot);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (os.contains("win")) {
            // Detach via cmd start so the game is not a Java child process
            // (old DSI builds are finicky when spawned directly).
            pb = new ProcessBuilder(
                    "cmd.exe", "/c", "start", "TrueStone", "/D",
                    gameRoot.toAbsolutePath().toString(),
                    exe.getFileName().toString());
            pb.directory(gameRoot.toFile());
        } else {
            pb = new ProcessBuilder(exe.toAbsolutePath().toString());
            pb.directory(gameRoot.toFile());
        }
        pb.start();
        log.log("[+] Game process started (detached).");
        log.log("[i] If it crashes before the menu: restore DATA.MTF.launcher_bak /");
        log.log("    DATA.MTF from BACKUP, and remove data\\pClass\\pclass.txt to test.");
    }

    /** Install + launch in one step. */
    public void installAndLaunch(Path gameRoot, Path seedDir, boolean replaceDataMtf) throws IOException {
        installSeed(gameRoot, seedDir, replaceDataMtf);
        launchGame(gameRoot);
    }

    private void listPack(Path seedDir) throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(seedDir, 3)) {
            walk.filter(Files::isRegularFile).forEach(p -> found.add(seedDir.relativize(p).toString()));
        }
        for (String f : found) {
            log.log("    + " + f);
        }
    }

    private static void copyIfPresent(Path src, Path dst) throws IOException {
        if (src == null || !Files.isRegularFile(src)) return;
        Files.createDirectories(dst.getParent());
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path firstExisting(Path... paths) {
        for (Path p : paths) {
            if (p != null && Files.isRegularFile(p)) return p;
        }
        return null;
    }

    public static String sanitize(String seed) {
        if (seed == null || seed.isBlank()) return "seed";
        String s = seed.replaceAll("[^A-Za-z0-9_-]", "");
        return s.isEmpty() ? "seed" : (s.length() > 32 ? s.substring(0, 32) : s);
    }
}
