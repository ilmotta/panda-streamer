(ns user
  (:require [portal.web :as portal]
            [re-frame.core :as re-frame]
            [re-frame.db :as re-frame-db]
            [acme.web.app :as app]))

(defn setup-portal
  []
  (add-tap #'portal/submit))

(defn ^:dev/after-load clear-cache-and-render!
  "Force a UI update by clearing the re-frame subscription cache."
  []
  (re-frame/clear-subscription-cache!)
  (app/mount-root))

(defn init! []
  ;; Set println to write to `console.log`.
  (enable-console-print!)
  (setup-portal))

(init!)

(comment
  (tap> @re-frame-db/app-db))
