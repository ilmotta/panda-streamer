(ns acme.web.event.stream
  (:require [acme.web.db :as db]
            [acme.web.domain.sablier :as sablier]
            [acme.web.domain.validation :as validation]
            [acme.web.effect :as effect]
            [acme.web.route :as route]
            [acme.web.util :as util]
            [cljs.core.async :refer [go <!]]
            [re-frame.core :refer [reg-event-db reg-event-fx dispatch]]))

;;; FILTERS

(reg-event-fx
 ::filter-logs
 (fn [{:keys [db]} [_ {:keys [hours]}]]
   (let [hours (js/parseInt (or hours (get-in db [:create-stream :max-history-hours])) 10)
         timestamp (- (util/unix-timestamp) (* 3600 hours))]
     (merge {:db (-> db
                     (assoc-in [:loading :streams] true)
                     (assoc-in [:create-stream :max-history-hours] hours))}
            (effect/fetch-block-number
             {:chain-id (get-in db [:wallet :chain-id])
              :timestamp timestamp
              :on-success [::filter-logs:block-found]
              :on-failure [::filter-logs:failed]})))))

(reg-event-fx
 ::filter-logs:block-found
 (fn [{:keys [db]} [_ {:keys [result]}]]
   (let [{:keys [provider chain-id]} (:wallet db)]
     {::effect/fetch-stream-logs
      {:block-number result
       :provider provider
       :chain-id chain-id
       :on-success [::filter-logs:finished]
       :on-failure [::filter-logs:failed]}})))

(reg-event-db
 ::filter-logs:finished
 (fn [db [_ logs]]
   (-> db
       (assoc-in [:loading :streams] false)
       (assoc-in [:create-stream :logs] logs))))

(reg-event-db
 ::filter-logs:failed
 (fn [db [_ _error]]
   (assoc-in db [:loading :streams] false)))

;;; CREATE STREAM

(reg-event-fx
 ::create:request-approval
 (fn [{:keys [db]} _]
   (let [fields (get-in db [:form :stream :fields])
         sablier-address (sablier/address-for (get-in db [:wallet :chain-id]))
         duration (util/time-in-seconds (get-in fields [:duration-unit :value])
                                        (get-in fields [:time :value]))
         preferred-deposit (util/bignum (get-in fields [:amount :value]))
         context (sablier/calculate-stream-deposit
                  {:preferred-deposit preferred-deposit
                   :initial-delay-seconds (get-in db [:create-stream :default-delay-seconds])
                   :duration-seconds duration})]
     {:db (-> db
              (assoc :overlay :overlay/create-stream)
              (assoc-in [:form :stream :status] :form/approving-sablier-contract))
      ::effect/contract-transaction
      {:abi ::sablier/erc20-token
       :address (get-in fields [:token-address :value])
       :wait true
       :method :approve
       :args [sablier-address (:deposit context)]
       :on-success [::create:verify-stream-call context]
       :on-failure [::create:approval-failed]}})))

(reg-event-db
 ::create:approval-failed
 (fn [db [_ error _approve-tx]]
   (-> db
       (dissoc :overlay)
       (assoc-in [:form :stream :status] :form/not-submitted)
       (assoc-in [:form :stream :errors]
                 (if (= "4001" (:code error))
                   [(assoc error :id :error/metamask:user-denied-transaction)]
                   [(assoc error :id :error/sablier:create-stream-failed)])))))

(reg-event-fx
 ::create:verify-stream-call
 (fn [{:keys [db]} [_ context _approve-tx]]
   {:db (assoc-in db [:form :stream :status] :form/validating-stream-call)
    ::effect/contract-transaction-verify
    {:abi ::sablier/sablier
     :address (sablier/address-for (get-in db [:wallet :chain-id]))
     :method :createStream
     :args [(get-in db [:form :stream :fields :recipient-address :value])
            (:deposit context)
            (get-in db [:form :stream :fields :token-address :value])
            (:start-time context)
            (:stop-time context)]
     :on-success [::create:create-stream-transaction context]
     :on-failure [::create:sablier-failed]}}))

(reg-event-fx
 ::create:create-stream-transaction
 (fn [{:keys [db]} [_ context]]
   {:db (assoc-in db [:form :stream :status] :form/creating-stream-transaction)
    ::effect/contract-transaction
    {:abi ::sablier/sablier
     :address (sablier/address-for (get-in db [:wallet :chain-id]))
     :method :createStream
     :wait false
     :args [(get-in db [:form :stream :fields :recipient-address :value])
            (:deposit context)
            (get-in db [:form :stream :fields :token-address :value])
            (:start-time context)
            (:stop-time context)]
     :on-success [::create:stream-created]
     :on-failure [::create:sablier-failed]}}))

(reg-event-fx
 ::create:stream-created
 (fn [{:keys [db]} [_ _receipt]]
   {:db (assoc-in db [:form :stream :status] :form/stream-transaction-created)
    :fx [[:dispatch-later
          {:ms 3500
           :dispatch [::create:redirect-to-streams]}]]}))

(reg-event-fx
 ::create:redirect-to-streams
 (fn [{:keys [db]}]
   {:db (dissoc db :overlay)
    :fx [[:dispatch [::form-reset]]]
    ::effect/route ::route/streams}))

(reg-event-db
 ::create:sablier-failed
 (fn [db [_ error _create-stream-tx]]
   (-> db
       (dissoc :overlay)
       (assoc-in [:form :stream :status] :form/not-submitted)
       (assoc-in [:form :stream :errors]
                 (if (= 4001 (:code error))
                   [{:id :error/metamask:user-denied-transaction}]
                   [(assoc error :id :error/sablier:create-stream-failed)])))))

(reg-event-fx
 ::fetch-token
 (fn [{:keys [db]} _]
   (let [token-address (get-in db [:form :stream :fields :token-address :value])]
     (if-let [symbol (get-in db [:form :stream :token-symbols token-address])]
       {:fx [[:dispatch [::token-fetched token-address symbol]]]}
       {::effect/contract-read-only
        {:abi ::sablier/erc20-token
         :address token-address
         :method :symbol
         :on-success [::token-fetched token-address]}}))))

(reg-event-db
 ::token-fetched
 (fn [db [_ token-address token-symbol]]
   (assoc-in db [:form :stream :token-symbols token-address] token-symbol)))

;;; RECEIPT FETCHER

(reg-event-fx
 ::receipt-fetched
 (fn [{:keys [db]} [_ receipt]]
   {:db (-> db
            (assoc-in [:create-stream :receipt-for-log] nil)
            (assoc-in [:create-stream :logs]
                      (->> (get-in db [:create-stream :logs])
                           (map (fn [log]
                                  (if (= (:tx-hash receipt) (:tx-hash log))
                                    (-> log
                                        (assoc :sync-status :complete)
                                        (assoc :receipt receipt))
                                    log))))))
    :fx [[:dispatch [::fetch-receipt]]]}))

(reg-event-fx
 ::fetch-receipt
 (fn [{:keys [db]} _]
   (let [poll-interval (get-in db [:create-stream :receipt-poll-interval-ms])]
     (if (util/wallet-connected? db)
       (if (get-in db [:create-stream :receipt-for-log])
         {:fx [[:dispatch-later {:ms poll-interval :dispatch [::fetch-receipt]}]]}
         (let [incomplete-log (->> (get-in db [:create-stream :logs])
                                   (filter #(= :incomplete (:sync-status %)))
                                   (first))]
           (if incomplete-log
             (do
               (go
                 (let [provider (get-in db [:wallet :provider])
                       receipt (<! (sablier/fetch-stream-receipt incomplete-log provider))]
                   (dispatch [::receipt-fetched receipt])))
               {:db (assoc-in db [:create-stream :receipt-for-log] incomplete-log)})
             {:fx [[:dispatch-later {:ms poll-interval :dispatch [::fetch-receipt]}]]})))
       {:fx [[:dispatch-later {:ms poll-interval :dispatch [::fetch-receipt]}]]}))))

;;; LOGS UPDATER

(reg-event-fx
 ::update-logs
 (fn [{:keys [db]} _]
   (let [interval-ms (get-in db [:create-stream :updater-interval-ms])]
     (if (util/wallet-connected? db)
       {:db (assoc-in db [:create-stream :updated-at] (util/unix-timestamp))
        :fx [[:dispatch-later {:ms interval-ms :dispatch [::update-logs]}]]}
       {:fx [[:dispatch-later {:ms interval-ms :dispatch [::update-logs]}]]}))))

;;; STREAM FORM

(reg-event-db
 ::on-mouse-enter:form-token-address
 (fn [db _]
   (assoc-in db [:form :stream :show-clipboard-action?] true)))

(reg-event-db
 ::on-mouse-leave:form-token-address
 (fn [db _]
   (assoc-in db [:form :stream :show-clipboard-action?] false)))

(reg-event-fx
 ::form-show
 (fn [{:keys [db]} _]
   {:db (assoc db :active-page ::route/create-stream)
    ::effect/route ::route/create-stream
    ::effect/focus-element "stream-recipient-address"}))

(reg-event-fx
 ::form-cancel
 (fn [_ _]
   {:fx [[:dispatch [::form-reset]]]
    ::effect/route ::route/home}))

(reg-event-db
 ::form-update-token-address
 (fn [db [_ value]]
   (let [db (assoc-in db [:form :stream :fields :token-address :value] value)]
     (if (validation/address? value)
       (assoc-in db [:form :stream :fields :token-address :error] nil)
       (assoc-in db [:form :stream :fields :token-address :error] :invalid)))))

(reg-event-db
 ::form-update-recipient-address
 (fn [db [_ value]]
   (let [db (assoc-in db [:form :stream :fields :recipient-address :value] value)]
     (if (validation/address? value)
       (assoc-in db [:form :stream :fields :recipient-address :error] nil)
       (assoc-in db [:form :stream :fields :recipient-address :error] :invalid)))))

(reg-event-db
 ::form-update-amount
 (fn [db [_ value]]
   (let [error (when-not (validation/money? value) :invalid)]
     (-> db
         (assoc-in [:form :stream :fields :amount :value] value)
         (assoc-in [:form :stream :fields :amount :error] error)))))

(reg-event-db
 ::form-update-duration-unit
 (fn [db [_ value]]
   (assoc-in db [:form :stream :fields :duration-unit :value] value)))

(reg-event-db
 ::form-update-time
 (fn [db [_ value]]
   (let [error (when-not (validation/pos-integer? value) :invalid)]
     (-> db
         (assoc-in [:form :stream :fields :time :value] value)
         (assoc-in [:form :stream :fields :time :error] error)))))

(reg-event-db
 ::form-reset
 (fn [db _]
   (-> db
       (assoc-in [:form :stream :status] :form/not-submitted)
       (assoc-in [:form :stream :fields] db/default-form-stream-fields)
       (assoc-in [:form :stream :errors] []))))

(reg-event-fx
 ::form-create
 (fn [{:keys [db]} _]
   (let [fields (get-in db [:form :stream :fields])]
     (when (validation/valid-stream-form?
            {:recipient-address (get-in fields [:recipient-address :value])
             :token-address (get-in fields [:token-address :value])
             :amount (get-in fields [:amount :value])
             :time (get-in fields [:time :value])
             :duration-unit (get-in fields [:duration-unit :value])})
       {:db (-> db
                (assoc-in [:form :stream :status] :form/submitted)
                (assoc-in [:form :stream :errors] []))
        :fx [[:dispatch [::create:request-approval]]]}))))
