# Darkstone PC — data notes (for PC randomizer port)

Folder: `artifacts/PC/` (updated PC install / editor export).  
This data is far more structured than the PSX PSM blobs and is the natural source for a **PC port** of the randomizer. Many string IDs also align with PSX `ITEM_*` / `MO_*` names.

---

## Layout

```
PC/
  ITEMOBJECT.DAT      ~247 KB — item definitions (binary)
  MONSTERCLASS.DAT    ~120 KB — monster definitions (binary)
  OBJ3D.DAT           ~5 KB  — 3D object name table
  SND.DAT             ~8 KB  — sound name table
  TRISPRITE.DAT       ~26 KB — UI / sprite names
  EDITOR/             .O3D editor meshes
  PATTERN/            UI patterns / TGA
  PCLASS/             **text + Excel exports (best for tooling)**
    MONSTER.TXT       193 monsters — LEVEL LMIN LMAX DMIN DMAX AC TOHIT SPEED …
    OBJECT.TXT        624 objects — DMIN DMAX AC LEVEL STR DEX MAG VIT DUR
    PCLASS.TXT        player class stats (MAX/BASE strength, magic, …)
    WCLASS.TXT        weapon kinds (epee1main, arcMD, torche, …)
    EFFECTCLASS.TXT   spells/effects (heal1, nova, inferno, …)
    MONSTEREXPORT.TXT class export dump
    CLASSES/          ARMES_*.XLS per archetype
    XLS/              PCLASS / WCLASS / EFFECTCLASS workbooks
```

---

## Text tables (preferred edit target)

### `MONSTER.TXT` (tab-separated)

| Column | Meaning (from header) |
|--------|------------------------|
| key | Monster id (`CHICKEN`, `WEREWOLF`, `BOSSX`, `AMAZA1`, …) |
| LEVEL | Template level flag (−1 common) |
| LMIN / LMAX | Level range |
| DMIN / DMAX | Damage range |
| AC | Armor class |
| TOHIT | To-hit |
| SPEED / ATTFRE / CHAAPP / CNTAPP / ATTSPD | Speed / attack timing |

**193 rows.** Ideal for PC enemy level/damage shuffle with real column semantics (unlike PSX size-band heuristics).

### `OBJECT.TXT`

| Column | Meaning |
|--------|---------|
| key | Item id (`ITBOWAERON`, `ITCROIX`, `ITEM_CRISTAL1`, …) |
| DMIN / DMAX | Damage |
| AC | Armor |
| LEVEL | Req / item level |
| STR DEX MAG VIT | Stat requirements or bonuses |
| DUR | Durability |

**624 rows.** Direct PC loot / weapon stat randomizer.

### Overlap with PSX

~56+ PSX `ITEM_*` strings match or map cleanly to PC keys (`ITEM_AMULET_KALIBA` ↔ `ITKALIBA`, `ITEM_CRISTAL1`, `ITEM_PICK` / `DROP` / `USE`, etc.).  
PC exports can **label** PSX QUEST$ slots and validate protected key lists.

---

## Binary DAT sketches

### `ITEMOBJECT.DAT`

```
+0  u16  version = 1
+2  u32  count   ≈ 627
+6  …    padding / flags
+14 fixed records, stride **394** bytes
    name at start of record (e.g. "ITBOWAERON", null-padded)
```

Parsed **606** consecutive `IT*` / `ITEM*` records at stride 394 from offset 14.

### `MONSTERCLASS.DAT`

```
+0  u16  version = 1
+2  u16  count   ≈ 205 (0xCD)
+8  name "AMAZA1", …
    stride **584** bytes between AMAZA1 → AMAZA2
```

Names include `STAMAZONE`, attack sound refs (`BOWFIRE`, `SWING`), etc.

### Other DATs

| File | Role |
|------|------|
| `OBJ3D.DAT` | Short name list (`AMULET`, `AXE2`, `BOOK`, …) |
| `SND.DAT` | SFX ids (`BATATK`, `BEHODIE`, `ABSORB2`, …) — PC audio rando |
| `TRISPRITE.DAT` | UI sprite ids |

Exact numeric field offsets inside the 394/584-byte records still need a full struct pass; **TXT exports are enough to ship a first PC randomizer**.

---

## PC randomizer plan (proposed)

| Module | Source | Action |
|--------|--------|--------|
| Enemy stats | `MONSTER.TXT` / `MONSTERCLASS.DAT` | Shuffle or range-roll LMIN/LMAX, DMIN/DMAX, AC |
| Item stats | `OBJECT.TXT` / `ITEMOBJECT.DAT` | Shuffle damage/AC/reqs among weapons/armor tiers |
| Spells | `EFFECTCLASS.TXT` | Optional effect remap |
| Player classes | `PCLASS.TXT` | Base/max stat shuffle (mirrors PSX hero module) |
| SFX | `SND.DAT` | Name/content shuffle (optional) |

**UI:** reuse JavaFX shell (presets, seed, bronze/purple theme); swap engine backend from PSM → DAT/TXT.  
**Shared:** seed format, preset names (General / Advanced / Chaotic), export `darkstone_pc_seed_<seed>.txt`.

---

## Value back to PSX

1. Authoritative **item & monster name lists** for safer QUEST$ protect-lists.  
2. Real **stat column meanings** to refine PSX heuristic ranges.  
3. Quest item French ids (`ITCROIX`, `ITCOUPE`, `ITFEE`) ↔ `ITEM_DPQ*` mapping table.

---

*PC dump inspected alongside PSX randomizer v3.x — ready for a `pc` engine package when you want implementation.*


## LAND/ (overworld props)

96 × `.O3D` meshes (grass, barriers, cottages, doors, columns…).  
Same-size shuffle (~74 files in groups of 4+) = visual clutter rando; filenames stay so references remain valid.

Install: `Darkstone\data\LAND\` override or rebuild into `DATA.MTF`.

## Quest MTF vs campaign

Custom quest packs in `Darkstone\quest\*.MTF` **replace** the normal campaign flow for that session — you will not get the main crystal questline from those.  
PC campaign uses **two quests per land**; full quest rando needs `SCRIPTS/*.SPT` from unpacked `DATA.MTF`, not only LAND meshes.


## SCRIPT/ (campaign quests)

25 files (mostly `.SPT`):

| Pattern | Role |
|---------|------|
| `DP0_QUEST*.SPT` … `DP4_*` | Side quests; `LAND {0..6}` |
| `FC3_*` … `FC6_*` | Further land quests |
| `QUESTFINAL.SPT` | Draak finale — `LAND {7}` — **do not land-shuffle** |
| `TOWN.SPT` / `ENTREE.SPT` | Town / entrance logic — protected |
| `BONUSROOM.SPTT` | Bonus room |

Structure: `QUEST { QUESTNAME, KEY, LAND, ENTRANCE, ROOM, OBJECT { KEY, PARENT }, … }`.  
PC: about **two quests per land**. Crystals appear as `ITEM_CRISTAL1`…`7` in scripts.

Randomizer: **Quest LAND ids** reshuffles `LAND {n}` among side quests; **Quest rewards** rewrites safe `PARENT {…}` (skips KEY/CLEF/CRISTAL/VIRTUAL).

## TOWN/

Town layout: `TOWN.TXT` placement list + `TOWN.B3D` / `.BRM` / `.CLD` + `PIECES/*.O3D` (shops, walls, floors).  
Shop buildings: `ARMURIER`, `BANQUE`, `MAGICIEN`, `BONAVENTURE`, etc.

---

## MTF rebuild size growth (v fix 2026-09)

**Symptom:** Replacing entries (especially class / DAT / TXT) then rebuilding `DATA.MTF` makes the archive or individual files grow by a few percent; game crashes on boot.

**Causes:**
1. Rebuild always re-compressed replacements with our LZ encoder, which often produces **larger stored payloads** than retail.
2. `TsvTable.save` used to pad numeric columns to width 6 and widen key columns — **decompressed TXT grew** even when values were unchanged in meaning.
3. Campaign `DATA.MTF` is sensitive to layout; grown archives are not reliable.

**Fixes in tree:**
- `MtfArchive.rebuild` — size-aware encode: keep uncompressed entries uncompressed; if recompress exceeds original stored size, fall back to uncompressed store; returns `RebuildReport` with warnings.
- `MtfArchive.rebuildSameSize` — refuses any decompressed size change (use for MONSTERCLASS.DAT / ITEMOBJECT.DAT).
- MTF Explorer — confirms on size mismatch; offers same-size-only rebuild mode; logs growth.
- `TsvTable.save` — no forced numeric padding; preserves original key cell width.

**Workflow for class DAT inside DATA.MTF:**
1. Extract entry → edit **in place** (same byte length) → Replace → Rebuild with **same-size** mode.
2. Prefer patching loose `MONSTERCLASS.DAT` next to the game (PC randomizer DAT path) instead of round-tripping through MTF when possible.

## TsvTable same-size (follow-up)

Even after removing `%6s` padding, files could still grow because `setInt` wrote
more digits than the original cell (`5` → `25`). MTF Replace then correctly
warned (+1.2% on MONSTER.TXT).

Now `TsvTable`:
- stores per-cell original widths
- `setInt` right-pads to that width
- preserves CRLF/LF
- `save(path, true)` **throws** if byte length ≠ original

PC randomizer logs a hard warning and only then writes a grown file for **loose
PCLASS** use — still do not Replace grown TXT into DATA.MTF.

Always run `build-pc.bat` after updating so `out-pc` classes match source
(the previous fix zip shipped a stale `TsvTable.class`).

## Real DATA.MTF findings (retail GOG sample)

- 6679 entries, ~83 MB
- `DATA\PCLASS\MONSTER.TXT`: decomp **13376**, stored 3695, AE BE compressed, **CRLF**
- Numeric cells are mostly width **1–2** (559 single-digit cells). Range rolls that write `25` into a `1` cell grow the file.
- Fix: `TsvTable.Row.setInt` **clamps** to the original cell width so save stays 13376.
- No-change MTF rebuild is byte-identical size; same-content replace may shrink slightly (our compressor is a bit tighter than retail).

## Shared-offset TOC bug (boot crash root cause)

Retail `DATA.MTF` has **761 TOC aliases**: multiple entries share one file offset
(e.g. several `DPQGODG*.CDF` → same blob). Old rebuild treated those as
`storedSize=0`, wrote empty payloads, and collapsed offsets — archive looked
plausible in the explorer but **crashed before the menu**.

Fix: group by offset, store each blob once, point all aliases at the same new
offset. No-op rebuild is now **byte-identical** to retail (verified on GOG
DATA.MTF). Same-size decomp replacements extract correctly after rebuild.


## v1.4.0-pc — completion pass

- **DATA.MTF boots** after shared-offset TOC fix + same-size PCLASS inject.
- Checkbox **Inject into DATA.MTF**: after randomize, backs up and rebuilds campaign MTF with MONSTER/OBJECT/PCLASS (skips any file that grew).
- **DAT patch** still on by default (actual combat stats).
- TsvTable clamps cell widths so TXT stays same-size for MTF.
- Presets enable inject by default.

### Recommended PC workflow

1. Point **Game** at GOG/install folder (contains `DATA.MTF` and `PCLASS/`).
2. Optional **Out** folder for non-destructive output.
3. Preset General / Advanced / Chaotic → Randomize.
4. With inject on: DATA.MTF is updated in place (backup under mtf_backups).
5. Copy any `MONSTERCLASS.DAT` / `ITEMOBJECT.DAT` from Out into the game if Out was set.
6. Boot.

### Still optional / future

- Enemy *type* shuffle (name/model swap), shop tables, music banks
- Quest SPT inject into DATA.MTF SCRIPT paths
- Stronger DAT field coverage beyond LMIN/LMAX/AC/TOHIT/DMIN/DMAX

## v1.5.0-pc — enemy types, shops, campaign pipeline, UI merge

### New options
- **Enemy type shuffle** — swaps full identities (key + stats) among non-protected combat monsters (LEVEL ≥ 1). Bosses, quest faces, town NPCs protected.
- **Shop / loot PARENT** — uses quest reward path: non-key OBJECT PARENT reassignment in SPT (shop-like + quest rewards).
- **Campaign pipeline** — extract PCLASS+SCRIPT from DATA.MTF → randomize → write `Out/seeds/<seed>/DATA_<seed>.MTF`.
- **Multi-seed** — generate up to 32 archives per run.
- **Copy to quest/** — optional copy under game `quest/DATA_<seed>.MTF`.

### UI
- Tabs: **Randomizer** | **MTF Explorer** (merged tool).
- Paths: Game, Out, **MTF**.

### Main-menu seed
Not possible without EXE patching. Use pipeline outputs and swap `DATA.MTF` (or drop into `quest/` for pack-style installs if your setup loads them).

### Quest SCRIPT inject
Same-size SPT only. Pipeline packs matching `DATA\SCRIPT\*.SPT` when sizes match retail.

## v1.5.1-pc — “nothing changed” fix

Root causes:
1. **Combat = DAT**, not MONSTER.TXT. Pipeline used a work folder without DAT → skip.
2. **PCLASS write used LF** on CRLF files → size change → MTF inject skipped → no class change.

Fixes: `datSourceRoot`, CRLF-safe PCLASS, clearer INSTALL log, DAT patch counts.

See `SEED_ENGINE_RE.md` for menu-seed RE plan.

## v1.5.2-pc — TsvTable width clamp (real "nothing changes" fix)

MONSTER.TXT cells had **per-cell** widths (often 1 digit). `setInt` clamped shuffled
values to 0–9, so stats barely moved.

Fix: after load, expand each numeric column to the **column max width**, borrowing
trailing spaces from the 32-char key field. Line length and file size unchanged;
LMIN can now be up to 4 digits (e.g. 2500), DMIN shuffle actually moves values.

## v1.6.0-pc — Seed launcher

### UI
- **Install Seed** — copies `seeds/<seed>/` into the game (`config\seed.txt`, `data\*.dat`, `data\pClass\pclass.txt`)
- **Launch** — starts `Darkstone.exe` in the game folder
- **Install + Launch** — both
- **Install replaces DATA.MTF** — optional; backs up to `DATA.MTF.launcher_bak`

### CLI
```bat
DarkstoneRando.bat ABC123 "C:\GOG\Delphine Software"
DarkstoneRando.bat ABC123 "C:\GOG\Delphine Software" "D:\rando-out"
set REPLACE_MTF=1 && DarkstoneRando.bat ABC123 "C:\GOG\Delphine Software"
```

### Flow
1. Randomize (creates `Out/seeds/<seed>/` with manifest + data)
2. Install Seed or Install + Launch
3. Use patched EXE so free-space check does not block

## v1.7.0-pc — Bloodstained-style Start Run

New first tab **Start Run**:
- Large **SEED** field + live `config\seed.txt` integer preview
- Presets (General / Advanced / Chaotic)
- Grouped toggles: Enemies · Items & Shops · Characters · Quests & World · Install
- **START RANDOMIZED GAME** = Randomize → package seed → install into game → launch EXE

Advanced tab keeps the old full sidebar + MTF Explorer.

This is the Bloodstained approach as an external front-end (true in-EXE menu still needs deeper hooks).
## TrueStone branding
App name: **TrueStone** — TrueRandomizer for Darkstone (v1.7.1-pc).

## v1.7.2-pc — launch crash / FX thread / CSS

- Fixed JavaFX CSS: removed `repeating-linear-gradient` (invalid for `-fx-background-color`).
- Fixed `IllegalStateException: Not on FX application thread` when Install+Launch updated the status label.
- Launch on Windows now uses `cmd /c start /D <game> Darkstone.exe` (detached), not a Java child process.
- Install backs up existing `data/pClass/pclass.txt` to `pclass.txt.truestone_bak`.

### If Darkstone dies before the menu after Install

1. Restore `DATA.MTF` from `DATA.MTF.launcher_bak` or `BACKUP\`.
2. Delete or restore `data\pClass\pclass.txt` (try without loose pclass first).
3. Launch `Darkstone.exe` manually from Explorer (not from TrueStone) to isolate.
4. Confirm free-space patch is still on the EXE you launch.

## v1.8.0-pc — Early-game safety caps

**Early-game safety caps** (on by default for General/Advanced; off for Chaotic):
- Monsters with LEVEL/LMAX ≤ 8: DMAX ≤ 28, AC ≤ 45
- Applied to MONSTER.TXT and MONSTERCLASS.DAT
- Low-level items softened similarly

Launch still only looks for `Darkstone.exe` in the **game root** (not under `data/`).


## Nexus / DCP

TrueStone is aimed at Nexus release. EXE redistributables: prefer patch JSON (`DCP/patches`) over full EXE upload.
See project `DCP_ROADMAP.md` and `NEXUS_README.md`.

## v1.8.1-pc — Uncap max stats

- Checkbox **Uncap max stats (999)** (default on)
- Requires **DCP-022** EXE (100-clamps NOPed) for elixirs/runtime to exceed 100
- Inventory expansion: not implemented (DS2 multi-bag approach planned)

## v1.9.0-pc — Speeds, land tiers, inventory

### Enemy speeds
**Randomize enemy speeds** — rolls `SPEED`, `ATTSPD`, `ATTFRE` on combat rows (not NPCs/bosses).

### Types per land/dungeon tier
With **Enemy type shuffle** + **Types per land/dungeon tier**, identities shuffle inside level bands (1–12, 13–25, …) so early lands do not get endgame templates wholesale.

### Inventory
DCP-023 expands grid to **10×6 (60)** — experimental; report crashes.
