package com.serifsystemworks.darkstone.ui;

import com.serifsystemworks.darkstone.engine.CdInstaller;
import com.serifsystemworks.darkstone.engine.LogSink;
import com.serifsystemworks.darkstone.engine.PsmArchive;
import com.serifsystemworks.darkstone.engine.RandomizerEngine;
import com.serifsystemworks.darkstone.engine.RandomizerOptions;
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
 * SOTN-style Darkstone randomizer shell: presets, CUE import/export with seed,
 * bronze metal frame + purple diamond banner.
 */
public final class MainView {

    public static final String VERSION = "3.1.0";

    private final Stage stage;
    private final Preferences prefs = Preferences.userNodeForPackage(MainView.class);

    private Path cdRoot;
    private Path outputRoot;
    private Path cuePath;

    private final Label cdPathLabel = new Label("Not set");
    private final Label outPathLabel = new Label("Not set");
    private final Label cuePathLabel = new Label("No CUE loaded");
    private final TextArea logArea = new TextArea();
    private final Label statusLabel = new Label("Load CUE or CD folder · pick preset · Randomize");
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

    private final CheckBox chkLoot = new CheckBox("Loot (QUEST$ items)");
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
    private final CheckBox chkInteriors = new CheckBox("Dungeon interiors");
    private final CheckBox chkCrossInterior = new CheckBox("Cross-interior (tier)");
    private final CheckBox chkFinalDungeon = new CheckBox("Final dungeon");
    private final CheckBox chkPalettes = new CheckBox("Palettes (TIM)");
    private final CheckBox chkPalShuffle = new CheckBox("Palette shuffle mode");
    private final CheckBox chkQuests = new CheckBox("Quest items");
    private final CheckBox chkVideos = new CheckBox("Skip videos");
    private final CheckBox chkMusic = new CheckBox("Music (RAW)");
    private final CheckBox chkVideoShuffle = new CheckBox("Videos (DPS)");
    private final CheckBox chkCopy = new CheckBox("Install to CD");
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
        chkLoot.setSelected(false);
        chkHeroes.setSelected(true);
        chkGear.setSelected(true);
        chkGold.setSelected(true);
        chkSpells.setSelected(true);
        chkWeapons.setSelected(true);
        chkSpellLv.setSelected(true);
        chkSkillLv.setSelected(true);
        chkPlayerLv.setSelected(true);
        chkEnemyLv.setSelected(false);
        chkEnemies.setSelected(false);
        chkShops.setSelected(false);
        chkMaps.setSelected(false);
        chkDungeons.setSelected(true);
        chkCrossLand.setSelected(false);
        chkInteriors.setSelected(true);
        chkCrossInterior.setSelected(false);
        chkFinalDungeon.setSelected(false);
        chkPalettes.setSelected(false);
        chkPalShuffle.setSelected(false);
        chkQuests.setSelected(false);
        chkVideos.setSelected(false);
        chkMusic.setSelected(false);
        chkVideoShuffle.setSelected(false);
        chkCopy.setSelected(true);
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
        Label ver = new Label("v" + VERSION + "  ·  PSX  ·  CUE in / seed out  ·  CDImg rebuild");
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
        chkGear.setSelected(true);
        chkGold.setSelected(true);
        chkSpells.setSelected(true);
        chkWeapons.setSelected(true);
        chkSpellLv.setSelected(true);
        chkSkillLv.setSelected(true);
        chkPlayerLv.setSelected(true);
        chkDungeons.setSelected(true);
        chkInteriors.setSelected(true);
        chkCrossLand.setSelected(false);
        chkCrossInterior.setSelected(false);
        chkFinalDungeon.setSelected(false);
        chkCopy.setSelected(true);
        chkLoot.setSelected(false);
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
        chkGold.setSelected(true);
        chkSpells.setSelected(true);
        chkWeapons.setSelected(true);
        chkSpellLv.setSelected(true);
        chkSkillLv.setSelected(true);
        chkPlayerLv.setSelected(true);
        chkEnemyLv.setSelected(true);
        chkEnemies.setSelected(true);
        chkShops.setSelected(true);
        chkDungeons.setSelected(true);
        chkInteriors.setSelected(true);
        chkCrossLand.setSelected(false);
        chkCrossInterior.setSelected(false);
        chkFinalDungeon.setSelected(false);
        chkPalettes.setSelected(true);
        chkMusic.setSelected(true);
        chkCopy.setSelected(true);
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
        chkQuests.setSelected(false); // still protect quest-script option
        chkLoot.setSelected(false);   // QUEST$ item names still off (softlock risk)
        chkVideos.setSelected(false); // shuffle instead of skip
        chkVideoShuffle.setSelected(true);
        chkCrossLand.setSelected(true);
        chkInteriors.setSelected(true);
        chkCrossInterior.setSelected(true);
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
        chkGold.setSelected(on);
        chkSpells.setSelected(on);
        chkWeapons.setSelected(on);
        chkSpellLv.setSelected(on);
        chkSkillLv.setSelected(on);
        chkPlayerLv.setSelected(on);
        chkEnemyLv.setSelected(on);
        chkEnemies.setSelected(on);
        chkShops.setSelected(on);
        chkMaps.setSelected(on);
        chkDungeons.setSelected(on);
        chkCrossLand.setSelected(on);
        chkInteriors.setSelected(on);
        chkCrossInterior.setSelected(on);
        chkFinalDungeon.setSelected(false); // never auto-on even in "all"
        chkPalettes.setSelected(on);
        chkPalShuffle.setSelected(on);
        chkQuests.setSelected(on);
        chkVideos.setSelected(on);
        chkMusic.setSelected(on);
        chkVideoShuffle.setSelected(on);
        chkCopy.setSelected(on);
    }

    private ScrollPane buildSidebar() {
        VBox side = new VBox(12);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(Region.USE_COMPUTED_SIZE);
        side.setMaxWidth(Double.MAX_VALUE);

        side.getChildren().add(card("Disc / folders",
                pathRow("CUE", cuePathLabel, this::importCue),
                pathRow("CD", cdPathLabel, this::selectCdFolder),
                pathRow("Out", outPathLabel, this::selectOutput),
                flow(chkForce, chkCopy)));

        side.getChildren().add(card("Seed",
                new HBox(8, grow(seedField),
                        action("New", () -> seedField.setText(RandomizerOptions.randomSeedString())),
                        action("Copy", this::copySeed)),
                new HBox(8,
                        action("Import CUE", this::importCue),
                        action("Export CUE", this::exportCueWithSeed),
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
        side.getChildren().add(card("Ranges", ranges));

        side.getChildren().add(card("Character / items",
                flow(chkLoot, chkHeroes, chkGear, chkGold, chkSpells,
                        chkWeapons, chkSpellLv, chkSkillLv, chkPlayerLv)));

        side.getChildren().add(card("World / lands",
                flow(chkEnemies, chkEnemyLv, chkShops, chkMaps, chkDungeons, chkCrossLand, chkInteriors, chkCrossInterior, chkFinalDungeon,
                        chkPalettes, chkPalShuffle, chkQuests)));

        side.getChildren().add(card("Audio / video",
                flow(chkMusic, chkVideoShuffle, chkVideos)));

        Button unpack = action("Unpack", () -> runInBackground("Unpacking...", this::unpackAll));
        Button scan = action("Scan", () -> runInBackground("Scanning...", this::scanTables));
        Button run = action("Randomize", () -> runInBackground("Randomizing...", this::runMaster));
        run.getStyleClass().add("master");
        Button install = action("Install only", this::confirmAndInstallToCd);
        install.getStyleClass().add("danger");
        HBox actions = new HBox(8, unpack, scan);
        HBox actions2 = new HBox(8, run, install);
        side.getChildren().add(card("Run", actions, actions2));

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

    /** Open a .cue; set CD root to its folder; pull seed from filename if present. */
    private void importCue() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Darkstone CUE sheet");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CUE sheets", "*.cue", "*.CUE"));
        File initial = prefsPath("cuePath");
        if (initial == null) initial = prefsPath("cdRoot");
        if (initial != null) chooser.setInitialDirectory(initial.isDirectory() ? initial : initial.getParentFile());
        File picked = chooser.showOpenDialog(stage);
        if (picked == null) return;
        try {
            cuePath = picked.toPath();
            cuePathLabel.setText(cuePath.getFileName().toString());
            prefs.put("cuePath", cuePath.toString());

            Path parent = cuePath.getParent();
            if (parent != null) {
                cdRoot = parent;
                cdPathLabel.setText(cdRoot.toString());
                prefs.put("cdRoot", cdRoot.toString());
            }

            String name = cuePath.getFileName().toString();
            Matcher m = Pattern.compile("(?i)(?:seed[_-]?)([A-Za-z0-9_-]{3,})").matcher(name);
            if (m.find()) {
                seedField.setText(m.group(1));
                logSink.log("Seed taken from CUE name: " + m.group(1));
            }
            // Also parse FILE line for sanity
            String text = Files.readString(cuePath, StandardCharsets.ISO_8859_1);
            Matcher fm = Pattern.compile("(?i)FILE\\s+\"([^\"]+)\"").matcher(text);
            if (fm.find()) {
                logSink.log("CUE image: " + fm.group(1));
            }
            logSink.log("Imported CUE: " + cuePath);
            statusLabel.setText("CUE loaded · CD = " + (parent != null ? parent.getFileName() : "?"));
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "CUE import failed: " + e.getMessage());
        }
    }

    /**
     * Write a new .cue next to the original (or in Out) named with the seed,
     * e.g. Darkstone_seedABC123.cue — same FILE target as the source sheet.
     */
    private void exportCueWithSeed() {
        try {
            String seed = seedField.getText() == null ? "" : seedField.getText().trim();
            if (seed.isBlank()) {
                alert(Alert.AlertType.WARNING, "Enter a seed first.");
                return;
            }
            Path sourceCue = cuePath;
            if (sourceCue == null || !Files.isRegularFile(sourceCue)) {
                // try find any .cue under cdRoot
                if (cdRoot != null && Files.isDirectory(cdRoot)) {
                    try (var stream = Files.list(cdRoot)) {
                        sourceCue = stream
                                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".cue"))
                                .findFirst()
                                .orElse(null);
                    }
                }
            }
            if (sourceCue == null || !Files.isRegularFile(sourceCue)) {
                alert(Alert.AlertType.WARNING, "Import a CUE first (or place one in the CD folder).");
                return;
            }

            String body = Files.readString(sourceCue, StandardCharsets.ISO_8859_1);
            String safe = sanitizeSeed(seed);
            String outName = "Darkstone_seed" + safe + ".cue";
            Path destDir = outputRoot != null ? outputRoot
                    : (sourceCue.getParent() != null ? sourceCue.getParent() : Path.of("."));
            Path dest = destDir.resolve(outName);

            // Annotate with seed comment at top (harmless for most tools)
            String annotated = "; Darkstone Randomizer " + VERSION + "\r\n"
                    + "; seed=" + seed + "\r\n"
                    + "; hash=" + RandomizerOptions.seedFromString(seed) + "\r\n"
                    + "; preset=" + activePreset + "\r\n"
                    + body;
            Files.writeString(dest, annotated, StandardCharsets.ISO_8859_1);

            // Also drop seed txt beside it
            Path seedTxt = destDir.resolve("darkstone_seed_" + safe + ".txt");
            Files.writeString(seedTxt, "seed=" + seed + "\n"
                    + "hash=" + RandomizerOptions.seedFromString(seed) + "\n"
                    + "preset=" + activePreset + "\n"
                    + "cue=" + dest.getFileName() + "\n"
                    + "version=" + VERSION + "\n");

            cuePath = dest;
            cuePathLabel.setText(dest.getFileName().toString());
            logSink.log("Exported CUE: " + dest);
            logSink.log("Exported seed: " + seedTxt);
            statusLabel.setText("Exported " + outName);
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "CUE export failed: " + e.getMessage());
        }
    }

    private static String sanitizeSeed(String seed) {
        String s = seed.replaceAll("[^A-Za-z0-9_-]", "");
        if (s.length() > 32) s = s.substring(0, 32);
        return s.isEmpty() ? "seed" : s;
    }

    private HBox buildStatus() {
        HBox bar = new HBox(12, statusLabel, spacer(), progressBar);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        return bar;
    }

    private static VBox card(String title, javafx.scene.Node... body) {
        Label t = new Label(title.toUpperCase());
        t.getStyleClass().add("section-label");
        VBox box = new VBox(8);
        box.getStyleClass().add("card");
        box.getChildren().add(t);
        box.getChildren().addAll(body);
        return box;
    }

    private HBox pathRow(String caption, Label pathLabel, Runnable pick) {
        Label c = new Label(caption);
        c.getStyleClass().add("muted");
        c.setPrefWidth(36);
        Button b = action("…", pick);
        b.getStyleClass().add("icon-btn");
        HBox row = new HBox(8, c, grow(pathLabel), b);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static int rangeRow(GridPane g, int row, String name, TextField min, TextField max) {
        Label l = new Label(name);
        l.getStyleClass().add("muted");
        g.add(l, 0, row);
        g.add(min, 1, row);
        g.add(new Label("–"), 2, row);
        g.add(max, 3, row);
        return row + 1;
    }

    private static HBox flow(javafx.scene.Node... nodes) {
        VBox col = new VBox(6);
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        int i = 0;
        for (javafx.scene.Node n : nodes) {
            row.getChildren().add(n);
            i++;
            if (i % 2 == 0) {
                col.getChildren().add(row);
                row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
            }
        }
        if (!row.getChildren().isEmpty()) {
            col.getChildren().add(row);
        }
        return new HBox(col);
    }

    private static TextField rangeField(String v) {
        TextField f = new TextField(v);
        f.setPrefColumnCount(3);
        f.getStyleClass().add("range-field");
        return f;
    }

    private static Label grow(Label label) {
        HBox.setHgrow(label, Priority.ALWAYS);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static TextField grow(TextField field) {
        HBox.setHgrow(field, Priority.ALWAYS);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private Button action(String text, Runnable handler) {
        Button b = new Button(text);
        b.setOnAction(e -> handler.run());
        actionButtons.add(b);
        return b;
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
            engine().disableVideos(requireCd());
        }
        if (options.copyToCd) {
            CdInstaller.install(outputRoot, requireCd(), logSink);
        }
        try {
            String seed = options.seedText == null ? "" : options.seedText.trim();
            Path seedFile = requireOutput().resolve("darkstone_seed_" + sanitizeSeed(seed) + ".txt");
            Files.writeString(seedFile, "seed=" + seed + "\n"
                    + "hash=" + RandomizerOptions.seedFromString(seed) + "\n"
                    + "preset=" + activePreset + "\n"
                    + "version=" + VERSION + "\n");
            logSink.log("Seed exported: " + seedFile);
            // Auto-export CUE with seed when one is known
            if (cuePath != null && Files.isRegularFile(cuePath)) {
                Platform.runLater(this::exportCueWithSeed);
            }
        } catch (Exception e) {
            logSink.log("[!] Seed export skipped: " + e.getMessage());
        }
        logSink.log("Next: rebuild ISO with CDImg (or burn the seeded CUE), then boot.");
    }

    private void confirmAndInstallToCd() {
        try {
            requireCd();
            requireOutput();
        } catch (Exception e) {
            alert(Alert.AlertType.WARNING, e.getMessage());
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Install to CD");
        confirm.setHeaderText("Overwrite PSM files on the CD extract?");
        confirm.setContentText("Backups are written as *.PSM.bak on first install.\nRebuild ISO with CDImg afterward.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            runInBackground("Installing to CD...", () -> CdInstaller.install(outputRoot, cdRoot, logSink));
        }
    }

    private RandomizerOptions currentOptions() {
        RandomizerOptions o = new RandomizerOptions();
        o.loot = chkLoot.isSelected();
        o.heroes = chkHeroes.isSelected();
        o.startingGear = chkGear.isSelected();
        o.startingGold = chkGold.isSelected();
        o.startingSpells = chkSpells.isSelected();
        o.weaponStats = chkWeapons.isSelected();
        o.spellLevels = chkSpellLv.isSelected();
        o.skillLevels = chkSkillLv.isSelected();
        o.playerLevels = chkPlayerLv.isSelected();
        o.enemyLevels = chkEnemyLv.isSelected();
        o.enemies = chkEnemies.isSelected();
        o.shops = chkShops.isSelected();
        o.maps = chkMaps.isSelected();
        o.dungeons = chkDungeons.isSelected();
        o.dungeonsCrossLand = chkCrossLand.isSelected();
        o.dungeonsInteriors = chkInteriors.isSelected();
        o.dungeonsCrossInterior = chkCrossInterior.isSelected();
        o.dungeonsFinal = chkFinalDungeon.isSelected();
        o.palettes = chkPalettes.isSelected();
        o.paletteShuffle = chkPalShuffle.isSelected();
        o.quests = chkQuests.isSelected();
        o.disableVideos = chkVideos.isSelected();
        o.music = chkMusic.isSelected();
        o.videos = chkVideoShuffle.isSelected();
        o.cdRoot = cdRoot;
        o.copyToCd = chkCopy.isSelected();
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
        logSink.log("SOTN-style flow: Import CUE → Preset → Randomize → Export CUE (seed in name).");
        logSink.log("Loot (QUEST$ items) is OFF by default to avoid softlocks.");
        logSink.log("Presets: General (safe) · Advanced · Chaotic.");
    }

    private void alert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message == null ? "" : message, ButtonType.OK);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    @FunctionalInterface
    private interface Worker {
        void run() throws Exception;
    }
}
