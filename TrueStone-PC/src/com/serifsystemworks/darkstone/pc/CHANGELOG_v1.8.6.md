# TrueStone v1.8.6

## Economy build fix

- Fixed the v1.8.5 compile error caused by the missing `randomizeStartingGold(...)` implementation.
- Starting gold now recognizes only explicit PCLASS starting-gold row names.
- Unknown PCLASS layouts are left untouched rather than guessing.
- Starting gold remains deterministic for a given seed.
- A configurable percentage range and safety floor are retained.
- Shop-price balancing remains in the existing `OBJECT.TXT` economy pass.

## Restricted workset

Only the five designated PC source files are modified/considered in this workset:
- PcCampaignPipeline.java
- PcOptions.java
- PcRandomizerEngine.java
- PcSeedLauncher.java
- TsvTable.java
