package com.serifsystemworks.darkstone.config;

import java.util.Locale;

/** Shared constants — PSX structure + protected items. */
public final class RandomizerConstants {

    public static final String VERSION = "3.2.0";
    public static final int UNCAPPED_STAT_MAX = 999;

    /** PSX: 8 lands, 1 dungeon each, 4 levels (final dungeon 3). */
    public static final int PSX_LAND_COUNT = 8;
    public static final int PSX_DUNGEONS_PER_LAND = 1;
    public static final int PSX_LEVELS_PER_DUNGEON = 4;
    public static final int PSX_FINAL_LEVELS = 3;
    public static final int PSX_VARIANT_BANKS = 3;

    public static final int[] TEMPLATE_SIZES = {56, 304, 664, 948, 1252, 1332, 1562};

    public static final String[] PROTECTED_ITEM_PATTERNS = {
            "ITEM_CRISTAL", "ITEM_CLEF", "ITEM_KEY", "FALSEKEY",
            "ITEM_VIRTUAL", "ITEM_DROP", "ITEM_PICK", "ITEM_USE",
            "ITEM_DRAAK", "QFINAL", "MIRROIR", "PRISME", "COUFFIN",
            "ITEM_AMULET_KALIBA"
    };

    /**
     * Unique/named enemy templates and town NPCs that should never be pulled
     * into the general same-size shuffle pool in {@code randomizeEnemies}.
     * Mirrors the PC-side isProtectedMonster denylist (verified there against
     * real data: RATMANLORD1-4 and similar unique bosses aren't safe to mix
     * into the trash-mob pool even though they pass the general "monster"
     * classification). Not yet cross-checked against real PSX template text —
     * flag anything that turns out to be missing once real extracted data is
     * available.
     */
    public static final String[] PROTECTED_MONSTER_PATTERNS = {
            "BOSS", "QUEST", "TOWN", "SPELL", "PNJ", "BILL", "LICORNE", "POULET",
            "ENFANT", "DRAAK", "FINAL", "HORGAN", "SHADIRE", "ROLLAND", "LUX",
            "KIRGARD", "ZORAM", "ERALDUS", "FELDER", "GOLEMFIRERIK", "GOLEMICERIK",
            "APOTHICAIRE", "ARMURIER", "BANQUIER", "TAVERNIER", "GUIDE", "PROF",
            "CHARPENTIER", "MINEUR", "PAYSAN", "VALET", "PORTEUR", "FANTOME",
            "LORD", "RATMANLORD"
    };

    public static final int DEFAULT_STAT_MIN = 12;
    public static final int DEFAULT_STAT_MAX = 28;
    public static final int DEFAULT_GOLD_MIN = 50;
    public static final int DEFAULT_GOLD_MAX = 500;

    private RandomizerConstants() {}

    public static boolean isProtectedItem(String itemName) {
        if (itemName == null) return true;
        String u = itemName.toUpperCase(Locale.ROOT);
        for (String pattern : PROTECTED_ITEM_PATTERNS) {
            if (u.contains(pattern.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Scans a template blob's raw text for protected monster/NPC name patterns. */
    public static boolean isProtectedMonster(byte[] templateData) {
        if (templateData == null) return true;
        String u = new String(templateData, java.nio.charset.StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
        for (String pattern : PROTECTED_MONSTER_PATTERNS) {
            if (u.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
