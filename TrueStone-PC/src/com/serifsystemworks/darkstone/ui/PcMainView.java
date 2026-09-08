package com.serifsystemworks.darkstone.ui;

import com.serifsystemworks.darkstone.engine.LogSink;
import com.serifsystemworks.darkstone.pc.PcCampaignPipeline;
import com.serifsystemworks.darkstone.pc.PcOptions;
import com.serifsystemworks.darkstone.pc.PcRandomizerEngine;
import com.serifsystemworks.darkstone.pc.PcSeedLauncher;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * PC Darkstone randomizer UI — game folder + PCLASS TXT modules, no CUE/ISO flow.
 */
public final class PcMainView {

    public static final String VERSION = "1.9.5-pc";
    public static final String APP_NAME = "TrueStone";
    public static final String APP_TAGLINE = "TrueRandomizer for Darkstone";

    private final Stage stage;
    private final Preferences prefs = Preferences.userNodeForPackage(PcMainView.class);

    private Path gameRoot;
    private Path outputRoot;

    private final Label gamePathLabel = new Label("Not set");
    private final Label outPathLabel = new Label("In-place (PCLASS)");
    private final TextArea logArea = new TextArea();
    private final Label statusLabel = new Label("Point at Darkstone PC folder - pick preset - Randomize");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final TextField seedField = new TextField();

    private final TextField dmgMin = rangeField("1");
    private final TextField dmgMax = rangeField("80");
    private final TextField acMin = rangeField("0");
    private final TextField acMax = rangeField("150");
    private final TextField levelMin = rangeField("1");
    private final TextField levelMax = rangeField("200");

    private final CheckBox chkMonsters = new CheckBox("Monster stats");
    private final CheckBox chkItems = new CheckBox("Item / weapon stats");
    private final CheckBox chkClasses = new CheckBox("Player class bases");
    private final CheckBox chkShuffleMon = new CheckBox("Shuffle monster packs");
    private final CheckBox chkShuffleItem = new CheckBox("Shuffle item packs");
    private final CheckBox chkRollMon = new CheckBox("Range-roll monsters");
    private final CheckBox chkRollItem = new CheckBox("Range-roll items");
    private final CheckBox chkDat = new CheckBox("Patch DAT (runtime)");
    private final CheckBox chkLand = new CheckBox("LAND props (O3D)");
    private final CheckBox chkQuest = new CheckBox("Quest LAND ids");
    private final CheckBox chkQuestRew = new CheckBox("Quest rewards");
    private final CheckBox chkInjectMtf = new CheckBox("Inject into DATA.MTF");
    private final CheckBox chkEnemyTypes = new CheckBox("Enemy type shuffle");
    private final CheckBox chkLandEnemies = new CheckBox("Types per land/dungeon tier");
    private final CheckBox chkSpeeds = new CheckBox("Randomize enemy speeds");
    private final CheckBox chkSpawn = new CheckBox("Randomize spawn counts");
    private final CheckBox chkQuestDensity = new CheckBox("Quest spawn density");
    private final CheckBox chkShops = new CheckBox("Shop / loot PARENT");
    private final CheckBox chkLootTiers = new CheckBox("Loot tier bands");
    private final CheckBox chkStartKits = new CheckBox("Start kits");
    private final CheckBox chkLandLogic = new CheckBox("Quest LAND logic");
    private final CheckBox chkPipeline = new CheckBox("Campaign pipeline (seeded MTF)");
    private final CheckBox chkQuestCopy = new CheckBox("Copy MTF to quest/");
    private final CheckBox chkReplaceMtf = new CheckBox("Install replaces DATA.MTF");
    private final CheckBox chkEarlyCaps = new CheckBox("Early-game safety caps");
    private final CheckBox chkUncapStats = new CheckBox("Uncap max stats (999)");
    private final CheckBox chkImproveClasses = new CheckBox("Improve classes (no weapon lock)");
    private final TextField multiSeedField = rangeField("1");
    private final Label mtfPathLabel = new Label("DATA.MTF not set");
    private Path dataMtfPath;

    private final Button btnNovice = new Button("Novice");
    private final Button btnExpert = new Button("Expert");
    private final Button btnMaster = new Button("Master");
    private final Button btnHero = new Button("Hero");
    private final Button btnLegend = new Button("Legend");

    private final List<Button> actionButtons = new ArrayList<>();
    private volatile boolean busy;
    private String activePreset = "Novice";

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

    public PcMainView(Stage stage) {
        this.stage = stage;
    }

    public Parent build() {
        defaults();
        BorderPane inner = new BorderPane();
        inner.getStyleClass().add("root-pane");
        inner.setTop(new VBox(buildHeader(), buildPresetBar()));
        inner.setCenter(buildSidebar());
        VBox bottom = new VBox(buildLog(), buildStatus());
        bottom.getStyleClass().add("bottom-stack");
        inner.setBottom(bottom);
        StackPane randoFrame = new StackPane(inner);
        randoFrame.getStyleClass().add("frame-outer");

        MtfExplorerView mtfView = new MtfExplorerView(stage);
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("pc-tabs");
        Tab tPlay = new Tab("TrueStone", buildBloodstainedMenu());
        tPlay.setClosable(false);
        Tab tRando = new Tab("Advanced", randoFrame);
        tRando.setClosable(false);
        Tab tMtf = new Tab("MTF Explorer", mtfView.getRoot());
        tMtf.setClosable(false);
        tabs.getTabs().addAll(tPlay, tRando, tMtf);
        tabs.getSelectionModel().select(tPlay);

        restorePaths();
        applyNovice();
        logStartup();
        return tabs;
    }

    private void defaults() {
        seedField.setText(PcOptions.randomSeedString());
        seedField.setPrefColumnCount(14);
        seedField.setPromptText("seed");
        logArea.setEditable(false);
        logArea.setWrapText(false);
        progressBar.setPrefWidth(160);
        gamePathLabel.getStyleClass().add("path-label");
        outPathLabel.getStyleClass().add("path-label");
        mtfPathLabel.getStyleClass().add("path-label");
        for (Button b : List.of(btnNovice, btnExpert, btnMaster, btnHero, btnLegend)) {
            b.getStyleClass().add("preset");
            actionButtons.add(b);
        }
        btnNovice.setOnAction(e -> applyNovice());
        btnExpert.setOnAction(e -> applyExpert());
        btnMaster.setOnAction(e -> applyMaster());
        btnHero.setOnAction(e -> applyHero());
        btnLegend.setOnAction(e -> applyLegend());
    }

    private HBox buildHeader() {
        Label title = new Label("TrueStone");
        title.getStyleClass().add("title");
        Label ver = new Label("v" + VERSION + "  —  " + APP_TAGLINE);
        ver.getStyleClass().add("subtitle");
        HBox header = new HBox(new VBox(2, title, ver));
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox buildPresetBar() {
        Label lab = new Label("PRESET");
        lab.getStyleClass().add("section-label");
        HBox bar = new HBox(8, lab, btnNovice, btnExpert, btnMaster, btnHero, btnLegend, spacer());
        bar.getStyleClass().add("preset-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    
    /**
     * Maps TrueStone presets to Darkstone 1.05b difficulty identity:
     * Novice, Expert, Master, Hero, Legend.
     */
    private void applyBalanceProfile(PcOptions o) {
        o.preset = activePreset;
        switch (activePreset) {
            case "Expert" -> {
                o.randomizeMonsterPower = true;
                o.startingGoldAbsoluteMin = -1;
                o.startingGoldAbsoluteMax = -1;
                o.startingGoldCap = 10000;
                o.itemPowerLag = 0.95;
                o.earlyMonsterScale = 0.80;
                o.earlyDmgCap = 22;
                o.earlyAcCap = 38;
                o.earlyLevelThreshold = 12;
                o.itemLevelUnlock = true;
                o.itemLevelReduction = 12;
            }
            case "Master" -> {
                o.randomizeMonsterPower = true;
                o.startingGoldAbsoluteMin = -1;
                o.startingGoldAbsoluteMax = -1;
                o.startingGoldCap = 10000;
                o.itemPowerLag = 0.85;
                o.earlyMonsterScale = 0.95;
                o.earlyDmgCap = 28;
                o.earlyAcCap = 45;
                o.earlyLevelThreshold = 10;
                o.itemLevelUnlock = true;
                o.itemLevelReduction = 8;
            }
            case "Hero" -> {
                o.randomizeMonsterPower = true;
                o.startingGoldAbsoluteMin = -1;
                o.startingGoldAbsoluteMax = -1;
                o.startingGoldCap = 10000;
                o.itemPowerLag = 0.78;
                o.earlyMonsterScale = 1.05;
                o.earlyDmgCap = 36;
                o.earlyAcCap = 55;
                o.earlyLevelThreshold = 8;
                o.itemLevelUnlock = true;
                o.itemLevelReduction = 4;
            }
            case "Legend" -> {
                o.randomizeMonsterPower = true;
                o.startingGoldAbsoluteMin = -1;
                o.startingGoldAbsoluteMax = -1;
                o.startingGoldCap = 10000;
                o.itemPowerLag = 0.70;
                o.earlyMonsterScale = 1.20;
                o.earlyDmgCap = 50;
                o.earlyAcCap = 70;
                o.earlyLevelThreshold = 6;
                o.itemLevelUnlock = false;
                o.itemLevelReduction = 0;
            }
            default -> { // Novice
                o.itemPowerLag = 1.12;
                o.earlyMonsterScale = 0.62;
                o.earlyDmgCap = 16;
                o.earlyAcCap = 28;
                o.earlyLevelThreshold = 16;
                o.itemLevelUnlock = true;
                o.itemLevelReduction = 22;
                // Vanilla enemy combat; only speeds (and optional spawns) change
                o.randomizeMonsterPower = false;
                o.shuffleMonsterStats = false;
                o.rangeRollMonsters = false;
                o.randomizeSpeeds = true;
                // Generous starting purse
                o.randomizeStartingGold = true;
                o.startingGoldAbsoluteMin = 2500;
                o.startingGoldAbsoluteMax = 10000;
                o.startingGoldCap = 10000;
            }
        }
    }

    private void markPreset(String name) {
        activePreset = name;
        for (Button b : List.of(btnNovice, btnExpert, btnMaster, btnHero, btnLegend)) {
            b.getStyleClass().remove("preset-active");
        }
        Button active = switch (name) {
            case "Expert" -> btnExpert;
            case "Master" -> btnMaster;
            case "Hero" -> btnHero;
            case "Legend" -> btnLegend;
            default -> btnNovice;
        };
        active.getStyleClass().add("preset-active");
    }

    
    /** Shared module toggles; balance knobs differ per 1.05b difficulty. */
    private void applyDifficultyBase() {
        chkMonsters.setSelected(true);
        chkItems.setSelected(true);
        chkClasses.setSelected(true);
        chkShuffleMon.setSelected(true);
        chkShuffleItem.setSelected(true);
        chkDat.setSelected(true);
        chkInjectMtf.setSelected(true);
        chkSpeeds.setSelected(true);
        chkSpawn.setSelected(true);
        chkUncapStats.setSelected(true);
        chkImproveClasses.setSelected(true);
        chkLootTiers.setSelected(true);
        chkStartKits.setSelected(true);
        chkLandLogic.setSelected(true);
        chkEarlyCaps.setSelected(true);
        multiSeedField.setText("1");
    }

    /** Novice — opening lands must be survivable; strong gear early. */
    private void applyNovice() {
        applyDifficultyBase();
        chkShuffleMon.setSelected(false); // vanilla enemy combat stats
        chkRollMon.setSelected(false);
        chkRollItem.setSelected(false);
        chkLand.setSelected(false);
        chkQuest.setSelected(false);
        chkQuestRew.setSelected(false);
        chkQuestDensity.setSelected(false);
        chkEnemyTypes.setSelected(false);
        chkLandEnemies.setSelected(false);
        chkShops.setSelected(false);
        chkPipeline.setSelected(false);
        chkQuestCopy.setSelected(false);
        chkReplaceMtf.setSelected(false);
        dmgMin.setText("1"); dmgMax.setText("40");
        acMin.setText("0"); acMax.setText("70");
        levelMin.setText("1"); levelMax.setText("80");
        // Balance profile applied in collectOptions via activePreset
        markPreset("Novice");
    }

    private void applyExpert() {
        applyDifficultyBase();
        chkRollMon.setSelected(false);
        chkRollItem.setSelected(false);
        chkLand.setSelected(false);
        chkQuest.setSelected(true);
        chkQuestRew.setSelected(false);
        chkQuestDensity.setSelected(true);
        chkEnemyTypes.setSelected(false);
        chkLandEnemies.setSelected(true);
        chkShops.setSelected(true);
        chkPipeline.setSelected(false);
        dmgMin.setText("1"); dmgMax.setText("60");
        acMin.setText("0"); acMax.setText("100");
        levelMin.setText("1"); levelMax.setText("120");
        markPreset("Expert");
    }

    private void applyMaster() {
        applyDifficultyBase();
        chkRollMon.setSelected(true);
        chkRollItem.setSelected(false);
        chkLand.setSelected(true);
        chkQuest.setSelected(true);
        chkQuestRew.setSelected(true);
        chkQuestDensity.setSelected(true);
        chkEnemyTypes.setSelected(true);
        chkLandEnemies.setSelected(true);
        chkShops.setSelected(true);
        chkPipeline.setSelected(true);
        dmgMin.setText("1"); dmgMax.setText("100");
        acMin.setText("0"); acMax.setText("160");
        levelMin.setText("1"); levelMax.setText("200");
        markPreset("Master");
    }

    private void applyHero() {
        applyDifficultyBase();
        chkRollMon.setSelected(true);
        chkRollItem.setSelected(true);
        chkLand.setSelected(true);
        chkQuest.setSelected(true);
        chkQuestRew.setSelected(true);
        chkQuestDensity.setSelected(true);
        chkEnemyTypes.setSelected(true);
        chkLandEnemies.setSelected(true);
        chkShops.setSelected(true);
        chkPipeline.setSelected(true);
        chkEarlyCaps.setSelected(true);
        dmgMin.setText("1"); dmgMax.setText("140");
        acMin.setText("0"); acMax.setText("220");
        levelMin.setText("1"); levelMax.setText("300");
        markPreset("Hero");
    }

    private void applyLegend() {
        applyDifficultyBase();
        chkRollMon.setSelected(true);
        chkRollItem.setSelected(true);
        chkLand.setSelected(true);
        chkQuest.setSelected(true);
        chkQuestRew.setSelected(true);
        chkQuestDensity.setSelected(true);
        chkEnemyTypes.setSelected(true);
        chkLandEnemies.setSelected(true);
        chkShops.setSelected(true);
        chkPipeline.setSelected(true);
        chkEarlyCaps.setSelected(false); // full chaos
        dmgMin.setText("1"); dmgMax.setText("200");
        acMin.setText("0"); acMax.setText("300");
        levelMin.setText("1"); levelMax.setText("400");
        markPreset("Legend");
    }



    /**
     * Bloodstained-style front door: seed, module toggles, one-click start.
     */
    private Parent buildBloodstainedMenu() {
        VBox root = new VBox(16);
        root.getStyleClass().add("sidebar");
        root.setStyle("-fx-padding: 16;");

        Label title = new Label("TrueStone");
        title.getStyleClass().add("title");
        Label sub = new Label(APP_TAGLINE + " — seed, toggles, start");
        sub.getStyleClass().add("subtitle");

        Label seedLab = new Label("SEED");
        seedLab.getStyleClass().add("section-label");
        seedField.setPromptText("enter seed or roll new");
        seedField.setStyle("-fx-font-size: 16px; -fx-font-family: monospace;");
        HBox seedRow = new HBox(10, grow(seedField),
                action("New", () -> seedField.setText(PcOptions.randomSeedString())),
                action("Copy", this::copySeed));
        seedRow.setAlignment(Pos.CENTER_LEFT);

        Label hashLab = new Label("Game seed.txt value: "
                + (PcOptions.seedFromString(seedField.getText() == null ? "" : seedField.getText()) & 0x7fffffffL));
        hashLab.getStyleClass().add("muted");
        seedField.textProperty().addListener((o, a, b) -> {
            long h = PcOptions.seedFromString(b == null ? "" : b) & 0x7fffffffL;
            hashLab.setText("Game seed.txt value: " + h);
        });

        Label preLab = new Label("PRESET");
        preLab.getStyleClass().add("section-label");
        HBox presets = new HBox(8, btnNovice, btnExpert, btnMaster, btnHero, btnLegend);
        presets.setAlignment(Pos.CENTER_LEFT);

        VBox enemies = card("Enemies",
                flow(chkMonsters, chkShuffleMon, chkRollMon, chkEnemyTypes, chkLandEnemies, chkSpeeds, chkSpawn, chkDat, chkEarlyCaps));
        VBox gear = card("Items & Shops",
                flow(chkItems, chkShuffleItem, chkRollItem, chkShops));
        VBox heroes = card("Characters",
                flow(chkClasses, chkUncapStats));
        VBox world = card("Quests & World",
                flow(chkQuest, chkQuestRew, chkLand, chkInjectMtf, chkPipeline, chkQuestCopy));
        VBox install = card("Install",
                flow(chkReplaceMtf),
                pathRow("Game", gamePathLabel, this::selectGame),
                pathRow("Out", outPathLabel, this::selectOut),
                pathRow("MTF", mtfPathLabel, this::selectMtf));

        Button start = action("START RANDOMIZED GAME",
                () -> runInBackground("Starting randomized game...", this::runStartGame));
        start.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 10 18;");
        start.getStyleClass().add("preset-active");

        Label tip = new Label(
                "Start = Randomize + Install seed into game + Launch Darkstone.exe\n"
                + "Writes config\\seed.txt and data\\monsterclass.dat / itemobject.dat (optional DATA.MTF).");
        tip.getStyleClass().add("muted");
        tip.setWrapText(true);

        root.getChildren().addAll(
                new VBox(4, title, sub),
                new VBox(6, seedLab, seedRow, hashLab),
                new VBox(6, preLab, presets),
                enemies, gear, heroes, world, install,
                start, tip
        );

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("sidebar-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        StackPane frame = new StackPane(scroll);
        frame.getStyleClass().add("frame-outer");
        return frame;
    }

    private ScrollPane buildSidebar() {
        VBox side = new VBox(12);
        side.getStyleClass().add("sidebar");

        side.getChildren().add(card("Game folders",
                pathRow("Game", gamePathLabel, this::selectGame),
                pathRow("Out", outPathLabel, this::selectOut),
                pathRow("MTF", mtfPathLabel, this::selectMtf),
                new Label("Out empty = write into Game/PCLASS (with .bak).")));

        side.getChildren().add(card("Seed",
                new HBox(8, grow(seedField),
                        action("New", () -> seedField.setText(PcOptions.randomSeedString())),
                        action("Copy", this::copySeed))));

        GridPane ranges = new GridPane();
        ranges.setHgap(6);
        ranges.setVgap(6);
        int r = 0;
        r = rangeRow(ranges, r, "Damage", dmgMin, dmgMax);
        r = rangeRow(ranges, r, "AC", acMin, acMax);
        r = rangeRow(ranges, r, "Level", levelMin, levelMax);
        side.getChildren().add(card("Range rolls", ranges));

        side.getChildren().add(card("Modules",
                flow(chkMonsters, chkItems, chkClasses,
                        chkShuffleMon, chkShuffleItem, chkRollMon, chkRollItem, chkDat, chkLand, chkQuest, chkQuestRew,
                        chkInjectMtf, chkEnemyTypes, chkLandEnemies, chkSpeeds, chkShops, chkPipeline, chkQuestCopy, chkReplaceMtf, chkEarlyCaps, chkUncapStats, chkImproveClasses, chkLootTiers, chkStartKits, chkLandLogic)));

        Button run = action("Randomize", () -> runInBackground("Randomizing PC data...", this::runRandomize));
        Button install = action("Install Seed", () -> runInBackground("Installing seed...", this::runInstallSeed));
        Button launch = action("Launch", () -> runInBackground("Launching Darkstone...", this::runLaunch));
        Button installLaunch = action("Install + Launch",
                () -> runInBackground("Install + launch...", this::runInstallAndLaunch));
        Button restore = action("Restore .bak", () -> runInBackground("Restoring backups...", this::restoreBackups));
        chkReplaceMtf.setSelected(false);
        chkEarlyCaps.setSelected(true);
        side.getChildren().add(card("Run",
                new HBox(8, run, install),
                new HBox(8, launch, installLaunch),
                new HBox(8, restore),
                chkReplaceMtf));

        ScrollPane scroll = new ScrollPane(side);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("sidebar-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private VBox buildLog() {
        Label label = new Label("Log");
        label.getStyleClass().add("section-label");
        logArea.setPrefRowCount(6);
        logArea.setMinHeight(100);
        logArea.setPrefHeight(120);
        logArea.setMaxHeight(160);
        VBox box = new VBox(4, label, logArea);
        box.getStyleClass().add("log-pane");
        return box;
    }

    private HBox buildStatus() {
        HBox bar = new HBox(12, statusLabel, spacer(), progressBar);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        return bar;
    }

    private void runInstallSeed() throws Exception {
        if (gameRoot == null) throw new IllegalStateException("Select the game folder.");
        PcOptions o = currentOptions();
        Path seedDir = PcSeedLauncher.resolveSeedDir(gameRoot, outputRoot, o.seedText);
        if (!java.nio.file.Files.isDirectory(seedDir)) {
            throw new IllegalStateException("Seed package not found: " + seedDir
                    + " — Randomize first.");
        }
        new PcSeedLauncher(logSink).installSeed(gameRoot, seedDir, chkReplaceMtf.isSelected());
        String installed = PcSeedLauncher.sanitize(o.seedText);
        Platform.runLater(() -> statusLabel.setText("Installed seed " + installed));
    }

    private void runLaunch() throws Exception {
        if (gameRoot == null) throw new IllegalStateException("Select the game folder.");
        new PcSeedLauncher(logSink).launchGame(gameRoot);
        Platform.runLater(() -> statusLabel.setText("Launched Darkstone"));
    }

    private void runInstallAndLaunch() throws Exception {
        if (gameRoot == null) throw new IllegalStateException("Select the game folder.");
        PcOptions o = currentOptions();
        Path seedDir = PcSeedLauncher.resolveSeedDir(gameRoot, outputRoot, o.seedText);
        if (!java.nio.file.Files.isDirectory(seedDir)) {
            throw new IllegalStateException("Seed package not found: " + seedDir
                    + " — Randomize first.");
        }
        new PcSeedLauncher(logSink).installAndLaunch(gameRoot, seedDir, chkReplaceMtf.isSelected());
        String launched = PcSeedLauncher.sanitize(o.seedText);
        Platform.runLater(() -> statusLabel.setText("Installed + launched " + launched));
    }


    /**
     * Bloodstained-style one-shot: randomize → package → install → launch.
     */
    private void runStartGame() throws Exception {
        if (gameRoot == null) {
            throw new IllegalStateException("Select the Darkstone game folder first.");
        }
        if (seedField.getText() == null || seedField.getText().isBlank()) {
            seedField.setText(PcOptions.randomSeedString());
        }
        logSink.log("========== START RANDOMIZED GAME ==========");
        logSink.log("Seed: " + seedField.getText().trim());
        runRandomize();
        PcOptions o = currentOptions();
        Path seedDir = PcSeedLauncher.resolveSeedDir(gameRoot, outputRoot, o.seedText);
        if (!java.nio.file.Files.isDirectory(seedDir)) {
            // package may live under outputRoot/seeds even if resolve missed
            throw new IllegalStateException("Seed package missing after randomize: " + seedDir);
        }
        new PcSeedLauncher(logSink).installAndLaunch(gameRoot, seedDir, chkReplaceMtf.isSelected());
        String running = PcSeedLauncher.sanitize(o.seedText);
        Platform.runLater(() -> statusLabel.setText("Running seed " + running));
        logSink.log("========== GAME LAUNCHED ==========");
    }

    private void runRandomize() throws Exception {
        if (gameRoot == null) throw new IllegalStateException("Select the Darkstone PC game folder.");
        Path pclass = gameRoot.resolve("PCLASS");
        if (!Files.isDirectory(pclass) && !gameRoot.getFileName().toString().equalsIgnoreCase("PCLASS")) {
            throw new IllegalStateException("Game folder must contain PCLASS/ (MONSTER.TXT, OBJECT.TXT).");
        }
        PcOptions o = currentOptions();
        if (o.campaignPipeline) {
            if (o.dataMtfPath == null || !Files.isRegularFile(o.dataMtfPath)) {
                throw new IllegalStateException("Campaign pipeline needs DATA.MTF (set MTF path).");
            }
            new PcCampaignPipeline(logSink).run(o);
        } else {
            new PcRandomizerEngine(logSink).run(o);
        }
    }

    private void restoreBackups() throws Exception {
        Path dir = outputRoot != null ? outputRoot.resolve("PCLASS") : gameRoot.resolve("PCLASS");
        if (gameRoot != null && gameRoot.getFileName().toString().equalsIgnoreCase("PCLASS")) {
            dir = outputRoot != null ? outputRoot : gameRoot;
        }
        int n = 0;
        for (String name : List.of("MONSTER.TXT", "OBJECT.TXT", "PCLASS.TXT")) {
            Path bak = dir.resolve(name + ".bak");
            Path cur = dir.resolve(name);
            if (Files.isRegularFile(bak)) {
                Files.copy(bak, cur, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logSink.log("Restored " + name);
                n++;
            }
        }
        if (n == 0) logSink.log("No .bak files found in " + dir);
        else statusLabel.setText("Restored " + n + " file(s)");
    }

    private PcOptions currentOptions() {
        PcOptions o = new PcOptions();
        o.gameRoot = gameRoot;
        o.outputRoot = outputRoot;
        o.monsters = chkMonsters.isSelected();
        o.items = chkItems.isSelected();
        o.playerClasses = chkClasses.isSelected();
        o.shuffleMonsterStats = chkShuffleMon.isSelected();
        o.shuffleItemStats = chkShuffleItem.isSelected();
        o.rangeRollMonsters = chkRollMon.isSelected();
        o.rangeRollItems = chkRollItem.isSelected();
        o.patchDat = chkDat.isSelected();
        o.landProps = chkLand.isSelected();
        o.questScripts = chkQuest.isSelected();
        o.questRewards = chkQuestRew.isSelected();
        o.injectMtf = chkInjectMtf.isSelected();
        o.shuffleEnemyTypes = chkEnemyTypes.isSelected();
        o.landEnemyShuffle = chkLandEnemies.isSelected();
        o.randomizeSpeeds = chkSpeeds.isSelected();
        o.randomizeSpawnCounts = chkSpawn.isSelected();
        o.questSpawnDensity = chkQuestDensity.isSelected();
        o.shuffleShops = chkShops.isSelected();
        o.lootTierBands = chkLootTiers.isSelected();
        o.startKits = chkStartKits.isSelected();
        o.questLandLogic = chkLandLogic.isSelected();
        o.earlyGameCaps = chkEarlyCaps.isSelected();
        o.uncapMaxStats = chkUncapStats.isSelected();
        o.improveClasses = chkImproveClasses.isSelected();
        applyBalanceProfile(o);
        o.campaignPipeline = chkPipeline.isSelected();
        o.copyToQuestFolder = chkQuestCopy.isSelected();
        o.multiSeedCount = parse(multiSeedField, 1);
        o.dataMtfPath = dataMtfPath;
        o.dmgMin = parse(dmgMin, 1);
        o.dmgMax = parse(dmgMax, 80);
        o.acMin = parse(acMin, 0);
        o.acMax = parse(acMax, 150);
        o.levelMin = parse(levelMin, 1);
        o.levelMax = parse(levelMax, 200);
        o.seedText = seedField.getText();
        o.preset = activePreset;
        return o;
    }

    private void selectMtf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select DATA.MTF");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MTF archive", "*.MTF", "*.mtf"));
        File initial = prefsPath("dataMtf");
        if (initial == null && gameRoot != null) {
            initial = gameRoot.toFile();
        }
        if (initial != null) {
            File dir = initial.isDirectory() ? initial : initial.getParentFile();
            if (dir != null) chooser.setInitialDirectory(dir);
        }
        File picked = chooser.showOpenDialog(stage);
        if (picked != null) {
            dataMtfPath = picked.toPath();
            mtfPathLabel.setText(dataMtfPath.toString());
            prefs.put("dataMtf", dataMtfPath.toString());
            logSink.log("DATA.MTF: " + dataMtfPath);
        }
    }

    private void selectGame() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Darkstone PC folder (contains PCLASS)");
        File initial = prefsPath("gameRoot");
        if (initial != null) chooser.setInitialDirectory(initial);
        File picked = chooser.showDialog(stage);
        if (picked != null) {
            gameRoot = picked.toPath();
            gamePathLabel.setText(gameRoot.toString());
            prefs.put("gameRoot", gameRoot.toString());
            logSink.log("Game: " + gameRoot);
        }
    }

    private void selectOut() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Output folder (optional — empty = in-place)");
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

    private void copySeed() {
        String seed = seedField.getText() == null ? "" : seedField.getText().trim();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(seed);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        statusLabel.setText("Seed copied: " + seed);
    }

    private void restorePaths() {
        String mtfPref = prefs.get("dataMtf", "");
        if (!mtfPref.isBlank()) {
            dataMtfPath = Path.of(mtfPref);
            mtfPathLabel.setText(dataMtfPath.toString());
        }
        String g = prefs.get("gameRoot", "");
        if (!g.isBlank() && Files.isDirectory(Path.of(g))) {
            gameRoot = Path.of(g);
            gamePathLabel.setText(g);
        }
        String o = prefs.get("outputRoot", "");
        if (!o.isBlank() && Files.isDirectory(Path.of(o))) {
            outputRoot = Path.of(o);
            outPathLabel.setText(o);
        }
    }

    private File prefsPath(String key) {
        String value = prefs.get(key, "");
        if (value.isBlank()) return null;
        Path p = Path.of(value);
        return Files.isDirectory(p) ? p.toFile() : null;
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
        }, "darkstone-pc-worker");
        t.setDaemon(true);
        t.start();
    }

    private void setActionsDisabled(boolean disabled) {
        for (Button b : actionButtons) b.setDisable(disabled);
    }

    private void logStartup() {
        logSink.log(APP_NAME + " " + VERSION + " — " + APP_TAGLINE);
        logSink.log("Tabs: Start Run (Bloodstained) | Advanced | MTF Explorer");
        logSink.log("START RANDOMIZED GAME = randomize + install + launch.");
        logSink.log("Edits PCLASS/MONSTER.TXT, OBJECT.TXT, PCLASS.TXT");
        logSink.log("Backups saved as *.TXT.bak — use Restore to undo.");
    }

    private void alert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message == null ? "" : message, ButtonType.OK);
        alert.initOwner(stage);
        alert.showAndWait();
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
        c.setPrefWidth(40);
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
        if (!row.getChildren().isEmpty()) col.getChildren().add(row);
        return new HBox(col);
    }

    private static TextField rangeField(String v) {
        TextField f = new TextField(v);
        f.setPrefColumnCount(4);
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

    private static int parse(TextField field, int fallback) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface Worker {
        void run() throws Exception;
    }
}
