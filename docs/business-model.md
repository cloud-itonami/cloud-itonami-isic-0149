# Business Model: Other-Livestock Farm Operations Coordinator

## Classification

- Repository: `cloud-itonami-isic-0149`
- ISIC Rev. 4: `0149`
- Industry: Raising of other animals (n.e.c.)
- Social impact: animal-welfare, food-security, rural-employment

## Customer

- Apiaries (beekeeping/honey producers) — this repository's chosen concrete illustration
- Rabbit and fur-animal husbandry operations
- Sericulture (silkworm) operations
- Game-farming operations
- Cooperative and integrator-affiliated other-livestock operations

## Offer

- Husbandry batch record-keeping (feeding/breeding/health-check data)
- Feeding/breeding/harvest scheduling coordination (e.g. honey extraction, pelt harvest)
- Health and biosecurity tracking (e.g. Varroa mite risk surfacing)
- Supply procurement coordination
- Audit trail and transparency

## Revenue

- SaaS subscription (per-holding/per-hive-or-hutch-per-month pricing)
- Supply chain integration fees
- API access for veterinary partners
- Data analytics and reporting add-ons

## Trust Controls

- No culling or harvest-finalization decisions without human sign-off
- No direct treatment administration
- All veterinary recommendations are proposals, not commands
- Facility/holding registration is required before any operation
- All animal health/biosecurity concerns are automatically escalated
- High-cost supply orders require approval
- Audit ledger is append-only and never editable

## What we do NOT do

- **Veterinary treatment decisions** — the veterinarian decides treatment
- **Animal welfare decisions** — the farm operator decides welfare actions
- **Economic decisions** (culling, harvest-finalization, breeding) — remain human authority
- **Direct animal handling** — the robot manages records and logistics only
- **Outbreak declarations / animal-health authority contact** — flagged
  concerns (e.g. suspected Varroa mite infestation) are surfaced for
  human/veterinary judgment only

## Supported Operations

### Husbandry Batch Record Logging
- Feeding batch counts
- Weight tracking
- Health status notes
- Breeding data (logging only, not decision-making)

### Feeding/Breeding/Harvest Scheduling
- Schedule feeding/breeding activities
- Schedule harvest operations (e.g. honey extraction, pelt harvest) —
  scheduling only, never finalizes the harvest/cull decision itself
- Track scheduled-operation results

### Health/Biosecurity Concern Escalation
- Flag suspected disease (e.g. Varroa mite, American Foulbrood,
  Myxomatosis, Rabbit Haemorrhagic Disease, Pebrine)
- Report injuries or welfare concerns
- Automatic escalation to farm operator/veterinarian

### Supply Procurement
- Feed orders
- Veterinary supply orders
- Husbandry-equipment procurement (hives, hutches, rearing trays)
- Cost threshold escalation for large orders
