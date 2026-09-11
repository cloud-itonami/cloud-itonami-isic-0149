(ns otherlivestockops.advisor
  "OtherLivestockOpsAdvisor -- the contained LLM/decision node. This
  actor's intelligence layer proposes back-office coordination actions
  (husbandry batch record logging, feeding/breeding/harvest scheduling,
  health/biosecurity-concern flags, supply procurement) based on
  facility/holding state and operator input. The advisor is SEALED into
  the `:advise` step of the operation flow; every proposal is routed
  through the independent Governor before committing.

  The advisor makes proposals but has NO direct authority. Proposals are
  always censored by:
    1. Governor (facility/holding registration, closed-op allowlist,
       cost/health gates)
    2. Phase gate (rollout stage)
    3. Human operator (for escalated actions)

  Current implementation is a mock advisor for testing. Production should
  use langchain/Claude or similar LLM backend (same seam point as
  `swineops.advisor`, cloud-itonami-isic-0145)."
  )

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with :op, :effect,
    :value, :cites, :summary, :confidence (plus any op-specific top-level
    keys the Governor independently verifies, e.g. :count/:cost)."))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op facility-id]} request]
      (case op
        :log-husbandry-record
        {:op :log-husbandry-record
         :effect :propose
         :count (:count request 0)
         :value {:facility-id facility-id
                 :count (:count request 0)
                 :weight (:weight request nil)
                 :health-status (:health-status request "unspecified")
                 :husbandry-line (:husbandry-line request nil)}
         :cites ["operator-submitted-count"]
         :summary "Feeding/breeding/health-check batch entry logged from operator submission"
         :confidence 0.9}

        :schedule-farm-operation
        {:op :schedule-farm-operation
         :effect :propose
         :value {:facility-id facility-id
                 :requested-date (:requested-date request)
                 :operation-type (:operation-type request "feeding")
                 :reason (:reason request "routine-schedule")}
         :cites ["operator-scheduling-request"]
         :summary "Feeding/breeding/harvest (e.g. honey extraction, pelt harvest) scheduling proposed per operator request"
         :confidence 0.85}

        :flag-animal-health-concern
        {:op :flag-animal-health-concern
         :effect :propose
         :concern (:concern request "unspecified concern")
         :value {:facility-id facility-id
                 :concern (:concern request "unspecified concern")
                 :recommended-action "veterinary-review"}
         :cites ["operator-observation"]
         :summary "Animal health/biosecurity concern (e.g. suspected Varroa mite infestation) flagged for veterinary/farm-operator review"
         :confidence 0.8}

        :order-supplies
        {:op :order-supplies
         :effect :propose
         :cost (:cost request 0)
         :value {:facility-id facility-id
                 :category (:category request "feed")
                 :cost (:cost request 0)}
         :cites ["operator-procurement-request"]
         :summary "Supply order (feed/veterinary-supply/husbandry-equipment) proposed for facility"
         :confidence 0.85}

        ;; fallback -- unrecognized op. The Governor's closed allowlist
        ;; independently rejects this regardless of what the advisor says.
        {:op op
         :effect :propose
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :facility-id (:facility-id request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
