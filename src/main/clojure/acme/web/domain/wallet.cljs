(ns acme.web.domain.wallet
  (:require [clojure.string :as string]))

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
