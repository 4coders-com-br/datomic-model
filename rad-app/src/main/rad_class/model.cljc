(ns rad-class.model
  "The attribute registry. Everything downstream — Datomic schema, Pathom
   resolvers, form fields, report columns — is generated from this one vector."
  (:require
    [rad-class.model.category :as category]
    [rad-class.model.item :as item]
    [com.fulcrologic.rad.attributes :as attr]))

(def all-attributes (into [] cat [category/attributes item/attributes]))

(def key->attribute (attr/attribute-map all-attributes))
