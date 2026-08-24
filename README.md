# Darkstone PSX Randomizer

JavaFX tool for **Darkstone** (PlayStation, 1999). Unpack `.PSM` archives, randomize gameplay data, install patched files, then rebuild a bootable image with **CDImg**.

In-place same-size patches keep the PSM TOC intact (no black-screen repacks).

## Requirements

- JDK 21+ and JavaFX SDK 26 (`C:\Program Files\Java\javafx-sdk-26.0.2`)
- **CDImg** (or similar) to rebuild the ISO after install

Edit `JFX_HOME` in `build.bat` / `run.bat` if needed.

## Workflow

1. Extract the CD to a folder (`SYSTEM.CNF`, `DATA1.PSM`, …).
2. `build.bat` → `run.bat`
3. Set **CD** and **Out**, **Unpack**, then **Randomize** (Copy to CD on).
4. Rebuild the ISO with **CDImg** and boot that image — folder boot is not supported.

## UI (v2.5)

Left sidebar:

- Folders, seed, **ranges** (stats / gold / levels / skills / weapon)
- **Character** modules, **World** modules, **Output** options
- Actions: Unpack · Scan · **Randomize** · Install only

One log panel on the right.

## Modules

| Module | Notes |
| --- | --- |
| Loot | 64-byte item pools |
| Hero stats | Attribute u16s in **stat range** |
| Starting gear / gold / spell books | Class starter table (DATA1) |
| Weapon stats | Min/max damage-like pairs in **weapon range** |
| Spell levels | Ranks near spell name tables in **skill range** |
| Skill levels | Isolated ranks in class blobs in **skill range** |
| Player levels | Starter level fields in **level range** |
| Enemy levels | Combat fields in templates in **level range** |
| Swap enemies | Same-size template shuffle |
| Shops / maps / quests | Optional; maps/quests can break progression |
| **Land/dungeon tiles** | FE objects + room templates in LAND* and QUEST0/1/2 LEVEL* |
| **Palettes (TIM)** | Hue-shift or shuffle RGB555 CLUTs on textures (DATA2/TOWN/LAND) |
| **Dungeons** | Shuffle FE map objects in LAND* (per-land or cross-land 56-byte) |
| Disable videos | Renames `.STR`/`.XA`/`*INTRO*`/`*MOVIE*` → `*.vidbak` |

Heuristic fields are best-effort from USA DATA1 layouts; keep ranges modest and test after each change set.

## Layout

```
src/com/serifsystemworks/darkstone/
  DarkstoneApp.java
  engine/          PSM I/O, randomize, install
  ui/              Modern sidebar UI + theme.css
PSM/               Reference archives
build.bat / run.bat
```
