package com.serifsystemworks.darkstone.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class ScanResult {
    public int loot;
    public int heroes;
    public int shops;
    public int enemies;
    public int maps;
    public int quests;
    public int other;
    public final Map<Integer, Integer> sizeHistogram = new LinkedHashMap<>();

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("LOOT TABLES  : %d%n", loot));
        sb.append(String.format("HERO STATS   : %d%n", heroes));
        sb.append(String.format("SHOP TABLES  : %d%n", shops));
        sb.append(String.format("ENEMY STATS  : %d%n", enemies));
        sb.append(String.format("MAP HEADERS  : %d%n", maps));
        sb.append(String.format("QUEST TARGETS: %d%n", quests));
        sb.append(String.format("OTHER        : %d%n%n", other));
        sb.append("--- Size Distribution Top 20 ---%n".replace("%n", System.lineSeparator()));
        sizeHistogram.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(20)
                .forEach(e -> sb.append(String.format(" %5d bytes : %d files%n", e.getKey(), e.getValue())));
        return sb.toString();
    }

    public String oneLine() {
        return String.format("Scan complete: Loot=%d, Heroes=%d, Shops=%d, Enemy=%d, Maps=%d, Quests=%d",
                loot, heroes, shops, enemies, maps, quests);
    }

    @Override
    public String toString() {
        return sizeHistogram.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
