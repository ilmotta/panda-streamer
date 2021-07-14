(ns user
  (:require [portal.web :as portal]
            [re-frame.core :as re-frame]
            [re-frame.db :as re-frame-db]
            [acme.web.app :as app]
            [acme.web.event.stream :as stream]))

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

(defn create-stream [{:keys [recipient token amount duration duration-unit]}]
  (re-frame/dispatch-sync [::stream/form-update-recipient-address recipient])
  (re-frame/dispatch-sync [::stream/form-update-token-address token])
  (re-frame/dispatch-sync [::stream/form-update-amount (or amount "5000")])
  (re-frame/dispatch-sync [::stream/form-update-time (or duration "10")])
  (re-frame/dispatch-sync [::stream/form-update-duration-unit (or duration-unit "minute")])
  (re-frame/dispatch [::stream/form-create]))

(comment
  (create-stream {:recipient "0x0"
                  ;; Rinkeby TestnetDAI
                  :token "0xc3dbf84Abb494ce5199D5d4D815b10EC29529ff8"}))

(comment
  (tap> @re-frame-db/app-db))
