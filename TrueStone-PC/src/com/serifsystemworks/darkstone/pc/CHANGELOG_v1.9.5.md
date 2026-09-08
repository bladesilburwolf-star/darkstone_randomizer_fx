# TrueStone v1.9.5-pc — DS2 track (1–3)

## 1. Loot tier bands (`lootTierBands`, default on)
Combat item stats (DMIN/DMAX/AC/DUR) shuffle **within LEVEL bands only**:
- Tier 0: L≤12
- Tier 1: L≤25
- Tier 2: L≤40
- Tier 3: L≤60
- Tier 4: above

LEVEL stays on the row so progressive bands remain readable (DS2-style pools).

## 2. Start kits (`startKits`, default on)
PCLASS `startItem` … `startItem4` re-rolled from seeded pools:
- slot1 weapons / early arms
- slot2–3 potions/food
- slot4 armor / books / jewelry  

Same-size aware (won’t expand cells).

## 3. Quest LAND logic (`questLandLogic`, default on)
When **Quest LAND ids** is enabled, side-quests (filename order) get:
- early third → LAND 0–2  
- middle → 2–4  
- late → 4–6  

Within each band, ids are seed-shuffled. QUESTFINAL / TOWN / ENTREE still protected.  
Disable `questLandLogic` for pure global shuffle.

## UI
Checkboxes: **Loot tier bands**, **Start kits**, **Quest LAND logic**.

## Log markers
```
[+] Loot tier bands: …
[+] Start kits: …
[+] Quest LAND logic: … (early->L0-2, mid->L2-4, late->L4-6; within-band shuffle)
```
