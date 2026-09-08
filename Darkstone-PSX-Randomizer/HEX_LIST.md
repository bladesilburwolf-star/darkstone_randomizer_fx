# Darkstone PSX — Hex List & Format Findings

Reverse-engineering notes for **Darkstone (PlayStation, USA)** used by the Serif System Works randomizer (v3.x).  
All multi-byte integers are **little-endian** unless noted.

---

## 1. PSM archive format

Every `.PSM` is a blob container with an interleaved TOC.

```
Offset  Size   Field
------  ----   -----
0x0000  u32    tableWordCount   // number of u32 entries in the table (NOT blob count)
0x0004  u32[]  table[tableWordCount]
               even indices (0,2,4…): id / hash (MUST be preserved)
               odd  indices (1,3,5…): file offset of a blob (absolute from start of PSM)
…       bytes  blob payload region (offsets point here)
```

### Rules verified on real USA dumps

| Rule | Detail |
|------|--------|
| Blob count | ≈ `tableWordCount / 2` (only odd slots are offsets) |
| Offset validity | `0 < offset < fileSize` |
| Blob end | Next sorted unique offset, or EOF |
| Black-screen bug | Old tools rewrote `tableWordCount` as blob count and dropped even id/hash slots → boot failure |
| Safe edit | **In-place same-size patch**; keep full header + even slots identical |
| Round-trip | Zero-edit unpack → repack is **byte-identical** when metadata is preserved (`_source.psm` + `_psm_meta.txt`) |

### Example headers (USA)

| File | Size | `tableWordCount` | First 8 bytes (hex) |
|------|------|------------------|---------------------|
| `TOWN.PSM` | 1 259 306 | 149 (`0x95`) | `95 00 00 00 c0 d6 d3 0d` |
| `DATA1.PSM` | 1 584 400 | 140 (`0x8c`) | `8c 00 00 00 f7 a2 10 2b` |
| `LAND32_0.PSM` | 1 236 772 | 321 (`0x141`) | `41 01 00 00 73 94 08 b2` |
| `LEVEL1.PSM` (QUEST0) | 1 151 954 | 182 (`0xb6`) | `b6 00 00 00 70 ff b3 30` |
| `AL0_Q0.PSM` (QUEST$) | 10 730 | 20 (`0x14`) | `14 00 00 00 40 06 a1 94` |

---

## 2. Map / dungeon objects (`0xFE`)

| Signature | Size | Role |
|-----------|------|------|
| First byte `0xFE` | **56** | Map header / tile object (very common) |
| First byte `0xFE` | variable (112…1000+) | Larger map / prop / encounter objects |

### LAND\* (overworld)

- Each `LAND32_0`…`LAND39_*` typically has **48 × 56-byte** `FE` headers plus larger `FE` blobs.
- Shuffling **same-size `FE` groups within one archive** rearranges terrain tiles (SotN “third castle” style).
- **Cross-pack** mode pools all 56-byte `FE` headers across lands and mixes them (much wilder layouts).

### QUEST LEVEL\* (dungeon interiors)

- `QUEST0` / `QUEST1` / `QUEST2` each hold `LEVEL1.PSM`…`LEVEL28.PSM` (difficulty tiers).
- QUEST0 LEVEL1 ≡ QUEST1 LEVEL1 (identical on USA); QUEST2 differs slightly.
- Per level: **~36–40 `FE` objects**, **9 × 664-byte** room/prop templates, plus sizes 948 / 1332 / 1562 / 304 / 1252…
- `LEVEL29` / `LEVEL30` / `DRAAK` live under **MAIN DATA** (final dungeon / dragon).

### Interior template sizes (shuffle-safe when ≥2 copies in one pack)

```
664, 948, 1332, 1562, 304, 1252, 56 (FE header)
```

---

## 3. TIM textures & CLUT (palettes)

PlayStation TIM magic at any alignment (land packs are often **not** 4-byte aligned):

```
Offset  Size  Field
------  ----  -----
+0x00   u32   0x00000010          // TIM magic
+0x04   u32   flags
              bits 0–2: bpp (0=4-bit, 1=8-bit, 2=16-bit, 3=24-bit)
              bit 3:    has CLUT
+0x08   …     CLUT block if has CLUT:
              u32 clutLen; u16 x,y; u16 w,h; then w*h × RGB555 colors
              RGB555: R 0–4, G 5–9, B 10–14, STP bit 15
```

| Location | Notes |
|----------|--------|
| `DATA2` / `TOWN` | Many whole-bin TIMs (`flags` 8 or 9 → 16 / 256 color CLUT) |
| `LAND*` large bins (e.g. `0319.bin`) | Embedded TIM strip; 16× 256-color CLUTs observed |
| Direct 16-bit TIM (`flags=2`) | No CLUT — skip for palette rando |

**Palette rando:** hue-shift or shuffle CLUT entries; keep index 0 when `0x0000` (transparency).

---

## 4. Heroes, stats, gear (DATA1)

| Finding | Detail |
|---------|--------|
| Hero class strings | Live in **large** DATA1 blobs (≈2–80 KB), not the 200–512 byte files |
| Starting gear / `ITEM_*` | Concentrated in **`0025.bin`**-style tables (≈32-byte slots) |
| Spell name lists | e.g. `0021.bin` |
| Loot “64-byte pools” | Almost empty / ineffective — **not** the real loot path |

Stat / level / skill / weapon heuristics: u8/u16 fields in plausible ranges, avoiding ASCII neighbors.

---

## 5. Enemies

| Size group (bytes) | Notes |
|--------------------|-------|
| 470, 934, 1398, 1870 | Template-like blobs in DATA1 / DRAAK |
| Filter | `looksLikeMonster()` — skip pure spell/effect defs in the same size bands |
| Strings | `MO_*` encounter names (`MO_GOBELIN`, `MO_SKELETON1`, `MO_BAT`, …) in LAND / LEVEL packs |

Same-size swap is the safe enemy shuffle; level fields can be rerolled in range.

---

## 6. Real loot — QUEST$

Quest packs (`AL0_Q0`…`AL3_Q2`, `AQFINAL`, `QTEXT`) hold **`ITEM_*` ASCII names** used as rewards / chest / pickup IDs.

```
System (do not touch):   ITEM_DROP  ITEM_PICK  ITEM_USE
Keys (protect by default): names containing KEY / CLEF / FALSEKEY
```

Slots are null-padded name fields (often name + 1 null; capacity capped at 32).  
Shuffling non-key `ITEM_*` across QUEST$ is the only loot path that actually changes gameplay; **left OFF by default** to avoid softlocks.

Example strings: `ITEM_SWORD1H_3`, `ITEM_POTION_VITALITY`, `ITEM_AMULET_KALIBA`, `ITEM_DPQ3_EPEE1`, `ITEM_CLEF_DRAAK`, …

Chest markers appear as script names (`dpqWitch4Chest`, `dpq5ChestGutrick`, `dpq9bChest`, …).

---

## 7. Music & video (loose CD files)

| Path | Pattern | Notes |
|------|---------|--------|
| `Music/` | `01.RAW` … `17.RAW`, `22.RAW`, `25.RAW` | PCM tracks; shuffle **contents**, keep filenames |
| | `NULL3MIS.DA` | Tiny pad — ignore if &lt; 4 KB |
| `CINE/` | `*.DPS` (`INTRO`, `LOGOS1`, `VILLAGE`, `CREDITS`, …) | FMV; same content-swap strategy |

These are **not** inside PSM; randomizer walks the CD root.

---

## 8. Disc layout (USA extract)

```
MAIN DATA/     DATA1 DATA2 TOWN DRAAK INGAME0–7 LEVEL29 LEVEL30
LANDS/         LAND32_0 … LAND39_0 (overworld)
QUEST0/1/2/    LEVEL1 … LEVEL28 (dungeon interiors, difficulty tiers)
QUEST$/        AL*_Q* quest scripts, AQFINAL, QTEXT
Music/         *.RAW
CINE/          *.DPS
```

Workflow: **Unpack PSM → patch in place → install back → rebuild ISO (CDImg)**.  
Folder-open of the extract alone is **not** a valid boot path.

---

## 9. Randomizer signatures (quick reference)

| Feature | Detection / action |
|---------|-------------------|
| PSM TOC | u32 count + interleaved id/offset |
| Map tile | `data[0]==0xFE`, size 56 or larger same-size groups |
| TIM+CLUT | magic `10 00 00 00`, flags bit3, 16–256 RGB555 |
| Quest loot | ASCII `ITEM_[A-Z0-9_]+` in QUEST$ bins |
| Hero | Large DATA1 blobs containing class name strings |
| Enemy | Size bands + monster heuristic / `MO_*` |
| Music | `*.RAW` under CD, size &gt; 4 KB |
| Video | `*.DPS` under CD, size &gt; 8 KB |

---

## 10. Boot-safety checklist

1. Never change `tableWordCount` or even-index TOC values.  
2. Never change blob **size** (only content).  
3. Prefer per-archive same-size shuffles over cross-file size mismatches.  
4. Protect `KEY` / `CLEF` item names.  
5. After install, rebuild with **CDImg** (or export seeded `.cue`) and boot that image.

---

*Documented for Darkstone Randomizer v3.0 — Serif System Works / Grok-assisted RE session.*


## Dungeon / FE shuffle rules (v3.1)

- **LAND\***: overworld FE56 (+ small repeated prop sizes). Optional **cross-land** pools FE56 across LAND packs only.
- **LEVEL\***: interiors; known template sizes only (`56, 304, 664, 948, 1252, 1332, 1562`).
- **Cross-interior**: FE56 pooled only within the same QUEST tier (QUEST0 / QUEST1 / QUEST2).
- **LEVEL29 / LEVEL30 / DRAAK**: off by default (`dungeonsFinal`).
- LAND tiles are **never** mixed with dungeon interior FE56.
