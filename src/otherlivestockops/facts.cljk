(ns otherlivestockops.facts
  "Reference facts for other-livestock farm operations coordination: supply
  category cost policy, husbandry-line classification, and
  health/biosecurity reference vocabulary. This namespace contains pure
  lookup functions for domain reference data -- the Governor and Advisor
  consult these instead of inventing thresholds. Mirrors `swineops.facts`
  (cloud-itonami-isic-0145) in shape.

  ISIC 0149 'Raising of other animals' is a residual (n.e.c.) category:
  any livestock raising not already classified under cattle/buffaloes
  (0141), horses/equines (0142), camels/camelids (0143), sheep/goats
  (0144), swine (0145), or poultry (0146). `husbandry-lines` below lists
  several illustrative product lines this actor's facility/holding
  records may cover; the README picks apiculture (beekeeping/honey
  production) as the one concrete worked illustration, but the same
  facility-registration + op-allowlist abstraction applies unchanged to
  the others (rabbit/fur-animal husbandry, sericulture, game farming).")

(def husbandry-lines
  "Illustrative other-animal husbandry product lines this actor's
  facility/holding records may cover (ISIC 0149: raising of other
  animals, not elsewhere classified). Purely descriptive -- the Governor
  and closed op-allowlist behave identically regardless of which line a
  holding is registered under."
  {"apiculture"     {:id "apiculture"     :name "養蜂 (apiculture / beekeeping)"}
   "rabbit"         {:id "rabbit"         :name "うさぎ飼育 (rabbit husbandry)"}
   "fur-animal"     {:id "fur-animal"     :name "毛皮動物飼育 (fur-animal husbandry)"}
   "sericulture"    {:id "sericulture"    :name "養蚕 (sericulture / silkworm rearing)"}
   "game-farming"   {:id "game-farming"   :name "その他の飼育動物 (game farming)"}})

(defn husbandry-line-by-id [id]
  (get husbandry-lines id))

(def supply-categories
  "Procurement categories this actor may propose orders for, and the
  default cost threshold above which an order proposal must escalate for
  human sign-off (farm operator/veterinarian)."
  {"feed"
   {:id "feed" :name "飼料" :cost-threshold 500}

   "veterinary-supply"
   {:id "veterinary-supply" :name "獣医用品" :cost-threshold 500}

   "husbandry-equipment"
   {:id "husbandry-equipment" :name "飼育設備 (hives/hutches/trays)" :cost-threshold 1000}})

(defn supply-category-by-id [id]
  (get supply-categories id))

(def default-cost-threshold
  "Fallback escalation threshold used when a supply-order proposal doesn't
  cite a known category (never invent a lower bar than this)."
  500)

(def health-concerns
  "Reference vocabulary for common other-livestock health/biosecurity
  concerns this actor's :flag-animal-health-concern op may cite (e.g.
  suspected Varroa mite infestation in an apiary, or a fresh mortality
  spike). Purely descriptive -- citing a concern (or leaving it free
  text) NEVER changes the Governor's disposition: EVERY flagged concern
  always escalates for veterinary/farm-operator review
  (`otherlivestockops.governor/always-escalate-ops`), regardless of the
  concern's `:notifiable` status or apparent severity. This actor has no
  authority to declare an outbreak, order a cull/harvest, or contact
  animal-health authorities -- it only surfaces the observation for
  human/veterinary judgment."
  {"varroa"     {:id "varroa"     :name "ミツバチヘギイタダニ (Varroa mite)" :notifiable false}
   "foulbrood"  {:id "foulbrood"  :name "アメリカ腐蛆病 (American Foulbrood)" :notifiable true}
   "myxomatosis" {:id "myxomatosis" :name "粘液腫症 (Myxomatosis)" :notifiable true}
   "rhd"        {:id "rhd"        :name "ウサギ出血病 (Rabbit Haemorrhagic Disease)" :notifiable true}
   "pebrine"    {:id "pebrine"    :name "微粒子病 (Pebrine, silkworm)" :notifiable false}})

(defn health-concern-by-id [id]
  (get health-concerns id))
