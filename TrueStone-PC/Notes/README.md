# TrueStone (PC)

**TrueRandomizer for Darkstone PC** — seed-driven DAT/MTF/EXE pipeline.

This tree is **PC only**. The PSX randomizer lives in a separate package: `Darkstone-PSX-Randomizer`.

## Requirements
- JDK 21+
- JavaFX SDK 26+ (default path in `build-pc.bat`)

## Build / run
```bat
build-pc.bat
run-pc.bat
```

Optional MTF explorer:
```bat
run-mtf.bat
```

## Layout
```
src/com/serifsystemworks/darkstone/
  DarkstonePcApp.java      entry
  MtfExplorerApp.java
  pc/                      randomizer engine, options, pipeline, TSV
  mtf/                     DATA.MTF archive
  engine/LogSink.java
  ui/PcMainView.java
DCP/patches/               EXE patch JSON (inventory, widescreen, …)
Darkstone_patched.exe      prebuilt DCP binary (backup stock EXE first)
HANDOFF.md                 agent handoff notes
```

## Version
See `src/com/serifsystemworks/darkstone/pc/VERSION.txt`.

## Related
- PSX: `Darkstone-PSX-Randomizer` (BIN/CUE, PSM, disc tools)
- Crosswalk notes were moved with the PSX package as historical reference
