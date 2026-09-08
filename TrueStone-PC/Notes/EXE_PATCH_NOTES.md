# Darkstone.exe patches (GOG / 1.5-era PE32)

## Patched binary

`Darkstone_patched.exe` — drop-in rename over `Darkstone.exe` (keep a backup).

### Free-space checks removed

The engine calls `GetDiskFreeSpaceA`, multiplies sectors × bytes × free clusters in **32-bit**, then compares:

| Site (file off) | Check | Symptom |
|-----------------|-------|---------|
| `0x71474` | free ≥ ~29 MB | silent fail / odd startup |
| `0xA66D3` | free ≥ ~49 MB | **Caution: no more hard disk space (REQUIRED:50MB)** |
| `0xA6702` | free ≥ ~69 MB | related warning path |

On modern drives with huge free space the multiply **overflows** and looks like “not enough space”.

Patch: NOP the compare and **always jump** past the error path (19 bytes changed total).

### How to install

1. Copy `Darkstone.exe` → `Darkstone.exe.bak`
2. Copy `Darkstone_patched.exe` → `Darkstone.exe`
3. Launch as usual (compatibility mode optional now)

---

## Built-in seed (next phase)

The EXE already references:

| Path | Role |
|------|------|
| `config\seed.txt` | Read/write as **`%d\n`** (decimal integer + newline) |
| `***** SEED:%d ******` | Debug/log format when a run starts |
| `quest\%s.mtf` / `quest\*.mtf` | Quest pack archives |
| `data\monsterclass.dat` | Runtime monster combat |
| `data\itemobject.dat` | Runtime items |
| `data\pClass\pclass.txt` | Class table (loose) |

### Seed file format

```
123456789
```

One signed/unsigned 32-bit decimal integer, optional newline. No hex, no labels.

### Recommended rando install layout (no MTF required for stats)

```
<game>\
  Darkstone.exe          (patched)
  config\seed.txt        (integer seed)
  data\monsterclass.dat  (patched by randomizer)
  data\itemobject.dat
  data\pClass\pclass.txt
  data\pClass\...
  DATA.MTF               (optional inject for SCRIPT/PCLASS inside archive)
```

Point the PC randomizer **Out** at the game folder (or copy DAT + `data\pClass` after run).  
Combat changes come from **`data\*.dat`**, not from `MONSTER.TXT` alone.

### Launcher sketch (next)

```bat
@echo off
set SEED=%1
if "%SEED%"=="" set SEED=42
echo %SEED%> config\seed.txt
copy /Y seeds\%SEED%\monsterclass.dat data\monsterclass.dat
copy /Y seeds\%SEED%\itemobject.dat data\itemobject.dat
start "" Darkstone.exe
```

---

## Randomizer follow-ups

1. Default DAT output paths to `data\monsterclass.dat` / `data\itemobject.dat`
2. Write `config\seed.txt` from the UI seed (numeric hash or user int)
3. Optional: generate `quest\<seed>.mtf` packs when pipeline is on

## DCP-020 — Difficulty + branding (2026-09-07)

Applied on top of DCP-001 in `Darkstone_patched.exe`.

### Difficulty
- **5 stock menu slots** exist (`MDIFFICULTY1`…`5`).
- Selecting level **4 or 5** now **stores difficulty 3** (hardest of the original three bands).
- Menu tokens 4/5 renamed to `MDIFFICULTY0` (missing language key — often blank/odd label until `language.dat` is edited).

### Branding
- Default window title string `DARKSTONE` → **`TrueStone`** (same 9-byte length).
- Adjacent tag `???` → **`v18`**.

### Test checklist
1. Taskbar / window title shows **TrueStone** (or TrueStone-related).
2. New game difficulty list: only meaningful play is 1–3; 4–5 should play like 3.
3. Free-space dialog still gone (DCP-001).

### Revert
Restore `Darkstone.exe` from GOG verify / backup. JSON: `DCP/patches/020_difficulty_and_brand.json`.

## DCP-021 — FOV / camera + timing (2026-09-07)

| Offset | Change |
|--------|--------|
| `0x1108C0` | Camera base distance **20 → 28** (wider view / zoom-out) |
| `0x1108C8` | View angle **~60° → ~75°** (radians in float table) |
| `0x1108CC` | Timing scale **1/60 → 1/120** (experimental) |

### FPS reality check
No classic `Sleep(16)` / `cmp eax,16` frame limiter found in this EXE. Frame rate is probably **VSync / flip** limited.

**True FPS uncap:** dgVoodoo2 or GPU control panel → **VSync Off** (and optionally Fast Sync / mailbox).

If character/animation speed feels wrong after DCP-021, revert **only** `timing_1_120` (restore float `1/60` at `0x1108CC`).

### Test
1. Outdoor view feels wider / farther  
2. Camera pitch limits feel less tight  
3. FPS counter (if you use one) vs VSync on/off  

## DCP-022 — Uncap max stats (100 clamp removed)

Five sites of `cmp eax,100 / jle / mov eax,100` NOPed.

**With TrueStone 1.8.1:** enable **Uncap max stats (999)** so `MAX_STRENGTH/MAGIC/DEXTERITY/VITALITY` rows in PCLASS are 999. EXE clamp removal lets elixirs/level math exceed 100.

### Inventory (notes only)
Dungeon Siege 2 style (expandable / multi-bag) needs inventory layout RE — not patched yet. Possible later approaches: grid size constants, extra stash pages, or shared chest MTF.

### In-game configuration menu
Adding new rows to the stock Options UI needs language.dat + menu layout hooks. Near-term: TrueStone **DCP/QoL** toggles + `config\truestone.ini` for future loader. Controller support deferred.

## DCP-023 — Inventory expand (experimental)

- UI grid **8×6 → 10×6** (60 slots) at `0xB16B8`
- Index bounds **48 → 60** at three compare sites

**Risk:** if the game still allocates only 48 item records, filling beyond 48 can crash. Backup EXE. If unstable, revert DCP-023 only.


## DCP-023 — Inventory 8×8 (64 slots)

The earlier **10×6** experiment broke inventory because slot index math is hard-wired to **8 columns** (`and …,7` / `sar …,3`). Expanding columns without rewriting that math corrupts placement.

**Fix:** keep **8 columns**, expand **6 → 8 rows** = **64 slots**.

| Site | Change |
|------|--------|
| `0xB16B8` | grid push `10,6` → `8,8` |
| several `cmp …, 48/60` | → `64` |
| `0xB1912` / `0xB19FA` | center width `48` → `64` |

## DCP-024 — Native widescreen assist

| Site | Change |
|------|--------|
| `0x1108D8` | aspect **1.25 → 16/9** |
| `0x1108C0` | camera distance **32** |
| `0x1108C8` | FOV **~80°** |

Does **not** invent new DD mode list entries. Use highest in-game mode; dgVoodoo still optional for forcing 1920×1080 if the menu only offers 4:3.
