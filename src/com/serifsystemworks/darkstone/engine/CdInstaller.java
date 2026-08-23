package com.serifsystemworks.darkstone.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Installs patched PSM archives back onto an extracted CD folder.
 * Prefers in-place {@code _source.psm} copies (header-preserving).
 * Falls back to {@code *.PSM.repacked} when present.
 */
public final class CdInstaller {

    private CdInstaller() {}

    public static int install(Path outputRoot, Path cdRoot, LogSink log) throws IOException {
        if (outputRoot == null || cdRoot == null) {
            log.log("[!] Error: Set CD folder and output directory first.");
            return 0;
        }
        if (!Files.isDirectory(cdRoot)) {
            log.log("[!] CD folder does not exist: " + cdRoot);
            return 0;
        }

        int fromSource = PsmArchive.installPatchedSources(outputRoot, cdRoot, log);
        if (fromSource > 0) {
            log.log("");
            log.log("DuckStation test:");
            log.log("  1. Open DuckStation");
            log.log("  2. File -> Open Folder...");
            log.log("  3. Choose: " + cdRoot);
            log.log("     (folder must contain SYSTEM.CNF)");
            return fromSource;
        }

        log.log("=== Copying repacked archives to CD folder (legacy) ===");
        List<Path> repacked;
        try (Stream<Path> walk = Files.walk(outputRoot)) {
            repacked = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toUpperCase(Locale.ROOT).endsWith(".PSM.REPACKED"))
                    .toList();
        }

        int copied = 0;
        int missing = 0;
        List<String> installed = new ArrayList<>();

        for (Path src : repacked) {
            Path relative = outputRoot.relativize(src);
            String name = relative.getFileName().toString();
            String psmName = name.substring(0, name.length() - ".repacked".length());
            Path destRelative = relative.getParent() == null
                    ? Path.of(psmName)
                    : relative.getParent().resolve(psmName);
            Path dest = cdRoot.resolve(destRelative);

            if (!Files.exists(dest.getParent())) {
                log.log("Skip (no CD subfolder): " + destRelative);
                missing++;
                continue;
            }

            Path backup = dest.resolveSibling(psmName + ".bak");
            if (Files.exists(dest) && !Files.exists(backup)) {
                Files.copy(dest, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            copied++;
            installed.add(destRelative.toString());
            log.log("INSTALLED: " + destRelative);
        }

        log.log("Copied " + copied + " archives (" + missing + " skipped).");
        log.log("");
        log.log("DuckStation test:");
        log.log("  1. Open DuckStation");
        log.log("  2. File -> Open Folder...");
        log.log("  3. Choose: " + cdRoot);
        if (!installed.isEmpty()) {
            log.analysis("Installed to CD:\n" + String.join("\n", installed));
        }
        return copied;
    }
}
