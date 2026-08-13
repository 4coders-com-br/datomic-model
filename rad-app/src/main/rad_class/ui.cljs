(ns rad-class.ui
  "All UI is *generated*: two defsc-form and two defsc-report declarations.
   There is no hand-written HTML for CRUD anywhere in this project."
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.picker-options :as picker-options]
    [com.fulcrologic.rad.routing :as rroute]
    [rad-class.model.category :as category]
    [rad-class.model.item :as item]))

;; ─── Category ────────────────────────────────────────────────────────

(form/defsc-form CategoryForm [this props]
  {fo/id           category/id
   fo/attributes   [category/label]
   fo/title        "Category"
   fo/route-prefix "category"})

(report/defsc-report CategoryList [this props]
  {ro/title            "Categories"
   ro/source-attribute :category/all-categories
   ro/row-pk           category/id
   ro/columns          [category/label]
   ro/form-links       {:category/label CategoryForm}
   ro/controls         {::new {:type   :button
                               :label  "New Category"
                               :action (fn [this] (form/create! this CategoryForm))}}
   ro/row-actions      [{:label  "Delete"
                         :action (fn [report-instance {:category/keys [id]}]
                                   (form/delete! report-instance :category/id id))}]
   ro/run-on-mount?    true
   ro/route            "categories"})

;; ─── Item ────────────────────────────────────────────────────────────

(defsc CategoryQuery [_ _]
  {:query [:category/id :category/label]
   :ident :category/id})

(form/defsc-form ItemForm [this props]
  {fo/id            item/id
   fo/attributes    [item/item-name item/price item/quantity item/category]
   fo/title         "Item"
   fo/route-prefix  "item"
   fo/field-styles  {:item/category :pick-one}
   fo/field-options {:item/category
                     {::picker-options/query-key       :category/all-categories
                      ::picker-options/query-component CategoryQuery
                      ::picker-options/options-xform
                      (fn [_ options]
                        (mapv (fn [{:category/keys [id label]}]
                                {:text (str label) :value [:category/id id]})
                          (sort-by :category/label options)))
                      ::picker-options/cache-time-ms   30000}}})

(report/defsc-report ItemList [this props]
  {ro/title            "Inventory Items"
   ro/source-attribute :item/all-items
   ro/row-pk           item/id
   ro/columns          [item/item-name item/price item/quantity]
   ro/form-links       {:item/name ItemForm}
   ro/controls         {::new {:type   :button
                               :label  "New Item"
                               :action (fn [this] (form/create! this ItemForm))}}
   ro/row-actions      [{:label  "Delete"
                         :action (fn [report-instance {:item/keys [id]}]
                                   (form/delete! report-instance :item/id id))}]
   ro/run-on-mount?    true
   ro/route            "items"})

;; ─── Shell ───────────────────────────────────────────────────────────

(defsc LandingPage [this props]
  {:query         ['*]
   :ident         (fn [] [:component/id ::landing])
   :initial-state {}
   :route-segment ["landing"]}
  (dom/div :.ui.container
    (dom/div :.ui.basic.segment
      (dom/h2 "Fulcro RAD × Datomic — attributes all the way down")
      (dom/p "Open Items or Categories above. Keep one eye on the server REPL
              and the browser console: every click is traced through
              Read → Render → Edit → Mutation → Transaction → DB."))))

(defrouter MainRouter [this {:keys [current-state]}]
  {:router-targets [LandingPage ItemList ItemForm CategoryList CategoryForm]}
  (when-not (= :routed current-state)
    (dom/div :.ui.active.loader)))

(def ui-main-router (comp/factory MainRouter))

(defsc Root [this {:keys [router]}]
  {:query         [{:router (comp/get-query MainRouter)}]
   :initial-state {:router {}}}
  (dom/div
    (dom/div :.ui.top.attached.menu
      (dom/div :.header.item "RAD Class")
      (dom/a :.item {:onClick #(rroute/route-to! this ItemList {})} "Items")
      (dom/a :.item {:onClick #(rroute/route-to! this CategoryList {})} "Categories"))
    (dom/div :.ui.bottom.attached.segment
      (ui-main-router router))))
