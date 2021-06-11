(ns acme.web.app
  (:require [acme.web.effect]
            [acme.web.event.core :as event]
            [acme.web.route :as route]
            [acme.web.sub]
            [acme.web.view.core :as view]
            [re-frame.core :as re-frame]
            [reagent.dom :as reagent-dom]))

;; Set println to write to `console.log`.
(enable-console-print!)

(defn mount-root []
  (reagent-dom/render [view/root]
                      (js/document.getElementById "root")))

(defn main []
  (re-frame/dispatch-sync [::event/initialize])
  (route/setup)
  (mount-root))

(defn ^:dev/after-load clear-cache-and-render!
  "Force a UI update by clearing the re-frame subscription cache."
  []
  (re-frame/clear-subscription-cache!)
  (mount-root))
