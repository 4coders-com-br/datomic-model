(ns rad-class.model.category
  "Category — the smallest possible RAD entity: an identity + one field +
   one 'give me all of them' resolver. Three attributes ARE the whole model:
   Datomic schema, Pathom resolvers, form fields and report columns are all
   derived from them."
  (:require
    [com.fulcrologic.rad.attributes :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.database-adapters.datomic-options :as do]
    #?(:clj [datomic.api :as d])))

(defattr id :category/id :uuid
  {ao/identity? true                 ; row identity → :db.unique/identity in Datomic
   ao/schema    :production})        ; which Datomic db this attribute lives in

(defattr label :category/label :string
  {ao/identities #{:category/id}     ; reachable from a :category/id — feeds resolver generation
   ao/schema     :production
   ao/required?  true})

;; A "virtual" attribute: no storage, just a resolver. Reports point their
;; ro/source-attribute here.
(defattr all-categories :category/all-categories :ref
  {ao/target    :category/id
   ao/pc-output [{:category/all-categories [:category/id]}]
   ao/pc-resolve
   (fn [env _]
     #?(:clj  (let [db (some-> (get-in env [do/databases :production]) deref)]
                {:category/all-categories
                 (mapv (fn [id] {:category/id id})
                   (d/q '[:find [?id ...] :where [_ :category/id ?id]] db))})
        :cljs nil))})

(def attributes [id label all-categories])
