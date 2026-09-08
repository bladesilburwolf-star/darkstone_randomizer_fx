# Darkstone Community Patch (DCP) — Roadmap

**TrueStone** = randomizer (seed, toggles, DAT/MTF, launcher)  
**DCP** = stability + QoL + optional integration hooks for TrueStone

Target game: GOG / retail PC *Darkstone* (PE32 `Darkstone.exe` ~1.3 MB, image base `0x400000`).

---

## Already shipped (TrueStone / proto-DCP)

| ID | Change | Status |
|----|--------|--------|
| `DCP-001` | Free-space check bypass (`GetDiskFreeSpaceA` overflow) | **Done** — `Darkstone_patched.exe` |
| `TS-MTF` | Same-size DATA.MTF rebuild (shared-offset TOC) | Done (tooling) |
| `TS-DAT` | `data\monsterclass.dat` / `itemobject.dat` rando + early caps | Done |
| `TS-SEED` | `config\seed.txt` as `%d\n` | Done |
| `TS-LAUNCH` | Detached Windows launch + seed package install | Done |

---

## Phase A — Nexus-ready TrueStone (now)

- [x] Playable rando + safety caps  
- [ ] Clean release zip (no PSX clutter, clear readme)  
- [ ] Permissions / credits / install steps for Nexus  
- [ ] Known issues list (fly damage without caps, MTF optional, etc.)  
- [ ] Optional: bundle pre-patched EXE **only if** Nexus allows binary redistribution (often **not** — document “apply patch yourself”)

**Nexus policy note:** Distributing a full patched `Darkstone.exe` may violate GOG/Nexus rules. Prefer a **patcher** or xdelta + user-owned EXE.

---

## Phase B — DCP core stability

| ID | Idea | Notes |
|----|------|--------|
| `DCP-002` | Robust free-space (same as 001, verified on more EXEs) | Steam vs GOG diff check |
| `DCP-003` | Crash on empty `temp\` / cleanup on boot | Delete stale `temp\*` safely |
| `DCP-004` | Windowed / borderless helper | May already be dgVoodoo; document |
| `DCP-005` | Save path redirect to user folder | Optional; avoid Program Files issues |
| `DCP-006` | High-DPI / path length hardening | Low priority |

---

## Phase C — TrueStone integration hooks (EXE)

| ID | Idea | Approach |
|----|------|----------|
| `DCP-010` | Show seed on character screen or load menu | String inject / UI text hook near `TSTARTGAME` |
| `DCP-011` | Auto-read `config\seed.txt` at main menu (already used in-game) | Verify write path on new game |
| `DCP-012` | “TrueStone active” marker file `config\truestone.ini` | EXE optional; launcher writes it |
| `DCP-013` | Prefer `quest\<seed>.mtf` load order | String `quest\%s.mtf` already present |
| `DCP-014` | Hot-reload DAT from `data\` without full reinstall | Research load once vs reopen |

---

## Phase D — Menu / UI (hard mode)

True in-EXE menu items (new buttons on title screen) need:

1. Find title-screen UI layout / widget list  
2. Code cave or DLL inject (`DCP.asi` / `dinput8` loader)  
3. Font/bitmap for labels  

**Recommended path:** `dinput8.dll` or `dsound.dll` proxy that:

- Applies byte patches on load  
- Reads `config\dcp.ini`  
- Optionally draws an overlay seed string  

Safer for Nexus than shipping a modified EXE.

---

## Phase E — Community content

- Shared seed database format (`manifest.txt` already exists per seed)  
- Spoiler log export (monster sample, item sample, seedInt)  
- Compatible with loose `data\pClass\` overrides  

---

## Patch file format (proposed)

```
DCP/
  dcp.ini                 ; master switches
  patches/
    001_freespace.xdelta  ; or .ips / custom
    001_freespace.json    ; offset + before/after bytes
  TrueStone/              ; optional bundled rando build
```

JSON patch example:

```json
{
  "id": "DCP-001",
  "exe": "Darkstone.exe",
  "imageBase": "0x400000",
  "edits": [
    { "fileOffset": "0x71474", "from": "81F900C0D4017306", "to": "909090909090EB06" }
  ]
}
```

TrueStone already applied DCP-001; DCP tools should detect and skip if present.

---

## Session queue (after lunch)

1. Nexus readme + permissions-safe packaging  
2. JSON patch descriptor for DCP-001 (reproducible)  
3. Prototype `dcp.ini` + seed overlay research  
4. Spoiler log writer in TrueStone  
5. Steam EXE offset diff if user provides file  

---

*TrueStone is the rando brand. DCP is the shared foundation so other mods (graphics, translation, QoL) can stack.*
