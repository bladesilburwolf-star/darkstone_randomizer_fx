# Darkstone PSX Randomizer

PlayStation disc-oriented randomizer (PSM / ISO / CUE). **PC TrueStone is a separate package.**

Non-functional PSX UI paths were trimmed in earlier sessions; this tree keeps the disc tools and PSX engine that still compile.

## Requirements
- JDK 21+
- JavaFX SDK 26+ (path in `build.bat` / `build-psx.bat`)

## Build / run
```bat
build.bat
run.bat
```

## Layout
```
src/com/serifsystemworks/darkstone/
  DarkstoneApp.java        entry
  engine/                  PSM, scanner, randomizer core
  config/ math/
  ui/MainView.java
src/com/serifsystemworks/psxdisc/
  CueSheet, Iso9660Patcher, PsxDiscTool
```

## Notes
- `CROSSWALK_PC_PSX.md` — historical PC↔PSX mapping
- For active PC development use **TrueStone-PC** instead
