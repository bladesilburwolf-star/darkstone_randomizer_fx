package com.serifsystemworks.psxdisc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Multi-file PSX BIN/CUE patcher for Darkstone-style multi-track discs.
 *
 * <pre>
 *   list    --cue game.cue
 *   replace --cue game.cue --dir patched_psm/ --out out_dir/ [--suffix _R]
 *   replace --cue game.cue --file DATA1.PSM=./DATA1.PSM --file TOWN.PSM=./TOWN.PSM --out out/
 * </pre>
 *
 * Copies all BIN tracks into the output folder, patches the primary data track
 * in place (same-size or smaller ISO extents only), writes a new CUE.
 */
public final class PsxDiscTool {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            System.exit(1);
        }
        String cmd = args[0].toLowerCase(Locale.ROOT);
        Map<String, String> opt = parseOpts(args);
        Path cuePath = Path.of(required(opt, "cue"));

        switch (cmd) {
            case "list" -> cmdList(cuePath);
            case "replace" -> cmdReplace(cuePath, opt);
            default -> {
                System.err.println("Unknown command: " + cmd);
                usage();
                System.exit(1);
            }
        }
    }

    private static void cmdList(Path cuePath) throws Exception {
        CueSheet cue = CueSheet.parse(cuePath);
        Path bin = cue.primaryDataBin();
        int sector = cue.sectorSizeForPrimary();
        System.out.println("CUE: " + cuePath);
        System.out.println("Data BIN: " + bin + " (sector " + sector + ")");
        for (CueSheet.FileEntry f : cue.files) {
            System.out.println("  FILE " + f.name + " tracks=" + f.tracks.size());
            for (CueSheet.Track t : f.tracks) {
                System.out.println("    TRACK " + t.number + " " + t.mode);
            }
        }
        Iso9660Patcher iso = new Iso9660Patcher(bin, sector);
        List<Iso9660Patcher.IsoFile> files = iso.listFiles();
        System.out.println("ISO files: " + files.size());
        for (Iso9660Patcher.IsoFile f : files) {
            if (f.path.toUpperCase(Locale.ROOT).endsWith(".PSM")
                    || f.path.toUpperCase(Locale.ROOT).contains("SYSTEM")
                    || f.path.toUpperCase(Locale.ROOT).endsWith(".STR")) {
                System.out.printf("  %-40s LBA=%d size=%d%n", f.path, f.extentLba, f.dataLength);
            }
        }
        System.out.println("(Listing highlights PSM/SYSTEM/STR; full tree available in code.)");
    }

    private static void cmdReplace(Path cuePath, Map<String, String> opt) throws Exception {
        Path outDir = Path.of(required(opt, "out"));
        Files.createDirectories(outDir);
        String suffix = opt.getOrDefault("suffix", "_RND");

        CueSheet cue = CueSheet.parse(cuePath);
        Path dataBin = cue.primaryDataBin();
        if (dataBin == null || !Files.isRegularFile(dataBin)) {
            throw new IllegalStateException("Primary data BIN not found for " + cuePath);
        }
        int sector = cue.sectorSizeForPrimary();

        // Copy every BIN referenced by the CUE into outDir with suffix
        for (CueSheet.FileEntry f : cue.files) {
            Path src = cue.baseDir.resolve(f.name);
            String outName = f.name;
            if (outName.toLowerCase(Locale.ROOT).endsWith(".bin")) {
                int dot = outName.lastIndexOf('.');
                outName = outName.substring(0, dot) + suffix + outName.substring(dot);
            }
            Path dst = outDir.resolve(outName);
            System.out.println("Copy " + src.getFileName() + " -> " + dst.getFileName());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }

        // Map output data bin path
        String dataName = dataBin.getFileName().toString();
        String outDataName = dataName;
        if (outDataName.toLowerCase(Locale.ROOT).endsWith(".bin")) {
            int dot = outDataName.lastIndexOf('.');
            outDataName = outDataName.substring(0, dot) + suffix + outDataName.substring(dot);
        }
        Path outDataBin = outDir.resolve(outDataName);

        Map<String, byte[]> reps = new LinkedHashMap<>();
        if (opt.containsKey("dir")) {
            reps.putAll(Iso9660Patcher.loadFolder(Path.of(opt.get("dir"))));
        }
        // --file NAME=path (multiple)
        for (Map.Entry<String, String> e : opt.entrySet()) {
            if (e.getKey().startsWith("file:")) {
                String isoName = e.getKey().substring(5);
                reps.put(isoName, Files.readAllBytes(Path.of(e.getValue())));
            }
        }
        if (reps.isEmpty()) {
            throw new IllegalStateException("No replacements: use --dir folder and/or --file NAME=path");
        }

        Iso9660Patcher iso = new Iso9660Patcher(outDataBin, sector);
        int n = iso.replaceAll(reps);
        System.out.println("Patched " + n + " / " + reps.size() + " file(s)");

        String cueText = cue.toCueText(suffix);
        Path outCue = outDir.resolve(stripExt(cuePath.getFileName().toString()) + suffix + ".cue");
        Files.writeString(outCue, cueText);
        System.out.println("Wrote " + outCue);
        System.out.println("Done. Point emulator at the new CUE.");
    }

    private static String stripExt(String n) {
        int d = n.lastIndexOf('.');
        return d > 0 ? n.substring(0, d) : n;
    }

    private static Map<String, String> parseOpts(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--") && i + 1 < args.length) {
                String key = a.substring(2).toLowerCase(Locale.ROOT);
                String val = args[++i];
                if (key.equals("file")) {
                    int eq = val.indexOf('=');
                    if (eq > 0) {
                        m.put("file:" + val.substring(0, eq), val.substring(eq + 1));
                    }
                } else {
                    m.put(key, val);
                }
            }
        }
        return m;
    }

    private static String required(Map<String, String> opt, String key) {
        String v = opt.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing --" + key);
        }
        return v;
    }

    private static void usage() {
        System.out.println("""
                PsxDiscTool — multi-file BIN/CUE patcher (Darkstone multi-track friendly)

                  list    --cue game.cue
                  replace --cue game.cue --out out_dir [--suffix _RND] \\
                          [--dir folder_of_psm] [--file DATA1.PSM=./DATA1.PSM] ...

                Replacements must be <= original ISO file size (PSM same-size patches OK).
                All BIN tracks are copied; only the primary MODE data track is patched.
                """);
    }
}
