# TrueStone v1.9.3-pc — Class affinity overhaul

## Improve classes (default on)
Removes hard weapon affinity so Amazon/bow, mage/staff, warrior/halberd, etc. are all viable.

| Change | Detail |
|--------|--------|
| **WEAPON_KIND[0..10]** | Flattened to ~18–22 for every class (was 15–40 specialty locks) |
| **combatHitReturn** | Hit recovery ~28–42 (was flat 25) |
| **combatHitBlock** | Block ~18–32 (was flat 15) |
| **BASE_*** | Floors: primary stats ≥12, LIFE ≥32, MANA ≥28 + small bumps |

## Resistances
PCLASS has **no** Poison/Flame/Magic resist rows in retail data. Survivability is improved via life/vit + hit/block until resist fields are mapped in DAT. AC remains on items/monsters (`OBJECT` / `MONSTER` / DAT).

## UI
Checkbox **Improve classes (no weapon lock)** — on for all 1.05b presets.
