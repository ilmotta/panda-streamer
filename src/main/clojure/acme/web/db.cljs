(ns acme.web.db
  (:require ["@ethersproject/providers" :refer [Web3Provider]]
            [acme.web.domain.validation :as validation]
            [acme.web.util :as util]
            [malli.core :as malli]
            [malli.error :as malli-error]))

(def state-spec
  [:map
   {:closed true}
   ;; The qualified name of the current page.
   [:active-page [qualified-keyword? {:namespace :acme.web.route}]]

   ;; A map of boolean flags that view components can subscribe to show that
   ;; asynchronous operations haven't finished.
   [:loading {:optional true} [:map-of keyword? boolean?]]

   ;; A namespaced keyword writen to the db whenever an
   ;; `:acme.web.event.core/clipboard-write` event finishes successfully. This value
   ;; is usually dispatched from the view and any interested components can
   ;; subscribe to it to give user feedback that something has been copied.
   [:clipboard-write-id {:optional true} qualified-keyword?]

   ;; Namespaced keyword of the name of the page overlay. The
   ;; `acme.web.view/overlay-for` multimethod uses it to dispatch to the correct
   ;; component.
   [:overlay {:optional true} [:enum :overlay/create-stream]]

   ;; Data derived from the MetaMask integration.
   [:wallet
    [:map
     [:chain-id {:optional true} string?]
     [:status [:enum
               :wallet/connected
               :wallet/connecting
               :wallet/connection-rejected
               :wallet/disconnected
               :wallet/missing-dependency
               :wallet/unknown]]
     [:accounts [:sequential [:fn validation/address?]]]
     ;; Store a reference to the Ethereum object injected by MetaMask. Notice
     ;; that the user will need to refresh the page after installing
     ;; MetaMask (if it's not already installed). There are on-boarding
     ;; libraries to reduce the burden on users, but here we do the simplest
     ;; thing and assume the user already has MetaMask installed.
     [:provider {:optional true} [:fn #(instance? Web3Provider %)]]]]

   ;; All forms should be scoped by their primary name, e.g. :stream.
   [:form
    [:map
     [:stream
      [:map
       ;; The :status value should be updated during various events, like form
       ;; submission.
       [:status [:enum
                 :form/approving-sablier-contract
                 :form/creating-stream-transaction
                 :form/not-submitted
                 :form/stream-transaction-created
                 :form/submitted
                 :form/validating-stream-call]]
       [:fields [:map-of keyword? [:map
                                   [:value string?]
                                   [:error any?]]]]
       ;; Errors not tied to any particular input field, i.e. more general about
       ;; the Create Stream form.
       [:errors [:sequential [:map
                              [:id [qualified-keyword? {:namespace :error}]]
                              [:code string?]
                              [:message string?]
                              [:reason [:or nil? string?]]]]]

       ;; Naive cache for token symbols fetched from the blockchain. This can be
       ;; used in the form to help the user understand the value of a token.
       [:token-symbols [:map-of [:fn validation/address?] string?]]]]]]

   [:create-stream
    [:map
     ;; The number of seconds to be added to the start time of every stream
     ;; creation transaction. It's stored in the app state and not in a constant
     ;; because in real-world applications it would probably be configurable, so
     ;; the user can create streams in moments of network instability. The
     ;; stream transaction must be processed by the Ethereum blockchain before
     ;; the start time of the stream, or otherwise the Sablier contract reverts
     ;; with a "start time before block.timestamp" message.
     [:default-delay-seconds pos-int?]

     ;; The interval in milliseconds to go over all fetched Stream logs and
     ;; update their properties to be reflected on re-frame subscriptions.
     [:updater-interval-ms pos-int?]

     ;; The Unix timestamp when the event `acme.web.event.stream/update-logs`
     ;; last run. Whenever this value changes based on `updater-interval-ms` the
     ;; streams list is updated as well so users can see the latest timing stats
     ;; about their streams.
     [:updated-at pos-int?]

     ;; The interval in milliseconds to poll for CreateStream transaction
     ;; receipts.
     [:receipt-poll-interval-ms pos-int?]

     ;; A temporary state used by `acme.web.event.core/stream-receipt:fetch` to
     ;; sequentially process one event log at a time. If it's non-nil it means a
     ;; receipt is being fetched for the log in question.
     [:receipt-for-log {:optional true} any?]

     ;; The successfull logs retrieved from the chain, i.e. logs filtered
     ;; primarily by the CreateStream event.
     [:logs [:sequential any?]]

     ;; The maximum window of hours to fetch CreateStream events. Events emitted
     ;; before `min` will be ignored. The `max` number of hours could be
     ;; increased, but we should consider the maximum number of events that
     ;; could be returned so as to not put too much pressure on the user's
     ;; device.
     [:max-history-hours [pos-int? {:min 24 :max (* 30 24)}]]

     ;; It's hard to find a definite explanation about the maximum drift allowed
     ;; in blocks' timestamps. According to (1), the accuracy of Ethereum
     ;; timestamps can vary in up to tens of seconds. Here we set the maximum
     ;; search error to be in the order of hours because we want the binary
     ;; block search to stop as fast as possible. See
     ;; `acme.web.util/find-block-by-timestamp` for more details.
     ;;
     ;; References:
     ;;   (1) https://www.sciencedirect.com/science/article/abs/pii/S0306457320309602
     ;;
     ;; DEPRECATED: Usage of this configuration is discouraged because the
     ;; Etherscan API offers a simpler way to find a block number by timestamp.
     [:block-search-max-error-seconds [pos-int? {:min (* 2 3600)}]]]]])

;; When `false` the `validate!` function will be a no-op. This is important
;; because we don't want to validate the entire app state whenever it changes in
;; production. Only enable this flag in test or development environments. See
;; the project's shadow-cljs.edn file for an example.
(goog-define ^boolean validation-enabled? false)

(defn validate!
  "Throws an exception if `db` doesn't match `spec`."
  [spec db]
  (when validation-enabled?
    (when-not (malli/validate spec db)
      (throw (ex-info (str "Invalid app db: "
                           (-> spec
                               (malli/explain db)
                               (malli-error/with-spell-checking)
                               (malli-error/humanize)))
                      {})))))

(def default-form-stream-fields
  {:recipient-address {:value "" :error nil}
   :amount {:value "" :error nil}
   :token-address {:value "" :error nil}
   :unit {:value "hour" :error nil}
   :time {:value "" :error nil}})

(def default
  {:active-page :acme.web.route/home
   :loading {}
   :wallet {:status :wallet/unknown
            :accounts []}
   :create-stream {;; The Sablier documentation explicitly recommends over 6min
                   ;; of delay in the main Ethereum network. In Rinkeby 1min is
                   ;; usually enough.
                   :default-delay-seconds (* 6 60)
                   :updated-at (util/unix-timestamp)
                   :updater-interval-ms 10000
                   :receipt-poll-interval-ms 5000
                   :logs []
                   :max-history-hours (* 1 24)
                   :block-search-max-error-seconds (* 3 3600)}
   :form {:stream {:status :form/not-submitted
                   :fields default-form-stream-fields
                   :show-clipboard-action? false
                   :errors []
                   :token-symbols {}}}})
