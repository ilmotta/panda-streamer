(ns acme.web.domain.wallet
  (:require [clojure.string :as string]
            [promesa.core :as p]))

(def chain-ids
  {3 :ropsten
   4 :rinkeby
   5 :goerli
   42 :kovan
   1337 :local})

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

(defn request-accounts []
  (-> (.request js/ethereum (clj->js {:method "eth_requestAccounts"}))
      (p/then js->clj)))

(defn fetch-accounts []
  (js->clj (-> js/ethereum .-_state .-accounts)))

(defn fetch-chain-id []
  (.request js/ethereum (clj->js {:method "eth_chainId"})))

(defn request-state
  "Request wallet state and resolve it to a map. The promise will be rejected if
  the user rejects the UI request."
  []
  (let [accounts* (atom nil)]
    (-> (request-accounts)
        (p/then (partial reset! accounts*))
        (p/then #(fetch-chain-id))
        (p/then (fn [chain-id]
                  {:accounts @accounts*
                   :chain-id chain-id}))
        (p/catch (fn [error]
                   ;; EIP-1193 userRejectedRequest error. If this happens, the
                   ;; user rejected the connection request.
                   (if (= 4001 (.-code error))
                     (throw (ex-info ::user-rejected-request {:error error}))
                     (throw (ex-info ::generic-error {:error error}))))))))

(defn fetch-state
  "Fetch wallet state and resolve it to a map. This function will not open the
  MetaMask window. You can also use this function to detect if MetaMask is
  disconnected."
  []
  (-> (fetch-chain-id)
      (p/then (fn [chain-id]
                (let [accounts (fetch-accounts)]
                  (if (seq accounts)
                    {:accounts accounts :chain-id chain-id}
                    (throw (ex-info ::disconnected {:error nil}))))))))

(defn listen-accounts-changed [f]
  (.on js/ethereum "accountsChanged"
       (fn [accounts]
         (f (js->clj accounts))))
  nil)

(defn listen-disconnect [f]
  (.on js/ethereum "disconnect" f)
  nil)

(defn listen-chain-changed [f]
  (.on js/ethereum "chainChanged" f)
  nil)
