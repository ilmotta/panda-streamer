(ns acme.web.domain.etherscan
  (:require [acme.web.domain.wallet :as wallet]
            [acme.web.util :as util]
            [ajax.core :as ajax]
            [promesa.core :as p]))

(def host-by-name
  {:goerli "https://api-goerli.etherscan.io"
   :kovan "https://api-kovan.etherscan.io"
   :rinkeby "https://api-rinkeby.etherscan.io"
   :ropsten "https://api-ropsten.etherscan.io"})

(defn fetch-block-number-by-timestamp
  [chain-id timestamp]
  (let [host (-> chain-id util/bignum->int wallet/chain-ids host-by-name)
        uri (str host "/api")]
    (p/create
     (fn [resolve reject]
       (ajax/ajax-request
        {:uri uri
         :method :get
         :timeout 5000
         :response-format (ajax/json-response-format {:keywords? true})
         :params {:timestamp timestamp
                  :closest "before"
                  :module "block"
                  :action "getblocknobytime"}
         :handler (fn [[ok? response]]
                    (if (and ok? (= "1" (:status response)))
                      (resolve (js/parseInt (:result response) 10))
                      (reject response)))})))))
