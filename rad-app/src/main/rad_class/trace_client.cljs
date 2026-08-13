(ns rad-class.trace-client
  "Client-side probes. Everything here prints to the browser console (async
   events happen outside any REPL eval); the helper fns at the bottom are
   meant to be evaled FROM the CLJS nREPL to inspect the client db on demand."
  (:require
    [cljs.pprint :refer [pprint]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.networking.http-remote :as net]))

(defonce app* (atom nil))
(defn register! [a] (reset! app* a) :registered)

(defonce log-remote? (atom true))
(defonce log-render? (atom false))

;; ── READ (and MUTATION) leaving the browser ─────────────────────────
(def request-middleware
  (let [base (net/wrap-fulcro-request)]
    (fn [request]
      (when @log-remote?
        (js/console.groupCollapsed "⇧ EQL request → /api")
        (js/console.log (with-out-str (pprint (:body request))))
        (js/console.groupEnd))
      (base request))))

(def response-middleware
  (let [base (net/wrap-fulcro-response)]
    (fn [response]
      (let [{:keys [body] :as r} (base response)]
        (when @log-remote?
          (js/console.groupCollapsed "⇩ EQL response ← /api")
          (js/console.log (with-out-str (pprint body)))
          (js/console.groupEnd))
        r))))

;; ── RENDER ──────────────────────────────────────────────────────────
(defn render-middleware [this real-render]
  (when @log-render?
    (js/console.log "⟳ render" (comp/component-name this)))
  (real-render))

(defn log-renders!   [] (reset! log-render? true))
(defn quiet-renders! [] (reset! log-render? false))

;; ── REPL helpers: the client db and form state ──────────────────────
(defn db "The whole normalized client database." [] (app/current-state @app*))

(defn entity "One normalized entity, e.g. (entity [:item/id #uuid \"...\"])"
  [ident] (get-in (db) ident))

(defn form-props
  "Denormalized props of a mounted form, straight from the client db."
  [FormClass ident]
  (let [state (db)]
    (fdn/db->tree (comp/get-query FormClass state) (get-in state ident) state)))

(defn dirty
  "EDIT stage, on demand: the minimal diff between what the user typed and
   the pristine entity — exactly what save-form will send as ::form/delta."
  [FormClass ident]
  (fs/dirty-fields (form-props FormClass ident) true))

;; ── EDIT, live: print the delta on every keystroke ──────────────────
(defn watch-edits!
  "(watch-edits! ItemForm [:item/id #uuid \"...\"]) — every state change that
   touches that entity prints its current dirty-field delta."
  [FormClass ident]
  (let [state-atom (::app/state-atom @app*)]
    (add-watch state-atom ::edits
      (fn [_ _ old new]
        (when (not= (get-in old ident) (get-in new ident))
          (js/console.log "✎ dirty"
            (with-out-str
              (pprint (fs/dirty-fields
                        (fdn/db->tree (comp/get-query FormClass new) (get-in new ident) new)
                        true)))))))
    :watching))

(defn unwatch-edits! []
  (remove-watch (::app/state-atom @app*) ::edits)
  :stopped)
