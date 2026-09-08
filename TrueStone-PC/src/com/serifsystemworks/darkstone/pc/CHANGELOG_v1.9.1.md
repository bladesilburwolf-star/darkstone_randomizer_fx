# TrueStone v1.9.1-pc

## Spawn counts
- `randomizeSpawnCounts` default **on**
- Runtime: `MONSTERCLASS.DAT` CNTAPP@216 + CHAAPP@214 (skips CNTAPP==1 uniques)
- Also mirrors into `MONSTER.TXT` when TXT path runs
- UI: **Randomize spawn counts**

## Economy (early shops cheaper)
- OBJECT.TXT: level-tiered % if PRICE column exists  
  - early (L≤12): 55–85%  
  - mid (L≤28): 75–100%  
  - late: 85–115%
- ITEMOBJECT.DAT: best-effort PRICE offset discovery + same early discount

## Quest / LAND pass (start)
- **Quest spawn density**: scales COUNT/NB/NUMBER/AMOUNT/… integers in side-quest SPT (same digit width only)
- Enable with **Quest LAND ids** + **Quest spawn density** (Advanced preset)
- LAND binary MO_* type swap still deferred (needs unpacked LAND samples)

## UI
- chkSpawn, chkQuestDensity
- Advanced/Chaotic enable quest + density
