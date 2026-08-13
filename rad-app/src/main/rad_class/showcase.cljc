(ns rad-class.showcase
  "The robotic REPL-takeover demo, packaged. Drives the running app —
   navigation, form edit, save — while narrating every step in the
   devtools console with colored banners (recording-friendly).

   From the shadow CLJS REPL:   (rad-class.showcase/run!)
   From the devtools console:   rad_class.showcase.run_BANG_()
   Slower/faster:               (rad-class.showcase/run! 0.5)  ; half speed"
  (:refer-clojure :exclude [run!])
  #?(:cljs
     (:require
       [com.fulcrologic.fulcro.application :as app]
       [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
       [com.fulcrologic.fulcro.ui-state-machines :as uism]
       [com.fulcrologic.rad.form :as form]
       [rad-class.trace-client :as trace]
       [rad-class.ui :as ui])))

#?(:cljs
   (do
     (defn- say [msg style] (js/console.log (str "%c" msg) style))
     (def ^:private big    "font-size:16px;font-weight:bold;color:#7b2ff7")
     (def ^:private blue   "font-size:13px;font-weight:bold;color:#0a84ff")
     (def ^:private orange "font-size:13px;font-weight:bold;color:#ff9500")
     (def ^:private red    "font-size:13px;font-weight:bold;color:#ff3b30")

     (defn- the-app [] @trace/app*)
     (defn- hammer-id []
       (some (fn [[id e]] (when (= "Hammer" (:item/name e)) id))
         (:item/id (trace/db))))

     (def ^:private steps
       ;; [ms-from-start thunk] — narration first, action right after.
       [[0     #(say "🤖 REPL TAKEOVER — hands off the mouse. Everything from here is driven by rad-class.showcase." big)]
        [4000  #(do (say "§3 RENDER · the client db is ONE normalized map — these are its tables:" blue)
                    (js/console.log (pr-str (sort (keys (trace/db))))))]
        [9000  #(do (say "one normalized entity — note :item/category is an IDENT (a pointer), not a nested map:" blue)
                    (js/console.log (pr-str (trace/entity [:item/id (hammer-id)]))))]
        [15000 #(do (trace/log-renders!)
                    (say "render log ON — routing Categories → Items with no mouse. Watch ⟳ renders and ⇧⇩ EQL:" blue)
                    (dr/change-route! (the-app) ["categories"]))]
        [20000 #(dr/change-route! (the-app) ["items"])]
        [25000 #(do (trace/quiet-renders!)
                    (say "§4 EDIT · opening the Hammer form robotically — (form/edit! app ItemForm id):" orange)
                    (form/edit! (the-app) ui/ItemForm (hammer-id)))]
        [30000 #(do (trace/watch-edits! ui/ItemForm [:item/id (hammer-id)])
                    (say "edit watcher armed. Robotic keystroke: quantity +1. The ✎ dirty line is the FORM DELTA — computed by diffing pristine vs current, not recorded:" orange))]
        [34000 #(do (swap! (:com.fulcrologic.fulcro.application/state-atom (the-app))
                      update-in [:item/id (hammer-id) :item/quantity] inc)
                    (app/schedule-render! (the-app) {:force-root? true}))]
        [40000 #(say "§5 MUTATION · pressing Save from the REPL — the exact UISM event the Save button fires:" red)]
        [42000 #(uism/trigger! (the-app) [:item/id (hammer-id)] :event/save {})]
        [47000 #(say "⇧ that was ONE generic save-form mutation carrying the delta. The server REPL now prints: delta → tx-data → committed datoms. Inspect → Transactions & Network tell the same story." red)]
        [51000 #(do (trace/unwatch-edits!)
                    (dr/change-route! (the-app) ["items"]))]
        [54000 #(say "🤖 done — READ → RENDER → EDIT → MUTATION → TRANSACTION → DB, driven entirely from code. The browser is just another parser client." big)]])

     (defn run!
       "Run the ~55s narrated showcase. Optional speed: 2.0 = twice as
        fast, 0.5 = half speed."
       ([] (run! 1.0))
       ([speed]
        (if-not (the-app)
          (js/console.error "showcase: app not registered — is the page loaded?")
          (doseq [[t f] steps]
            (js/setTimeout f (long (/ t speed)))))
        :showcase-running))))

#?(:clj
   (defn run!
     "The showcase drives the BROWSER. Run it from the shadow CLJS REPL
      (Terminal 3), or paste rad_class.showcase.run_BANG_() into the
      devtools console."
     []
     (println "CLJS-only — run from the shadow CLJS REPL or the browser console:")
     (println "  (rad-class.showcase/run!)   /   rad_class.showcase.run_BANG_()")))
