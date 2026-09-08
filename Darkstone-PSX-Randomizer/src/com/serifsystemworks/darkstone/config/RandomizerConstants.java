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
}
