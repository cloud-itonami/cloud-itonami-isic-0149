# Contributing

**Maturity: `:implemented`** — `src/otherlivestockops/` implements the
reference OtherLivestockOpsAdvisor / OtherLivestockFarmOperationsGovernor
actor as a synchronous stub (langgraph-clj StateGraph wiring deferred, see
`operation.cljc`). Contributions that extend coverage are welcome:
langgraph-clj StateGraph integration (real
`interrupt-before`/checkpoint-based human-in-the-loop resume for escalated
operations), a Datomic/kotoba-server `Store` backend, a real LLM `Advisor`
implementation, additional Governor rules, and husbandry-line/health-concern
reference-data expansion in `otherlivestockops.facts` (e.g. deeper coverage
of rabbit/fur-animal/sericulture/game-farming lines beyond the apiculture
illustration). Open an issue or PR. License: AGPL-3.0-or-later.
