package com.serifsystemworks.darkstone.mtf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class MtfBackupManager {
    private static final String BACKUP_DIR = "mtf_backups";
    private static final String BACKUP_SUFFIX = ".bak";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path mtfPath;

    public MtfBackupManager(Path mtfPath) {
        this.mtfPath = mtfPath;
    }

    public Path createBackup() throws IOException {
        Path backupDir = mtfPath.getParent() != null
                ? mtfPath.getParent().resolve(BACKUP_DIR)
                : Path.of(BACKUP_DIR);
        Files.createDirectories(backupDir);
        String timestamp = LocalDateTime.now().format(FORMAT);
        Path backupPath = backupDir.resolve(mtfPath.getFileName() + "." + timestamp + BACKUP_SUFFIX);
        Files.copy(mtfPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        Path simpleBackup = backupDir.resolve(mtfPath.getFileName() + BACKUP_SUFFIX);
        Files.copy(mtfPath, simpleBackup, StandardCopyOption.REPLACE_EXISTING);
        return backupPath;
    }

    public void restoreFromLatest() throws IOException {
        Path backupDir = mtfPath.getParent() != null
                ? mtfPath.getParent().resolve(BACKUP_DIR)
                : Path.of(BACKUP_DIR);
        if (!Files.isDirectory(backupDir)) {
            throw new IOException("No backups found");
        }
        String prefix = mtfPath.getFileName().toString();
        Path latest = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith(prefix))
                .max((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElseThrow(() -> new IOException("No backups found"));
        Files.copy(latest, mtfPath, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Path createBackup(Path mtfPath) throws IOException {
        return new MtfBackupManager(mtfPath).createBackup();
    }

    public static void restoreFromLatest(Path mtfPath) throws IOException {
        new MtfBackupManager(mtfPath).restoreFromLatest();
    }
}
