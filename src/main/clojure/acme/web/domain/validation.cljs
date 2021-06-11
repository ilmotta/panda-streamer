(ns acme.web.domain.validation
  (:require ["@ethersproject/address" :as Address]))

(def supported-stream-duration-units
  {:day "day"
   :hour "hour"
   :minute "minute"})

(defn address? [value]
  (Address/isAddress (str value)))

(defn money? [value]
  (re-find #"^\d+\.?\d*$" (str value)))

(defn stream-duration-unit? [unit]
  (supported-stream-duration-units (keyword unit)))

(defn pos-integer? [value]
  (let [n (js/parseInt value 10)]
    (and (pos-int? n)
         (re-find #"^\d+$" (str value)))))

(defn pos-float? [value]
  (let [n (js/parseFloat value 10)]
    (and (float? n) (pos? n))))

(defn valid-stream-form?
  [{:keys [recipient-address token-address amount time unit]}]
  (and (money? amount)
       (pos-float? amount)
       (address? recipient-address)
       (address? token-address)
       (stream-duration-unit? unit)
       (pos-integer? time)))
