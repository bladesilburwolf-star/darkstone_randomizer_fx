# TrueStone-PC handoff

**Package:** TrueStone-PC only (PSX split out).

**Version:** see `src/com/serifsystemworks/darkstone/pc/VERSION.txt` (v1.9.5-pc at split).

## Do not regress
- Novice: `randomizeMonsterPower=false`; speeds/spawns OK; gold 2500–10000 absolute.
- Inventory EXE: **8×8** only (never 10-column grid — mod-8 slot math).
- DAT authoritative for combat; same-size TXT for MTF inject.
- PC workset: `pc/*`, `mtf/*`, `ui/PcMainView.java`, `engine/LogSink.java`.

## Sister package
`Darkstone-PSX-Randomizer` — disc/PSM only; do not re-merge sources without a deliberate monorepo decision.

## Build
```
build-pc.bat
run-pc.bat
```

## Repository split (2026-09-08)
PC and PSX are separate packages. Do not mix source trees.
