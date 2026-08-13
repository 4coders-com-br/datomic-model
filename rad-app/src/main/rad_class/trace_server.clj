(ns rad-class.trace-server
  "Server-side probes for the class. Three stages of the round trip live
   here: the MUTATION arriving (form delta), the TRANSACTION the adapter
   derives from it, and the DB committing datoms (tx-report-queue)."
  (:require
    [clojure.pprint :refer [pprint]]
    [com.fulcrologic.rad.form :as form]
    [datomic.api :as d]))

;; The last things that flowed through a save — inspect them at the REPL.
(defonce last-delta  (atom nil))
(defonce last-result (atom nil))

(defn wrap-traced-save
  "RAD save middleware. Wraps the Datomic save middleware and prints the
   ::form/delta the browser sent plus the tempid remaps the save returned."
  [handler]
  (fn [{::form/keys [params] :as pathom-env}]
    (let [delta (::form/delta params)]
      (reset! last-delta delta)
      (println "\n══ MUTATION › ::form/delta from the browser ══════════════")
      (pprint delta)
      (let [result (handler pathom-env)]
        (reset! last-result result)
        (println "── save result (tempid → real id) ────────────────────────")
        (pprint result)
        result))))

;; ── DB stage: tail the transaction log as it commits ────────────────
(defonce tx-watcher (atom nil))

(defn watch-transactions!
  "Tail the Datomic tx-report-queue and print every committed datom —
   the last stop of the round trip: actual storage."
  [conn]
  (when-not @tx-watcher
    (let [q (d/tx-report-queue conn)]
      (reset! tx-watcher
        (future
          (loop []
            (let [{:keys [db-after tx-data]} (.take ^java.util.concurrent.BlockingQueue q)]
              (println "\n══ DB › committed datoms ═════════════════════════════════")
              (doseq [d tx-data]
                (println (format "  [%-6d %-18s %-38s %d %s]"
                           (:e d) (d/ident db-after (:a d))
                           (pr-str (:v d)) (:tx d) (:added d))))
              (recur))))))
    :watching))

(defn unwatch-transactions! [conn]
  (when-let [fut @tx-watcher]
    (d/remove-tx-report-queue conn)
    (future-cancel fut)
    (reset! tx-watcher nil))
  :stopped)
