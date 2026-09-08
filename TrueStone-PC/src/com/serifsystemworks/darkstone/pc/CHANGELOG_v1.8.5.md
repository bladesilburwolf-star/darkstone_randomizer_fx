# TrueStone v1.8.5 — Economy Pass

## Scope
Only the five-file PC workset is modified.

## Changes
- Added a conservative shop-economy pass to `OBJECT.TXT`.
- Shop/item prices receive a deterministic per-seed 85–115% adjustment by default.
- Added starting-gold randomization to supported `PCLASS.TXT` starting-gold rows.
- Starting gold receives a deterministic per-seed 80–120% adjustment by default.
- Starting gold has a safety floor of 50.
- Exact supported field names are required; unknown fields are left untouched.
- Existing MTF, EXE, DCP, and archive machinery is untouched.

## Safety
If `OBJECT.TXT` does not expose a recognized price column, the economy patch logs that fact and changes nothing.
If `PCLASS.TXT` does not expose a recognized starting-gold row, starting gold remains unchanged.
