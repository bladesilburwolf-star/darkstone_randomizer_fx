# Seed engine reverse-engineering notes (PC Darkstone)

Goal: integrated seed randomizer — one seed drives a full campaign build, ideally
selectable without manually swapping `DATA.MTF`.

## What actually affects gameplay

| Data | Role | Loaded from |
|------|------|-------------|
| `MONSTERCLASS.DAT` | Runtime monster combat | Loose next to game (or data/) |
| `ITEMOBJECT.DAT` | Runtime item combat | Loose |
| `PCLASS.TXT` | New character base stats | `DATA.MTF` → `DATA\PCLASS\PCLASS.TXT` |
| `MONSTER.TXT` / `OBJECT.TXT` | Designer tables; weak runtime | `DATA.MTF` PCLASS |
| `SCRIPT\*.SPT` | Quests / rewards / LAND | `DATA.MTF` SCRIPT |

**If only TXT is randomized and DAT is not copied into the game folder, combat
will look unchanged.** Character bases need PCLASS inside DATA.MTF (or a loose
override if the EXE supports it).

## Pipeline fix (v1.5.1)

- `datSourceRoot` = real game folder while extract work tree is temporary.
- PCLASS rewrite preserves CRLF so same-size MTF inject works.
- Log shows sample BASE_ before/after and DAT patched counts.

## Path to “seed on main menu”

1. Locate EXE code that opens `DATA.MTF` (string xref `DATA.MTF` / `PCLASS`).
2. Find character-create path that reads class bases.
3. Options:
   - **A.** Patch EXE to load `DATA_<seed>.MTF` or `quest\DATA_<seed>.MTF`
   - **B.** External launcher: write seed → copy archive → start game
   - **C.** In-process ASI/DLL inject (GOG) — heavier

Recommended near-term: **B launcher** + multi-seed pipeline (no EXE patch).

## Next RE tasks

- [ ] Confirm GOG layout: where `MONSTERCLASS.DAT` sits relative to `Darkstone.exe`
- [ ] String scan EXE for `MONSTERCLASS`, `PCLASS`, `DATA.MTF`
- [ ] Verify whether loose `PCLASS\` overrides MTF entries
- [ ] Map shop inventory storage (SPT vs DAT vs other)
- [ ] Prototype launcher: `DarkstoneRando.bat seed` copies `seeds\SEED\*` then runs game

## Seed format (current)

```
seed=<text>
hash=<fnv-ish long>
preset=<name>
platform=PC
```

Pipeline output: `Out/seeds/<seed>/DATA_<seed>.MTF` + `PCLASS/` + `*.DAT`
