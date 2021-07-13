(ns acme.web.interceptor
  (:require [acme.web.db :as db]
            [re-frame.core :as re-frame]))

(def validate-state
  "Validate the db after it changes and throw if it fails. The variable
  `acme.web.db/validation-enabled?` can be configured at compile time in the
  shadow-cljs.edn file."
  (re-frame/after
   (partial db/validate! db/state-spec)))
