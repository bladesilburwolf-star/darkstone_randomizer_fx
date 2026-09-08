# Notes: loot vs gear, dungeon doors, enemy types (v3.4)

## Loot XOR starting gear

Quest / dungeon weapons as **starting gear** crash the game.

- UI: enabling **Loot** clears **Start gear** (+ spells); enabling **gear** clears **Loot**.
- Engine: `RandomizerOptions.resolveConflicts()` forces gear off if both were true.

## Dungeon doors (replaces interiors)

Interior FE shuffle did **not** change dungeons in-game.

**Dungeon doors** (v3.4): cross-land shuffle of fixed-count structural `FE` props
(sizes 238, 300, 360, 408, 414, 496, 530, 544, … present in every LAND sample).

Still not a full entrance-table rando (coupled/decoupled doors). Next step remains
mapping explicit entrance hex on overworld.

## Enemy types

`MO_*` name slots shuffled by string capacity across LAND/LEVEL blobs
(skips names containing QUEST / BOSS / DRAAK).

## Combat extras

Sparse u16 rewrites in AC / hit / speed bands on hero & enemy templates
(in addition to primary stat ranges).
