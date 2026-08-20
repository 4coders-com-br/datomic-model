(ns rad-class.model.item
  "Inventory item — same shape as category, plus a scalar spread (:string,
   :decimal, :int) and a :ref to category so the form gets a picker."
  (:require
    [com.fulcrologic.rad.attributes :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.database-adapters.datomic-options :as do]
    #?(:clj [datomic.api :as d])))

(defattr id :item/id :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr item-name :item/name :string
  {ao/identities #{:item/id}
   ao/schema     :production
   ao/required?  true})

(defattr price :item/price :decimal
  {ao/identities #{:item/id}
   ao/schema     :production})

(defattr quantity :item/quantity :int
  {ao/identities #{:item/id}
   ao/schema     :production})

(defattr category :item/category :ref
  {ao/target      :category/id     ; ref target — the form renders this as a picker
   ao/cardinality :one
   ao/identities  #{:item/id}
   ao/schema      :production})

(defattr in-stock? :item/in-stock? :boolean
  {ao/identities #{:item/id}
   ao/schema     :production})

(defattr tags :item/tags :string
  {ao/identities #{:item/id}
   ao/cardinality :many          ; ← the only new idea
   ao/schema      :production})

(defattr all-items :item/all-items :ref
  {ao/target    :item/id
   ao/pc-output [{:item/all-items [:item/id]}]
   ao/pc-resolve
   (fn [env _]
     #?(:clj  (let [db (some-> (get-in env [do/databases :production]) deref)]
                {:item/all-items
                 (mapv (fn [id] {:item/id id})
                   (d/q '[:find [?id ...] :where [_ :item/id ?id]] db))})
        :cljs nil))})

(def attributes [id item-name price quantity category  in-stock? all-items])
