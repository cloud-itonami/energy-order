# Energy Order Protocol — suite digest

This is the **suite-level orchestrator** for the Energy Order Protocol — not a Tier-B
actor, but the cross-actor SSoT that composes all five actors into one picture. It sits
*above* the actors (it depends on all of them; no actor depends on it).

```
撓 tawami (flexibility) ┐
燠 okibi   (waste-heat) ├─ claim emitters ─→ 澪 mio verify (§9) ─→ reward proposals
樋 toi     (compute)    ┤                         │
委 yudane  (intention)  ┘                         └─→ digest: org Flowrate + per-leg + per-class + CID
```

`digest.cljc` runs the full pipeline once and renders the unified Energy Order picture:
the org **Flowrate** (verified useful-flow total), each leg's contribution, the breakdown
by flow class, and the advisory moyai reward total. It content-addresses the whole digest
via the shared `mio.kotoba` commit-DAG CID, so the org-wide Energy Order state is one
verifiable CID.

`Proof of Work → Proof of Useful Flow`: value is **ordered** flow, never **consumed** energy.

## Contents

- `digest.cljc` — the cross-actor digest (org Flowrate + per-leg + per-class + CID).
- `validate.cljc` — suite-wide integrity checker: proves every `:unrepresentable` charter
  gate each ontology declares is ACTUALLY absent from that actor's emitted datoms
  (ontology ⊨ code), and seed ids are unique. A green run = no charter gate has silently
  regressed across the suite.
- `test_digest.cljc` · `test_cells.cljc` (the 5 `fire` heartbeat contracts) · `test_validate.cljc`.

## Run

```bash
./20-actors/energy_order/run_tests.sh                               # digest + cells + validate
bb --classpath 20-actors 20-actors/energy_order/digest.cljc         # render the cross-actor digest
bb --classpath 20-actors 20-actors/energy_order/validate.cljc       # suite integrity (ontology ⊨ code)
```

Current seed run: 25 claims (tawami 12 / okibi 4 / toi 5 / yudane 4) → mio verifies 23 →
**org Flowrate 13,039.6 kWh-equiv** → advisory moyai credit 13,039.6 (cash≡0).

OBSERVATION ONLY. ADR-2606211200 · Energy Order Protocol.
