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
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

public final class MainView {

    public static final String VERSION = "2.3.1";

    private final Stage stage;
    private final Preferences prefs = Preferences.userNodeForPackage(MainView.class);

    private Path cdRoot;
    private Path outputRoot;

    private final Label cdPathLabel = new Label("Not set");
    private final Label outPathLabel = new Label("Not set");
    private final TextArea logArea = new TextArea();
    private final Label statusLabel = new Label("Set CD + output, unpack, then run.");
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

    private final CheckBox chkLoot = new CheckBox("Loot tables");
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
    private final CheckBox chkDungeons = new CheckBox("Land tiles (FE)");
    private final CheckBox chkCrossLand = new CheckBox("Cross-land tiles");
    private final CheckBox chkQuests = new CheckBox("Quest items");
    private final CheckBox chkVideos = new CheckBox("Skip videos");
    private final CheckBox chkCopy = new CheckBox("Install to CD");
    private final CheckBox chkForce = new CheckBox("Force unpack");

    private final List<Button> actionButtons = new ArrayList<>();
    private volatile boolean busy;

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
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildHeader());
        root.setCenter(buildSidebar());
        VBox bottom = new VBox(buildLog(), buildStatus());
        bottom.getStyleClass().add("bottom-stack");
        root.setBottom(bottom);
        restorePaths();
        logStartup();
        return root;
    }

    private void defaults() {
        chkLoot.setSelected(true);
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
        chkQuests.setSelected(false);
        chkVideos.setSelected(false);
        chkCopy.setSelected(true);
        seedField.setText(RandomizerOptions.randomSeedString());
        seedField.setPrefColumnCount(12);
        seedField.setPromptText("seed");
        logArea.setEditable(false);
        logArea.setWrapText(false);
        progressBar.setPrefWidth(160);
        progressBar.setProgress(0);
        cdPathLabel.getStyleClass().add("path-label");
        outPathLabel.getStyleClass().add("path-label");
    }

    private HBox buildHeader() {
        Label title = new Label("Darkstone Randomizer");
        title.getStyleClass().add("title");
        Label ver = new Label("v" + VERSION + "  ·  PSX  ·  rebuild ISO with CDImg");
        ver.getStyleClass().add("subtitle");
        VBox text = new VBox(2, title, ver);
        HBox header = new HBox(text);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private ScrollPane buildSidebar() {
        VBox side = new VBox(12);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(Region.USE_COMPUTED_SIZE);
        side.setMaxWidth(Double.MAX_VALUE);

        side.getChildren().add(card("Folders",
                pathRow("CD", cdPathLabel, this::selectCdFolder),
                pathRow("Out", outPathLabel, this::selectOutput),
                chkForce));

        side.getChildren().add(card("Seed",
                new HBox(8, grow(seedField),
                        action("New", () -> seedField.setText(RandomizerOptions.randomSeedString())),
                        action("Copy", this::copySeed),
                        action("Save", this::exportSeed))));

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
                flow(chkEnemies, chkEnemyLv, chkShops, chkMaps, chkDungeons, chkCrossLand, chkQuests)));

        side.getChildren().add(card("Install",
                flow(chkCopy, chkVideos)));

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
            Path file = dir.resolve("darkstone_seed.txt");
            String body = "seed=" + seed + "\n"
                    + "hash=" + RandomizerOptions.seedFromString(seed) + "\n"
                    + "version=" + VERSION + "\n";
            Files.writeString(file, body);
            statusLabel.setText("Seed saved: " + file);
            logSink.log("Seed exported: " + file);
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Could not save seed: " + e.getMessage());
        }
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
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("chip-row");
        // wrap-like: put in VBox of HBoxes every 3
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
        HBox outer = new HBox(col);
        return outer;
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
            Path seedFile = requireOutput().resolve("darkstone_seed.txt");
            String seed = options.seedText == null ? "" : options.seedText.trim();
            Files.writeString(seedFile, "seed=" + seed + "\n"
                    + "hash=" + RandomizerOptions.seedFromString(seed) + "\n"
                    + "version=" + VERSION + "\n");
            logSink.log("Seed exported: " + seedFile);
        } catch (Exception e) {
            logSink.log("[!] Seed export skipped: " + e.getMessage());
        }
        logSink.log("Next: rebuild the ISO with CDImg, then boot that image.");
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
        o.quests = chkQuests.isSelected();
        o.disableVideos = chkVideos.isSelected();
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
        if (cdRoot == null) throw new IllegalStateException("Select the CD folder first.");
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
    }

    private File prefsPath(String key) {
        String value = prefs.get(key, "");
        if (value.isBlank()) return null;
        Path p = Path.of(value);
        if (Files.isDirectory(p)) return p.toFile();
        Path parent = p.getParent();
        return parent != null && Files.isDirectory(parent) ? parent.toFile() : null;
    }

    private void logStartup() {
        logSink.log("Darkstone PSX Randomizer " + VERSION);
        logSink.log("Workflow: Unpack → Randomize → Install → rebuild ISO with CDImg.");
        logSink.log("Land tiles (FE): shuffles terrain objects per land. Cross-land mixes tiles between lands.");
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
