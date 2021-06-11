(ns acme.web.event.core
  (:require [acme.web.db :as db]
            [acme.web.effect :as effect]
            [acme.web.interceptor :refer [default-interceptors]]
            [acme.web.event.stream]
            [acme.web.event.wallet :as wallet]
            [re-frame.core :refer [reg-event-db reg-event-fx]]))

;;; CLIPBOARD

(reg-event-fx
 ::clipboard-write
 default-interceptors
 (fn [{:keys [db]} [_ {:keys [id text db-path]}]]
   {::effect/clipboard-write
    {:text (or text (get-in db db-path))
     :on-success [::clipboard-write-finished id]}}))

(reg-event-db
 ::clipboard-reset
 default-interceptors
 (fn [db _]
   (dissoc db :clipboard-write-id)))

(reg-event-fx
 ::clipboard-write-finished
 default-interceptors
 (fn [{:keys [db]} [_ id]]
   {:db (assoc db :clipboard-write-id id)
    :fx [[:dispatch-later {:ms 1000 :dispatch [::clipboard-reset]}]]}))

;;; ROUTING

(reg-event-db
 ::set-active-page
 default-interceptors
 (fn [db [_ page]]
   (assoc db :active-page page)))

;;; INITIALIZATION

(reg-event-fx
 ::initialize
 default-interceptors
 (fn [_ _]
   {:db db/default
    ;; The ::wallet/init event must be dispatched asynchronously to give the
    ;; MetaMask extension time to inject its global object and for the app to
    ;; detect it. Without this delayed dispatch sometimes the app would show the
    ;; landing page, as if the user was disconnected.
    :fx [[:dispatch-later {:ms 1000 :dispatch [::wallet/init]}]]}))
