package com.serifsystemworks.darkstone.ui;

import com.serifsystemworks.darkstone.engine.LogSink;
import com.serifsystemworks.darkstone.engine.PsmArchive;
import com.serifsystemworks.darkstone.engine.RandomizerEngine;
import com.serifsystemworks.darkstone.engine.RandomizerOptions;
import com.serifsystemworks.psxdisc.CueSheet;
import com.serifsystemworks.psxdisc.Iso9660Patcher;
import com.serifsystemworks.darkstone.engine.ScanResult;
import com.serifsystemworks.darkstone.engine.TableScanner;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SOTN-style Darkstone randomizer: PSM patch + integrated multi-BIN disc build,
 * bronze metal frame + purple diamond banner.
 */
public final class MainView {

    public static final String VERSION = "3.5.1";

    private final Stage stage;
    private final Preferences prefs = Preferences.userNodeForPackage(MainView.class);

    private Path cdRoot;
    private Path outputRoot;
    private Path cuePath;
    private Path discOutRoot;

    private final Label cdPathLabel = new Label("Not set");
    private final Label outPathLabel = new Label("Not set");
    private final Label cuePathLabel = new Label("No CUE (optional — for BIN build)");
    private final Label discOutLabel = new Label("Not set");
    private final CheckBox chkBuildDisc = new CheckBox("Build new BIN/CUE after randomize");
    private final TextField discSuffixField = new TextField("_RND");
    private final CheckBox chkCleanup = new CheckBox("Delete unpacked folders after randomize");
    private final TextArea logArea = new TextArea();
    private final Label statusLabel = new Label("CD + Out · optional CUE · Randomize builds disc");
    private final ProgressBar progressBar = new ProgressBar(0);

    private final TextField seedField = new TextField();
    private final TextField statMin = rangeField("12");
    private final TextField statMax = rangeField("35");
    private final TextField goldMin = rangeField("50");
    private final TextField goldMax = rangeField("500");
    private final TextField levelMin = rangeField("1");
    private final TextField levelMax = rangeField("5");
    private final TextField skillMin = rangeField("1");
    private final TextField skillMax = rangeField("5");
    private final TextField weaponMin = rangeField("3");
    private final TextField weaponMax = rangeField("25");
    private final TextField acMin = rangeField("0");
    private final TextField acMax = rangeField("80");
    private final TextField hitMin = rangeField("20");
    private final TextField hitMax = rangeField("120");
    private final TextField speedMin = rangeField("5");
    private final TextField speedMax = rangeField("40");

    private final CheckBox chkLoot = new CheckBox("Loot (QUEST$ — no start gear)");
    private final CheckBox chkHeroes = new CheckBox("Hero attributes");
    private final CheckBox chkGear = new CheckBox("Start gear");
    private final CheckBox chkGold = new CheckBox("Start gold");
    private final CheckBox chkSpells = new CheckBox("Start spell books");
    private final CheckBox chkWeapons = new CheckBox("Weapon damage");
    private final CheckBox chkSpellLv = new CheckBox("Spell ranks");
    private final CheckBox chkSkillLv = new CheckBox("Skill ranks");
    private final CheckBox chkPlayerLv = new CheckBox("Start level");
    private final CheckBox chkEnemyLv = new CheckBox("Enemy power");
    private final CheckBox chkEnemies = new CheckBox("Enemy templates");
    private final CheckBox chkShops = new CheckBox("Shop prices");
    private final CheckBox chkMaps = new CheckBox("Map headers");
    private final CheckBox chkDungeons = new CheckBox("Land/dungeon tiles");
    private final CheckBox chkCrossLand = new CheckBox("Cross-land FE56");
    private final CheckBox chkDoors = new CheckBox("Dungeon doors");
    private final CheckBox chkEnemyTypes = new CheckBox("Enemy types (MO_)");
    private final CheckBox chkCombatExtras = new CheckBox("AC / hit / speed");
    private final CheckBox chkFinalDungeon = new CheckBox("Final dungeon");
    private final CheckBox chkPalettes = new CheckBox("Palettes (TIM)");
    private final CheckBox chkPalShuffle = new CheckBox("Palette shuffle mode");
    private final CheckBox chkQuests = new CheckBox("Quest items");
    private final CheckBox chkVideos = new CheckBox("Skip videos");
    private final CheckBox chkMusic = new CheckBox("Music (RAW)");
    private final CheckBox chkVideoShuffle = new CheckBox("Videos (DPS)");
    private final CheckBox chkForce = new CheckBox("Force unpack");

    private final Button btnPresetGeneral = new Button("General");
    private final Button btnPresetAdvanced = new Button("Advanced");
    private final Button btnPresetChaotic = new Button("Chaotic");

    private final List<Button> actionButtons = new ArrayList<>();
    private volatile boolean busy;
    private String activePreset = "General";

    private final LogSink logSink = new LogSink() {
        @Override public void log(String message) {
            Platform.runLater(() -> logArea.appendText(message + "\n"));
        }
        @Override public void status(String message) {
            Platform.runLater(() -> statusLabel.setText(message));
            log(message);
        }
        @Override public void analysis(String text) {
            Platform.runLater(() -> {
                logArea.appendText("\n--- analysis ---\n");
                logArea.appendText(text);
                if (!text.endsWith("\n")) logArea.appendText("\n");
            });
        }
    };

    public MainView(Stage stage) {
        this.stage = stage;
    }

    public Parent build() {
        defaults();
        BorderPane inner = new BorderPane();
        inner.getStyleClass().add("root-pane");
        VBox top = new VBox(buildHeader(), buildPresetBar());
        inner.setTop(top);
        inner.setCenter(buildSidebar());
        VBox bottom = new VBox(buildLog(), buildStatus());
        bottom.getStyleClass().add("bottom-stack");
        inner.setBottom(bottom);

        StackPane frame = new StackPane(inner);
        frame.getStyleClass().add("frame-outer");
        restorePaths();
        applyPresetGeneral();
        logStartup();
        return frame;
    }

    private void defaults() {
        // Quest item loot OFF by default — too easy to softlock
        chkLoot.setSelected(true);
        chkHeroes.setSelected(true);
        chkGear.setSelected(false);
        chkGold.setSelected(false);
        chkSpells.setSelected(true);
        chkWeapons.setSelected(true);
        chkSpellLv.setSelected(true);
        chkSkillLv.setSelected(true);
        chkPlayerLv.setSelected(true);
        chkEnemyLv.setSelected(false);
        chkEnemies.setSelected(false);
        chkShops.setSelected(false);
        chkMaps.setSelected(false);
        chkDungeons.setSelected(false);
        chkCrossLand.setSelected(false);
        chkDoors.setSelected(false);
        chkEnemyTypes.setSelected(true);
        chkCombatExtras.setSelected(true);
        chkFinalDungeon.setSelected(false);
        chkPalettes.setSelected(false);
        chkPalShuffle.setSelected(false);
        chkQuests.setSelected(false);
        chkVideos.setSelected(false);
        chkMusic.setSelected(false);
        chkVideoShuffle.setSelected(false);
        chkBuildDisc.setSelected(true);
        chkCleanup.setSelected(true);
        discSuffixField.setPrefColumnCount(10);
        // Gear XOR loot — quest weapons as starters crash the game
        chkLoot.selectedProperty().addListener((o, was, on) -> {
            if (on) {
                chkGear.setSelected(false);
                chkSpells.setSelected(false);
                statusLabel.setText("Loot on → starting gear disabled");
            }
        });
        chkGear.selectedProperty().addListener((o, was, on) -> {
            if (on && chkLoot.isSelected()) {
                chkLoot.setSelected(false);
                statusLabel.setText("Starting gear on → loot disabled");
            }
        });

        seedField.setText(RandomizerOptions.randomSeedString());
        seedField.setPrefColumnCount(14);
        seedField.setPromptText("seed");
        logArea.setEditable(false);
        logArea.setWrapText(false);
        progressBar.setPrefWidth(160);
        progressBar.setProgress(0);
        cdPathLabel.getStyleClass().add("path-label");
        outPathLabel.getStyleClass().add("path-label");
        cuePathLabel.getStyleClass().add("path-label");

        for (Button b : List.of(btnPresetGeneral, btnPresetAdvanced, btnPresetChaotic)) {
            b.getStyleClass().add("preset");
            actionButtons.add(b);
        }
        btnPresetGeneral.setOnAction(e -> applyPresetGeneral());
        btnPresetAdvanced.setOnAction(e -> applyPresetAdvanced());
        btnPresetChaotic.setOnAction(e -> applyPresetChaotic());
    }

    private HBox buildHeader() {
        Label title = new Label("DARKSTONE RANDOMIZER");
        title.getStyleClass().add("title");
        Label ver = new Label("v" + VERSION + "  ·  PSX  ·  PSM patch  ·  loot + types  ·  UI text protected");
        ver.getStyleClass().add("subtitle");
        VBox text = new VBox(2, title, ver);
        HBox header = new HBox(text);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox buildPresetBar() {
        Label lab = new Label("PRESET");
        lab.getStyleClass().add("section-label");
        HBox bar = new HBox(10, lab, btnPresetGeneral, btnPresetAdvanced, btnPresetChaotic, spacer());
        bar.getStyleClass().add("preset-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void markPreset(String name) {
        activePreset = name;
        btnPresetGeneral.getStyleClass().remove("preset-active");
        btnPresetAdvanced.getStyleClass().remove("preset-active");
        btnPresetChaotic.getStyleClass().remove("preset-active");
        if ("General".equals(name)) btnPresetGeneral.getStyleClass().add("preset-active");
        if ("Advanced".equals(name)) btnPresetAdvanced.getStyleClass().add("preset-active");
        if ("Chaotic".equals(name)) btnPresetChaotic.getStyleClass().add("preset-active");
        statusLabel.setText("Preset: " + name);
        logSink.log("Preset → " + name);
    }

    /** Safe casual run — hero variety, light dungeons, no quest softlocks. */
    private void applyPresetGeneral() {
        setAllOptions(false);
        chkHeroes.setSelected(true);
        chkLoot.setSelected(true);
        chkGear.setSelected(false);
        chkGold.setSelected(false);
        chkSpells.setSelected(false);
        chkWeapons.setSelected(true);
        chkSpellLv.setSelected(true);
        chkSkillLv.setSelected(true);
        chkPlayerLv.setSelected(true);
        chkDungeons.setSelected(false);
        chkDoors.setSelected(false);
        chkEnemyTypes.setSelected(true);
        chkCombatExtras.setSelected(false);
        chkCrossLand.setSelected(false);
        chkFinalDungeon.setSelected(false);
        chkMaps.setSelected(false);
        chkQuests.setSelected(false);
        statMin.setText("12"); statMax.setText("28");
        goldMin.setText("50"); goldMax.setText("300");
        levelMin.setText("1"); levelMax.setText("3");
        skillMin.setText("1"); skillMax.setText("3");
        weaponMin.setText("3"); weaponMax.setText("18");
        markPreset("General");
    }

    /** Deeper rando — enemies, shops, palettes, music; still no quest-item shuffle. */
    private void applyPresetAdvanced() {
        setAllOptions(false);
        chkHeroes.setSelected(true);
        chkGear.setSelected(true);
        chkGold.setSelected(false);
        chkSpells.setSelected(true);
        chkWeapons.setSelected(true);
        chkSpellLv.setSelected(true);
        chkSkillLv.setSelected(true);
        chkPlayerLv.setSelected(true);
        chkEnemyLv.setSelected(true);
        chkEnemies.setSelected(true);
        chkShops.setSelected(false);
        chkDungeons.setSelected(true);
        chkDoors.setSelected(true);
        chkEnemyTypes.setSelected(true);
        chkCombatExtras.setSelected(true);
        chkCrossLand.setSelected(false);
        chkFinalDungeon.setSelected(false);
        chkPalettes.setSelected(true);
        chkMusic.setSelected(true);
        chkLoot.setSelected(false);
        chkQuests.setSelected(false);
        statMin.setText("10"); statMax.setText("35");
        goldMin.setText("20"); goldMax.setText("600");
        levelMin.setText("1"); levelMax.setText("6");
        skillMin.setText("1"); skillMax.setText("6");
        weaponMin.setText("2"); weaponMax.setText("28");
        markPreset("Advanced");
    }

    /** Kitchen sink — cross-pack tiles, video shuffle, optional loot (still no keys). */
    private void applyPresetChaotic() {
        setAllOptions(true);
        chkQuests.setSelected(false);
        chkLoot.setSelected(true);
        chkGear.setSelected(false);
        chkSpells.setSelected(false);
        chkVideos.setSelected(false);
        chkVideoShuffle.setSelected(true);
        chkCrossLand.setSelected(false);
        chkDungeons.setSelected(false);
        chkDoors.setSelected(false);
        chkMaps.setSelected(false);
        chkEnemyTypes.setSelected(true);
        chkCombatExtras.setSelected(false);
        chkFinalDungeon.setSelected(false);
        chkPalShuffle.setSelected(true);
        chkForce.setSelected(false);
        statMin.setText("5"); statMax.setText("40");
        goldMin.setText("0"); goldMax.setText("999");
        levelMin.setText("1"); levelMax.setText("10");
        skillMin.setText("1"); skillMax.setText("10");
        weaponMin.setText("1"); weaponMax.setText("40");
        markPreset("Chaotic");
    }

    private void setAllOptions(boolean on) {
        chkLoot.setSelected(on);
        chkHeroes.setSelected(on);
        chkGear.setSelected(on);
        chkGold.setSelected(false);
        chkSpells.setSelected(on);
        chkWeapons.setSelected(on);
        chkSpellLv.setSelected(on);
        chkSkillLv.setSelected(on);
        chkPlayerLv.setSelected(on);
        chkEnemyLv.setSelected(on);
        chkEnemies.setSelected(on);
        chkShops.setSelected(false);
        chkMaps.setSelected(false);
        chkDungeons.setSelected(false);
        chkCrossLand.setSelected(false);
        chkDoors.setSelected(false);
        chkEnemyTypes.setSelected(on);
        chkCombatExtras.setSelected(on);
        chkFinalDungeon.setSelected(false); // never auto-on even in "all"
        chkPalettes.setSelected(on);
        chkPalShuffle.setSelected(on);
        chkQuests.setSelected(on);
        chkVideos.setSelected(on);
        chkMusic.setSelected(on);
        chkVideoShuffle.setSelected(on);
        chkBuildDisc.setSelected(on);
    }

    private ScrollPane buildSidebar() {
        VBox side = new VBox(12);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(Region.USE_COMPUTED_SIZE);
        side.setMaxWidth(Double.MAX_VALUE);

        side.getChildren().add(card("Disc / folders",
                pathRow("CD", cdPathLabel, this::selectCdFolder),
                pathRow("Out", outPathLabel, this::selectOutput),
                pathRow("CUE", cuePathLabel, this::selectCue),
                pathRow("BIN out", discOutLabel, this::selectDiscOut),
                flow(chkForce, chkBuildDisc, chkCleanup),
                new HBox(8, new Label("Suffix"), discSuffixField)));

        side.getChildren().add(card("Seed",
                new HBox(8, grow(seedField),
                        action("New", () -> seedField.setText(RandomizerOptions.randomSeedString())),
                        action("Copy", this::copySeed)),
                new HBox(8,
                        action("Save seed", this::exportSeed))));

        GridPane ranges = new GridPane();
        ranges.setHgap(6);
        ranges.setVgap(6);
        int r = 0;
        r = rangeRow(ranges, r, "Stats", statMin, statMax);
        r = rangeRow(ranges, r, "Gold", goldMin, goldMax);
        r = rangeRow(ranges, r, "Levels", levelMin, levelMax);
        r = rangeRow(ranges, r, "Skills", skillMin, skillMax);
        r = rangeRow(ranges, r, "Weapon", weaponMin, weaponMax);
        r = rangeRow(ranges, r, "AC", acMin, acMax);
        r = rangeRow(ranges, r, "Hit", hitMin, hitMax);
        r = rangeRow(ranges, r, "Speed", speedMin, speedMax);
        side.getChildren().add(card("Ranges", ranges));

        side.getChildren().add(card("Character / items",
                flow(chkLoot, chkHeroes, chkWeapons, chkSpellLv, chkSkillLv, chkPlayerLv)));

        side.getChildren().add(card("World / lands",
                flow(chkEnemies, chkEnemyTypes, chkEnemyLv, chkPalettes, chkPalShuffle, chkFinalDungeon)));

        side.getChildren().add(card("Audio / video",
                flow(chkMusic, chkVideoShuffle, chkVideos)));

        Button unpack = action("Unpack", () -> runInBackground("Unpacking...", this::unpackAll));
        Button scan = action("Scan", () -> runInBackground("Scanning...", this::scanTables));
        Button run = action("Randomize", () -> runInBackground("Randomizing...", this::runMaster));
        run.getStyleClass().add("master");
        HBox actions = new HBox(8, unpack, scan, run);
        side.getChildren().add(card("Run", actions));

        ScrollPane scroll = new ScrollPane(side);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("sidebar-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private VBox buildLog() {
        Label label = new Label("Log");
        label.getStyleClass().add("section-label");
        logArea.setPrefRowCount(5);
        logArea.setMinHeight(90);
        logArea.setPrefHeight(110);
        logArea.setMaxHeight(140);
        VBox box = new VBox(4, label, logArea);
        box.getStyleClass().add("log-pane");
        box.setMaxHeight(160);
        return box;
    }

    private void copySeed() {
        String seed = seedField.getText() == null ? "" : seedField.getText().trim();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(seed);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        statusLabel.setText("Seed copied: " + seed);
        logSink.log("Seed copied to clipboard: " + seed);
    }

    private void exportSeed() {
        try {
            String seed = seedField.getText() == null ? "" : seedField.getText().trim();
            Path dir = outputRoot != null ? outputRoot : (cdRoot != null ? cdRoot : Path.of("."));
            Path file = dir.resolve("darkstone_seed_" + sanitizeSeed(seed) + ".txt");
            String body = "seed=" + seed + "\n"
                    + "hash=" + RandomizerOptions.seedFromString(seed) + "\n"
                    + "preset=" + activePreset + "\n"
                    + "version=" + VERSION + "\n";
            Files.writeString(file, body);
            statusLabel.setText("Seed saved: " + file.getFileName());
            logSink.log("Seed exported: " + file);
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Could not save seed: " + e.getMessage());
        }
    }


    private void selectCue() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select disc CUE (multi-track OK)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CUE", "*.cue", "*.CUE"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        cuePath = f.toPath();
        cuePathLabel.setText(cuePath.toString());
        prefs.put("cuePath", cuePath.toString());
        Path parent = cuePath.getParent();
        if (parent != null && cdRoot == null) {
            cdRoot = parent;
            cdPathLabel.setText(cdRoot.toString());
            prefs.put("cdRoot", cdRoot.toString());
        }
        logSink.log("CUE: " + cuePath);
        statusLabel.setText("CUE ready for disc build");
    }

    private void selectDiscOut() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Output folder for new BIN/CUE set");
        File initial = prefsPath("discOutRoot");
        if (initial != null) chooser.setInitialDirectory(initial);
        File picked = chooser.showDialog(stage);
        if (picked != null) {
            discOutRoot = picked.toPath();
            discOutLabel.setText(discOutRoot.toString());
            prefs.put("discOutRoot", discOutRoot.toString());
            logSink.log("Disc out: " + discOutRoot);
        }
    }

    private void selectCdFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Extracted Darkstone CD folder");
        File initial = prefsPath("cdRoot");
        if (initial != null) chooser.setInitialDirectory(initial);
        File picked = chooser.showDialog(stage);
        if (picked != null) {
            cdRoot = picked.toPath();
            cdPathLabel.setText(cdRoot.toString());
            prefs.put("cdRoot", cdRoot.toString());
            logSink.log("CD folder: " + cdRoot);
        }
    }

    private void selectOutput() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Unpack output directory");
        File initial = prefsPath("outputRoot");
        if (initial != null) chooser.setInitialDirectory(initial);
        File picked = chooser.showDialog(stage);
        if (picked != null) {
            outputRoot = picked.toPath();
            outPathLabel.setText(outputRoot.toString());
            prefs.put("outputRoot", outputRoot.toString());
            logSink.log("Output: " + outputRoot);
        }
    }

    private void unpackAll() throws Exception {
        Path cd = requireCd();
        Path out = requireOutput();
        Files.createDirectories(out);
        PsmArchive.unpackTree(cd, out, !chkForce.isSelected(), logSink);
    }

    private void scanTables() throws Exception {
        ScanResult result = TableScanner.scan(requireOutput());
        logSink.analysis(result.summary());
        logSink.status(result.oneLine());
    }

    private void runMaster() throws Exception {
        requireOutput();
        RandomizerOptions options = currentOptions();
        engine().runMaster(options);
        if (options.disableVideos) {
            try {
                engine().disableVideos(requireCd());
            } catch (Exception ex) {
                logSink.log("[!] Video disable skipped: " + ex.getMessage());
            }
        }

        // Collect repacked *.PSM next to unpacked folders (clean names, no .repacked)
        Path out = requireOutput();
        java.util.Map<String, byte[]> psmMap = new java.util.LinkedHashMap<>();
        try (var walk = Files.walk(out, 3)) {
            for (Path f : walk.filter(Files::isRegularFile).toList()) {
                String n = f.getFileName().toString().toUpperCase(java.util.Locale.ROOT);
                if (n.endsWith(".PSM") && !n.endsWith(".PSM.BAK")) {
                    psmMap.put(f.getFileName().toString(), Files.readAllBytes(f));
                }
            }
        }
        logSink.log("Repacked PSM ready: " + psmMap.size());

        if (chkBuildDisc.isSelected()) {
            buildDiscImage(psmMap);
        } else {
            logSink.log("Disc build skipped (checkbox off). Copy PSM into your extract or enable Build BIN/CUE.");
        }

        if (chkCleanup.isSelected()) {
            deleteUnpackedFolders(out);
        }

        try {
            String seed = options.seedText == null ? "" : options.seedText.trim();
            Path seedFile = out.resolve("darkstone_seed_" + sanitizeSeed(seed) + ".txt");
            Files.writeString(seedFile, "seed=" + seed + "\n"
                    + "hash=" + RandomizerOptions.seedFromString(seed) + "\n"
                    + "preset=" + activePreset + "\n"
                    + "version=" + VERSION + "\n");
            logSink.log("Seed exported: " + seedFile);
        } catch (Exception e) {
            logSink.log("[!] Seed export skipped: " + e.getMessage());
        }
        logSink.log("Done. Boot the new CUE if disc build was enabled.");
    }

    private void buildDiscImage(java.util.Map<String, byte[]> replacements) throws Exception {
        if (cuePath == null || !Files.isRegularFile(cuePath)) {
            // try auto-find under CD
            if (cdRoot != null && Files.isDirectory(cdRoot)) {
                try (var stream = Files.list(cdRoot)) {
                    cuePath = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".cue"))
                            .findFirst().orElse(null);
                }
            }
        }
        if (cuePath == null || !Files.isRegularFile(cuePath)) {
            logSink.log("[!] No CUE set — cannot build BIN/CUE. Select CUE under Disc / folders.");
            return;
        }
        Path outDir = discOutRoot != null ? discOutRoot : requireOutput().resolve("disc_out");
        Files.createDirectories(outDir);
        String suffix = discSuffixField.getText() == null || discSuffixField.getText().isBlank()
                ? "_RND" : discSuffixField.getText().trim();
        // Prefer seed in suffix when available
        String seed = seedField.getText() == null ? "" : seedField.getText().trim();
        if (!seed.isBlank() && !suffix.contains(sanitizeSeed(seed))) {
            suffix = suffix + "_" + sanitizeSeed(seed);
        }

        CueSheet cue = CueSheet.parse(cuePath);
        Path dataBin = cue.primaryDataBin();
        if (dataBin == null || !Files.isRegularFile(dataBin)) {
            logSink.log("[!] Data BIN not found for CUE.");
            return;
        }
        int sector = cue.sectorSizeForPrimary();

        for (CueSheet.FileEntry f : cue.files) {
            Path src = cue.baseDir.resolve(f.name);
            if (!Files.isRegularFile(src)) {
                logSink.log("[!] Missing track: " + src.getFileName());
                continue;
            }
            String outName = f.name;
            if (outName.toLowerCase().endsWith(".bin")) {
                int dot = outName.lastIndexOf('.');
                outName = outName.substring(0, dot) + suffix + outName.substring(dot);
            }
            Path dst = outDir.resolve(outName);
            logSink.log("Copy track " + src.getFileName() + " -> " + dst.getFileName());
            Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        String dataName = dataBin.getFileName().toString();
        String outDataName = dataName;
        if (outDataName.toLowerCase().endsWith(".bin")) {
            int dot = outDataName.lastIndexOf('.');
            outDataName = outDataName.substring(0, dot) + suffix + outDataName.substring(dot);
        }
        Path outDataBin = outDir.resolve(outDataName);

        Iso9660Patcher iso = new Iso9660Patcher(outDataBin, sector);
        int n = iso.replaceAll(replacements);
        logSink.log("Disc patch: " + n + " file(s) into " + outDataName);

        String cueText = cue.toCueText(suffix);
        String base = cuePath.getFileName().toString();
        int d = base.lastIndexOf('.');
        if (d > 0) base = base.substring(0, d);
        Path outCue = outDir.resolve(base + suffix + ".cue");
        Files.writeString(outCue, cueText);
        logSink.log("Wrote " + outCue);
        statusLabel.setText("Disc ready: " + outCue.getFileName());
    }

    private void deleteUnpackedFolders(Path root) throws Exception {
        int removed = 0;
        try (var walk = Files.walk(root, 6)) {
            java.util.List<Path> dirs = walk.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith("_unpacked"))
                    .sorted((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount()))
                    .toList();
            for (Path dir : dirs) {
                try (var files = Files.walk(dir)) {
                    java.util.List<Path> all = files.sorted((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount())).toList();
                    for (Path f : all) {
                        Files.deleteIfExists(f);
                    }
                }
                removed++;
                logSink.log("Removed " + root.relativize(dir));
            }
        }
        logSink.log("Cleanup: deleted " + removed + " unpacked folder(s).");
    }

    private RandomizerOptions currentOptions() {
        RandomizerOptions o = new RandomizerOptions();
        o.loot = chkLoot.isSelected();
        o.heroes = chkHeroes.isSelected();
        o.startingGear = chkGear.isSelected();
        o.startingGold = false; // no reliable field
        o.startingSpells = chkSpells.isSelected();
        o.weaponStats = chkWeapons.isSelected();
        o.spellLevels = chkSpellLv.isSelected();
        o.skillLevels = chkSkillLv.isSelected();
        o.playerLevels = chkPlayerLv.isSelected();
        o.enemyLevels = chkEnemyLv.isSelected();
        o.enemies = chkEnemies.isSelected();
        o.shops = false; // unsafe until price fields mapped
        // Land/indoor tiles + door prop shuffle disabled until entrance tables are mapped
        o.maps = false;
        o.dungeons = false;
        o.dungeonsCrossLand = false;
        o.dungeonDoors = false;
        o.dungeonsInteriors = false;
        o.dungeonsCrossInterior = false;
        o.dungeonsFinal = chkFinalDungeon.isSelected();
        o.enemyTypes = chkEnemyTypes.isSelected();
        o.combatExtras = false; // was spraying into string tables; re-enable after field map
        o.startingGear = false;
        o.startingSpells = false;
        o.palettes = chkPalettes.isSelected();
        o.paletteShuffle = chkPalShuffle.isSelected();
        o.quests = chkQuests.isSelected();
        o.disableVideos = chkVideos.isSelected();
        o.music = chkMusic.isSelected();
        o.videos = chkVideoShuffle.isSelected();
        o.cdRoot = cdRoot;
        o.copyToCd = false;
        o.seedText = seedField.getText();
        o.statMin = parse(statMin, 12);
        o.statMax = parse(statMax, 35);
        o.goldMin = parse(goldMin, 50);
        o.goldMax = parse(goldMax, 500);
        o.levelMin = parse(levelMin, 1);
        o.levelMax = parse(levelMax, 5);
        o.skillMin = parse(skillMin, 1);
        o.skillMax = parse(skillMax, 5);
        o.weaponMin = parse(weaponMin, 3);
        o.weaponMax = parse(weaponMax, 25);
        o.acMin = parse(acMin, 0);
        o.acMax = parse(acMax, 80);
        o.hitMin = parse(hitMin, 20);
        o.hitMax = parse(hitMax, 120);
        o.speedMin = parse(speedMin, 5);
        o.speedMax = parse(speedMax, 40);
        return o;
    }

    private static int parse(TextField field, int fallback) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private RandomizerEngine engine() throws Exception {
        return new RandomizerEngine(requireOutput(), logSink);
    }

    private Path requireCd() {
        if (cdRoot == null) throw new IllegalStateException("Select the CD folder (or Import CUE) first.");
        return cdRoot;
    }

    private Path requireOutput() {
        if (outputRoot == null) throw new IllegalStateException("Select an output directory first.");
        return outputRoot;
    }

    private void runInBackground(String status, Worker worker) {
        if (busy) {
            alert(Alert.AlertType.INFORMATION, "Wait for the current task to finish.");
            return;
        }
        busy = true;
        setActionsDisabled(true);
        progressBar.setProgress(-1);
        statusLabel.setText(status);
        Thread t = new Thread(() -> {
            try {
                worker.run();
                Platform.runLater(() -> statusLabel.setText("Done."));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error");
                    logSink.log("[!] " + e.getMessage());
                    alert(Alert.AlertType.ERROR, e.getMessage());
                });
            } finally {
                Platform.runLater(() -> {
                    busy = false;
                    setActionsDisabled(false);
                    progressBar.setProgress(0);
                });
            }
        }, "darkstone-worker");
        t.setDaemon(true);
        t.start();
    }

    private void setActionsDisabled(boolean disabled) {
        for (Button b : actionButtons) b.setDisable(disabled);
    }

    private void restorePaths() {
        String cd = prefs.get("cdRoot", "");
        if (!cd.isBlank() && Files.isDirectory(Path.of(cd))) {
            cdRoot = Path.of(cd);
            cdPathLabel.setText(cd);
        }
        String out = prefs.get("outputRoot", "");
        if (!out.isBlank() && Files.isDirectory(Path.of(out))) {
            outputRoot = Path.of(out);
            outPathLabel.setText(out);
        }
        String cue = prefs.get("cuePath", "");
        if (!cue.isBlank() && Files.isRegularFile(Path.of(cue))) {
            cuePath = Path.of(cue);
            cuePathLabel.setText(cuePath.getFileName().toString());
        }
        String dout = prefs.get("discOutRoot", "");
        if (!dout.isBlank() && Files.isDirectory(Path.of(dout))) {
            discOutRoot = Path.of(dout);
            discOutLabel.setText(dout);
        }
    }

    private File prefsPath(String key) {
        String value = prefs.get(key, "");
        if (value.isBlank()) return null;
        Path p = Path.of(value);
        if (Files.isDirectory(p)) return p.toFile();
        if (Files.isRegularFile(p)) {
            Path parent = p.getParent();
            return parent != null && Files.isDirectory(parent) ? parent.toFile() : null;
        }
        Path parent = p.getParent();
        return parent != null && Files.isDirectory(parent) ? parent.toFile() : null;
    }

    private void logStartup() {
        logSink.log("Darkstone PSX Randomizer " + VERSION);
        logSink.log("Flow: Unpack → Randomize (repack + optional BIN/CUE build + cleanup).");
        logSink.log("Loot (QUEST$ — no start gear) is OFF by default to avoid softlocks.");
        logSink.log("Presets: General (safe) · Advanced · Chaotic.");
    }

    private void alert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message == null ? "" : message, ButtonType.OK);
        alert.initOwner(stage);
        alert.showAndWait();
    }


    private HBox buildStatus() {
        HBox box = new HBox(10, statusLabel, progressBar);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        progressBar.setPrefWidth(160);
        progressBar.setMaxWidth(200);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.getStyleClass().add("status-bar");
        return box;
    }

    private static TextField rangeField(String value) {
        TextField f = new TextField(value);
        f.setPrefColumnCount(4);
        f.getStyleClass().add("range-field");
        return f;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static HBox pathRow(String caption, Label pathLabel, Runnable onBrowse) {
        Label cap = new Label(caption);
        cap.setPrefWidth(56);
        Button browse = new Button("…");
        browse.setOnAction(e -> onBrowse.run());
        HBox.setHgrow(pathLabel, Priority.ALWAYS);
        pathLabel.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(8, cap, pathLabel, browse);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private static FlowPane flow(javafx.scene.Node... nodes) {
        FlowPane fp = new FlowPane(10, 8);
        fp.getChildren().addAll(nodes);
        return fp;
    }

    private static javafx.scene.Node grow(TextField field) {
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private Button action(String text, Runnable r) {
        Button b = new Button(text);
        b.setOnAction(e -> r.run());
        actionButtons.add(b);
        return b;
    }

    private static VBox card(String title, javafx.scene.Node... body) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        VBox box = new VBox(8);
        box.getStyleClass().add("card");
        box.getChildren().add(t);
        box.getChildren().addAll(body);
        return box;
    }

    private static int rangeRow(GridPane grid, int row, String label, TextField min, TextField max) {
        grid.add(new Label(label), 0, row);
        grid.add(min, 1, row);
        grid.add(new Label("–"), 2, row);
        grid.add(max, 3, row);
        return row + 1;
    }

    private static String sanitizeSeed(String seed) {
        if (seed == null || seed.isBlank()) return "noseed";
        String s = seed.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        if (s.length() > 40) s = s.substring(0, 40);
        return s;
    }

    @FunctionalInterface
    private interface Worker {
        void run() throws Exception;
    }
}
