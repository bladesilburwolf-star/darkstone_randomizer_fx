package com.serifsystemworks.darkstone.ui;

import com.serifsystemworks.darkstone.mtf.MtfArchive;
import com.serifsystemworks.darkstone.mtf.MtfBackupManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Standalone MTF archive explorer/editor: open a .MTF, browse entries,
 * preview/extract/replace content, and rebuild via {@link MtfArchive#rebuild}.
 * <p>
 * Built as its own view (like {@link PcMainView}) so it can be dropped
 * straight into the main randomizer shell as another tab once testing
 * against a real DATA.MTF is done.
 */
public final class MtfExplorerView {

    /** Row model for the entry table. */
    public static final class Row {
        final MtfArchive.Entry entry;
        boolean modified;
        byte[] pendingContent; // null unless staged for replacement

        Row(MtfArchive.Entry entry) {
            this.entry = entry;
        }

        String status() {
            return modified ? "MODIFIED" : "original";
        }
    }

    private final Stage stage;
    private final BorderPane root = new BorderPane();

    private final Label pathLabel = new Label("No archive open");
    private final Label statusLabel = new Label("Open a .MTF archive to begin");
    private final TextArea previewArea = new TextArea();
    private final TextArea logArea = new TextArea();
    private final TextField filterField = new TextField();

    private final TableView<Row> table = new TableView<>();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();

    private MtfArchive archive;
    private Path archivePath;
    private final Map<String, byte[]> pendingChanges = new HashMap<>();

    private final Button btnOpen = new Button("Open .MTF...");
    private final Button btnExtract = new Button("Extract Selected...");
    private final Button btnReplace = new Button("Replace Selected...");
    private final Button btnRevert = new Button("Revert Selected");
    private final Button btnBackup = new Button("Backup Original");
    private final Button btnRebuild = new Button("Rebuild / Save As...");

    public MtfExplorerView(Stage stage) {
        this.stage = stage;
    }

    public Parent getRoot() {
        return build();
    }

    public Parent build() {
        root.setPadding(new Insets(10));

        VBox top = new VBox(6, topBar(), filterBar());
        root.setTop(top);

        buildTable();
        previewArea.setEditable(false);
        previewArea.setWrapText(false);
        previewArea.setStyle("-fx-font-family: monospace;");
        previewArea.setPromptText("Select an entry to preview its first bytes here (hex + ASCII).");

        VBox previewBox = new VBox(4, new Label("Preview"), previewArea);
        VBox.setVgrow(previewArea, Priority.ALWAYS);
        previewBox.setPadding(new Insets(0, 0, 0, 8));

        SplitPane split = new SplitPane(table, previewBox);
        split.setDividerPositions(0.62);
        root.setCenter(split);

        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        VBox bottom = new VBox(4, statusLabel, logArea);
        root.setBottom(bottom);

        wireActions();
        updateButtonStates();
        return root;
    }

    // ---- layout pieces ----------------------------------------------------

    private HBox topBar() {
        HBox bar = new HBox(8, btnOpen, pathLabel, spacer(), btnBackup, btnRebuild);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox filterBar() {
        filterField.setPromptText("Filter by path...");
        filterField.textProperty().addListener((obs, old, val) -> applyFilter(val));
        HBox bar = new HBox(8,
                new Label("Filter:"), filterField, spacer(),
                btnExtract, btnReplace, btnRevert);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    @SuppressWarnings("unchecked")
    private void buildTable() {
        TableColumn<Row, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().entry.path));
        pathCol.setPrefWidth(320);

        TableColumn<Row, String> sizeCol = new TableColumn<>("Decompressed");
        sizeCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().entry.decompSize)));
        sizeCol.setPrefWidth(100);

        TableColumn<Row, String> storedCol = new TableColumn<>("Stored");
        storedCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().entry.storedSize)));
        storedCol.setPrefWidth(90);

        TableColumn<Row, String> compCol = new TableColumn<>("Compressed");
        compCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().entry.compressed ? "yes" : "no"));
        compCol.setPrefWidth(90);

        TableColumn<Row, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status()));
        statusCol.setPrefWidth(90);

        table.getColumns().setAll(pathCol, sizeCol, storedCol, compCol, statusCol);
        table.setItems(rows);
        table.setRowFactory(tv -> new TableRow<Row>() {
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                setStyle(!empty && row.modified ? "-fx-background-color: #fff3b0;" : "");
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            updateButtonStates();
            showPreview(sel);
        });
    }

    // ---- actions ------------------------------------------------------

    private void wireActions() {
        btnOpen.setOnAction(e -> onOpen());
        btnExtract.setOnAction(e -> onExtract());
        btnReplace.setOnAction(e -> onReplace());
        btnRevert.setOnAction(e -> onRevert());
        btnBackup.setOnAction(e -> onBackup());
        btnRebuild.setOnAction(e -> onRebuild());
    }

    private void onOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Darkstone .MTF archive");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MTF archives", "*.MTF", "*.mtf"));
        var file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            MtfArchive newArchive = new MtfArchive();
            newArchive.open(file.toPath());

            if (archive != null) {
                try { archive.close(); } catch (IOException ignored) { }
            }
            archive = newArchive;
            archivePath = file.toPath();
            pendingChanges.clear();

            rows.setAll(archive.getEntries().stream().map(Row::new).toList());
            pathLabel.setText(archivePath.toString());
            statusLabel.setText("Opened " + rows.size() + " entries from " + archivePath.getFileName());
            log("Opened " + archivePath + " — " + rows.size() + " entries.");
        } catch (Exception ex) {
            error("Failed to open archive", ex);
        }
        updateButtonStates();
    }

    private void onExtract() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null || archive == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save extracted file");
        chooser.setInitialFileName(safeFileName(row.entry.path));
        var file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            byte[] content = row.pendingContent != null ? row.pendingContent : archive.extract(row.entry.path);
            Files.write(file.toPath(), content);
            log("Extracted " + row.entry.path + " -> " + file + " (" + content.length + " bytes)");
            statusLabel.setText("Extracted " + row.entry.path);
        } catch (Exception ex) {
            error("Failed to extract " + row.entry.path, ex);
        }
    }

    private void onReplace() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null || archive == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose replacement content for " + row.entry.path);
        var file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            byte[] newContent = Files.readAllBytes(file.toPath());
            if (newContent.length != row.entry.decompSize) {
                double pct = row.entry.decompSize == 0 ? 0
                        : (newContent.length - row.entry.decompSize) * 100.0 / row.entry.decompSize;
                String msg = row.entry.path + " size changed: "
                        + row.entry.decompSize + " -> " + newContent.length
                        + String.format(" (%+.1f%%).\n\n", pct)
                        + "Fixed-record files (MONSTERCLASS.DAT, ITEMOBJECT.DAT, PCLASS tables) "
                        + "must keep the exact decompressed size or the game will crash.\n\n"
                        + "Continue staging this replacement anyway?";
                Alert warn = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
                warn.setHeaderText("Decompressed size mismatch");
                var ans = warn.showAndWait();
                if (ans.isEmpty() || ans.get() != ButtonType.YES) {
                    log("Replace cancelled (size mismatch) for " + row.entry.path);
                    return;
                }
            }
            row.pendingContent = newContent;
            row.modified = true;
            pendingChanges.put(row.entry.path, newContent);
            table.refresh();
            showPreview(row);
            log("Staged replacement for " + row.entry.path + " from " + file
                    + " (" + newContent.length + " bytes, was " + row.entry.decompSize + ")");
            statusLabel.setText(pendingChanges.size() + " entr"
                    + (pendingChanges.size() == 1 ? "y" : "ies") + " staged for rebuild.");
        } catch (Exception ex) {
            error("Failed to read replacement file", ex);
        }
        updateButtonStates();
    }

    private void onRevert() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            return;
        }
        row.pendingContent = null;
        row.modified = false;
        pendingChanges.remove(row.entry.path);
        table.refresh();
        showPreview(row);
        log("Reverted staged change for " + row.entry.path);
        statusLabel.setText(pendingChanges.size() + " entr" + (pendingChanges.size() == 1 ? "y" : "ies") + " staged for rebuild.");
        updateButtonStates();
    }

    private void onBackup() {
        if (archivePath == null) {
            return;
        }
        try {
            Path backup = MtfBackupManager.createBackup(archivePath);
            log("Backup created: " + backup);
            statusLabel.setText("Backup created at " + backup);
        } catch (Exception ex) {
            error("Backup failed", ex);
        }
    }

    private void onRebuild() {
        if (archive == null || archivePath == null) {
            return;
        }
        if (pendingChanges.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "No entries have been changed — nothing to rebuild.", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save rebuilt archive as");
        chooser.setInitialFileName(archivePath.getFileName().toString());
        chooser.setInitialDirectory(archivePath.getParent().toFile());
        var file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        boolean overwritingOriginal = file.toPath().toAbsolutePath().equals(archivePath.toAbsolutePath());
        if (overwritingOriginal) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "This will overwrite the currently open archive. A backup will be made first. Continue?",
                    ButtonType.YES, ButtonType.NO);
            var result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) {
                return;
            }
            try {
                Path backup = MtfBackupManager.createBackup(archivePath);
                log("Backup created before overwrite: " + backup);
            } catch (Exception ex) {
                error("Backup before overwrite failed — aborting rebuild", ex);
                return;
            }
        }

        // Prefer same-size decomp for fixed-record safety (user can opt out)
        boolean anySizeChange = pendingChanges.entrySet().stream().anyMatch(e -> {
            MtfArchive.Entry ent = archive.getEntry(e.getKey());
            return ent != null && ent.decompSize != e.getValue().length;
        });
        boolean sameSizeOnly = true;
        if (anySizeChange) {
            Alert mode = new Alert(Alert.AlertType.CONFIRMATION);
            mode.setHeaderText("Decompressed size will change");
            mode.setContentText(
                    "At least one staged file has a different decompressed size than retail.\n\n"
                    + "YES = same-size only (refuse size-changing entries — safest for DATA.MTF)\n"
                    + "NO  = allow size changes (archive may grow; game may crash)");
            mode.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
            var modeAns = mode.showAndWait();
            if (modeAns.isEmpty() || modeAns.get() == ButtonType.CANCEL) {
                return;
            }
            sameSizeOnly = modeAns.get() == ButtonType.YES;
        }

        try {
            log("Rebuilding " + pendingChanges.size() + " changed entr"
                    + (pendingChanges.size() == 1 ? "y" : "ies")
                    + (sameSizeOnly ? " [same-size mode]" : " [size changes allowed]")
                    + " -> " + file + " ...");
            MtfArchive.RebuildReport report = sameSizeOnly
                    ? archive.rebuildSameSize(file.toPath(), pendingChanges)
                    : archive.rebuild(file.toPath(), pendingChanges);
            log("Rebuild complete: " + file + " — " + report);
            for (String w : report.warnings) {
                log("  warn: " + w);
            }
            if (report.rebuiltBytes > report.originalBytes) {
                log("  NOTE: archive grew by " + (report.rebuiltBytes - report.originalBytes)
                        + " bytes. Prefer same-size patches for campaign DATA.MTF.");
            }
            statusLabel.setText("Rebuilt " + file.getName() + " (" + report.rebuiltBytes + " bytes)");

            MtfArchive reopened = new MtfArchive();
            reopened.open(file.toPath());
            archive.close();
            archive = reopened;
            archivePath = file.toPath();
            pendingChanges.clear();
            rows.setAll(archive.getEntries().stream().map(Row::new).toList());
            pathLabel.setText(archivePath.toString());
            table.refresh();
        } catch (Exception ex) {
            error("Rebuild failed", ex);
        }
        updateButtonStates();
    }

    // ---- helpers ------------------------------------------------------

    private void applyFilter(String needle) {
        if (archive == null) {
            return;
        }
        String upper = needle == null ? "" : needle.toUpperCase();
        rows.setAll(archive.getEntries().stream()
                .map(Row::new)
                .filter(r -> r.entry.path.toUpperCase().contains(upper))
                .toList());
        // reapply any pending modification markers after refiltering
        for (Row r : rows) {
            if (pendingChanges.containsKey(r.entry.path)) {
                r.modified = true;
                r.pendingContent = pendingChanges.get(r.entry.path);
            }
        }
        table.refresh();
    }

    private void showPreview(Row row) {
        if (row == null || archive == null) {
            previewArea.setText("");
            return;
        }
        try {
            byte[] content = row.pendingContent != null ? row.pendingContent : archive.extract(row.entry.path);
            previewArea.setText(hexPreview(content, row.entry.path, row.pendingContent != null));
        } catch (Exception ex) {
            previewArea.setText("Failed to preview: " + ex);
        }
    }

    private static String hexPreview(byte[] data, String path, boolean isPending) {
        StringBuilder sb = new StringBuilder();
        sb.append(path).append(isPending ? "  [STAGED REPLACEMENT]" : "").append('\n');
        sb.append(data.length).append(" bytes total\n\n");

        int limit = Math.min(data.length, 4096);
        for (int off = 0; off < limit; off += 16) {
            sb.append(String.format("%06X  ", off));
            StringBuilder ascii = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                if (off + i < limit) {
                    int b = data[off + i] & 0xFF;
                    sb.append(String.format("%02X ", b));
                    ascii.append(b >= 32 && b < 127 ? (char) b : '.');
                } else {
                    sb.append("   ");
                }
            }
            sb.append(" ").append(ascii).append('\n');
        }
        if (data.length > limit) {
            sb.append("\n... (").append(data.length - limit).append(" more bytes not shown)\n");
        }
        return sb.toString();
    }

    private static String safeFileName(String archivePath) {
        String name = archivePath.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private void updateButtonStates() {
        boolean hasArchive = archive != null;
        boolean hasSelection = table.getSelectionModel().getSelectedItem() != null;
        btnExtract.setDisable(!hasSelection);
        btnReplace.setDisable(!hasSelection);
        btnRevert.setDisable(!hasSelection || table.getSelectionModel().getSelectedItem() == null
                || !table.getSelectionModel().getSelectedItem().modified);
        btnBackup.setDisable(!hasArchive);
        btnRebuild.setDisable(!hasArchive);
    }

    private void log(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private void error(String context, Exception ex) {
        log("ERROR: " + context + " — " + ex);
        Alert alert = new Alert(Alert.AlertType.ERROR, context + ":\n" + ex, ButtonType.OK);
        alert.showAndWait();
    }
}
