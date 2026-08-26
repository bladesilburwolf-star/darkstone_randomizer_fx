package com.serifsystemworks.darkstone.engine;

/**
 * Attempts to raise class MAX stat caps in DATA1-style blobs to 999.
 * Uses little-endian u16 (PSX). Only patches when the expected PC-derived
 * cap pattern is found next to a class name string.
 */
public final class StatCapRemover {

    private static final int UNCAPPED_VALUE = 999;

    private static final String[] CLASS_NAMES = {
            "KNIGHT", "AMAZONE", "MAGE", "SORCIERE",
            "ASSASSIN", "ROGUE", "MOINE", "PRETRESS"
    };

    /** STR, MAG, DEX, VIT max from PC PCLASS export. */
    private static final int[][] EXPECTED_CAPS = {
            {999, 50, 100, 150},
            {999, 50, 100, 150},
            {50, 999, 150, 100},
            {50, 999, 150, 100},
            {150, 100, 999, 50},
            {150, 100, 999, 50},
            {100, 150, 50, 999},
            {100, 150, 50, 999}
    };

    private final LogSink log;

    public StatCapRemover(LogSink log) {
        this.log = log != null ? log : LogSink.NULL;
    }

    public boolean removeStatCaps(byte[] psmData) {
        if (psmData == null) return false;
        boolean modified = false;
        for (int classIndex = 0; classIndex < CLASS_NAMES.length; classIndex++) {
            int classNamePos = findAscii(psmData, CLASS_NAMES[classIndex]);
            if (classNamePos < 0) {
                continue;
            }
            log.log("StatCap: found " + CLASS_NAMES[classIndex] + " @ 0x" + Integer.toHexString(classNamePos));
            int statCapsPos = findStatCaps(psmData, classNamePos, classIndex);
            if (statCapsPos < 0) {
                log.log("  (no matching MAX pattern nearby)");
                continue;
            }
            for (int s = 0; s < 4; s++) {
                int off = statCapsPos + s * 2;
                int cap = readU16LE(psmData, off);
                int expected = EXPECTED_CAPS[classIndex][s];
                if (cap == expected && cap != UNCAPPED_VALUE) {
                    writeU16LE(psmData, off, UNCAPPED_VALUE);
                    log.log("  " + statName(s) + " " + cap + " -> " + UNCAPPED_VALUE);
                    modified = true;
                }
            }
        }
        return modified;
    }

    private int findStatCaps(byte[] data, int classNamePos, int classIndex) {
        int searchStart = classNamePos + CLASS_NAMES[classIndex].length();
        int searchEnd = Math.min(searchStart + 256, data.length - 8);
        for (int i = searchStart; i < searchEnd; i++) {
            boolean matches = true;
            for (int s = 0; s < 4; s++) {
                if (readU16LE(data, i + s * 2) != EXPECTED_CAPS[classIndex][s]) {
                    matches = false;
                    break;
                }
            }
            if (matches) return i;
        }
        return -1;
    }

    private static int findAscii(byte[] data, String text) {
        byte[] name = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= data.length - name.length; i++) {
            for (int j = 0; j < name.length; j++) {
                if (data[i + j] != name[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static int readU16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static void writeU16LE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static String statName(int i) {
        return switch (i) {
            case 0 -> "STR";
            case 1 -> "MAG";
            case 2 -> "DEX";
            case 3 -> "VIT";
            default -> "?";
        };
    }
}
