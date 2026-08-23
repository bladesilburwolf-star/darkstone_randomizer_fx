package com.serifsystemworks.darkstone.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class TableScanner {

    public static final int LOOT_TABLE_SIZE = 64;
    public static final int MAP_OBJECT_SIZE = 56;
    public static final byte MAP_OBJECT_SIGNATURE = (byte) 0xFE;
    /** Template sizes in DATA1 — many are spell/effect defs, not combat enemies. */
    public static final int[] TEMPLATE_SIZES = {470, 934, 1398, 1870};
    public static final byte TEMPLATE_SIGNATURE = 0x01;

    private TableScanner() {}

    public static ScanResult scan(Path root) throws IOException {
        ScanResult result = new ScanResult();
        TreeMap<Integer, Integer> histogram = new TreeMap<>();

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        if (name.startsWith("_")) {
                            return false;
                        }
                        try {
                            return Files.size(p) < 500_000;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            byte[] data = Files.readAllBytes(p);
                            histogram.merge(data.length, 1, Integer::sum);
                            switch (categorize(p, data)) {
                                case LOOT -> result.loot++;
                                case HERO -> result.heroes++;
                                case SHOP -> result.shops++;
                                case ENEMY -> result.enemies++;
                                case MAP -> result.maps++;
                                case QUEST -> result.quests++;
                                default -> result.other++;
                            }
                        } catch (Exception ignored) {
                            result.other++;
                        }
                    });
        }

        histogram.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> result.sizeHistogram.put(e.getKey(), e.getValue()));
        return result;
    }

    public static Category categorize(Path path, byte[] data) {
        if (isLoot(data)) {
            return Category.LOOT;
        }
        if (isMap(data)) {
            return Category.MAP;
        }
        if (isEnemy(data)) {
            return Category.ENEMY;
        }
        if (isQuest(data)) {
            return Category.QUEST;
        }
        if (isHero(data)) {
            return Category.HERO;
        }
        if (isShop(path, data)) {
            return Category.SHOP;
        }
        return Category.OTHER;
    }

    /** 64-byte pools with values in 0x00–0x0F (DATA1 item-id style). */
    public static boolean isLoot(byte[] data) {
        if (data.length != LOOT_TABLE_SIZE) {
            return false;
        }
        for (byte b : data) {
            if ((b & 0xFF) > 0x0F) {
                return false;
            }
        }
        return true;
    }

    public static boolean isMap(byte[] data) {
        return data.length == MAP_OBJECT_SIZE && data[0] == MAP_OBJECT_SIGNATURE;
    }

    public static boolean isEnemy(byte[] data) {
        if (data.length == 0 || data[0] != TEMPLATE_SIGNATURE) {
            return false;
        }
        return Arrays.stream(TEMPLATE_SIZES).anyMatch(s -> s == data.length);
    }

    /** Prefer combat-looking templates over pure spell/effect defs. */
    public static boolean looksLikeMonster(byte[] data) {
        if (!isEnemy(data)) {
            return false;
        }
        String txt = latin1(data).toUpperCase(Locale.ROOT);
        String[] monsterHints = {
                "RAT", "GHOST", "ORC", "TROLL", "SKELE", "DRAGON", "SPIDER", "WOLF",
                "BAT", "SLIME", "GOBLIN", "KNIGHT", "ZOMBIE", "DEMON", "DRAAK", "BOSS",
                "ATK", "DIE", "FRONT", "WALK"
        };
        for (String h : monsterHints) {
            if (txt.contains(h)) {
                return true;
            }
        }
        return false;
    }

    /** Hero class tables live in larger DATA1 blobs embedding multiple class names. */
    public static boolean isHero(byte[] data) {
        if (data.length < 2000 || data.length > 80_000) {
            return false;
        }
        String txt = latin1(data);
        int hits = 0;
        if (txt.contains("WARRIOR")) hits++;
        if (txt.contains("AMAZON")) hits++;
        if (txt.contains("WIZARD")) hits++;
        if (txt.contains("SORCERESS")) hits++;
        if (txt.contains("MONK")) hits++;
        if (txt.contains("PRIESTESS")) hits++;
        if (txt.contains("THIEF")) hits++;
        if (txt.contains("ASSASSIN")) hits++;
        return hits >= 3;
    }

    public static boolean isShop(Path path, byte[] data) {
        if (data.length != 128 && data.length != 256 && data.length != 664) {
            return false;
        }
        Path parent = path.getParent();
        if (parent == null) {
            return false;
        }
        String folder = parent.getFileName().toString().toUpperCase(Locale.ROOT);
        return folder.contains("TOWN");
    }

    public static boolean isQuest(byte[] data) {
        if (data.length < 396 || data.length > 500) {
            return false;
        }
        String txt = latin1(data);
        return txt.contains("ITEM_") || txt.contains("SPRITE_");
    }

    public static String latin1(byte[] data) {
        return new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    public enum Category {
        LOOT, HERO, SHOP, ENEMY, MAP, QUEST, OTHER
    }
}
