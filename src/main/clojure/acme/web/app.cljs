(ns acme.web.app
  (:require [acme.web.effect]
            [acme.web.event.core :as event]
            [acme.web.interceptor :as interceptor]
            [acme.web.route :as route]
            [acme.web.sub]
            [acme.web.view.core :as view]
            [day8.re-frame.http-fx]
            [re-frame.core :as re-frame]
            [reagent.dom :as reagent-dom]))

(defn mount-root []
  (reagent-dom/render [view/root]
                      (js/document.getElementById "root")))

(defn main []
  (re-frame/reg-global-interceptor interceptor/validate-state)
  (re-frame/dispatch-sync [::event/initialize])
  (route/setup)
  (mount-root))
