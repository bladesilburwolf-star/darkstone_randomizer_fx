package com.serifsystemworks.darkstone.ui;

import com.serifsystemworks.darkstone.engine.LogSink;
import com.serifsystemworks.darkstone.pc.PcOptions;
import com.serifsystemworks.darkstone.pc.PcRandomizerEngine;
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

    public static final String VERSION = "1.3.0-pc";

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

    private final Button btnGeneral = new Button("General");
    private final Button btnAdvanced = new Button("Advanced");
    private final Button btnChaotic = new Button("Chaotic");

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
        StackPane frame = new StackPane(inner);
        frame.getStyleClass().add("frame-outer");
        restorePaths();
        applyGeneral();
        logStartup();
        return frame;
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
        for (Button b : List.of(btnGeneral, btnAdvanced, btnChaotic)) {
            b.getStyleClass().add("preset");
            actionButtons.add(b);
        }
        btnGeneral.setOnAction(e -> applyGeneral());
        btnAdvanced.setOnAction(e -> applyAdvanced());
        btnChaotic.setOnAction(e -> applyChaotic());
    }

    private HBox buildHeader() {
        Label title = new Label("DARKSTONE PC RANDOMIZER");
        title.getStyleClass().add("title");
        Label ver = new Label("v" + VERSION + "  -  Windows PC  -  PCLASS TXT  -  seed export");
        ver.getStyleClass().add("subtitle");
        HBox header = new HBox(new VBox(2, title, ver));
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox buildPresetBar() {
        Label lab = new Label("PRESET");
        lab.getStyleClass().add("section-label");
        HBox bar = new HBox(10, lab, btnGeneral, btnAdvanced, btnChaotic, spacer());
        bar.getStyleClass().add("preset-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void markPreset(String name) {
        activePreset = name;
        btnGeneral.getStyleClass().remove("preset-active");
        btnAdvanced.getStyleClass().remove("preset-active");
        btnChaotic.getStyleClass().remove("preset-active");
        if ("General".equals(name)) btnGeneral.getStyleClass().add("preset-active");
        if ("Advanced".equals(name)) btnAdvanced.getStyleClass().add("preset-active");
        if ("Chaotic".equals(name)) btnChaotic.getStyleClass().add("preset-active");
        statusLabel.setText("Preset: " + name);
        logSink.log("Preset -> " + name);
    }

    private void applyGeneral() {
        chkMonsters.setSelected(true);
        chkItems.setSelected(true);
        chkClasses.setSelected(true);
        chkShuffleMon.setSelected(true);
        chkShuffleItem.setSelected(true);
        chkRollMon.setSelected(false);
        chkRollItem.setSelected(false);
        chkDat.setSelected(true);
        chkLand.setSelected(false);
        chkQuest.setSelected(false);
        chkQuestRew.setSelected(false);
        dmgMin.setText("1"); dmgMax.setText("60");
        acMin.setText("0"); acMax.setText("100");
        levelMin.setText("1"); levelMax.setText("120");
        markPreset("General");
    }

    private void applyAdvanced() {
        chkMonsters.setSelected(true);
        chkItems.setSelected(true);
        chkClasses.setSelected(true);
        chkShuffleMon.setSelected(true);
        chkShuffleItem.setSelected(true);
        chkRollMon.setSelected(true);
        chkRollItem.setSelected(false);
        chkDat.setSelected(true);
        chkLand.setSelected(true);
        chkQuest.setSelected(true);
        chkQuestRew.setSelected(false);
        dmgMin.setText("1"); dmgMax.setText("100");
        acMin.setText("0"); acMax.setText("180");
        levelMin.setText("1"); levelMax.setText("250");
        markPreset("Advanced");
    }

    private void applyChaotic() {
        chkMonsters.setSelected(true);
        chkItems.setSelected(true);
        chkClasses.setSelected(true);
        chkShuffleMon.setSelected(true);
        chkShuffleItem.setSelected(true);
        chkRollMon.setSelected(true);
        chkRollItem.setSelected(true);
        chkDat.setSelected(true);
        chkLand.setSelected(true);
        chkQuest.setSelected(true);
        chkQuestRew.setSelected(true);
        dmgMin.setText("0"); dmgMax.setText("200");
        acMin.setText("0"); acMax.setText("250");
        levelMin.setText("1"); levelMax.setText("400");
        markPreset("Chaotic");
    }

    private ScrollPane buildSidebar() {
        VBox side = new VBox(12);
        side.getStyleClass().add("sidebar");

        side.getChildren().add(card("Game folders",
                pathRow("Game", gamePathLabel, this::selectGame),
                pathRow("Out", outPathLabel, this::selectOut),
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
                        chkShuffleMon, chkShuffleItem, chkRollMon, chkRollItem, chkDat, chkLand, chkQuest, chkQuestRew)));

        Button run = action("Randomize", () -> runInBackground("Randomizing PC data...", this::runRandomize));
        run.getStyleClass().add("master");
        Button restore = action("Restore .bak", () -> runInBackground("Restoring...", this::restoreBackups));
        restore.getStyleClass().add("danger");
        side.getChildren().add(card("Run", new HBox(8, run, restore)));

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

    private void runRandomize() throws Exception {
        if (gameRoot == null) throw new IllegalStateException("Select the Darkstone PC game folder.");
        Path pclass = gameRoot.resolve("PCLASS");
        if (!Files.isDirectory(pclass) && !gameRoot.getFileName().toString().equalsIgnoreCase("PCLASS")) {
            throw new IllegalStateException("Game folder must contain PCLASS/ (MONSTER.TXT, OBJECT.TXT).");
        }
        PcOptions o = currentOptions();
        new PcRandomizerEngine(logSink).run(o);
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
        logSink.log("Darkstone PC Randomizer " + VERSION);
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
