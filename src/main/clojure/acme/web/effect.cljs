(ns acme.web.effect
  (:require ["@ethersproject/contracts" :refer [Contract]]
            [acme.web.db :as db]
            [acme.web.domain.sablier :as sablier]
            [acme.web.domain.wallet :as wallet]
            [acme.web.route :as route]
            [acme.web.util :refer [>reg-fx]]
            [pushy.core :as pushy]
            [promesa.core :as p]
            [re-frame.core :refer [reg-fx dispatch]]
            [re-frame.db :as rf-db]
            [reagent.core :as reagent]))

;; ::promise
;;
;; Call `thunk` as a promise and dispatch `on-success` when resolved or
;; `on-failure` with the error object. `on-success` and `on-failure` are
;; optional.
;;
(reg-fx
 ::promise
 (fn [{:keys [thunk on-success on-failure]}]
   (-> (thunk)
       (p/then (fn [result]
                 (when on-success
                   (dispatch (conj on-success result)))))
       (p/catch (fn [error]
                  (if on-failure
                    (dispatch (conj on-failure error))
                    (throw error)))))))

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
     (-> (.apply contract-fn contract (clj->js args))
         (p/then (fn [result]
                   (when on-success
                     (dispatch (conj on-success result)))))
         (p/catch (fn [error]
                    (when on-failure
                      (dispatch (conj on-failure (wallet/normalize-provider-error error))))))))))

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
     (-> (.apply contract-fn contract (clj->js args))
         (p/then (fn [result]
                   (when on-success
                     (dispatch (conj on-success result)))))
         (p/catch (fn [error]
                    (when on-failure
                      (dispatch (conj on-failure (wallet/normalize-provider-error error))))))))))

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
         contract-abi (clj->js (get sablier/abi abi))
         contract (new Contract address contract-abi signer)
         contract-fn (aget contract (name method))]
     (-> (.apply contract-fn contract (clj->js args))
         (p/then (fn [transaction]
                   (if wait
                     (let [wait (if (true? wait) 1 wait)]
                       (-> (.wait transaction wait)
                           (p/then (fn [receipt]
                                     (when on-success
                                       (dispatch (conj on-success receipt)))))
                           (p/catch (fn [error]
                                      (when on-failure
                                        (dispatch (conj on-failure (wallet/normalize-provider-error error) transaction)))))))
                     (when on-success
                       (dispatch (conj on-success transaction))))))
         (p/catch (fn [error]
                    (when on-failure
                      (dispatch (conj on-failure (wallet/normalize-provider-error error))))))))))

;; Set focus on DOM `element-id`.
;;
;; It fails silently if the element can't be found.
(>reg-fx
 ::focus-element
 {:schema [:=> [:cat :string] [:any]]}
 (fn [element-id]
   (reagent/after-render #(some-> js/document
                                  (.getElementById element-id)
                                  .focus))))

;; Change the browser history to the path found for `page`.
;;
;; There is no 404 handling, therefore it's assumed `route/path-for` always
;; returns a valid path for a given `page`.
(>reg-fx
 ::route
 {:schema [:=> [:cat db/page-spec] [:any]]}
 (fn [page]
   (pushy/set-token! route/history (route/path-for page))))
