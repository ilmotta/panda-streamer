(ns acme.web.event.wallet
  (:require ["@ethersproject/providers" :refer [Web3Provider]]
            [acme.web.db :as db]
            [acme.web.effect :as effect]
            [acme.web.interceptor :refer [default-interceptors]]
            [acme.web.route :as route]
            [acme.web.util :as util]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [re-frame.core :refer [reg-event-db reg-event-fx dispatch]]))

(reg-event-fx
 ::accounts-changed
 default-interceptors
 (fn [{:keys [db]} [_ accounts]]
   ;; MetaMask also reports changed accounts when none are connected.
   (if (util/wallet-connected? db)
     {:db (assoc-in db [:wallet :accounts] accounts)
      :fx [[:dispatch [:acme.web.event.stream/filter]]]}
     {:fx [[:dispatch [::disconnected]]]})))

(reg-event-fx
 ::disconnected
 default-interceptors
 (fn [_ _]
   {:db (assoc-in db/default [:wallet :status] :wallet/disconnected)
    ::effect/route ::route/home}))

(reg-event-fx
 ::listen-disconnected
 default-interceptors
 (fn [_ _]
   (when (util/metamask-installed?)
     (.on js/ethereum "disconnect"
          (fn [error]
            (dispatch [::disconnected error]))))
   nil))

(reg-event-fx
 ::listen-accounts-changed
 default-interceptors
 (fn [_ _]
   (when (util/metamask-installed?)
     (.on js/ethereum "accountsChanged"
          (fn [accounts]
            (dispatch [::accounts-changed (js->clj accounts)]))))
   nil))

;; It's recommended by the Metamask documentation to reload the page.
(reg-event-fx
 ::listen-chain-changed
 default-interceptors
 (fn [_ _]
   (when (util/metamask-installed?)
     (.on js/ethereum "chainChanged"
          (fn [_chain-id]
            (-> js/window .-location .reload))))
   nil))

(reg-event-db
 ::cancel-connection-request
 default-interceptors
 (fn [db _]
   (assoc-in db [:wallet :status] :wallet/connection-rejected)))

(reg-event-fx
 ::ready
 default-interceptors
 (fn [{:keys [db]} [_ {:keys [accounts chain-id provider]}]]
   {:db (-> db
            (assoc-in [:wallet :accounts] accounts)
            (assoc-in [:wallet :chain-id] chain-id)
            (assoc-in [:wallet :provider] provider)
            (assoc-in [:wallet :status] :wallet/connected))
    ::effect/route (:active-page db)
    :fx [[:dispatch [::listen-disconnected]]
         [:dispatch [::listen-chain-changed]]
         [:dispatch [::listen-accounts-changed]]
         [:dispatch [:acme.web.event.stream/fetch-receipt]]
         [:dispatch [:acme.web.event.stream/filter]]
         [:dispatch [:acme.web.event.stream/update-logs]]]}))

(reg-event-fx
 ::request-connection
 default-interceptors
 (fn [{:keys [db]} _]
   (when (util/metamask-installed?)
     (let [accounts* (atom nil)]
       (-> (.request js/ethereum (clj->js {:method "eth_requestAccounts"}))
           (.then (fn [accounts]
                    (reset! accounts* (js->clj accounts))
                    (.request js/ethereum (clj->js {:method "eth_chainId"}))))
           (.then (fn [chain-id]
                    (dispatch [::ready {:accounts @accounts*
                                        :chain-id chain-id
                                        :provider (new Web3Provider js/ethereum)}])))
           (.catch (fn [error]
                     ;; EIP-1193 userRejectedRequest error. If this happens, the
                     ;; user rejected the connection request.
                     (if (= 4001 (.-code error))
                       (dispatch [::cancel-connection-request])
                       (js/console.error error))))))
     {:db (assoc-in db [:wallet :status] :wallet/connecting)})))

(reg-event-fx
 ::init
 default-interceptors
 (fn [{:keys [db]} _]
   (if (util/metamask-installed?)
     (do
       (go
         (let [accounts (js->clj (-> js/ethereum .-_state .-accounts))
               chain-id (<p! (-> (.request js/ethereum (clj->js {:method "eth_chainId"}))
                                 (.catch (fn [_] nil))))
               connected? (and chain-id (seq accounts))]
           (if connected?
             (dispatch [::ready {:accounts accounts
                                 :chain-id chain-id
                                 :provider (new Web3Provider js/ethereum)}])
             ;; When signed-out, clean-up before redirecting.
             (dispatch [::disconnected]))))
       nil)
     {:db (assoc-in db [:wallet :status] :wallet/missing-dependency)})))
