(ns otherlivestockops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: before this namespace
  existed there was NO demo page and no generator at all. Everything on
  the rendered page is produced by driving the REAL actor stack --
  `otherlivestockops.operation/build` -> `.../advisor` -> `.../governor`
  -> `.../phase` -> `.../store` -- and reading back what those
  namespaces actually returned. No hand-typed entity, id, count, cost,
  disposition or Japanese detail string appears in the output: the hold
  details you see are the literal `:detail` strings
  `otherlivestockops.governor` emitted during this run.

  Measured before this file was written (not assumed):
    * `clojure -M:run` DOES execute end to end and returns
      `:escalate` -- the actor is live, not dead scaffolding.
    * `otherlivestockops.operation/build` returns a plain closure over
      `run-operation`; langgraph-clj is NOT wired in this repo (its own
      docstring says the StateGraph integration is deferred), so there
      is no `g/run*` to call. This renderer drives the closure, which is
      the whole flow the repo actually implements.
    * The sim driver seeds its facility inline and there is no
      `otherlivestockops.store/demo-data` to reuse, so the facility seed
      below lives here. Its `:husbandry-line` values are real
      `otherlivestockops.facts/husbandry-lines` ids and the facility
      table is rendered from `store/registered-facility` reads rather
      than from the seed literal, so the page shows what the Store
      answers, not what was typed.

  The scenario deliberately exercises every one of the Governor's five
  HARD rules (`facility-not-registered`, `no-execution`,
  `treatment-or-harvest-blocked`, `op-not-allowed`,
  `husbandry-count-invalid`) alongside the approved paths (autonomous
  commits and escalations queued for a farm operator / veterinarian).

  It also keeps apart two things that are easy to blur: a **Governor
  refusal** (hard violation, non-negotiable) and a **rollout phase-gate
  hold** (`otherlivestockops.phase`). In this repo the phase gate's
  conservative default emits a fact of the SAME `:t :governor-hold`
  type, carrying an EMPTY `:violations` vector -- so counting
  `:governor-hold` facts alone would report a refusal that never
  happened. `-main` therefore enforces a two-stage invariant: at least
  one `:governor-hold` fact AND at least one of them carrying a
  non-empty `:violations`.

  Deterministic: no timestamps, no UUIDs, all collections iterated in an
  explicit order, byte-identical across reruns from the same seed.

  Usage: `clojure -M:render-html [out-file]`
         (default `docs/samples/operator-console.html`)

  `:dev` is not needed: this repo's `:deps` is empty, so the `:dev`
  alias's `:override-deps` for langchain/langgraph overrides nothing."
  (:refer-clojure :exclude [num])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [otherlivestockops.advisor :as advisor]
            [otherlivestockops.facts :as facts]
            [otherlivestockops.governor :as governor]
            [otherlivestockops.operation :as operation]
            [otherlivestockops.phase :as phase]
            [otherlivestockops.store :as store]
            [jp-go-dds.skin :as skin]))

;; --------------------------------------------------------------------
;; Scenario advisor
;; --------------------------------------------------------------------

(defrecord ScenarioAdvisor [base]
  advisor/Advisor
  (-advise [_this st request]
    ;; Delegates to the repo's own `otherlivestockops.advisor/mock-advisor`,
    ;; then applies an explicit per-request override. The override exists
    ;; to simulate a MISBEHAVING advisor (an LLM that lowers its own
    ;; confidence, or claims a non-`:propose` effect) so the Governor's
    ;; independent checks can be seen refusing it. It never fabricates a
    ;; verdict -- the Governor still runs, unmodified, on the resulting
    ;; proposal.
    (merge (advisor/-advise base st request)
           (:demo/advisor-override request))))

(defn- scenario-advisor []
  (->ScenarioAdvisor (advisor/mock-advisor)))

;; --------------------------------------------------------------------
;; Seed + scenario
;; --------------------------------------------------------------------

(def ^:private facility-seed
  "Ordered facility seed. `:husbandry-line` values are ids of real
  entries in `otherlivestockops.facts/husbandry-lines` -- the page
  resolves them through `facts/husbandry-line-by-id`, so a typo here
  would render as an unresolved line id rather than as a
  plausible-looking invention. Record shape follows the one this repo's
  own sim seeds (`:id :name :location :husbandry-line`)."
  [["apiary-001"  {:id "apiary-001" :name "Sunrise Apiary"
                   :location "Yard 2, Row 5" :husbandry-line "apiculture"}]
   ["rabbitry-002" {:id "rabbitry-002" :name "Kitayama Rabbitry"
                    :location "Hutch Bank 3, Row 1" :husbandry-line "rabbit"}]
   ["furfarm-003" {:id "furfarm-003" :name "Hokuriku Fur Station"
                   :location "Enclosure 9, Pen 2" :husbandry-line "fur-animal"}]
   ["sericul-004" {:id "sericul-004" :name "Shikoku Rearing House"
                   :location "Rearing House 1, Tray Rack 3"
                   :husbandry-line "sericulture"}]
   ["gamefarm-005" {:id "gamefarm-005" :name "Tohoku Game Farm"
                    :location "Range 4, Paddock 6" :husbandry-line "game-farming"}]])

(def ^:private unregistered-facility-id
  "Deliberately NOT seeded -- used to exercise the Governor's
  `facility-not-registered` hard rule."
  "apiary-999")

(def ^:private context-approver
  "A named human sign-off authority, SUPPLIED to every run in the actor
  context. Nothing in this repo reads it -- which is the point. Handing
  the actor an approver and then measuring whether it survives into any
  fact or record turns 'no approver appears on the page' from an absence
  (which could just mean the scenario never provided one) into a
  measured DROP. If a resume/approve path is ever added, the probe in
  section 8 finds this exact value and the disclosure rewrites itself."
  "sato-dvm-01")

(def ^:private operator-context-base
  {:actor-id "other-livestock-ops-01"
   :role :farm-operator
   ;; Inert for the Governor and the phase gate: `governor/check` takes
   ;; the context as `_context`, and `phase/gate` is never given it. Only
   ;; `operation/commit-fact` reads the context at all, and it reads
   ;; `:actor-id`. Adding this key therefore cannot change a disposition.
   :approver context-approver})

(def ^:private scenario
  "Ordered scenario. Each entry is scenario INPUT (the operator request
  and the rollout phase); every disposition, verdict, violation and
  audit fact shown on the page is OUTPUT read back from the actor."
  [{:id "R01" :phase :phase-2
    :note "routine hive batch entry, reduced supervision"
    :request {:op :log-husbandry-record :facility-id "apiary-001"
              :count 24 :health-status "healthy"
              :husbandry-line "apiculture"}}

   {:id "R02" :phase :phase-0
    :note "same request under simulation rollout -- no autonomous commit"
    :request {:op :log-husbandry-record :facility-id "apiary-001"
              :count 24 :health-status "healthy"}}

   {:id "R03" :phase :phase-2
    :note "routine feeding schedule for the hutch bank"
    :request {:op :schedule-farm-operation :facility-id "rabbitry-002"
              :operation-type "feeding" :requested-date "2026-09-01"
              :reason "routine-schedule"}}

   {:id "R04" :phase :phase-3
    :note "notifiable disease suspicion -- escalates even at full autonomy"
    :request {:op :flag-animal-health-concern :facility-id "rabbitry-002"
              :concern "myxomatosis"}}

   {:id "R05" :phase :phase-2
    :note "hive equipment order under its category threshold"
    :request {:op :order-supplies :facility-id "furfarm-003"
              :category "husbandry-equipment" :cost 800}}

   {:id "R06" :phase :phase-2
    :note "feed order above its category threshold"
    :request {:op :order-supplies :facility-id "furfarm-003"
              :category "feed" :cost 1200}}

   {:id "R07" :phase :phase-2
    :note "advisor reports low confidence on a rearing schedule"
    :request {:op :schedule-farm-operation :facility-id "sericul-004"
              :operation-type "rearing-transfer" :requested-date "2026-10-15"
              :reason "seasonal-rearing-window"
              :demo/advisor-override {:confidence 0.55}}}

   {:id "R08" :phase :phase-2
    :note "zero batch count submitted -- not a real observation"
    :request {:op :log-husbandry-record :facility-id "sericul-004"
              :count 0 :health-status "unspecified"}}

   {:id "R09" :phase :phase-2
    :note "proposal targets a holding the Store cannot verify"
    :request {:op :log-husbandry-record :facility-id "apiary-999"
              :count 40 :health-status "healthy"}}

   {:id "R10" :phase :phase-3
    :note "direct treatment administration -- veterinary exclusive"
    :request {:op :administer-treatment :facility-id "apiary-001"}}

   {:id "R11" :phase :phase-3
    :note "finalizing a pelt-harvest/cull decision -- farm operator exclusive"
    :request {:op :order-cull-or-harvest :facility-id "furfarm-003"}}

   {:id "R12" :phase :phase-2
    :note "op outside the closed coordination allowlist"
    :request {:op :sell-hive :facility-id "gamefarm-005"}}

   {:id "R13" :phase :phase-3
    :note "advisor claims a direct actuation instead of a proposal"
    :request {:op :log-husbandry-record :facility-id "apiary-001"
              :count 90 :health-status "healthy"
              :demo/advisor-override {:effect :execute}}}

   {:id "R14" :phase :phase-9
    :note "misconfigured rollout phase -- conservative hold, no violation"
    :request {:op :log-husbandry-record :facility-id "rabbitry-002"
              :count 60 :health-status "healthy"}}

   {:id "R15" :phase :phase-1
    :note "Varroa suspicion under supervised rollout"
    :request {:op :flag-animal-health-concern :facility-id "apiary-001"
              :concern "varroa"}}

   {:id "R16" :phase :phase-3
    :note "unverifiable holding AND a permanently blocked op"
    :request {:op :administer-treatment :facility-id "apiary-999"}}

   {:id "R17" :phase :phase-3
    :note "game-farm batch entry at full autonomy"
    :request {:op :log-husbandry-record :facility-id "gamefarm-005"
              :count 132 :health-status "healthy"
              :husbandry-line "game-farming"}}])

;; --------------------------------------------------------------------
;; Driving the real stack
;; --------------------------------------------------------------------

(defn- facility-ids
  "Every facility id this run touches, seeded or not, in a stable order."
  []
  (concat (map first facility-seed) [unregistered-facility-id]))

(defn- store-snapshot
  "What the Store answers for every facility id this run touches."
  [st]
  (into [] (map (fn [id] [id (store/registered-facility st id)])) (facility-ids)))

(defn run-demo!
  "Runs the whole scenario against a freshly seeded MemStore through
  `otherlivestockops.operation/build`. Returns
  {:store .. :runs [..] :before .. :after ..} where each run carries the
  actor's real `:disposition`, `:verdict`, `:audit` and `:record`."
  []
  (let [st (store/mem-store {:initial-facilities (into {} facility-seed)})
        actor (operation/build st {:advisor (scenario-advisor)})
        before (store-snapshot st)
        runs (mapv (fn [{:keys [phase request] :as spec}]
                     (let [context (assoc operator-context-base :phase phase)]
                       (assoc spec
                              :context context
                              :result (actor request context))))
                   scenario)]
    {:store st :runs runs :before before :after (store-snapshot st)}))

;; --------------------------------------------------------------------
;; Derivations over the real run output
;; --------------------------------------------------------------------

(defn- advisor-fact [run] (first (get-in run [:result :audit])))
(defn- disposition-fact [run] (last (get-in run [:result :audit])))
(defn- verdict [run] (get-in run [:result :verdict]))
(defn- violations [run] (:violations (verdict run)))
(defn- disposition [run] (get-in run [:result :disposition]))

(defn- base-disposition
  "The disposition the Governor's verdict implies BEFORE the rollout
  phase gate -- recomputed from this run's real verdict with the very
  same pure function `otherlivestockops.operation` calls."
  [run]
  (phase/verdict->disposition (verdict run)))

(defn- phase-changed? [run]
  (not= (base-disposition run) (disposition run)))

(defn- hard-hold?
  "A Governor REFUSAL: the run ended in `:hold` AND the disposition fact
  the actor wrote is a `:governor-hold` carrying a non-empty
  `:violations`. Classified on the fact TYPE first and the violation
  payload second, because in this repo the rollout phase gate emits a
  fact of the same type with an empty `:violations`."
  [run]
  (let [f (disposition-fact run)]
    (boolean (and (= :hold (disposition run))
                  (= :governor-hold (:t f))
                  (seq (:violations f))))))

(defn- phase-only-hold?
  "A `:hold` the Governor did NOT ask for -- the rollout phase gate's
  conservative default for an unrecognised phase. Carries an empty
  `:violations`, which is exactly why the build invariant below cannot
  simply count `:governor-hold` facts."
  [run]
  (and (= :hold (disposition run)) (not (hard-hold? run))))

(defn- escalation-driver
  "Why this run needs a human, derived here from the run's own verdict
  plus the Governor's own vars -- deliberately NOT read from the audit
  fact's `:reason`, so the two can be compared on the page."
  [run]
  (let [v (verdict run)
        op (get-in run [:request :op])]
    (cond
      (not= :escalate (base-disposition run)) :rollout-phase-gate
      (contains? governor/always-escalate-ops op) :always-escalate-op
      (:high-stakes? v) :cost-above-threshold
      :else :low-confidence)))

(defn- reason-discrepancies
  "Escalations where the reason the actor RECORDED disagrees with the
  reason derived from the verdict. Measured, so the footnote about it
  disappears on its own if the actor is ever fixed."
  [runs]
  (into []
        (comp (filter #(= :escalate (disposition %)))
              (keep (fn [run]
                      (let [fact-reason (:reason (disposition-fact run))
                            derived (escalation-driver run)]
                        (when (and (not= fact-reason derived)
                                   ;; phase-gate reasons legitimately name the
                                   ;; gate rather than the driver
                                   (not (contains? #{:phase-0-simulation-only
                                                     :phase-1-always-escalate}
                                                   fact-reason)))
                          {:id (:id run) :fact fact-reason :derived derived})))))
        runs))

;; ---- attribution probe ----------------------------------------------

(def ^:private approver-key-candidates
  "Keys a store record, committed record or audit fact could plausibly
  use to name the HUMAN who signed off. Probed at render time so the
  disclosure self-corrects if this repo later grows an approval path.

  `:actor` is deliberately NOT in this list: this repo's `:committed`
  and `:governor-hold` facts carry `:actor`, but it is bound from
  `(:actor-id context)` -- the EXECUTING actor, not an approver.
  Counting it would turn 'nobody approved' into 'approved by the robot'."
  [:approver :approved-by :approval-by :signed-off-by :sign-off-by
   :reviewer :reviewed-by :authorized-by :authorised-by :human
   :operator :decided-by :decider :by])

(defn- deep-keys
  "Every map key appearing anywhere in `x`, at any depth."
  [x]
  (cond
    (map? x) (into (set (keys x)) (mapcat deep-keys (vals x)))
    (sequential? x) (into #{} (mapcat deep-keys x))
    (set? x) (into #{} (mapcat deep-keys x))
    :else #{}))

(defn- deep-values
  "Every non-collection value appearing anywhere in `x`, at any depth.
  Used to ask whether a value the caller HANDED the actor came back out
  the other side, independent of what key it might have arrived under."
  [x]
  (cond
    (map? x) (into #{} (mapcat deep-values (vals x)))
    (coll? x) (into #{} (mapcat deep-values x))
    :else #{x}))

(defn- attribution-probe
  "MEASURES what this repo's Store, committed records and audit facts
  actually retain about WHO acted, instead of asserting a known scaffold
  defect. Walks every fact and record this run produced (at every
  depth), the Store protocol's own method set, and a before/after Store
  snapshot."
  [{:keys [runs before after]}]
  (let [all-facts (mapcat #(get-in % [:result :audit]) runs)
        records (keep #(get-in % [:result :record]) runs)
        present (fn [ks] (filterv (set ks) approver-key-candidates))
        by-type (reduce (fn [m f] (update m (:t f) (fnil into #{}) (keys f)))
                        {} all-facts)]
    {:fact-types (into []
                       (map (fn [[t ks]]
                              (let [of-type (filter #(= t (:t %)) all-facts)]
                                {:t t
                                 :n (count of-type)
                                 :keys (vec (sort-by name ks))
                                 :actor? (contains? ks :actor)
                                 :approver-keys (present ks)
                                 :deep-approver-keys (present (mapcat deep-keys of-type))})))
                       (sort-by (comp name key) by-type))
     :record-keys (vec (sort-by name (into #{} (mapcat keys) records)))
     :record-approver-keys (present (mapcat keys records))
     :record-deep-keys (vec (sort-by name (into #{} (mapcat deep-keys) records)))
     :record-deep-approver-keys (present (mapcat deep-keys records))
     :records-returned (count records)
     :record-effects (vec (sort-by name (into #{} (map :effect) records)))
     ;; This repo's `commit-record` writes BOTH `:value` and `:payload`.
     ;; Measure whether they actually differ rather than assuming either.
     :value=payload? (every? #(= (:value %) (:payload %)) records)
     :payload-nil-count (count (filter #(nil? (:payload %)) records))
     :store-reads (vec (sort (map name (keys (:sigs store/Store)))))
     :store-mutated? (not= before after)
     :store-deep-approver-keys (present (deep-keys (map second after)))
     ;; Was an approver SUPPLIED, and did the actor carry it through?
     ;; Searched by VALUE across every fact, record and the post-run Store,
     ;; so it is found even if a future implementation files it under a
     ;; key this probe's candidate list never guessed.
     :context-approver context-approver
     :context-approver-supplied? (= context-approver
                                    (:approver operator-context-base))
     :context-approver-survived?
     (contains? (into (deep-values (vec all-facts))
                      (into (deep-values (vec records))
                            (deep-values (mapv second after))))
                context-approver)}))

;; --------------------------------------------------------------------
;; HTML
;; --------------------------------------------------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- kw [v] (if (keyword? v) (name v) (str v)))
(defn- code [v] (str "<code>" (esc v) "</code>"))
(defn- span [cls v] (str "<span class=\"" cls "\">" v "</span>"))
(defn- num [v] (str "<span class=\"num\">" (esc v) "</span>"))
(defn- muted [v] (span "muted" (esc v)))
(defn- dash [] (span "muted" "&#8212;"))

(defn- conf-str
  "Locale-independent so the page is byte-identical on any machine.
  `clojure.core/format` binds `*out*`'s default locale, which would make
  the decimal separator machine-dependent; `String/format` with an
  explicit ROOT locale does not."
  [c]
  (String/format java.util.Locale/ROOT "%.2f" (into-array Object [(double c)])))

(defn- tr [cells]
  (str "        <tr>" (apply str (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- section
  "One `<section class=\"card\">`. When `rows` is empty an explicit
  'none in this run' row is emitted -- an empty table must not read the
  same as a table that was never populated."
  [{:keys [title lede headers rows]}]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       (when lede (str "    <p class=\"muted\">" lede "</p>\n"))
       "    <table>\n"
       "      <thead><tr>" (apply str (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (if (seq rows)
         (str (str/join "\n" rows) "\n")
         (str "        <tr><td colspan=\"" (count headers) "\">"
              (muted "この実行では該当なし — no rows produced by this run")
              "</td></tr>\n"))
       "      </tbody>\n"
       "    </table>\n"
       "  </section>\n"))

(defn- disposition-badge [run]
  (case (disposition run)
    :commit   (span "ok" "commit")
    :escalate (span "warn" "escalate")
    :hold     (if (hard-hold? run)
                (span "critical" "HARD hold")
                (span "critical" "hold (phase gate)"))
    (muted (kw (disposition run)))))

;; ---- §1 registered facilities ---------------------------------------

(defn- last-run-for [runs fid]
  (last (filter #(= fid (get-in % [:request :facility-id])) runs)))

(defn- facility-rows [{:keys [store runs]}]
  (mapv (fn [fid]
          (let [rec (store/registered-facility store fid)
                line (some-> (:husbandry-line rec) facts/husbandry-line-by-id)
                lr (last-run-for runs fid)]
            (tr [(code fid)
                 (if rec (esc (:name rec)) (span "critical" "未登録 / not registered"))
                 (if rec (esc (:location rec)) (dash))
                 (cond
                   (nil? rec) (dash)
                   line (str (esc (:name line)) " " (span "muted" (code (:id line))))
                   :else (span "critical"
                               (str "unresolved husbandry-line id "
                                    (esc (:husbandry-line rec)))))
                 (if lr
                   ;; `span`, not `muted`: the content already contains markup
                   ;; from `code`, and `muted` escapes its argument.
                   (str (disposition-badge lr) " "
                        (span "muted" (str "· " (code (kw (get-in lr [:request :op]))))))
                   (muted "no activity"))])))
        (facility-ids)))

;; ---- §2 run ledger --------------------------------------------------

(defn- run-rows [runs]
  (mapv (fn [{:keys [id phase request] :as run}]
          (tr [(code id)
               (code (kw (:op request)))
               (code (:facility-id request))
               (code (kw phase))
               (num (conf-str (:confidence (advisor-fact run) 0.0)))
               (disposition-badge run)
               (code (kw (:t (disposition-fact run))))
               (muted (:note run))]))
        runs))

;; ---- §3 governor refusals -------------------------------------------

(defn- hard-hold-rows [runs]
  (into []
        (comp
         (filter hard-hold?)
         (mapcat (fn [{:keys [id request] :as run}]
                   (map (fn [{:keys [rule detail]}]
                          (tr [(code id)
                               (code (kw (:op request)))
                               (code (:facility-id request))
                               (span "critical" (esc (kw rule)))
                               (esc detail)]))
                        (violations run)))))
        runs))

;; ---- §4 rollout phase gate ------------------------------------------

(defn- phase-gate-rows [runs]
  (into []
        (comp
         (filter #(or (phase-only-hold? %)
                      (and (phase-changed? %) (not (hard-hold? %)))))
         (map (fn [{:keys [id phase request] :as run}]
                (let [f (disposition-fact run)]
                  (tr [(code id)
                       (code (kw phase))
                       (code (kw (:op request)))
                       (code (kw (base-disposition run)))
                       (disposition-badge run)
                       (code (kw (or (:phase-reason f) (:reason f) :none)))
                       (if (seq (violations run))
                         (span "critical" "yes")
                         (span "ok" "no — governor was clean"))])))))
        runs))

;; ---- §5 approval queue ----------------------------------------------

(defn- approval-rows [runs probe]
  (into []
        (comp
         (filter #(= :escalate (disposition %)))
         (map (fn [{:keys [id phase request] :as run}]
                (let [f (disposition-fact run)
                      fact-reason (:reason f)
                      derived (escalation-driver run)
                      approver (some #(get f %) approver-key-candidates)]
                  (tr [(code id)
                       (code (kw (:op request)))
                       (code (:facility-id request))
                       (code (kw phase))
                       (code (kw fact-reason))
                       (if (= fact-reason derived)
                         (code (kw derived))
                         (span "warn" (code (kw derived))))
                       (cond
                         approver (esc approver)

                         ;; An approver WAS handed to the actor in the
                         ;; context and did not survive into the fact.
                         ;; Derived from the probe, so this cell turns
                         ;; into the approver's name by itself if the
                         ;; actor is ever taught to retain it.
                         (and (:context-approver-supplied? probe)
                              (not (:context-approver-survived? probe)))
                         (str (span "muted" (esc (:context-approver probe))) " "
                              (span "critical"
                                    "(audit only — not retained in record)"))

                         :else (dash))])))))
        runs))

;; ---- §6 committed records -------------------------------------------

(defn- commit-rows [runs]
  (into []
        (comp
         (filter #(= :commit (disposition %)))
         (map (fn [{:keys [id request] :as run}]
                (let [r (get-in run [:result :record])
                      f (disposition-fact run)]
                  (tr [(code id)
                       (code (kw (:op request)))
                       (code (str/join " / " (:path r)))
                       (code (kw (:effect r)))
                       (esc (str/join ", " (sort (map kw (keys (:value r))))))
                       (if (= (:value r) (:payload r))
                         (span "ok" "identical")
                         (span "warn" "differs"))
                       (code (kw (:actor f)))])))))
        runs))

;; ---- §7 audit ledger ------------------------------------------------

(defn- fact-detail
  "The salient field of a fact, chosen by its own `:t` and read from the
  fact itself -- never from the scenario."
  [f]
  (case (:t f)
    :advisor-proposal   (str (muted "confidence ") (num (conf-str (:confidence f 0.0))))
    :governor-hold      (if (seq (:basis f))
                          (span "critical"
                                (esc (str/join ", " (map kw (:basis f)))))
                          (str (span "warn" "no violation") " "
                               (span "muted" (code (kw (or (:phase-reason f) :none))))))
    :approval-requested (code (kw (:reason f)))
    :committed          (esc (:summary f))
    (dash)))

(defn- ledger-rows [runs]
  (into []
        (comp
         (mapcat (fn [run]
                   (map (fn [f] [run f]) (get-in run [:result :audit]))))
         (map-indexed
          (fn [i [run f]]
            (tr [(num (inc i))
                 (code (:id run))
                 (code (kw (:t f)))
                 (code (kw (:op f)))
                 (code (or (:subject f) (:facility-id f)))
                 (fact-detail f)]))))
        runs))

;; ---- §8 attribution probe -------------------------------------------

(defn- attribution-rows
  [{:keys [fact-types record-keys record-deep-keys record-deep-approver-keys
           store-reads store-mutated? records-returned record-effects
           value=payload? payload-nil-count store-deep-approver-keys
           context-approver context-approver-supplied?
           context-approver-survived?] :as probe}]
  (into
   (mapv (fn [{:keys [t n keys actor? deep-approver-keys]}]
           (tr [(code (kw t))
                (num n)
                (esc (str/join ", " (map kw keys)))
                (if actor? (span "warn" "yes — executing actor") (span "muted" "no"))
                (if (seq deep-approver-keys)
                  (span "ok" (esc (str/join ", " (map kw deep-approver-keys))))
                  (span "critical" "none"))]))
         fact-types)
   [(tr [(code ":record (returned by run-operation)")
         (num records-returned)
         (esc (str/join ", " (map kw record-deep-keys)))
         (span "muted" "no")
         (if (seq record-deep-approver-keys)
           (span "ok" (esc (str/join ", " (map kw record-deep-approver-keys))))
           (span "critical" "none"))])
    (tr [(code ":record top-level keys")
         (num (count record-keys))
         (esc (str/join ", " (map kw record-keys)))
         (span "muted" (str "effects: " (esc (str/join ", " (map kw record-effects)))))
         (if value=payload?
           (span "ok" (str ":value and :payload identical on all "
                           records-returned " records"))
           (span "warn" (str ":value and :payload differ; "
                             payload-nil-count " empty :payload")))])
    (tr [(code "otherlivestockops.store/Store reads")
         (num (count store-reads))
         (esc (str/join ", " store-reads))
         (span "muted" "n/a")
         (span "critical" "no approval register to read")])
    (tr [(code "Store contents after the run")
         (num (if store-mutated? 1 0))
         (if store-mutated?
           (span "warn" "changed during the run")
           (esc "identical to the seed — no committed record was persisted"))
         (span "muted" "n/a")
         (if (seq store-deep-approver-keys)
           (span "ok" (esc (str/join ", " (map kw store-deep-approver-keys))))
           (span "critical" "none"))])
    ;; The control row: an approver was HANDED to the actor. Searching for
    ;; it by value (not by key) across every fact, record and the post-run
    ;; Store separates "the scenario never named a human" from "the actor
    ;; was told and dropped it".
    (tr [(code ":approver supplied in the run context")
         (num (count (filter identity [context-approver-supplied?])))
         (if context-approver-supplied?
           (str (span "ok" "supplied to all runs: ") (code context-approver))
           (span "warn" "not supplied — the probe below proves nothing"))
         (span "muted" "n/a")
         (cond
           (not context-approver-supplied?) (dash)
           context-approver-survived?
           (span "ok" "survived — found in the run output")
           :else
           (span "critical"
                 (str "DROPPED — handed in, found in none of the "
                      (reduce + (map :n fact-types))
                      " emitted facts, none of the " records-returned
                      " records, and nowhere in the Store")))])
    (tr [(code "probe search method")
         (num 2)
         (esc (str "by key (" (count approver-key-candidates)
                   " candidate approver key names) AND by value "
                   "(deep scan for the supplied approver string)"))
         (span "muted" "n/a")
         (span "muted"
               "a value scan cannot be defeated by an unguessed key name")])]))

;; ---- §9 closed op contract ------------------------------------------

(defn- op-contract-rows []
  (mapv (fn [op]
          (tr [(code (kw op))
               (cond
                 (contains? governor/blocked-ops op)
                 (span "critical" "HARD blocked — permanent, never escalates")
                 (contains? governor/always-escalate-ops op)
                 (span "warn" "ALWAYS human sign-off at every phase")
                 :else (span "ok" "may auto-commit when the Governor is clean"))
               (if (contains? governor/blocked-ops op)
                 (esc "treatment-or-harvest-blocked")
                 (dash))]))
        (vec (sort-by name governor/all-recognized-ops))))

;; ---- §10/§11/§12 reference data -------------------------------------

(defn- supply-rows []
  (mapv (fn [[id c]] (tr [(code id) (esc (:name c)) (num (:cost-threshold c))]))
        (sort-by key facts/supply-categories)))

(defn- concern-rows []
  (mapv (fn [[id c]]
          (tr [(code id) (esc (:name c))
               (if (:notifiable c)
                 (span "warn" "notifiable")
                 (muted "not notifiable"))]))
        (sort-by key facts/health-concerns)))

(defn- husbandry-line-rows []
  (let [seeded (frequencies (keep (comp :husbandry-line second) facility-seed))]
    (mapv (fn [[id l]]
            (tr [(code id) (esc (:name l))
                 (if-let [n (get seeded id)]
                   (num n)
                   (muted "0"))]))
          (sort-by key facts/husbandry-lines))))

;; ---- document -------------------------------------------------------

(defn render
  "Renders the whole operator console from the output of `run-demo!`."
  [{:keys [runs] :as run-data}]
  (let [probe (attribution-probe run-data)
        hard (filterv hard-hold? runs)
        hard-rules (vec (sort (distinct (map (comp name :rule)
                                             (mapcat violations hard)))))
        phase-holds (filterv phase-only-hold? runs)
        n-commit (count (filter #(= :commit (disposition %)) runs))
        n-escalate (count (filter #(= :escalate (disposition %)) runs))
        discrepancies (reason-discrepancies runs)]
    (str
     "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
     "<meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
     "<meta name=\"color-scheme\" content=\"light\">\n"
     "<title>cloud-itonami-isic-0149 · Other-Livestock Farm Operations — Operator Console</title>\n"
     "<style>" (skin/dds+skin) "</style>\n"
     "</head>\n<body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Raising of other animals (ISIC 0149) — Operator Console</h1>\n"
     "</header>\n"
     "<p class=\"subtitle\"><span class=\"badge\">read-only sample</span> "
     "<span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">treatment &amp; cull/harvest permanently blocked</span> "
     "<span class=\"badge\">health concerns always human-reviewed</span></p>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>This run at a glance</h2>\n"
     "    <p>Generated by <code>otherlivestockops.render-html</code> "
     "(<code>clojure -M:render-html</code>) by driving "
     "<code>otherlivestockops.operation</code> → <code>advisor</code> → <code>governor</code> → "
     "<code>phase</code> → <code>store</code>. Every id, count, cost, disposition and "
     "Japanese hold detail below is read back from that run; nothing on this page is typed by hand.</p>\n"
     "    <ul>\n"
     "      <li><strong>" (num (count runs)) "</strong> operations run across <strong>"
     (num (count facility-seed)) "</strong> registered holdings plus one unregistered id "
     (code unregistered-facility-id) ".</li>\n"
     "      <li><strong>" (span "critical" (num (count hard))) "</strong> HARD Governor refusals, covering <strong>"
     (num (count hard-rules)) "</strong> distinct rules: "
     (str/join ", " (map code hard-rules)) ". None of these reached a human.</li>\n"
     "      <li><strong>" (span "ok" (num n-commit)) "</strong> autonomous commits and <strong>"
     (span "warn" (num n-escalate)) "</strong> escalations queued for a farm operator / veterinarian.</li>\n"
     "      <li><strong>" (num (count phase-holds)) "</strong> rollout phase-gate hold"
     (when (not= 1 (count phase-holds)) "s") " — counted separately from the refusals above, "
     "because in this repo the phase gate emits the same <code>:governor-hold</code> fact type "
     "with an empty <code>:violations</code>.</li>\n"
     "    </ul>\n"
     "  </section>\n"

     (section
      {:title "1 · Registered holdings (facility register)"
       :lede (str "Read back through <code>otherlivestockops.store/registered-facility</code> — not printed "
                  "from the seed literal. Husbandry lines are resolved through "
                  "<code>otherlivestockops.facts/husbandry-line-by-id</code>.")
       :headers ["Facility id" "Name" "Location" "Husbandry line" "Last operation"]
       :rows (facility-rows run-data)})

     (section
      {:title "2 · Operations run (scenario ledger)"
       :lede (str "Left of the disposition column is scenario INPUT (request + rollout phase); "
                  "confidence, disposition and audit-fact type are actor OUTPUT. Confidence is taken from "
                  "the <code>:advisor-proposal</code> fact the run actually emitted, not from the scenario.")
       :headers ["Run" "Op" "Facility" "Phase" "Confidence" "Disposition" "Audit fact" "Scenario"]
       :rows (run-rows runs)})

     (section
      {:title "3 · Governor refusals (HARD holds — never reach a human)"
       :lede (str "One row per violation. The <em>detail</em> column is the literal <code>:detail</code> "
                  "string <code>otherlivestockops.governor</code> emitted during this run. A HARD hold "
                  "cannot be overridden by confidence, by cites, or by advancing the rollout phase.")
       :headers ["Run" "Op" "Facility" "Rule" "Governor detail (verbatim)"]
       :rows (hard-hold-rows runs)})

     (section
      {:title "4 · Rollout phase gate (a different thing from a refusal)"
       :lede (str "<code>otherlivestockops.phase</code> can hold or escalate a proposal the Governor found "
                  "<em>clean</em>. The last column is the reason this table is separate: a phase-gate hold "
                  "is written as a <code>:governor-hold</code> fact with an EMPTY <code>:violations</code> "
                  "vector, so counting that fact type alone would report a refusal that never happened.")
       :headers ["Run" "Phase" "Op" "Governor said" "Phase gate said" "Phase reason" "Governor violation?"]
       :rows (phase-gate-rows runs)})

     (section
      {:title "5 · Human approval queue (escalations)"
       :lede (str "Escalated to a farm operator / veterinarian. <em>Fact reason</em> is what the actor wrote "
                  "into the <code>:approval-requested</code> fact; <em>derived driver</em> is recomputed here "
                  "from the run's own verdict and <code>otherlivestockops.governor/always-escalate-ops</code>. "
                  "A highlighted derived driver marks a row where the two disagree.")
       :headers ["Run" "Op" "Facility" "Phase" "Fact reason" "Derived driver" "Approver on record"]
       :rows (approval-rows runs probe)})

     (section
      {:title "6 · Committed records (autonomous path)"
       :lede (str "The record <code>otherlivestockops.operation/run-operation</code> returned for each "
                  "auto-committed proposal. <em>Value fields</em> lists the keys actually present on the "
                  "record's <code>:value</code>; <em>:payload</em> compares that map against the record's "
                  "second copy; <em>Actor</em> is the <code>:actor</code> the <code>:committed</code> fact "
                  "carries — the executing actor, not an approver.")
       :headers ["Run" "Op" "Record path" "Effect" "Value fields" ":payload vs :value" "Actor"]
       :rows (commit-rows runs)})

     (section
      {:title "7 · Audit ledger (every fact this run emitted, in order)"
       :lede (str "Two facts per operation: the advisor's proposal trace, then the disposition fact. "
                  "The <em>detail</em> column is selected by each fact's own <code>:t</code> and read from "
                  "that fact. This is the complete ledger the attribution probe below scans.")
       :headers ["#" "Run" "Fact" "Op" "Subject" "Detail"]
       :rows (ledger-rows runs)})

     (section
      {:title "8 · Who-acted attribution (measured, not assumed)"
       :lede (str "Probed at render time by walking every fact and record this run produced <em>at every "
                  "depth</em>, the <code>otherlivestockops.store/Store</code> protocol's own method set, and "
                  "a before/after Store snapshot. If this repo later grows an approval path these rows change "
                  "on their own — nothing here is a hard-coded claim about the scaffold. "
                  "<code>:actor</code> is reported in its own column and is deliberately excluded from the "
                  "approver probe: it is bound from <code>(:actor-id context)</code>, the executing actor.")
       :headers ["Surface" "Count" "Keys present (all depths)" "Names the acting actor?" "Names a human approver?"]
       :rows (attribution-rows probe)})

     "  <section class=\"card\">\n"
     "    <h3>What the probe above means</h3>\n"
     "    <p>Commits and holds do name the acting actor (<code>:actor "
     (esc (:actor-id operator-context-base))
     "</code>), so the audit trail is not anonymous. But "
     (if (seq (:record-deep-approver-keys probe))
       (str "an approver-shaped key <strong>was</strong> found: "
            (code (str/join ", " (map kw (:record-deep-approver-keys probe))))
            ". The disclosure below no longer applies and should be re-read against the code.")
       (str "<strong>no human approver is recorded anywhere</strong>, at any depth, on any fact or "
            "record this run produced — and that is a measured drop, not merely an absence: every "
            "run in this scenario was handed <code>:approver "
            (esc (:context-approver probe))
            "</code> in its context, and a deep scan by value finds it in "
            (if (:context-approver-survived? probe)
              "the run output."
              "none of the emitted facts, none of the returned records, and nowhere in the Store. ")
            "The reason is structural rather than a single dropped field: "
            "<code>otherlivestockops.store/Store</code> exposes "
            (num (count (:store-reads probe))) " read method"
            (when (not= 1 (count (:store-reads probe))) "s")
            " (<code>" (esc (str/join ", " (:store-reads probe))) "</code>) and <em>no write method at "
            "all</em> — <code>add-facility</code> mutates the MemStore atom directly, outside the "
            "protocol — there is no resume/approve entry point in the repo, and the "
            "<code>:approval-requested</code> fact has no approver slot to fill."))
     "</p>\n"
     "    <p>"
     (if (:store-mutated? probe)
       "The Store DID change during this run."
       (str "The Store is identical before and after the run, and the "
            (num (:records-returned probe))
            " committed record(s) exist only as return values of "
            "<code>run-operation</code> — <strong>nothing this actor commits is persisted</strong>. "
            "A reader should not have to guess whether nobody approved or whether the approval was "
            "lost: for this build, <em>no approval can be recorded at all</em>, and no commit "
            "survives the call that produced it."))
     "</p>\n"
     "  </section>\n"

     (section
      {:title "9 · Closed op contract"
       :lede (str "Derived from <code>otherlivestockops.governor/all-recognized-ops</code>, "
                  "<code>/blocked-ops</code> and <code>/always-escalate-ops</code> — the same vars the "
                  "Governor checks against, so this table cannot drift from the enforced allowlist. "
                  "Anything outside this union is <code>op-not-allowed</code>.")
       :headers ["Op" "Gate" "Hard rule"]
       :rows (op-contract-rows)})

     (section
      {:title "10 · Supply-order escalation thresholds"
       :lede (str "From <code>otherlivestockops.facts/supply-categories</code>. An order at or below its "
                  "threshold may auto-commit; above it escalates. Unknown categories fall back to the "
                  "conservative default <code>" (num facts/default-cost-threshold) "</code>. Checked by "
                  "<code>otherlivestockops.registry/cost-exceeds-threshold?</code>, never taken from the "
                  "advisor.")
       :headers ["Category id" "Name" "Escalation threshold"]
       :rows (supply-rows)})

     (section
      {:title "11 · Health / biosecurity concern vocabulary"
       :lede (str "From <code>otherlivestockops.facts/health-concerns</code>. Purely descriptive: notifiable "
                  "status does NOT change the disposition — <em>every</em> flagged concern escalates, at "
                  "every phase. This actor never declares an outbreak, orders a cull or harvest, or contacts "
                  "animal-health authorities.")
       :headers ["Concern id" "Name" "Notifiable"]
       :rows (concern-rows)})

     (section
      {:title "12 · Husbandry line reference (ISIC 0149 is a residual category)"
       :lede (str "From <code>otherlivestockops.facts/husbandry-lines</code> — the ids the holdings above "
                  "resolve against. The rightmost column counts how many seeded holdings in this run are "
                  "registered under each line, so an unexercised line is visible as such.")
       :headers ["Line id" "Name" "Seeded holdings in this run"]
       :rows (husbandry-line-rows)})

     "</main>\n"
     "<footer class=\"footer\">\n"
     "  <p>Regenerate with <code>clojure -M:render-html</code>. The page contains no timestamps and no "
     "per-run identifiers, and is byte-identical across reruns from the same seed. <code>-main</code> "
     "refuses to write unless the run produced at least one <code>:governor-hold</code> fact carrying a "
     "non-empty <code>:violations</code> vector, at least one commit and at least one escalation — a "
     "phase-gate hold with empty violations does not satisfy it.</p>\n"
     (when (seq discrepancies)
       (str "  <p><strong>Known defect, surfaced not patched:</strong> "
            "<code>otherlivestockops.operation/run-operation</code> derives an escalation's "
            "<code>:reason</code> from <code>:high-stakes?</code>, which "
            "<code>otherlivestockops.governor/check</code> sets for BOTH an always-escalate op and a cost "
            "above threshold. A cost-driven escalation is therefore recorded as "
            "<code>:always-escalate</code>. Measured in this run on "
            (str/join ", " (map #(code (:id %)) discrepancies))
            " — see the highlighted <em>derived driver</em> cells in section 5.</p>\n"))
     "  <p><strong>Second finding, surfaced not patched:</strong> the rollout phase gate's conservative "
     "hold for an unrecognised phase is written as a <code>:governor-hold</code> fact, the same type the "
     "Governor uses for a real refusal. The two are only separable by the emptiness of "
     "<code>:violations</code> (section 4). Any downstream consumer counting fact types alone will "
     "over-report refusals.</p>\n"
     "  <p>cloud-itonami-isic-0149 · AGPL-3.0-or-later · UI: "
     "<a href=\"https://github.com/kotoba-lang/jp-go-digital-design-system\">jp-go-digital-design-system</a> "
     "(デジタル庁デザインシステム).</p>\n"
     "</footer>\n"
     "</body>\n</html>\n")))

;; --------------------------------------------------------------------
;; entrypoint
;; --------------------------------------------------------------------

(defn assert-invariants!
  "Build-time invariant, two-stage on purpose. A phase-gating hold
  (`:unknown-phase`) produces a `:governor-hold` fact with an EMPTY
  `:violations`, so a naive hold count would pass on a run in which the
  Governor never actually refused anything.

  Public so a test can call it; throws rather than returning a flag, so
  a failing run leaves no file behind."
  [runs]
  (let [all-facts (mapcat #(get-in % [:result :audit]) runs)
        holds (filterv #(= :governor-hold (:t %)) all-facts)
        substantive (filterv #(seq (:violations %)) holds)
        commits (filterv #(= :committed (:t %)) all-facts)
        escalations (filterv #(= :approval-requested (:t %)) all-facts)]
    (when (empty? holds)
      (throw (ex-info "render-html: scenario produced no :governor-hold facts at all"
                      {:runs (count runs)})))
    (when (empty? substantive)
      (throw (ex-info (str "render-html: every hold this run produced carried an empty "
                           ":violations vector — those are rollout phase-gate holds, not "
                           "Governor refusals")
                      {:holds (count holds)
                       :phase-reasons (vec (distinct (keep :phase-reason holds)))})))
    (when (empty? commits)
      (throw (ex-info "render-html: scenario produced no committed (autonomous) path"
                      {:runs (count runs)})))
    (when (empty? escalations)
      (throw (ex-info "render-html: scenario produced no human-approval path"
                      {:runs (count runs)})))
    {:holds (count holds)
     :hard-holds (count substantive)
     :commits (count commits)
     :escalations (count escalations)
     :rules (vec (sort (distinct (map (comp name :rule)
                                      (mapcat :violations substantive)))))}))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [runs] :as run-data} (run-demo!)
        ;; Throws BEFORE anything is written: a run that fails the
        ;; invariant must leave no file behind.
        stats (assert-invariants! runs)
        html (render run-data)]
    (io/make-parents out)
    (spit out html)
    (println "wrote" out
             (str "(" (count runs) " operations, "
                  (:hard-holds stats) " HARD governor refusals over rules "
                  (str/join "/" (:rules stats)) ", "
                  (:commits stats) " commits, "
                  (:escalations stats) " escalations)"))))
