# Stat system notes (Darkstone PSX) + Vagrant Story parallel

## Darkstone combat model (player-facing)

| Concept | Driven by | Notes |
|---------|-----------|--------|
| Strength | STR | Damage contribution |
| Magic | MAG | Spell power |
| Dexterity | DEX | **Accuracy / hit rate**, often **speed**-related behavior |
| Vitality | VIT | HP / toughness |
| Armor rating | **Isolated** field | Not the same as VIT; separate AR / AC |
| Weapon damage | Min–max range | Own fields on weapon defs |
| Durability | Own field | Weapon (and possibly armor) wear |
| Hit count | Separate or DEX-linked | Multi-hit / number of hits — confirm in DATA1 |
| Speed | DEX-influenced + optional dedicated field | Do not treat every low u16 as speed |

Current randomizer still uses **heuristic u16 bands** for “combat extras.” That can look like **garbled UI text** if a value lands in a **string length**, **pointer**, or **enum** rather than a true stat field.

## Vagrant Story lesson (what to copy later)

VS equipment is a **fixed record** (Data Crystal: `$30` bytes) with **named offsets**:

- `$5` STR, `$6` INT, `$7` AGI  
- `$8/$a` DP / DPmax, `$c/$e` PP / PPmax  
- Range, affinities, classes at fixed places  

Randomizers change **those integers**. The in-game status screen still prints **real numbers** because the layout never breaks.

Balance modes (ChaoticBrave VSR, etc.) clamp ranges so early gear stays usable — same idea as our General/Advanced ranges.

**Darkstone needs the same:**

1. Map **exact offsets** for class max/base STR/MAG/DEX/VIT in DATA1 class blobs  
2. Map **weapon** min/max damage, durability, AR on item defs  
3. Map **monster** HP, AR, to-hit, speed, damage on `MO_*` / template blobs  
4. Only then show a **post-rando summary** (seed log / optional window) listing e.g.  
   `Knight STR 28 DEX 19 AR 12` — not raw hex dumps of misaligned fields  

Until the map exists, keep combat-extra patches **sparse and optional** (current v3.4 behavior).

## Garbled text checklist

If the menu shows nonsense after a rando:

- [ ] Endianness (must be **LE** u16/u32)  
- [ ] Wrote into an **ITEM_*** / **MO_*** name slot  
- [ ] Overwrote a **length prefix** or **pointer**  
- [ ] Stat cap / display string table out of sync with numeric field  

## Next research (when ready)

1. Dump one hero class blob and one weapon def next to PC `PCLASS.TXT` / `OBJECT.TXT` column names  
2. Lock offsets for AR, durability, weapon min/max, DEX-adjacent hit  
3. Replace heuristic `combatExtras` with **field-list patcher** + optional “Stat report” panel  

## Refs

- Vagrant Story equip layout: Data Crystal `Vagrant_Story/equip_data`  
- VS randomizer feature set: ChaoticBrave/VagrantStoryRandomizer (balance item/Ashley stats)  
- Our protect list / gear XOR loot: `NOTES_DUNGEONS_AND_LOOT.md`

## UI string corruption (v3.5 fix)

**Symptom:** Menus show `STMN_NEWG`, `STR_EQUIPMENT`, `STR_BAG` instead of localized text.

**Cause:** Five DATA1 language blobs (~27–32 KB) contain both class names (WARRIOR…) and UI keys (`STMN_*`). `isHero()` treated them as class tables; hero/combat u16 sprays destroyed string payloads so only keys remained.

**Fix:** `TableScanner.isUiStringTable()` + `isHero()` rejects `STMN_` / `STR_EQUIP` blobs and size-caps hero matches to ≤12 KB. Combat extras disabled until field map exists.
