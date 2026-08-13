(ns rad-class.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.networking.http-remote :as net]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.rad.application :as rad-app]
    [com.fulcrologic.rad.rendering.semantic-ui.semantic-ui-controls :as sui]
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
  (rad-app/install-ui-controls! app sui/all-controls)
  (trace/register! app)
  (app/mount! app ui/Root "app")
  (dr/change-route! app ["landing"]))
