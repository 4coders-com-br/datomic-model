(ns rad-class.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.networking.http-remote :as net]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.rad.application :as rad-app]
    [com.fulcrologic.rad.rendering.semantic-ui.semantic-ui-controls :as sui]
    [com.fulcrologic.rad.routing.history :as history]
    [com.fulcrologic.rad.routing.html5-history :refer [html5-history restore-route!]]
    [fulcro.inspect.tool :as inspect]
    [rad-class.showcase]
    [rad-class.trace-client :as trace]
    [rad-class.ui :as ui]))

(defonce app
  (rad-app/fulcro-rad-app
    {:remotes           {:remote (net/fulcro-http-remote
                                   {:url                 "/api"
                                    :request-middleware  trace/request-middleware
                                    :response-middleware trace/response-middleware})}
     :render-middleware trace/render-middleware}))

(defn refresh
  "shadow-cljs hot-reload hook."
  []
  (app/mount! app ui/Root "app"))

(defn ^:export init []
  (inspect/add-fulcro-inspect! app)   ; Fulcro Inspect (chrome devtools tab)
  (rad-app/install-ui-controls! app sui/all-controls)
  ;; Route history: forms' Done/Cancel pop back to the report they came
  ;; from, and routes show up in the URL (deep-linkable, browser back).
  (history/install-route-history! app (html5-history))
  (trace/register! app)
  (app/mount! app ui/Root "app")
  (restore-route! app ui/LandingPage {}))
