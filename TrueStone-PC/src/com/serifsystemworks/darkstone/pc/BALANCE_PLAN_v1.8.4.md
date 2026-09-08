# TrueStone v1.8.4 — Balance Pass

## Restricted workset
Only these five source files are in scope:
- PcCampaignPipeline.java
- PcOptions.java
- PcRandomizerEngine.java
- PcSeedLauncher.java
- TsvTable.java

No EXE, MTF compressor, archive implementation, DCP patch, or other project file is modified.

## Objective
Improve player/enemy stat randomization without introducing startup/archive-format risk.

## Method
The current Qwen implementation is preserved as the baseline. The next code change should:
1. Keep deterministic seed behavior.
2. Keep the original level/role relationship intact.
3. Prevent extreme low/high rolls from crossing broad progression tiers.
4. Preserve aggregate power so randomization changes distribution more than total difficulty.
5. Apply bounded perturbations independently to player and enemy stat domains.
6. Avoid touching serialization, archive writing, or executable startup code.

## Validation gates
- Source compiles.
- No changes outside the five-file workset.
- Vanilla/no-randomization path remains byte/behavior compatible where possible.
- Randomized values remain within legal ranges.
- No zero/negative combat values are introduced.
- Enemy difficulty should rise gradually with level rather than by random spikes.
