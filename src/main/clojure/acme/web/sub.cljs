(ns acme.web.sub
  (:require [acme.web.domain.validation :as validation]
            [acme.web.domain.wallet :as wallet]
            [acme.web.util :as util]
            [re-frame.core :refer [reg-sub subscribe]]))

;;; CLIPBOARD

(reg-sub
 ::clipboard-write-id
 (fn [db _]
   (get db :clipboard-write-id)))

(reg-sub
 ::active-page
 (fn [db _]
   (:active-page db)))

(reg-sub
 ::overlay
 (fn [db _]
   (:overlay db)))

;;; LIST STREAMS

(reg-sub
 ::streams-updated-at
 (fn [db _]
   (get-in db [:create-stream :updated-at])))

(reg-sub
 ::loading-streams?
 (fn [db _]
   (get-in db [:loading :streams])))

(reg-sub
 ::max-history-hours
 (fn [db _]
   (get-in db [:create-stream :max-history-hours])))

(reg-sub
 ::stream-logs
 (fn [db _]
   (get-in db [:create-stream :logs])))

(reg-sub
 ::streams:show-recipient-address-copy-button?
 (fn [db _]
   (:show-recipient-address-copy-button? db)))

;;; WALLET

(reg-sub
 ::wallet-accounts
 (fn [db _]
   (seq (get-in db [:wallet :accounts]))))

(reg-sub
 ::wallet-status
 (fn [db _]
   (get-in db [:wallet :status])))

(reg-sub
 ::wallet-account-address
 (fn [db _]
   (first (get-in db [:wallet :accounts]))))

(reg-sub
 ::wallet-account-short-address
 (fn [_]
   (subscribe [::wallet-account-address]))
 (fn [address _]
   (util/short-hash address)))

(reg-sub
 ::wallet-main-network?
 (fn [db _]
   (= "0x1" (get-in db [:wallet :chain-id]))))

(reg-sub
 ::wallet-chain
 (fn [db _]
   (when-let [chain-id (get-in db [:wallet :chain-id])]
     (wallet/chain-ids (util/bignum->int chain-id)))))

;;; STREAM FORM

(reg-sub
 ::form-stream-show-clipboard-action?
 (fn [db _]
   (get-in db [:form :stream :show-clipboard-action?])))

(reg-sub
 ::form-stream-token-symbol
 (fn [db _]
   (get-in db [:form :stream :token-symbols
               (get-in db [:form :stream :fields :token-address :value])])))

(reg-sub
 ::form-stream-recipient-address
 (fn [db _]
   (get-in db [:form :stream :fields :recipient-address :value])))

(reg-sub
 ::form-stream-duration-unit
 (fn [db _]
   (get-in db [:form :stream :fields :duration-unit :value])))

(reg-sub
 ::form-stream-amount
 (fn [db _]
   (get-in db [:form :stream :fields :amount :value])))

(reg-sub
 ::form-stream-time
 (fn [db _]
   (get-in db [:form :stream :fields :time :value])))

(reg-sub
 ::form-stream-token-address
 (fn [db _]
   (get-in db [:form :stream :fields :token-address :value])))

(reg-sub
 ::form-stream-invalid-time
 (fn [db _]
   (get-in db [:form :stream :fields :time :error])))

(reg-sub
 ::form-stream-invalid-recipient-address
 (fn [db _]
   (get-in db [:form :stream :fields :recipient-address :error])))

(reg-sub
 ::form-stream-invalid-amount
 (fn [db _]
   (get-in db [:form :stream :fields :amount :error])))

(reg-sub
 ::form-stream-invalid-token-address
 (fn [db _]
   (get-in db [:form :stream :fields :token-address :error])))

(reg-sub
 ::form-stream-status
 (fn [db _]
   (get-in db [:form :stream :status])))

(reg-sub
 ::form-stream-errors
 (fn [db]
   (get-in db [:form :stream :errors])))

(reg-sub
 ::form-stream-valid?
 (fn [_]
   [(subscribe [::form-stream-recipient-address])
    (subscribe [::form-stream-token-address])
    (subscribe [::form-stream-amount])
    (subscribe [::form-stream-time])
    (subscribe [::form-stream-duration-unit])])
 (fn [[recipient-address token-address amount time duration-unit]]
   (validation/valid-stream-form?
    {:recipient-address recipient-address
     :token-address token-address
     :amount amount
     :time time
     :duration-unit duration-unit})))
