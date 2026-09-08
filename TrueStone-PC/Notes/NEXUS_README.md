# TrueStone — TrueRandomizer for Darkstone (PC)

Bloodstained-style randomizer for *Darkstone* (Windows / GOG).

**Seed · toggles · install · launch** — combat via `data\monsterclass.dat` & `itemobject.dat`, optional `DATA.MTF` inject, native `config\seed.txt`.

## Requirements

- Darkstone PC (GOG recommended)
- JDK 21+ and JavaFX 26 (`JFX_HOME` in `build-pc.bat` / `run-pc.bat`)
- Optional: free-space EXE fix (see below) if you hit “REQUIRED:50MB”

## Quick start

1. Extract TrueStone anywhere (not inside Program Files).
2. `build-pc.bat` then `run-pc.bat`.
3. **TrueStone** tab → set **Game** folder → seed / preset → **START RANDOMIZED GAME**.

Or: Randomize → **Install Seed** → run `Darkstone.exe` yourself.

### CLI

```bat
TrueStone.bat <seed> "C:\GOG\Delphine Software"
```

## What gets randomized

| Module | Effect |
|--------|--------|
| Enemies | Stats / types; **DAT** is what combat uses |
| Items & shops | Weapon/armor stats; shop PARENT where safe |
| Characters | Class BASE_/MAX_ rows in PCLASS |
| Quests & world | Side-quest LAND / rewards (optional) |
| Early-game safety caps | On by default (General/Advanced) — limits trash-mob nukes |

## Files TrueStone writes

```
config\seed.txt              integer seed for the EXE
data\monsterclass.dat
data\itemobject.dat
data\pClass\pclass.txt       optional
seeds\<seed>\                full package + manifest
```

Optional: replace `DATA.MTF` (checkbox; creates `DATA.MTF.launcher_bak`).

## Free disk space error

Old engine bug on large free drives. Use the community free-space patch offsets (TrueStone docs / DCP-001) or the provided patch notes. **Do not** redistribute a full commercial EXE on Nexus unless allowed; ship a patcher or instructions.

## Presets

- **General** — shuffle + safety caps  
- **Advanced** — + types, shops, quests, pipeline  
- **Chaotic** — range rolls, caps off  

## Credits

- Delphine / original *Darkstone*  
- TrueStone tooling: community (MTF format RE, DAT layouts, seed path)  
- Inspired by Bloodstained-style in-menu rando UX (external front-end for PC Darkstone)

## Permissions (draft for Nexus)

- Free to use and share  
- Do not sell  
- Credit TrueStone / DCP when redistributing builds  
- No reupload of GOG/Steam game files  

## Known issues

- Without DAT install, combat will look vanilla  
- Full `DATA.MTF` replace is optional and riskier than DAT-only  
- Quest logic softlocks possible if aggressive SCRIPT options enabled  
- Not a full logic rando (crystals/keys) yet  

## Related: Darkstone Community Patch (DCP)

See `DCP_ROADMAP.md` — stability patches + future EXE hooks so TrueStone and other mods share one foundation.
