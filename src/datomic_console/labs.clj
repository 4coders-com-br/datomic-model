(ns datomic-console.labs
  "DATOMIC CONSOLE — Wall to Wall
   ════════════════════════════════════════════════════════════════════
   Companion for a 2-hour, Console-first class that walks every surface
   of the Datomic Console: schema tree, query builder, entities,
   transactions, indexes, as-of/since/history, and data sources.

   The Console is a separate process. This namespace is the *seed and
   verifier* — you plant a rich database here, then spend the class
   in the browser. Slide anchors match the deck (`◆ CONSOLE §n`).

   Unlike the domain-modeling class, this one needs a real transactor
   and storage. Reuse the Production class infra:

     docker compose -f infra/docker-compose.yml up -d
     $DATOMIC/bin/transactor infra/pg-transactor.properties
     clj -M:infra:repl
     (require 'datomic-console.labs)
     (datomic-console.labs/seed!)

   Then start the Console (storage URI, NO database name):

     $DATOMIC/bin/console -p 8080 \\
       pg \"datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic\"

   Open http://localhost:8080/browse/  ·  storage: pg  ·  DB: store

   ── Conventions ────────────────────────────────────────────────────
   * `seed!` is the one side-effect you run before class. It is
     idempotent: delete + recreate `store` and `warehouse`.
   * Everything else lives in (comment ...) — evaluate to VERIFY what
     Console just showed you, not to drive the class.
   * `;; =>` shapes are from one rehearsal. Entity ids and t values
     WILL differ; the shapes won't.
   * Keep a second terminal on the Console process. Its stdout is quiet
     once started; errors show up there."
  (:require [datomic.api :as d]
            [clojure.pprint :refer [pprint]]))

;; ═════════════════════════════════════════════════════════════════════
;; SETUP — same storage as the Production class
;; ═════════════════════════════════════════════════════════════════════

(def jdbc
  "jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic")

(defn uri
  "Datomic URI for one named database in SQL storage.
   Two question marks is correct: the second opens the JDBC params."
  [db-name]
  (str "datomic:sql://" db-name "?" jdbc))

(def system-uri
  "Storage-level URI (db name = *) — list / admin only."
  (uri "*"))

(def store-uri     (uri "store"))
(def warehouse-uri (uri "warehouse"))

;; ═════════════════════════════════════════════════════════════════════
;; SCHEMA — designed so every Console surface has something to show
;; ═════════════════════════════════════════════════════════════════════
;;
;; Why these choices (map each to a Console demo):
;;   :product/sku unique/identity  → AVET + lookup refs in Entities
;;   :product/tags card/many       → multi-valued entity tree
;;   :product/category ref→ident   → enum-as-ident in schema tree
;;   :order/customer, :line/product → VAET reverse nav in Entities
;;   :order/lines card/many refs   → nested entity expand
;;   :tx/user, :tx/note            → annotated txs in Transactions
;;   two DBs (store + warehouse)   → data sources / multi-db query

(def schema
  [;; ── products ──────────────────────────────────────────────────
   {:db/ident       :product/sku
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "Stable product code. Lookup-ref friendly."}
   {:db/ident       :product/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Display name."}
   {:db/ident       :product/price
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc         "Unit price in cents. Changes over time — history demo."}
   {:db/ident       :product/tags
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/many
    :db/doc         "Free-form labels. Card-many for entity tree + with."}
   {:db/ident       :product/category
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Enum ref → :category/* idents."}
   {:db/ident       :product/active?
    :db/valueType   :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc         "Soft availability flag."}

   ;; ── category enums (installed as entities with :db/ident) ─────
   {:db/ident :category/apparel}
   {:db/ident :category/gear}
   {:db/ident :category/digital}

   ;; ── customers ─────────────────────────────────────────────────
   {:db/ident       :customer/email
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/doc         "Login identity."}
   {:db/ident       :customer/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :customer/joined
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc         "Signup instant — useful for since filters."}

   ;; ── orders + line items ───────────────────────────────────────
   {:db/ident       :order/id
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :order/customer
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "→ customer. Reverse: who ordered?"}
   {:db/ident       :order/placed
    :db/valueType   :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident       :order/status
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc         "Enum ref → :status/*."}
   {:db/ident       :order/lines
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/isComponent true
    :db/doc         "Component line items — expand in Entities."}

   {:db/ident :status/pending}
   {:db/ident :status/paid}
   {:db/ident :status/shipped}
   {:db/ident :status/cancelled}

   {:db/ident       :line/product
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :line/qty
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident       :line/unit-price
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc         "Price snapshot at order time (cents)."}

   ;; ── transaction annotations (tx is just an entity) ────────────
   {:db/ident       :tx/user
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Who submitted this transaction."}
   {:db/ident       :tx/note
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Human-readable reason / ticket id."}])

(def warehouse-schema
  "Tiny second schema — only exists so multi-db queries have a peer."
  [{:db/ident       :sku/code
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :sku/on-hand
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc         "Units available in the warehouse."}])

;; ═════════════════════════════════════════════════════════════════════
;; SEED — backdated transactions so the Transactions graph has shape
;; ═════════════════════════════════════════════════════════════════════
;;
;; Console's Transactions tab buckets by :db/txInstant. If everything
;; lands in one second of wall-clock time the chart is a single spike.
;; We pin :db/txInstant on each batch (must be strictly increasing and
;; not in the future relative to wall clock).

(defn- tx!
  "Transact `tx-data` at a fixed instant, with optional :tx/user + note.
   Pass nil user/note for the tx that INSTALLS :tx/* — an attribute
   cannot be used in the transaction that defines it, whatever the
   ordering inside tx-data (:db.error/not-an-entity)."
  [conn inst user note tx-data]
  (let [ann (cond-> {:db/id "datomic.tx"
                     :db/txInstant inst}
              user (assoc :tx/user user)
              note (assoc :tx/note note))]
    @(d/transact conn (concat tx-data [ann]))))

(defn seed-store!
  "Install schema + a short history of a tiny store. Returns the conn."
  [conn]
  ;; Schema first — no :tx/user|:tx/note yet (those attrs ARE the schema).
  ;; Only built-in :db/txInstant is safe on this transaction.
  (tx! conn #inst "2024-01-02T10:00:00.000-00:00" nil nil schema)
  ;; Stamp provenance on a no-op follow-up so Transactions still shows a note.
  (tx! conn #inst "2024-01-02T10:00:01.000-00:00"
       "admin" "install schema"
       [])

  (tx! conn #inst "2024-01-05T14:00:00.000-00:00"
       "admin" "initial catalog"
       [{:product/sku      "TEE-001"
         :product/name     "Ada T-Shirt"
         :product/price    2900
         :product/category :category/apparel
         :product/active?  true
         :product/tags     ["cotton" "unisex" "bestseller"]}
        {:product/sku      "MUG-001"
         :product/name     "Lambda Mug"
         :product/price    1800
         :product/category :category/gear
         :product/active?  true
         :product/tags     ["ceramic" "gift"]}
        {:product/sku      "EBOOK-01"
         :product/name     "Datomic Field Notes"
         :product/price    1500
         :product/category :category/digital
         :product/active?  true
         :product/tags     ["pdf" "longform"]}
        {:product/sku      "HOOD-001"
         :product/name     "Persistent Hoodie"
         :product/price    6400
         :product/category :category/apparel
         :product/active?  true
         :product/tags     ["fleece" "winter"]}])

  (tx! conn #inst "2024-02-01T09:30:00.000-00:00"
       "import" "COI customer load #1"
       [{:customer/email  "bruna@example.com"
         :customer/name   "Bruna"
         :customer/joined #inst "2024-02-01T09:30:00.000-00:00"}
        {:customer/email  "rich@example.com"
         :customer/name   "Rich"
         :customer/joined #inst "2024-02-01T09:30:00.000-00:00"}
        {:customer/email  "nikita@example.com"
         :customer/name   "Nikita"
         :customer/joined #inst "2024-02-10T12:00:00.000-00:00"}])

  (tx! conn #inst "2024-03-12T16:05:00.000-00:00"
       "web" "checkout ORD-1001"
       [{:db/id           "line-a"
         :line/product    [:product/sku "TEE-001"]
         :line/qty        2
         :line/unit-price 2900}
        {:db/id           "line-b"
         :line/product    [:product/sku "MUG-001"]
         :line/qty        1
         :line/unit-price 1800}
        {:order/id       "ORD-1001"
         :order/customer [:customer/email "bruna@example.com"]
         :order/placed   #inst "2024-03-12T16:05:00.000-00:00"
         :order/status   :status/paid
         :order/lines    ["line-a" "line-b"]}])

  (tx! conn #inst "2024-03-18T11:20:00.000-00:00"
       "web" "checkout ORD-1002"
       [{:db/id           "line-c"
         :line/product    [:product/sku "EBOOK-01"]
         :line/qty        1
         :line/unit-price 1500}
        {:order/id       "ORD-1002"
         :order/customer [:customer/email "rich@example.com"]
         :order/placed   #inst "2024-03-18T11:20:00.000-00:00"
         :order/status   :status/pending
         :order/lines    ["line-c"]}])

  (tx! conn #inst "2024-04-02T08:00:00.000-00:00"
       "web" "checkout ORD-1003"
       [{:db/id           "line-d"
         :line/product    [:product/sku "HOOD-001"]
         :line/qty        1
         :line/unit-price 6400}
        {:db/id           "line-e"
         :line/product    [:product/sku "TEE-001"]
         :line/qty        1
         :line/unit-price 2900}
        {:order/id       "ORD-1003"
         :order/customer [:customer/email "nikita@example.com"]
         :order/placed   #inst "2024-04-02T08:00:00.000-00:00"
         :order/status   :status/paid
         :order/lines    ["line-d" "line-e"]}])

  ;; history-worthy changes
  (tx! conn #inst "2024-05-01T00:00:00.000-00:00"
       "pricing" "Q2 price list — TEE-001"
       [{:product/sku   "TEE-001"
         :product/price 3400}])

  (tx! conn #inst "2024-05-03T13:40:00.000-00:00"
       "ops" "warehouse scan ORD-1001"
       [{:order/id     "ORD-1001"
         :order/status :status/shipped}])

  (tx! conn #inst "2024-05-04T10:15:00.000-00:00"
       "support" "ticket #8841 — customer cancelled"
       [{:order/id     "ORD-1002"
         :order/status :status/cancelled}])

  (tx! conn #inst "2024-05-10T09:00:00.000-00:00"
       "merch" "drop 'bestseller' from TEE-001 for May"
       [[:db/retract [:product/sku "TEE-001"] :product/tags "bestseller"]])

  (tx! conn #inst "2024-06-01T00:00:00.000-00:00"
       "merch" "EBOOK-01 end of life"
       [{:product/sku     "EBOOK-01"
         :product/active? false}])

  (tx! conn #inst "2024-07-01T00:00:00.000-00:00"
       "pricing" "summer sale — TEE-001"
       [{:product/sku   "TEE-001"
         :product/price 2500}])

  conn)

(defn seed-warehouse!
  "Tiny inventory DB for the multi-db data-source demo."
  [conn]
  ;; Warehouse is a separate DB — it does not share store's schema.
  ;; Install sku attrs (+ the same :tx/* used by tx!) then stock.
  (tx! conn #inst "2024-01-05T15:00:00.000-00:00" nil nil
       (into warehouse-schema
             [{:db/ident       :tx/user
               :db/valueType   :db.type/string
               :db/cardinality :db.cardinality/one}
              {:db/ident       :tx/note
               :db/valueType   :db.type/string
               :db/cardinality :db.cardinality/one}]))
  (tx! conn #inst "2024-01-05T15:00:01.000-00:00"
       "admin" "warehouse schema + stock"
       [{:sku/code "TEE-001"  :sku/on-hand 120}
        {:sku/code "MUG-001"  :sku/on-hand 45}
        {:sku/code "EBOOK-01" :sku/on-hand 9999}
        {:sku/code "HOOD-001" :sku/on-hand 18}])
  conn)

(defn seed!
  "Delete and recreate both demo databases, then load history.
   Safe to re-run mid-class if someone mutates the data.

   => {:store <conn> :warehouse <conn> :dbs [\"store\" \"warehouse\"]}"
  []
  (doseq [u [store-uri warehouse-uri]]
    (d/delete-database u)
    (d/create-database u))
  (let [store-conn (seed-store! (d/connect store-uri))
        wh-conn    (seed-warehouse! (d/connect warehouse-uri))]
    (println "seeded. open Console → storage pg → DB store")
    (println "  products :" (count (d/q '[:find ?e :where [?e :product/sku]]
                                        (d/db store-conn))))
    (println "  orders   :" (count (d/q '[:find ?e :where [?e :order/id]]
                                        (d/db store-conn))))
    (println "  basis-t  :" (d/basis-t (d/db store-conn)))
    {:store     store-conn
     :warehouse wh-conn
     :dbs       (vec (d/get-database-names system-uri))}))

(defn store-conn [] (d/connect store-uri))
(defn store-db   [] (d/db (store-conn)))

;; ═════════════════════════════════════════════════════════════════════
;; §0 · FRAME — what Console is / is not                    (slides 1–4)
;; ═════════════════════════════════════════════════════════════════════
;; Pure deck framing — no REPL forms. Console is a read-only peer UI;
;; use it to see, the REPL to script. Continue at §1.

;; ═════════════════════════════════════════════════════════════════════
;; §1 · LAUNCH + WINDOW — start Console, read the chrome    (slides 5–10)
;; ═════════════════════════════════════════════════════════════════════
;;
;; Console anatomy (project this once, then stop talking about chrome):
;;
;;   ┌─ storage | DB | as-of | since | history ─────────────────────┐
;;   │ schema tree │  Query | Entities | Transactions | Indexes     │
;;   │             │  (active tab editor / controls)                │
;;   │ data sources│  ───────────────────────────────────────────── │
;;   │             │  data set (results)                            │
;;   └─────────────┴────────────────────────────────────────────────┘
;;
;; Every later section is one of those panes, full depth.

(comment

  ;; ═════════════════════════════════════════════════ SLIDE 6 ═══
  ;; Prerequisite check: storage is up, both DBs exist after seed!
  (seed!)
  ;; => {:store #..., :warehouse #..., :dbs ["store" "warehouse" ...]}

  (d/get-database-names system-uri)
  ;; => (... "store" "warehouse" ...)

  ;; Console launch command (shell — not eval'd here):
  ;;
  ;;   $DATOMIC/bin/console -p 8080 \
  ;;     pg "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"
  ;;
  ;; Critical details the class must say out loud:
  ;;   1. URI has NO database name — Console lists every DB in storage.
  ;;   2. The alias `pg` is the name you pick in the Storage dropdown.
  ;;   3. You can pass multiple alias/URI pairs for multi-storage setups.
  ;;   4. Chrome is the recommended browser.
  ;;   5. Console is a peer: it holds an object cache and reads storage
  ;;      directly. Stopping the transactor does NOT stop Console reads.

  ;; ═════════════════════════════════════════════════ SLIDE 10 ═══
  ;; Verify the DB the class is about to open has the expected shape.
  (let [db (store-db)]
    {:basis-t  (d/basis-t db)
     :products (sort (map first (d/q '[:find ?sku :where [_ :product/sku ?sku]] db)))
     :orders   (sort (map first (d/q '[:find ?id  :where [_ :order/id ?id]] db)))
     :attrs    (->> (d/q '[:find ?ns
                           :where
                           [?e :db/ident ?a]
                           [?e :db/valueType]
                           [(namespace ?a) ?ns]]
                         db)
                    (map first) set)})
  ;; => {:basis-t N
  ;;     :products ("EBOOK-01" "HOOD-001" "MUG-001" "TEE-001")
  ;;     :orders   ("ORD-1001" "ORD-1002" "ORD-1003")
  ;;     :attrs    #{"customer" "line" "order" "product" "tx" ...}}

  )

;; ═════════════════════════════════════════════════════════════════════
;; §2 · SCHEMA TREE — attributes are entities, visible       (slides 11–14)
;; ═════════════════════════════════════════════════════════════════════
;;
;; IN CONSOLE:
;;   1. Expand `product` namespace → list of attributes.
;;   2. Expand `product/sku` → valueType, cardinality, unique, doc.
;;   3. Expand `product/tags` → cardinality/many (contrast with sku).
;;   4. Expand `product/category` → valueType/ref.
;;   5. Find `:category/apparel` — enums are ordinary entities with
;;      :db/ident, not a separate type system.
;;   6. Expand `order/lines` → isComponent true.
;;
;; Teaching point: the schema tree IS a query over attributes-as-entities.
;; Nothing here is metadata outside the database.

(comment

  ;; ═════════════════════════════════════════════════ SLIDE 14 ═══
  ;; Same view the schema tree is showing, from the REPL.
  (->> (d/q '[:find ?a ?vt ?card ?uniq
              :where
              [?e :db/ident ?a]
              [?e :db/valueType ?vte] [(datomic.api/ident $ ?vte) ?vt]
              [?e :db/cardinality ?ce] [(datomic.api/ident $ ?ce) ?card]
              [(namespace ?a) ?ns] [(= ?ns "product")]
              [(get-else $ ?e :db/unique :none) ?ue]
              [(datomic.api/ident $ ?ue) ?uniq]]
            (store-db))
       (sort-by first)
       pprint)
  ;; => ([:product/active?  :db.type/boolean :db.cardinality/one   :none]
  ;;     [:product/category :db.type/ref     :db.cardinality/one   :none]
  ;;     [:product/name     :db.type/string  :db.cardinality/one   :none]
  ;;     [:product/price    :db.type/long    :db.cardinality/one   :none]
  ;;     [:product/sku      :db.type/string  :db.cardinality/one   :db.unique/identity]
  ;;     [:product/tags     :db.type/string  :db.cardinality/many  :none])

  (d/pull (store-db) '[:db/ident :db/isComponent :db/doc :db/cardinality]
          :order/lines)

  )

;; ═════════════════════════════════════════════════════════════════════
;; §3 · QUERY TAB — builder, text, dataset, saved queries    (slides 15–24)
;; ═════════════════════════════════════════════════════════════════════
;;
;; IN CONSOLE — work every control, in this order:
;;
;; A. Minimal query (builder)
;;    find:  ?sku
;;    where: _ | :product/sku | ?sku
;;    Run → dataset caption shows shape + count (4).
;;
;; B. Edit the TEXT box instead; watch the builder sync.
;;    Paste from app code; paste back. Two views, one query.
;;
;; C. Add a join (where table + rows)
;;    ?e | :product/sku      | ?sku
;;    ?e | :product/price    | ?price
;;    ?e | :product/active?  | true
;;    find: ?sku ?price
;;
;; D. :in parameter
;;    in table:  ?min | 2000
;;    where + predicate [(> ?price ?min)]
;;    find: ?sku ?price
;;
;; E. :with — card-many trap
;;    :find ?sku alone does NOT explode; :find ?sku ?tag does.
;;    :with ?tag also preserves multiplicity without widening :find.
;;
;; F. Click an entity id in the dataset → jumps to Entities tab.
;;    Sort columns by clicking headers.
;;
;; G. Save the query with the + control above the text box. Reload it.
;;    (Saved queries are process-local — they die with the Console.)
;;
;; H. Console is query-centric, not pull-centric. When you want pull
;;    trees, drop to the REPL companion against the same DB.

(comment

  ;; ═════════════════════════════════════════════════ SLIDE 16 ═══
  (d/q '[:find ?sku :where [_ :product/sku ?sku]] (store-db))
  ;; => #{["TEE-001"] ["MUG-001"] ["EBOOK-01"] ["HOOD-001"]}

  ;; ═════════════════════════════════════════════════ SLIDE 18 ═══
  (d/q '[:find ?sku ?price
         :where
         [?e :product/sku ?sku]
         [?e :product/price ?price]
         [?e :product/active? true]]
       (store-db))

  ;; ═════════════════════════════════════════════════ SLIDE 19 ═══
  (d/q '[:find ?sku ?price
         :in $ ?min
         :where
         [?e :product/sku ?sku]
         [?e :product/price ?price]
         [(> ?price ?min)]]
       (store-db) 2000)

  ;; ═════════════════════════════════════════════════ SLIDE 20 ═══
  ;; E. the :with trap — set semantics hide card-many until you ask
  (let [db (store-db)]
    {:find-sku-only
     (count (d/q '[:find ?sku
                   :where
                   [?e :product/sku ?sku]
                   [?e :product/tags ?tag]]
                 db))
     ;; => 4  (?tag projected away — NO explosion)

     :find-sku+tag
     (count (d/q '[:find ?sku ?tag
                   :where
                   [?e :product/sku ?sku]
                   [?e :product/tags ?tag]]
                 db))
     ;; => 8  (two tags × four products after the May retract)

     :find-sku-with-tag
     (count (d/q '[:find ?sku
                   :with ?tag
                   :where
                   [?e :product/sku ?sku]
                   [?e :product/tags ?tag]]
                 db))
     ;; => 8  (:with keeps ?tag out of the shape but blocks dedupe)

     :products
     (count (d/q '[:find ?e :where [?e :product/sku]] db))})
  ;; Teach: bare :find ?sku does NOT explode. :find ?sku ?tag does.
  ;; :with means "do not dedupe on these"; it is NOT group-by.

  (d/q '[:find ?oid ?status
         :in $ ?email
         :where
         [?c :customer/email ?email]
         [?o :order/customer ?c]
         [?o :order/id ?oid]
         [?o :order/status ?s]
         [?s :db/ident ?status]]
       (store-db) "bruna@example.com")
  ;; => #{["ORD-1001" :status/shipped]}

  )

;; ═════════════════════════════════════════════════════════════════════
;; §4 · ENTITIES TAB — graph walk both directions            (slides 25–28)
;; ═════════════════════════════════════════════════════════════════════
;;
;; IN CONSOLE:
;;   1. From Query results, click the eid of ORD-1001
;;      (Entity ID wants a numeric eid or :db/ident — not a lookup ref.
;;       Resolve via query first.)
;;   2. Expand :order/customer → Bruna's entity.
;;   3. Expand :order/lines → each line → :line/product → product.
;;   4. On a product, find reverse refs — who points HERE?
;;   5. Walk product → lines → orders → other customers.
;;
;; Teaching point: Entities tab is the graph database costume. Refs are
;; bidirectional at read time.

(comment

  ;; ═════════════════════════════════════════════════ SLIDE 28 ═══
  (d/pull (store-db)
          '[:order/id
            {:order/status [:db/ident]}
            {:order/customer [:customer/email :customer/name]}
            {:order/lines [:line/qty
                           :line/unit-price
                           {:line/product [:product/sku :product/name]}]}]
          [:order/id "ORD-1001"])

  (d/q '[:find ?oid ?qty
         :where
         [?p :product/sku "TEE-001"]
         [?l :line/product ?p]
         [?l :line/qty ?qty]
         [?o :order/lines ?l]
         [?o :order/id ?oid]]
       (store-db))
  ;; => #{["ORD-1001" 2] ["ORD-1003" 1]}

  )

;; ═════════════════════════════════════════════════════════════════════
;; §5 · TRANSACTIONS TAB — zoom from day to datom            (slides 29–32)
;; ═════════════════════════════════════════════════════════════════════
;;
;; IN CONSOLE:
;;   1. Open Transactions. Bars should span 2024-01 → 2024-07
;;      (seed! backdated :db/txInstant).
;;   2. Day scale: bars = # of transactions that day.
;;   3. Click into a busy day (May) → Hour → Minute → Second.
;;   4. At Second scale, bars = # of datoms. Click → dataset pane
;;      fills with raw datoms of that transaction.
;;   5. Find the pricing tx — dataset shows retract of old price +
;;      assert of new + :tx/user + :tx/note + :db/txInstant.
;;   6. Pan in time, zoom out with the arrow controls.
;;
;; Teaching point: the log IS browsable. Transactions are entities.

(comment

  ;; ═════════════════════════════════════════════════ SLIDE 31 ═══
  (->> (d/q '[:find ?t ?inst ?user ?note
              :where
              [?tx :db/txInstant ?inst]
              [(datomic.api/tx->t ?tx) ?t]
              [(get-else $ ?tx :tx/user "") ?user]
              [(get-else $ ?tx :tx/note "") ?note]]
            (store-db))
       (sort-by first)
       pprint)

  (->> (d/tx-range (d/log (store-conn)) nil nil)
       (map (fn [{:keys [t data]}]
              {:t t :n-datoms (count data)}))
       pprint)

  )

;; ═════════════════════════════════════════════════════════════════════
;; §6 · INDEXES TAB — EAVT AEVT AVET VAET, raw               (slides 33–35)
;; ═════════════════════════════════════════════════════════════════════
;;
;; IN CONSOLE:
;;   1. AVET + :product/sku + "TEE-001" → unique identity lookup.
;;   2. AVET + :product/price (no value) → prices as a sorted column.
;;   3. EAVT + eid of ORD-1001 → "row" of that order.
;;   4. AEVT + :order/status → every entity's status (column).
;;   5. VAET + customer eid + :order/customer → reverse nav raw.
;;
;; Teaching point: same four indexes as domain-modeling Part 6 —
;; Console pages them without writing d/datoms.

(comment

  ;; ═════════════════════════════════════════════════ SLIDE 35 ═══
  (let [db (store-db)]
    {:avet-sku
     (map (juxt :e :a :v)
          (seq (d/datoms db :avet :product/sku "TEE-001")))

     :eavt-order
     (let [eid (d/entid db [:order/id "ORD-1001"])]
       (map (fn [dd] [(d/ident db (:a dd)) (:v dd)])
            (seq (d/datoms db :eavt eid))))

     :vaet-customer
     (let [cid (d/entid db [:customer/email "bruna@example.com"])]
       (map (fn [dd] [(d/ident db (:a dd)) (:e dd)])
            (seq (d/datoms db :vaet cid :order/customer))))})

  )

;; ═════════════════════════════════════════════════════════════════════
;; §7 · TIME FILTERS — as-of, since, history                 (slides 36–41)
;; ═════════════════════════════════════════════════════════════════════
;;
;; IN CONSOLE (top bar):
;;
;; A. as-of 2024-04-15 → TEE-001 price still 2900.
;;    Clear → 2500 (present, after summer sale).
;;
;; B. since 2024-06-01 → only novelty after that point.
;;
;; C. history checkbox + price query for TEE-001 → assert/retract pairs
;;    for 2900, 3400, 2500.
;;
;; D. as-of + Entities on TEE-001 in April → tags still include
;;    "bestseller" (retracted in May).
;;
;; Teaching point: the top bar rebinds `$` for every tab. as-of/since/
;; history are database-value features, not query features.

(comment

  ;; ═════════════════════════════════════════════════ SLIDE 38 ═══
  (defn price-at [inst]
    (d/q '[:find ?price .
           :where
           [?e :product/sku "TEE-001"]
           [?e :product/price ?price]]
         (d/as-of (store-db) inst)))

  {:apr-15 (price-at #inst "2024-04-15")  ;; => 2900
   :may-15 (price-at #inst "2024-05-15")  ;; => 3400
   :today  (price-at #inst "2024-08-01")} ;; => 2500

  ;; ═════════════════════════════════════════════════ SLIDE 40 ═══
  (->> (d/q '[:find ?price ?inst ?added
              :where
              [?e :product/sku "TEE-001"]
              [?e :product/price ?price ?tx ?added]
              [?tx :db/txInstant ?inst]]
            (d/history (store-db)))
       (sort-by second)
       pprint)
  ;; => ([2900 #inst "2024-01-05" true]
  ;;     [2900 #inst "2024-05-01" false]
  ;;     [3400 #inst "2024-05-01" true]
  ;;     [3400 #inst "2024-07-01" false]
  ;;     [2500 #inst "2024-07-01" true])

  {:before (d/q '[:find [?tag ...]
                  :where
                  [?e :product/sku "TEE-001"]
                  [?e :product/tags ?tag]]
                (d/as-of (store-db) #inst "2024-05-09"))
   :after  (d/q '[:find [?tag ...]
                  :where
                  [?e :product/sku "TEE-001"]
                  [?e :product/tags ?tag]]
                (d/as-of (store-db) #inst "2024-05-11"))}

  )

;; ═════════════════════════════════════════════════════════════════════
;; §8 · DATA SOURCES — named dbs, multi-db, datasets         (slides 42–45)
;; ═════════════════════════════════════════════════════════════════════
;;
;; IN CONSOLE:
;;   1. Data sources pane (lower left). + to add.
;;   2. Save current DB as "store-now".
;;   3. Switch DB dropdown to `warehouse`. Save as "warehouse".
;;   4. Multi-db query joining $store prices to $stock on-hand on sku.
;;   5. Dataset-as-source: save a result set, feed it into a later query.
;;
;; Teaching point: `$` is just the default input. Named sources make
;; multi-database joins tangible.

(comment

  ;; ═════════════════════════════════════════════════ SLIDE 44 ═══
  (let [store (store-db)
        stock (d/db (d/connect warehouse-uri))]
    (d/q '[:find ?sku ?price ?n
           :in $store $stock
           :where
           [$store ?p :product/sku ?sku]
           [$store ?p :product/price ?price]
           [$stock ?s :sku/code ?sku]
           [$stock ?s :sku/on-hand ?n]]
         store stock))
  ;; => #{["TEE-001" 2500 120] ["MUG-001" 1800 45] ...}

  )

;; ═════════════════════════════════════════════════════════════════════
;; §9 · LIMITS, SAFETY, WHEN TO REACH FOR IT                 (slides 46–49)
;; ═════════════════════════════════════════════════════════════════════
;;
;; Say these out loud before people try Console on prod tomorrow:
;;
;;   • Console is a peer. It caches segments. Unbounded queries against
;;     a huge prod DB from a laptop can hurt storage.
;;   • Console does not write (no transact UI). Read-only exploration.
;;     Still treat queries as load.
;;   • Saved queries and data sources are NOT durable. Restart = gone.
;;   • No pull builder, no rules editor, no d/with, no tx-annotate UI.
;;     When the UI runs out, drop to the REPL — same DB.
;;   • This Console is an on-prem peer tool. Cloud has different surfaces.
;;   • Chrome recommended; other browsers occasionally mis-draw the
;;     Transactions chart.
;;
;; When Console shines:
;;   • Onboarding a colleague to an unfamiliar schema
;;   • Auditing "what happened to entity X" via history + Entities
;;   • Showing non-Clojure stakeholders a queryable model
;;   • Teaching (this class)
;;
;; When the REPL wins:
;;   • Rules, pull, d/with, filters, tx functions, anything programmatic
;;   • Repeatable scripts, tests, CI
;;   • Large analytical queries you want to time and tune

(comment

  ;; ═════════════════════════════════════════════════ SLIDE 47 ═══
  '{:console-covers #{:schema-tree :q :entities :tx-log-browse
                      :datoms-indexes :as-of :since :history
                      :data-sources :multi-db}
    :console-misses #{:transact :pull-builder :rules :d/with
                      :d/filter :tx-report-queue :tx-functions
                      :saved-query-durability}}

  )

;; ═════════════════════════════════════════════════════════════════════
;; §10 · EXERCISES — last 15 minutes                         (slides 50–52)
;; ═════════════════════════════════════════════════════════════════════
;;
;; Students drive. Instructor only unsticks. Solutions below the fold.

(comment

  ;; EXERCISE 1 — schema tree
  ;; In Console, without using the REPL: what is the uniqueness of
  ;; :customer/email, and what is the valueType of :order/status?
  ;; Console shows keywords; :db/unique is a ref — pull the :db/ident.
  (d/pull (store-db)
          '[{:db/unique [:db/ident]} {:db/valueType [:db/ident]}]
          :customer/email)
  ;; => {:db/unique {:db/ident :db.unique/identity}
  ;;     :db/valueType {:db/ident :db.type/string}}
  (d/pull (store-db)
          '[{:db/valueType [:db/ident]} {:db/cardinality [:db/ident]}]
          :order/status)
  ;; => {:db/valueType {:db/ident :db.type/ref}
  ;;     :db/cardinality {:db/ident :db.cardinality/one}}

  ;; EXERCISE 2 — query
  ;; Build in the Query tab: every paid-or-shipped order with customer
  ;; name and total line count. Shape: ?oid ?name ?n-lines
  (d/q '[:find ?oid ?name (count ?l)
         :where
         [?o :order/id ?oid]
         [?o :order/customer ?c]
         [?c :customer/name ?name]
         [?o :order/status ?s]
         [?s :db/ident ?st]
         [(contains? #{:status/paid :status/shipped} ?st)]
         [?o :order/lines ?l]]
       (store-db))

  ;; EXERCISE 3 — time
  ;; Using as-of only: what was ORD-1001's status on 2024-05-02?
  ;; On 2024-05-04?
  (defn status-at [oid inst]
    (d/q '[:find ?st .
           :in $ ?oid
           :where
           [?o :order/id ?oid]
           [?o :order/status ?s]
           [?s :db/ident ?st]]
         (d/as-of (store-db) inst) oid))
  [(status-at "ORD-1001" #inst "2024-05-02")
   (status-at "ORD-1001" #inst "2024-05-04")]
  ;; => [:status/paid :status/shipped]

  ;; EXERCISE 4 — multi-db
  ;; Join store prices to warehouse on-hand. Which active product has
  ;; the lowest on-hand?
  (let [store (store-db)
        stock (d/db (d/connect warehouse-uri))]
    (d/q '[:find ?sku ?n
           :in $store $stock
           :where
           [$store ?p :product/sku ?sku]
           [$store ?p :product/active? true]
           [$stock ?s :sku/code ?sku]
           [$stock ?s :sku/on-hand ?n]]
         store stock))
  ;; HOOD-001 has 18 — lowest among active (EBOOK is inactive).

  )

;; ═════════════════════════════════════════════════════════════════════
;; Instructor cheat-sheet — t milestones after a clean seed!
;; Rehearse once; write the t values on a sticky for live as-of demos
;; if you prefer t over calendar dates.
;; ═════════════════════════════════════════════════════════════════════

(comment
  (->> (d/q '[:find ?t ?note
              :where
              [?tx :tx/note ?note]
              [(datomic.api/tx->t ?tx) ?t]]
            (store-db))
       (sort-by first)
       pprint)
  )
