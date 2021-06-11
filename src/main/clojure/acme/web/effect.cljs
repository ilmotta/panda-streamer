(ns acme.web.effect
  (:require ["@ethersproject/contracts" :refer [Contract]]
            [acme.web.domain.sablier :as sablier]
            [acme.web.route :as route]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as string]
            [pushy.core :as pushy]
            [re-frame.core :refer [reg-fx dispatch]]
            [re-frame.db :as rf-db]
            [reagent.core :as reagent]))

(defn normalize-provider-error
  "Return a more consistent error map. Unfortunately local blockchains, like the
  ones provided by Ganache and HardHat behave differently in undocumented ways.
  For example, signed & failed Sablier transactions in Rinkeby return more
  information than in local chains."
  [^js error]
  (if-let [error-data (some-> error .-cause .-data)]
    (let [[_ actual-error] (first (filter (fn [[k _v]]
                                            (string/starts-with? (name k) "0x"))
                                          (js->clj (.-data error-data))))]
      {:code (str (.-code error-data))
       :message (.-message error-data)
       :reason (get actual-error "reason")})
    {:code (str (-> error .-cause .-code))
     :message (-> error .-cause .-message)
     :reason (-> error .-cause .-reason)}))

;; ::clipboard-write
;;
;; Write `text` to the system clipboard. This implementation uses the old
;; `execCommand` function and not the new asynchronous Clipboard API. It has the
;; side-effect of removing the current user selection if there's any.
;;
;; Usage:
;; {::clipboard-write {:text "Sample"
;;                     :on-success [:event-id-success]
;;                     :on-failure [:event-id-failure]}}
;;
;; `:on-success` and `:on-failure` are optional.
;;
(reg-fx
 ::clipboard-write
 (fn [{:keys [text on-success on-failure]}]
   (try
     (let [el (.createElement js/document "textarea")]
       (set! (-> el .-value) text)
       (.setAttribute el "readonly" "")
       ;; We can't simply use display: none.
       (set! (-> el .-style .-position) "absolute")
       (set! (-> el .-style .-left) "-99999px")
       (.appendChild (.-body js/document) el)
       (.select el)
       (.execCommand js/document "copy")
       (.removeChild  (.-body js/document) el)
       (when on-success
         (dispatch on-success)))
     (catch js/Error error
       (when on-failure
         (dispatch (conj on-failure error)))))))

;; ::contract-read-only
;;
;; Call a read-only `method` on a contract. The call is free and doesn't require
;; ether, but at the same time it can't change the blockchain state.
;;
;; Usage:
;; {::contract-read-only
;;  {:abi :acme.web.domain.sablier/erc20-token
;;   :address token-address
;;   :method :symbol
;;   :on-success [:event-id-success "val-a" "val-b"]
;;   :on-failure [:event-id-failure]}}
;;
;; `method` is a symbol and will be directly converted to a string. Don't use
;; kebab keywords. `abi` is the keyword used in the
;; `acme.web.domain.sablier/abi` map. `args` is simply converted using
;; `clj->js`. Both `on-success` and `on-failure` are optional and will be called
;; with the result/error appended to the end of the event vector, which allows
;; this effect to pass arbitrary data to subsequent success/failure events.
;;
(reg-fx
 ::contract-read-only
 (fn [{:keys [abi address method args on-success on-failure]}]
   (let [db @rf-db/app-db
         provider (get-in db [:wallet :provider])
         contract-abi (clj->js (get sablier/abi abi))
         contract (new Contract address contract-abi provider)
         contract-fn (aget contract (name method))]
     (go
       (try
         (let [result (<p! (.apply contract-fn contract (clj->js args)))]
           (when on-success
             (dispatch (conj on-success result))))
         (catch js/Error error
           (when on-failure
             (dispatch (conj on-failure (normalize-provider-error error))))))))))

;; ::contract-transaction-verify
;;
;; Call a read-only `method` on a contract using `callStatic`. The call is free
;; and doesn't require ether, but at the same time it can't change the
;; blockchain state. Contrary to the `::contract-read-only` effect, this one can
;; be used to validate transactions and thus uses the provider signer, i.e. the
;; account currently connected in MetaMask.
;;
;; Usage:
;; {::contract-transaction-verify
;;  {:abi :acme.web.domain.sablier/erc20-token
;;   :address token-address
;;   :method :approve
;;   :args [spender-address amount]
;;   :on-success [:event-id-success "val-a" "val-b"]
;;   :on-failure [:event-id-failure]}}
;;
;; `method` is a symbol and will be directly converted to a string. Don't use
;; kebab keywords. `abi` is the keyword used in the
;; `acme.web.domain.sablier/abi` map. `args` is simply converted using
;; `clj->js`. Both `on-success` and `on-failure` are optional and will be called
;; with the result/error appended to the end of the event vector, which allows
;; this effect to pass arbitrary data to subsequent success/failure events.
;;
;; Note: Although this effect can be useful to perform validations without the
;; user spending ether, it sometimes fail when the real call actually works just
;; fine.
;;
(reg-fx
 ::contract-transaction-verify
 (fn [{:keys [abi address method args on-success on-failure]}]
   (let [db @rf-db/app-db
         provider (get-in db [:wallet :provider])
         signer (.getSigner provider)
         contract-abi (clj->js (get sablier/abi abi))
         contract (new Contract address contract-abi signer)
         contract-fn (aget (.-callStatic contract) (name method))]
     (go
       (try
         (let [result (<p! (.apply contract-fn contract (clj->js args)))]
           (when on-success
             (dispatch (conj on-success result))))
         (catch js/Error error
           (when on-failure
             (dispatch (conj on-failure (normalize-provider-error error))))))))))

;; ::contract-transaction
;;
;; Call a non-constant (write) `method` on a contract. This effect requires the
;; transaction to be signed and will have an associated fee.
;;
;; Usage:
;; {::contract-transaction
;;  {:abi :acme.web.domain.sablier/erc20-token
;;   :address token-address
;;   :method :approve
;;   :wait 2
;;   :args [spender-address amount]
;;   :on-success [:event-id-success "val-a" "val-b"]
;;   :on-failure [:event-id-failure]}}
;;
;; `method` is a symbol and will be directly converted to a string. Don't use
;; kebab keywords. `abi` is the keyword used in the
;; `acme.web.domain.sablier/abi` map. `args` is simply converted using
;; `clj->js`. Both `on-success` and `on-failure` are optional and will be called
;; with the result/error appended to the end of the event vector, which allows
;; this effect to pass arbitrary data to subsequent success/failure events.
;;
;; Use the `wait` option to decide if you want `:on-success` to be dispatched
;; only after waiting for the transaction to be mined. Instead of a boolean you
;; can also pass the number of blocks to be confirmed. See the Ethers
;; TransactionResponse documentation for further details. By default `wait` is
;; false.
;;
(reg-fx
 ::contract-transaction
 (fn [{:keys [wait abi address method args on-success on-failure]}]
   (let [db @rf-db/app-db
         signer (.getSigner (get-in db [:wallet :provider]))
         transaction (atom nil)
         contract-abi (clj->js (get sablier/abi abi))
         contract (new Contract address contract-abi signer)
         contract-fn (aget contract (name method))]
     (go
       (try
         (reset! transaction (<p! (.apply contract-fn contract (clj->js args))))
         (catch js/Error error
           (when on-failure
             (dispatch (conj on-failure (normalize-provider-error error) @transaction)))))
       (when @transaction
         (if wait
           (try
             (let [wait (if (true? wait) 1 wait)
                   receipt (<p! (.wait @transaction wait))]
               (when on-success
                 (dispatch (conj on-success receipt))))
             (catch js/Error error
               (when on-failure
                 (dispatch (conj on-failure (normalize-provider-error error) @transaction)))))
           (when on-success
             (dispatch (conj on-success @transaction)))))))))

;; ::focus-element
;;
;; Set focus on DOM `element-id`.
;;
;; Usage:
;; {::focus-element "some-element-id"}
;;
;; It fails silently if the element can't be found.
;;
(reg-fx
 ::focus-element
 (fn [element-id]
   (reagent/after-render #(some-> js/document
                                  (.getElementById element-id)
                                  .focus))))

;; ::route
;;
;; Change the browser history to the path found for `page`, where `page` is a
;; namespaced keyword defined in `acme.web.route/routes`.
;;
;; Usage:
;; {::route :acme.web.route/about}
;;
;; There is no 404 handling, therefore it's assumed `route/path-for` always
;; returns a valid path for a given `page`.
;;
(reg-fx
 ::route
 (fn [page]
   (pushy/set-token! route/history (route/path-for page))))
