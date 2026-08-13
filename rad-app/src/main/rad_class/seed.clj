(ns rad-class.seed
  "A handful of rows so reports have something to show. Plain d/transact —
   note the schema it targets was written by RAD, not by us."
  (:require
    [datomic.api :as d]
    [rad-class.server :as server]))

(defn seed! []
  (let [conn (server/connection)]
    @(d/transact conn
       [{:db/id "tools"    :category/id (random-uuid) :category/label "Tools"}
        {:db/id "hardware" :category/id (random-uuid) :category/label "Hardware"}
        {:db/id "garden"   :category/id (random-uuid) :category/label "Garden"}
        {:item/id (random-uuid) :item/name "Hammer"        :item/price 39.90M :item/quantity 12 :item/category "tools"}
        {:item/id (random-uuid) :item/name "Screwdriver"   :item/price 19.50M :item/quantity 30 :item/category "tools"}
        {:item/id (random-uuid) :item/name "M6 bolt (100)" :item/price  8.75M :item/quantity 84 :item/category "hardware"}
        {:item/id (random-uuid) :item/name "Garden hose"   :item/price 55.00M :item/quantity  7 :item/category "garden"}])
    :seeded))
