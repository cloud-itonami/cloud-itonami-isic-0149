# cloud-itonami-isic-0149

Open Occupation Blueprint for **ISIC Rev. 4 0149**: Raising of other animals.

ISIC 0149 is a residual ("not elsewhere classified") livestock category:
any animal-raising activity not already covered by cattle/buffaloes
(0141), horses/other equines (0142), camels/camelids (0143), sheep/goats
(0144), swine (0145), or poultry (0146) -- e.g. rabbits, fur animals,
bees/apiculture, silkworms, or game farming. **This repository picks
apiculture (beekeeping/honey production) as its one concrete worked
illustration** (see the demo in `otherlivestockops.sim` and the sample
facility "Sunrise Apiary" throughout the tests), but the actor's
facility/holding abstraction is generic across the whole n.e.c. category
-- `otherlivestockops.facts/husbandry-lines` also lists rabbit,
fur-animal, sericulture (silkworm), and game-farming as illustrative
lines the same Store/Governor machinery covers unchanged.

This repository implements a forkable OSS **other-livestock farm
operations coordinator**: a facility-management and record-keeping robot
manages husbandry batch logging (feeding/breeding/health-check data),
feeding/breeding/harvest scheduling (e.g. honey extraction, pelt
harvest), and supply procurement under a governor-gated actor, so an
other-livestock operation keeps its own operational records and
maintains full transparency over decisions.

**Maturity: `:implemented`.** `src/otherlivestockops/` implements the
`OtherLivestockOpsAdvisor` (`otherlivestockops.advisor`) and the
independent `OtherLivestockFarmOperationsGovernor`
(`otherlivestockops.governor`), composed by `otherlivestockops.operation`
following the itonami actor pattern (ADR-2607011000):
`advise -> govern -> phase-gate -> commit | escalate | hold`. 32 tests /
103 assertions green (`clojure -M:test`).

`otherlivestockops.operation` is a synchronous stub of this flow (see its
docstring) — production wiring into a `langgraph-clj` StateGraph with
`interrupt-before`/checkpoint-based human-in-the-loop resume for
escalated operations is deferred, mirroring
`cloud-itonami-isic-0145`'s own `swineops.operation`.

## What this does NOT do

This actor coordinates **back-office logistics only**. It explicitly does **NOT**:

- **Direct animal handling** — remains the farm operator's exclusive authority
- **Veterinary treatment decisions** — remains the veterinarian/farm operator authority
- **Culling or harvest-finalization decisions** (e.g. finalizing a pelt
  harvest or silkworm cocoon harvest) — economic and ethical authority
  remains human. Note that *scheduling* a harvest operation
  (`:schedule-farm-operation`) is a routine coordination proposal; only
  *finalizing* the cull/harvest itself is blocked.
- **Direct treatment administration** — any proposal for direct treatment is a hard block
- **Outbreak declarations / animal-health authority contact** — a flagged
  health/biosecurity concern (e.g. suspected Varroa mite infestation or
  American Foulbrood) is surfaced for human/veterinary judgment only;
  this actor never itself declares an outbreak or notifies authorities

## HARD invariants (always hold, never overridable)

1. **facility-not-registered** — the request's `facility-id` must resolve to a
   registered facility/holding (apiary/hutch bank/enclosure) in the Store before any proposal
   can proceed
2. **no-execution** — every proposal's `:effect` must be `:propose` (the governor
   never directly handles animals, never administers treatment, never orders
   a cull or harvest)
3. **treatment-or-harvest-blocked** — `:administer-treatment` and
   `:order-cull-or-harvest` proposals are unconditionally, permanently blocked
4. **op-not-allowed** — any op outside the closed allowlist below is rejected
5. **husbandry-count-invalid** — `:log-husbandry-record` with a non-positive count is rejected

## Always-escalate operations (human sign-off, regardless of confidence)

- `:flag-animal-health-concern` — any welfare or biosecurity concern (e.g.
  suspected Varroa mite infestation, American Foulbrood) → automatic escalation
- `:order-supplies` over its category cost threshold (default 500 currency
  units; see `otherlivestockops.facts/supply-categories`)
- Any proposal with confidence below the Governor's floor (0.7)

## Operational requests (closed allowlist, all `:effect :propose`)

```text
:log-husbandry-record
  — record feeding/breeding/health-check batch data (count, weight,
    health status)
  — requires a registered facility; non-positive counts are rejected

:schedule-farm-operation
  — propose feeding/breeding/harvest scheduling (e.g. honey extraction,
    pelt harvest)
  — does NOT finalize a cull/harvest decision, and does NOT make
    treatment decisions

:flag-animal-health-concern
  — surface a disease, injury, or biosecurity concern (e.g. suspected
    Varroa mite infestation)
  — ALWAYS escalates for human review

:order-supplies
  — procurement for feed, veterinary supplies, husbandry equipment
    (hives, hutches, rearing trays)
  — escalates if cost exceeds its category threshold
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs the
physical domain work**. Here a facility-management robot handles:

- Husbandry batch record logging and entry
- Feeding/breeding/harvest scheduling and reminders
- Supply inventory and ordering
- Audit ledger maintenance

The **OtherLivestockFarmOperationsGovernor** is the independent safety layer that gates all
proposals before a robot action is executed. The governor never dispatches hardware directly;
`:high`/`:safety-critical` actions (such as escalated health/biosecurity concerns or
high-cost supply orders) require human sign-off.

## Core Contract

```text
operational request (log, schedule, concern, order)
        |
        v
OtherLivestockOpsAdvisor -> OtherLivestockFarmOperationsGovernor -> phase gate -> commit, or escalate for human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated operation can dispatch a robot action the governor refuses, suppress an
operating record, or hide a health/biosecurity concern without governor approval and
audit evidence.

## Module structure

Mirrors `cloud-itonami-isic-0145` (`swineops.*`) module-for-module:

- `otherlivestockops.facts` — reference data: husbandry-line
  classification, supply-category cost thresholds,
  health/biosecurity-concern vocabulary
- `otherlivestockops.registry` — pure independent verification functions (cost/count/confidence)
- `otherlivestockops.store` — `Store` protocol + in-memory `MemStore` (facility registration lookup)
- `otherlivestockops.advisor` — `Advisor` protocol + `MockAdvisor` (the sealed LLM/decision node)
- `otherlivestockops.governor` — `OtherLivestockFarmOperationsGovernor`: hard invariants + escalation gates
- `otherlivestockops.phase` — 0→3 rollout phase gate
- `otherlivestockops.operation` — composes advisor → governor → phase into one operation run
- `otherlivestockops.sim` — demo runner (`clojure -M:run`)

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISIC Rev. 4 `0149`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Testing

```bash
clojure -M:test   # 32 tests / 103 assertions
clojure -M:lint   # clj-kondo, 0 errors / 0 warnings
clojure -M:run    # demo runner
```

## License

AGPL-3.0-or-later.
