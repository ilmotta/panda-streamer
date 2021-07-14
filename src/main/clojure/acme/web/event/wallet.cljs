(ns acme.web.event.wallet
  (:require ["@ethersproject/providers" :refer [Web3Provider]]
            [acme.web.db :as db]
            [acme.web.domain.wallet :as wallet]
            [acme.web.effect :as effect]
            [acme.web.route :as route]
            [acme.web.util :as util]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [promesa.core :as p]
            [re-frame.core :refer [reg-event-db reg-event-fx dispatch]]))

(reg-event-fx
 ::accounts-changed
 (fn [{:keys [db]} [_ accounts]]
   ;; MetaMask also reports changed accounts when none are connected.
   (if (util/wallet-connected? db)
     {:db (assoc-in db [:wallet :accounts] accounts)
      :fx [[:dispatch [:acme.web.event.stream/filter]]]}
     {:fx [[:dispatch [::disconnected]]]})))

(reg-event-fx
 ::disconnected
 (fn [_ _]
   {:db (assoc-in db/default [:wallet :status] :wallet/disconnected)
    ::effect/route ::route/home}))

(reg-event-fx
 ::listen-disconnected
 (fn [_ _]
   (when (util/metamask-installed?)
     (.on js/ethereum "disconnect"
          (fn [error]
            (dispatch [::disconnected error]))))
   nil))

(reg-event-fx
 ::listen-accounts-changed
 (fn [_ _]
   (when (util/metamask-installed?)
     (.on js/ethereum "accountsChanged"
          (fn [accounts]
            (dispatch [::accounts-changed (js->clj accounts)]))))
   nil))

;; It's recommended by the Metamask documentation to reload the page.
(reg-event-fx
 ::listen-chain-changed
 (fn [_ _]
   (when (util/metamask-installed?)
     (.on js/ethereum "chainChanged"
          (fn [_chain-id]
            (-> js/window .-location .reload))))
   nil))

(reg-event-db
 ::cancel-connection-request
 (fn [db _]
   (assoc-in db [:wallet :status] :wallet/connection-rejected)))

(reg-event-fx
 ::ready
 (fn [{:keys [db]} [_ {:keys [accounts chain-id]}]]
   {:db (-> db
            (assoc-in [:wallet :accounts] accounts)
            (assoc-in [:wallet :chain-id] chain-id)
            (assoc-in [:wallet :provider] (new Web3Provider js/ethereum))
            (assoc-in [:wallet :status] :wallet/connected))
    ::effect/route (:active-page db)
    :fx [[:dispatch [::listen-disconnected]]
         [:dispatch [::listen-chain-changed]]
         [:dispatch [::listen-accounts-changed]]
         [:dispatch [:acme.web.event.stream/fetch-receipt]]
         [:dispatch [:acme.web.event.stream/filter-logs]]
         [:dispatch [:acme.web.event.stream/update-logs]]]}))

(reg-event-fx
 ::request-connection
 (fn [{:keys [db]} _]
   (when (util/metamask-installed?)
     {:db (assoc-in db [:wallet :status] :wallet/connecting)
      ::effect/promise {:thunk wallet/request-state
                        :on-success [::ready]
                        :on-failure [::connection-request-failed]}})))

(reg-event-db
 ::connection-request-failed
 (fn [db [_ error]]
   (if (= (ex-message error) ::wallet/user-rejected-request)
     (assoc-in db [:wallet :status] :wallet/connection-rejected)
     db)))

(reg-event-fx
 ::init
 (fn [{:keys [db]} _]
   (if (util/metamask-installed?)
     {::effect/promise {:thunk wallet/fetch-state
                        :on-success [::ready]
                        ;; When signed-out, clean-up before redirecting.
                        :on-failure [::disconnected]}}
     {:db (assoc-in db [:wallet :status] :wallet/missing-dependency)})))
