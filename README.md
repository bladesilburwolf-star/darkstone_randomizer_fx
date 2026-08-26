# Darkstone PSX Randomizer

**v3.4** — JavaFX tool for *Darkstone* (PlayStation USA). Same-size PSM patches, multi-BIN disc build, SOTN-style presets.

## Requirements

- JDK 21+ and JavaFX SDK 26 (`C:\Program Files\Java\javafx-sdk-26.0.2`)
- USA disc extract + `.cue` (multi-track BINs)

Edit `JFX_HOME` in `build.bat` / `run.bat` if needed.

## Workflow

1. `build.bat` → `run.bat`
2. Set **CD** (extract), **Out** (work folder), **CUE** (for disc build), optional **BIN out**
3. **Unpack** → **Randomize**
4. Randomize will: patch → repack clean `*.PSM` → optional **new BIN/CUE** → optional **delete `*_unpacked`**
5. Boot the new CUE in DuckStation / Beetle

No separate “Install to CD” and no seed-named CUE export. Disc build is integrated.

## Presets

| Preset | Intent |
|--------|--------|
| **General** | Heroes, gear, light dungeons, palettes. Loot **off**. |
| **Advanced** | + enemies, shops, music. Loot still **off**. |
| **Chaotic** | Cross-land / cross-interior flags, wider ranges. Loot still **off**. |

## World structure (PSX)

- **8 lands**, **1 dungeon per land**, **4 levels per dungeon** (final dungeon **3** levels)
- **3 variant banks** of land/quest data (`_0` / `_1` / `_2` style tiers)
- Atomic dungeon unit for future rando = **4-level block**, not single floors

See also `HEX_LIST.md`, `CROSSWALK_PC_PSX.md`.

## Known limitations (read before enabling loot)

### Starting gear **XOR** quest/loot item shuffle

Quest weapons / unique dungeon gear must **not** be written into **starting gear** slots.

If loot (QUEST$ `ITEM_*`) and starting gear both run, the starter table can receive a **quest-bound weapon**. The game then **crashes** on new game / first equip.

**Rule: enable only one**

| Option | Safe with |
|--------|-----------|
| Starting gear / gold / books | Hero stats, weapons stats, dungeons (layout), palettes, enemies |
| Loot / QUEST$ item names | Hero stats, etc. — **not** starting gear |

Presets keep **loot off** on purpose. If you turn loot on, turn **starting gear** (and related starter tables) **off**.

### Dungeon doors (v3.4)

**Dungeon doors** replaces the old interiors toggle: cross-land shuffle of fixed-count
structural FE props on LAND packs. Full entrance-table rando still future work.

### Dungeon interiors (old)

Interior FE shuffle did **not** produce reliable interior layout changes.

What works better today:

- **Overworld / LAND** FE-style tile mix (when it behaves like land-tile shuffle)
- Enemy / hero **stats** independent of layout

What we still need (later):

1. Find **hex / table for dungeon entrances** on the overworld
2. Shuffle entrances the old-fashioned way (entrance A → dungeon B)
3. And/or shuffle **overworld** only
4. Optional **coupled** vs **decoupled** entrances (SotN-style):  
   - coupled = two-way links stay consistent  
   - decoupled = exit may not return to the door you used

Until entrance tables are mapped, treat **interiors** as experimental / ineffective.

### Other safety

- Never shuffle **crystals, keys, Draak gate, VIRTUAL** tokens as free loot
- Same-size PSM patches only (TOC id/hash preserved)
- Disc build: replacement file size ≤ ISO extent

## Modules (summary)

| Module | Status |
|--------|--------|
| Hero stats / ranges | Works |
| Starting gear / gold / spells | Works — **conflicts with loot** |
| Loot (QUEST$) | Works only if gear is off; crash if both |
| Enemies / levels | Works |
| Palettes / land colors | Works |
| LAND / outdoor tiles | Partial / useful |
| Dungeon doors | Structural FE cross-land (v3.4) |
| Enemy types (MO_) | Per-land encounter names |
| AC / hit / speed | Sparse combat field bands |
| Dungeon interiors | Removed — was ineffective |
| Multi-BIN disc build | Integrated |
| Auto-delete unpacked | Optional (default on) |

## Build log

Failed compiles write `build_error.log` next to `build.bat`.

## PC / MTF

Campaign `DATA.MTF` needs real compress+pack. Shelved; see `CROSSWALK_PC_PSX.md` and `MtfTool/`.
