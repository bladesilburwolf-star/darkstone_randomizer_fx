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
