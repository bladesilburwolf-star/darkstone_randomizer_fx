# PC → PSX crosswalk (useful data only)

The **PC randomizer is shelved** for now. Replacing campaign `DATA.MTF` grows the archive and fails to boot; MTF tools are oriented toward *new* quest packs under `quest\`, not in-place main-campaign edits.

What we keep from the PC dump is **name lists and structure**, applied to the **PSX** randomizer (size-preserving PSM patches that do boot).

---

## Shared item IDs

PSX `QUEST$` and PC `SCRIPT/*.SPT` both use `ITEM_*` ASCII ids.

### Never shuffle (progression)

| Pattern / id | Role |
|--------------|------|
| `ITEM_CRISTAL1`…`7` | Seven crystals (main quest) |
| `ITEM_CLEF_DRAAK` | Draak key |
| `*KEY*`, `*CLEF*`, `*FALSEKEY*` | Quest gates |
| `*VIRTUAL*` | Script flags / invisible tokens |
| `ITEM_QFINAL_*` | Final confrontation tokens |
| `ITEM_DROP` / `PICK` / `USE` | System hooks |
| Unique pieces | `MIRROIR*`, `PRISME*`, `COUFFIN`, `ITEM_AMULET_KALIBA` |

### Safe-ish loot pool (examples)

Weapons/armor/potions/scrolls without KEY/CRISTAL/VIRTUAL in the name — same idea as PSX QUEST$ shuffle when loot is enabled.

---

## Quest layout

| PC | PSX |
|----|-----|
| `SCRIPT/DP*_QUEST*.SPT`, `FC*_*.SPT` | `QUEST$/AL*_Q*.PSM` + dungeon `QUEST0/1/2/LEVEL*.PSM` |
| `LAND {0..6}` + two quests/land | Lands via `LANDS/LAND32–39`, interiors via LEVEL packs |
| `QUESTFINAL.SPT` `LAND {7}` | `DRAAK` / `LEVEL29–30` + final QUEST$ |
| `TOWN.SPT` + `TOWN/` meshes | `TOWN.PSM` |

PC quest keys (for documentation / future PSX string work):  
CROIXSOL, SORCIERE, RIKEN, LICORNE, GENNA, ROSEAU, BEBE, TRESOR, RUCHE, TEMPLIER, HORGAN, SHADIRE, POISON, LANGOLIN, MEDUSE, DEMONS, CERCLES, DAMNES, CODE, LUXURIUS, DRUIDES, FINAL.

---

## Stats / combat

| PC source | PSX analogue |
|-----------|----------------|
| `MONSTER.TXT` / `MONSTERCLASS.DAT` columns | DATA1 size-band templates + LAND `MO_*` |
| `OBJECT.TXT` damage/AC | DATA1 gear tables + QUEST$ names |
| `PCLASS.TXT` BASE_* | Large DATA1 hero class blobs |

PC numeric columns validated that LMIN/LMAX/DMIN/DMAX/AC/TOHIT are the fields that matter — keep PSX range-roll options modest.

---

## Why PC main DATA.MTF fails

1. Unpack → edit → repack often **changes size** (compression not preserved).  
2. Boot expects original layout/size for campaign MTF.  
3. Official workflow is **quest\*.MTF** mods, not rewriting campaign DATA.MTF.

PSX path stays: **in-place same-size PSM patch → CDImg rebuild**.

---

*PC port remains in-tree under `pc/` + `DarkstonePcApp` for reference only.*
