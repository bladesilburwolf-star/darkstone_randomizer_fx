package com.serifsystemworks.darkstone.math;

import java.util.Random;

/** Reference formulas (PC cross-check). Not injected into the PSX binary. */
public final class MathFramework {

    public static final int UNCAPPED_STAT_MAX = 999;

    public static final int[][] ORIGINAL_STAT_CAPS = {
            {999, 50, 100, 150},
            {999, 50, 100, 150},
            {50, 999, 150, 100},
            {50, 999, 150, 100},
            {150, 100, 999, 50},
            {150, 100, 999, 50},
            {100, 150, 50, 999},
            {100, 150, 50, 999}
    };

    private MathFramework() {}

    public static int calculateDamage(int baseDamage, int weaponDamage,
                                      int attackerStrength, int defenderAC,
                                      int attackerLevel, int weaponClassBonus, Random random) {
        double damage = baseDamage + weaponDamage;
        damage *= 1.0 + (attackerStrength / 100.0) * 0.5;
        damage *= 1.0 + (weaponClassBonus / 100.0);
        damage *= 1.0 + (attackerLevel - 1) * 0.02;
        damage *= 1.0 - Math.min(defenderAC * 0.01, 0.8);
        damage *= 1.0 + (random.nextDouble() * 0.3 - 0.15);
        return Math.max(1, (int) Math.round(damage));
    }

    public static long calculateNextLevelXP(int currentLevel) {
        if (currentLevel <= 1) return 100;
        if (currentLevel <= 10) return 100L * (1L << (currentLevel - 1));
        if (currentLevel <= 30) return 5000 + (currentLevel - 10) * 10000L;
        return 250000 + (currentLevel - 30) * 50000L;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
