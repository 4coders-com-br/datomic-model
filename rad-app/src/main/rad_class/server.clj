(ns rad-class.server
  "The whole backend: Datomic (in-memory peer) + Pathom parser + Ring/http-kit.
   Note what is NOT here: no schema edn, no resolvers for entities, no
   save/delete endpoints — all generated from rad-class.model/all-attributes."
  (:require
    [com.fulcrologic.fulcro.server.api-middleware :as fmw]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    [com.fulcrologic.rad.database-adapters.datomic-options :as do]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.pathom :as rad.pathom]
    [com.fulcrologic.rad.resolvers :as res]
    [org.httpkit.server :as http]
    [rad-class.model :as model]
    [rad-class.trace-server :as trace]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.resource :refer [wrap-resource]]))

(def db-config
  "One in-memory Datomic database named :main holding the :production schema.
   Swap :datomic/driver for :postgresql to reuse the Production-class infra."
  {do/databases
   {:main {:datomic/schema   :production
           :datomic/driver   :mem
           :datomic/database "rad-class"}}})

(defonce runtime (atom {}))            ; {:connection :parser :stop-fn}

(defn connection [] (:connection @runtime))

(defn build-parser
  "READ and MUTATION both land here: one Pathom parser, resolvers generated
   from the attributes, save/delete middleware provided by the Datomic adapter
   (wrapped in the class tracer)."
  [conn]
  (rad.pathom/new-parser
    {:com.fulcrologic.rad.pathom/config
     {:log-requests?  true            ; READ stage: every incoming EQL is logged
      :log-responses? true}}
    [(attr/pathom-plugin model/all-attributes)
     (form/pathom-plugin
       (trace/wrap-traced-save (datomic/wrap-datomic-save))
       (datomic/wrap-datomic-delete))
     (datomic/pathom-plugin (fn [_env] {:production conn}))]
    [(datomic/generate-resolvers model/all-attributes :production)
     (res/generate-resolvers model/all-attributes)   ; attribute-declared resolvers (all-items etc.)
     form/resolvers]))

(defn- wrap-index [handler]
  (fn [{:keys [uri] :as req}]
    (handler (cond-> req (= "/" uri) (assoc :uri "/index.html")))))

(defn- not-found [_]
  {:status 404 :headers {"Content-Type" "text/plain"} :body "Not found"})

(defn build-handler [parser]
  (-> not-found
    (fmw/wrap-api {:uri "/api" :parser (fn [tx] (parser {} tx))})
    (fmw/wrap-transit-params)
    (fmw/wrap-transit-response)
    (wrap-resource "public")
    (wrap-content-type)
    (wrap-index)))

(defn start!
  "Start Datomic (schema is generated + transacted here), the parser, and
   the web server. Idempotent-ish: stop! first if already running."
  ([] (start! {:port 3000}))
  ([{:keys [port]}]
   (let [connections (datomic/start-databases model/all-attributes db-config)
         conn        (:main connections)
         parser      (build-parser conn)
         stop-fn     (http/run-server (build-handler parser) {:port port})]
     (reset! runtime {:connection conn :parser parser :stop-fn stop-fn})
     (trace/watch-transactions! conn)
     (println (str "▶ up: http://localhost:" port "  (API at /api)"))
     :started)))

(defn stop! []
  (when-let [stop (:stop-fn @runtime)] (stop))
  (when-let [conn (connection)] (trace/unwatch-transactions! conn))
  (reset! runtime {})
  :stopped)

(defn q
  "Run an EQL query through the very same parser the browser hits.
   (q [{:item/all-items [:item/name :item/price]}])"
  [tx]
  ((:parser @runtime) {} tx))
