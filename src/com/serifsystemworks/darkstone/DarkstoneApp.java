package com.serifsystemworks.darkstone;

import com.serifsystemworks.darkstone.engine.CdInstaller;
import com.serifsystemworks.darkstone.engine.LogSink;
import com.serifsystemworks.darkstone.engine.PsmArchive;
import com.serifsystemworks.darkstone.engine.RandomizerEngine;
import com.serifsystemworks.darkstone.engine.RandomizerOptions;
import com.serifsystemworks.darkstone.engine.ScanResult;
import com.serifsystemworks.darkstone.engine.TableScanner;
import com.serifsystemworks.darkstone.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DarkstoneApp extends Application {

    private static final LogSink CONSOLE = System.out::println;

    @Override
    public void start(Stage stage) {
        MainView view = new MainView(stage);
        Scene scene = new Scene(view.build(), 1280, 820);
        var css = MainView.class.getResource("theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setTitle("Darkstone PSX Randomizer " + MainView.VERSION);
        stage.setMinWidth(1024);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        if (args.length == 0 || hasFlag(args, "--gui")) {
            launch(args);
            return;
        }
        try {
            int code = runCli(args);
            System.exit(code);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int runCli(String[] args) throws Exception {
        if (hasFlag(args, "--help") || hasFlag(args, "-h")) {
            printUsage();
            return 0;
        }

        String seed = valueOf(args, "--seed");
        if (seed == null) {
            seed = RandomizerOptions.randomSeedString();
        }

        if (hasFlag(args, "--unpack")) {
            List<String> rest = after(args, "--unpack");
            if (rest.size() < 2) {
                System.err.println("--unpack requires <cd-folder> <output-folder>");
                return 2;
            }
            PsmArchive.unpackTree(Path.of(rest.get(0)), Path.of(rest.get(1)), !hasFlag(args, "--force"), CONSOLE);
            return 0;
        }

        if (hasFlag(args, "--scan")) {
            Path out = Path.of(requireValue(args, "--scan"));
            ScanResult result = TableScanner.scan(out);
            System.out.println(result.summary());
            System.out.println(result.oneLine());
            return 0;
        }

        if (hasFlag(args, "--repack")) {
            PsmArchive.repackAll(Path.of(requireValue(args, "--repack")), CONSOLE);
            return 0;
        }

        if (hasFlag(args, "--install")) {
            List<String> rest = after(args, "--install");
            if (rest.size() < 2) {
                System.err.println("--install requires <output-folder> <cd-folder>");
                return 2;
            }
            CdInstaller.install(Path.of(rest.get(0)), Path.of(rest.get(1)), CONSOLE);
            return 0;
        }

        if (hasFlag(args, "--randomize-all") || hasFlag(args, "--randomize")) {
            String key = hasFlag(args, "--randomize-all") ? "--randomize-all" : "--randomize";
            Path out = Path.of(requireValue(args, key));
            boolean modules = hasAnyModuleFlag(args);
            RandomizerOptions options = new RandomizerOptions();
            options.seedText = seed;
            options.loot = modules ? hasFlag(args, "--loot") : true;
            options.enemies = modules ? hasFlag(args, "--enemies") : true;
            options.heroes = modules ? hasFlag(args, "--heroes") : true;
            options.shops = modules ? hasFlag(args, "--shops") : true;
            options.maps = hasFlag(args, "--maps");
            options.quests = hasFlag(args, "--quests");
            options.startingGear = !modules || hasFlag(args, "--gear");
            options.startingGold = !modules || hasFlag(args, "--gold");
            options.startingSpells = !modules || hasFlag(args, "--spells");
            options.disableVideos = hasFlag(args, "--no-videos");
            String statRange = valueOf(args, "--stat-range");
            if (statRange != null && statRange.contains("-")) {
                String[] parts = statRange.split("-", 2);
                options.statMin = Integer.parseInt(parts[0].trim());
                options.statMax = Integer.parseInt(parts[1].trim());
            }
            String goldRange = valueOf(args, "--gold-range");
            if (goldRange != null && goldRange.contains("-")) {
                String[] parts = goldRange.split("-", 2);
                options.goldMin = Integer.parseInt(parts[0].trim());
                options.goldMax = Integer.parseInt(parts[1].trim());
            }
            RandomizerEngine engine = new RandomizerEngine(out, CONSOLE);
            engine.runMaster(options);
            String cd = valueOf(args, "--cd");
            if (cd != null) {
                if (options.disableVideos) {
                    engine.disableVideos(Path.of(cd));
                }
                CdInstaller.install(out, Path.of(cd), CONSOLE);
            }
            return 0;
        }

        printUsage();
        return 2;
    }

    private static boolean hasAnyModuleFlag(String[] args) {
        return hasFlag(args, "--loot") || hasFlag(args, "--enemies") || hasFlag(args, "--heroes")
                || hasFlag(args, "--shops") || hasFlag(args, "--maps") || hasFlag(args, "--quests")
                || hasFlag(args, "--gear") || hasFlag(args, "--gold") || hasFlag(args, "--spells");
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) {
            if (flag.equals(a)) {
                return true;
            }
        }
        return false;
    }

    private static String valueOf(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                String next = args[i + 1];
                if (!next.startsWith("--")) {
                    return next;
                }
            }
        }
        return null;
    }

    private static String requireValue(String[] args, String flag) {
        String v = valueOf(args, flag);
        if (v == null) {
            throw new IllegalArgumentException(flag + " requires a path argument");
        }
        return v;
    }

    private static List<String> after(String[] args, String flag) {
        List<String> rest = new ArrayList<>();
        boolean take = false;
        for (String a : args) {
            if (take) {
                if (a.startsWith("--")) {
                    break;
                }
                rest.add(a);
            } else if (flag.equals(a)) {
                take = true;
            }
        }
        return rest;
    }

    private static void printUsage() {
        System.out.println("""
                Darkstone PSX Randomizer %s

                GUI:
                  java ... com.serifsystemworks.darkstone.DarkstoneApp
                  java ... com.serifsystemworks.darkstone.DarkstoneApp --gui

                CLI:
                  --unpack <cd-folder> <output-folder> [--force]
                  --scan <output-folder>
                  --randomize-all <output-folder> --seed <seed>
                      [--loot] [--enemies] [--heroes] [--shops] [--maps] [--quests]
                      [--gear] [--gold] [--spells] [--no-videos]
                      [--stat-range 12-35] [--gold-range 50-500]
                      [--cd <cd-folder>]
                  --repack <output-folder>
                  --install <output-folder> <cd-folder>
                  --help
                """.formatted(MainView.VERSION));
    }
}
