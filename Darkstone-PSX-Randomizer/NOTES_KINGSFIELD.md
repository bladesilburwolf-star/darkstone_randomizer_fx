# King's Field randomizer parallels (for Darkstone)

Primary source: **IvanDSM/KingsFieldRE** — KF2 (US King's Field / JP KF2) tools + KFRandomizer prototype.

Darkstone is the same *class* of problem: PSX disc → one big data container → structured tables for monsters, items, maps → same-size or in-place edits → reinsert into image.

## Pipeline (matches us almost 1:1)

| King's Field | Darkstone PSX |
|--------------|----------------|
| Extract **FDAT.T** from Track 1 via CDmage | Unpack **\*.PSM** (or patch via disc tool) |
| Randomize in memory | In-place same-size blob patches |
| Import FDAT.T back into the image | Repack PSM / multi-BIN CUE build |
| Boot modified image | DuckStation on new CUE |

KFRandomizer options (prototype): **monsters**, **monster drops**, **items** (items still fragile / can softlock). Same caution as our **loot XOR starting gear**.

## How they avoid “garbled stats”

KFModTool does **not** spray random u16s across the file. It exposes a **Game Database**:

- Armor stats  
- Weapon stats  
- Object classes  
- Spell attributes  
- Player level data  

Those are **named fields on fixed structures**. Edit the integer → menu shows a real number.

That is the same principle as Vagrant Story’s `$30` equip record and what we want for Darkstone:

1. Discover record stride + field offsets (HexOffsetFinder in KFRE finds structure sizes from sorted hex lists).  
2. Patch only those fields (AR, durability, weapon min/max, DEX-linked hit, etc.).  
3. Optional live peek (KF: **KFPeek.lua** on emulator) to verify values in RAM while testing.

Until then, our heuristic “AC / hit / speed” bands stay experimental.

## Map / entity model (door / enemy placement)

KF maps hold **entities and objects** with instance IDs. Randomizer **replaces monsters (and drops) per map**. Map editing tool can move entities in 3D.

Darkstone analogue:

| KF | Darkstone |
|----|-----------|
| Map entity = monster instance | LAND/LEVEL `MO_*` slots + templates |
| Item objects on map | QUEST$ / chest `ITEM_*` (protect keys/crystals) |
| Door / warp objects | Still under-mapped; our “dungeon doors” = structural FE props cross-land |

True **entrance rando** in either game needs the warp/door object table, not only terrain tiles.

## Extra KF infrastructure worth mirroring later

| Tool | Use for Darkstone |
|------|-------------------|
| **checksum_tool** | If any archive has integrity checks (PSM may not; MTF/PC might) |
| **HexOffsetFinder** | Derive struct sizes from sorted constant lists in NOTES |
| **tfile_tool** | Pattern for named extract from big archives |
| **Game DB UI** | Long-term: show real STR/DEX/AR after rando in our log/UI |

## Practical takeaways for us

1. **One primary data target** (FDAT.T ↔ core PSM set) + image reinsert.  
2. **Monster type + drop** shuffle is the proven KF path — we have MO_ type shuffle + enemy template swap.  
3. **Item rando last** and gated (KF items break; we already gate loot vs gear).  
4. **Stats only at known offsets** — goal is a field map like KFModTool’s weapon/armor DB, then a **stat report** with real numbers.  
5. Emulator RAM watch helps confirm offsets (KFPeek-style).

## Refs

- https://github.com/IvanDSM/KingsFieldRE  
- KFRandomizer manual under `Tools/KFRandomizer/README.md`  
- ImJecht setup video: King's Field 1U (2J) Randomizer Tool Setup  
- Our notes: `NOTES_STATS.md`, `NOTES_DUNGEONS_AND_LOOT.md`
