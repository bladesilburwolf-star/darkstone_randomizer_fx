package com.serifsystemworks.darkstone.engine;

import com.serifsystemworks.darkstone.config.RandomizerConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Helpers for PSX dungeon tiers (8 lands x 4 levels).
 * Real FE/PSM mutation remains in {@link RandomizerEngine#randomizeDungeons}.
 */
public final class DungeonRandomizerEnhanced {

    public enum RoomDifficulty {
        EASY, MEDIUM, HARD, VERY_HARD, BOSS
    }

    private static final double[][] ROOM_WEIGHTS = {
            {0.6, 0.3, 0.1, 0.0, 0.0},
            {0.5, 0.4, 0.1, 0.0, 0.0},
            {0.4, 0.4, 0.2, 0.0, 0.0},
            {0.3, 0.4, 0.25, 0.05, 0.0},
            {0.2, 0.4, 0.3, 0.1, 0.0},
            {0.1, 0.3, 0.4, 0.15, 0.05},
            {0.05, 0.2, 0.4, 0.25, 0.1},
            {0.0, 0.1, 0.3, 0.4, 0.2}
    };

    private final LogSink log;
    private final Random random;

    public DungeonRandomizerEnhanced(LogSink log, long seed) {
        this.log = log != null ? log : LogSink.NULL;
        this.random = new Random(seed);
    }

    public RoomDifficulty pickDifficultyForLand(int landIndex) {
        int tier = Math.max(0, Math.min(7, landIndex));
        double[] weights = ROOM_WEIGHTS[tier];
        double roll = random.nextDouble();
        double c = 0;
        RoomDifficulty[] vals = RoomDifficulty.values();
        for (int i = 0; i < weights.length; i++) {
            c += weights[i];
            if (roll <= c) {
                return vals[i];
            }
        }
        return RoomDifficulty.HARD;
    }

    public static List<Integer> levelsForDungeonBlock(int blockIndex) {
        List<Integer> out = new ArrayList<>(4);
        int base = blockIndex * RandomizerConstants.PSX_LEVELS_PER_DUNGEON;
        for (int i = 1; i <= RandomizerConstants.PSX_LEVELS_PER_DUNGEON; i++) {
            out.add(base + i);
        }
        return out;
    }

    public List<Integer> shuffleDungeonBlocks() {
        List<Integer> blocks = new ArrayList<>();
        for (int i = 0; i < RandomizerConstants.PSX_LAND_COUNT; i++) {
            blocks.add(i);
        }
        Collections.shuffle(blocks, random);
        log.log("Dungeon blocks order: " + blocks);
        return blocks;
    }
}
