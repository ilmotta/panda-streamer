(ns acme.web.route
  (:require [bidi.bidi :as bidi]
            [pushy.core :as pushy]
            [re-frame.core :refer [dispatch]]))

(def routes
  ["/" {"" ::home
        "about" ::about
        "create-stream" ::create-stream
        "streams" ::streams}])

(def path-for
  (partial bidi/path-for routes))

(defn dispatch-route [matched-route]
  (dispatch [:acme.web.event.core/set-active-page (:handler matched-route)]))

(def history
  (pushy/pushy dispatch-route (partial bidi/match-route routes)))

(defn setup
  "Add an event listener to all click events."
  []
  (pushy/start! history))
