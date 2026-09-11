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

    /**
     * Verified this session against real extracted data (CHICKEN/POULET/
     * PNJ1/FPNJ1/SPELLGOLEMFIRE cross-checked byte-for-byte against known PC
     * MONSTER.TXT values — TOHIT/CHAAPP/CNTAPP matched exactly; PNJ1/FPNJ1's
     * LMIN/AC/DMIN/DMAX matched PC's APOTHICAIRE/ARMURIER/BANQUIER exactly).
     * Monster/NPC combat stat records are 474 bytes each, packed N-in-a-row
     * per land/quest file (blob size is always an exact multiple of 474).
     * Found 669 such records (137 unique names) across 115 of 144 real PSM
     * files in this session's survey.
     */
    public static final int MONSTER474_SIZE = 474;
    public static final int OFF474_LMIN = 68;
    public static final int OFF474_LMAX = 70;
    public static final int OFF474_AC = 72;
    public static final int OFF474_TOHIT = 74;
    public static final int OFF474_DMIN = 76;
    public static final int OFF474_DMAX = 78;
    public static final int OFF474_CHAAPP = 176;
    public static final int OFF474_CNTAPP = 178;
    /**
     * LEVEL — a SEPARATE field from LMIN, sitting 2 bytes before it. Verified
     * this session against real PSX data: CHICKEN and SPELLGOLEMFIRE both read
     * exactly -1 at this offset, matching PC's real MONSTER.TXT LEVEL column
     * for CHICKEN exactly. LMIN/LMAX are a raw combat-difficulty scalar (goes
     * into the hundreds); LEVEL is the small value the game's XP system
     * actually compares against player level. Root-caused from a real
     * gameplay report: hard goblins gave ~0 XP after monster-power
     * randomization while easier bats gave plenty, because only LMIN-based
     * combat stats were ever touched — LEVEL stayed at its untouched vanilla
     * value, decoupling combat difficulty from XP reward.
     */
    public static final int OFF474_LEVEL = 66;

    /**
     * Player class baseline stats — verified this session against real PC
     * PCLASS.TXT values, cross-referenced from DATA1.PSM (found via searching
     * for all 8 class names KNIGHT/AMAZONE/MAGE/SORCIERE/ASSASSIN/ROGUE/
     * MOINE/PRETRESS together). 8 records of 344 bytes each, 4-byte header.
     * All 6 BASE_* + 4 MAX_* fields matched 8/8 classes exactly:
     *   MAX_STRENGTH@0, MAX_MAGIC@2, MAX_DEXTERITY@4, MAX_VITALITY@6,
     *   BASE_STRENGTH@8, BASE_MAGIC@10, BASE_DEXTERITY@12, BASE_VITALITY@14,
     *   BASE_LIFE@16, BASE_MANA@18 (all relative to each 344-byte record start).
     * Class name string sits at relative offset 128 within the record
     * (e.g. "KNIGHT1"), useful for locating/verifying this table by content
     * rather than by file path, since PSM extraction indices aren't stable
     * file names.
     */
    public static final int PLAYERCLASS_HEADER_SIZE = 4;
    public static final int PLAYERCLASS_RECORD_SIZE = 344;
    public static final int PLAYERCLASS_COUNT = 8;
    public static final int OFF_PC_MAX_STR = 0;
    public static final int OFF_PC_MAX_MAG = 2;
    public static final int OFF_PC_MAX_DEX = 4;
    public static final int OFF_PC_MAX_VIT = 6;
    public static final int OFF_PC_BASE_STR = 8;
    public static final int OFF_PC_BASE_MAG = 10;
    public static final int OFF_PC_BASE_DEX = 12;
    public static final int OFF_PC_BASE_VIT = 14;
    public static final int OFF_PC_BASE_LIFE = 16;
    public static final int OFF_PC_BASE_MANA = 18;
    public static final String[] PLAYERCLASS_NAMES = {
            "KNIGHT", "AMAZONE", "MAGE", "SORCIERE", "ASSASSIN", "ROGUE", "MOINE", "PRETRESS"
    };

    /** True if this blob is the player-class baseline stat table (8x344-byte records + 4-byte header). */
    public static boolean isPlayerClassTable(byte[] data) {
        if (data == null || data.length != PLAYERCLASS_HEADER_SIZE + PLAYERCLASS_COUNT * PLAYERCLASS_RECORD_SIZE) {
            return false;
        }
        String text = latin1(data).toUpperCase(Locale.ROOT);
        for (String name : PLAYERCLASS_NAMES) {
            if (!text.contains(name)) return false;
        }
        return true;
    }

    /**
     * Equipment/item stat table — 394-byte records (same size as PC's
     * ITEMOBJECT.DAT, but internally laid out differently). Verified this
     * session against real PC OBJECT.TXT data (hundreds of real items,
     * cross-referenced from DATA1.PSM):
     *   name string at relative offset 206 (NOT offset 0 like PC's format)
     *   DMIN @ 390 (u16) — 100% match, wide real variance confirmed
     *   AC   @ 392 (single byte, not u16) — 100% match on all nonzero armor
     * DMAX and LEVEL were searched for exhaustively (every offset, both u16
     * and single-byte, plus a DMAX-DMIN delta hypothesis) and never found —
     * a full byte-by-byte dump of a known item's record turned up neither
     * value anywhere. They likely live in a different structure entirely, or
     * are computed at runtime rather than stored. Only DMIN/AC are patched;
     * DMAX/LEVEL are left completely untouched rather than guessed at.
     */
    public static final int ITEM_RECORD_SIZE = 394;
    public static final int OFF_ITEM_NAME = 206;
    public static final int OFF_ITEM_DMIN = 390;
    public static final int OFF_ITEM_AC = 392;

    /** True if {@code data.length} is a clean multiple of 474 and every 474-byte
     *  chunk starts with a plausible ASCII name — i.e. a monster/NPC roster blob. */
    public static boolean isMonsterRosterBlob(byte[] data) {
        if (data == null || data.length == 0 || data.length % MONSTER474_SIZE != 0) {
            return false;
        }
        int n = data.length / MONSTER474_SIZE;
        if (n > 50) return false; // sanity guard against accidental large-file matches
        for (int i = 0; i < n; i++) {
            int off = i * MONSTER474_SIZE;
            byte b0 = data[off];
            if (!((b0 >= 'A' && b0 <= 'Z'))) {
                return false;
            }
        }
        return true;
    }
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
    /**
     * UI / language tables embed class names AND STMN_/STR_ keys.
     * Never treat those as hero stat blobs (patching them shows raw keys in menus).
     */
    public static boolean isUiStringTable(byte[] data) {
        if (data == null || data.length < 100) return false;
        String txt = latin1(data);
        return txt.contains("STMN_") || txt.contains("STR_EQUIP") || txt.contains("STR_BAG")
                || txt.contains("STR_VALID") || txt.contains("STMN_NEWG");
    }

    public static boolean isHero(byte[] data) {
        // Real class tables are compact; the ~27–32 KB language blobs are NOT heroes.
        if (data.length < 1500 || data.length > 12_000) {
            return false;
        }
        if (isUiStringTable(data)) {
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
