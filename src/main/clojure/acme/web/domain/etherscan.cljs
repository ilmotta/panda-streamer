(ns acme.web.domain.etherscan
  (:require [acme.web.domain.wallet :as wallet]
            [acme.web.util :as util]))

(def host-by-name
  {:goerli "https://api-goerli.etherscan.io"
   :kovan "https://api-kovan.etherscan.io"
   :rinkeby "https://api-rinkeby.etherscan.io"
   :ropsten "https://api-ropsten.etherscan.io"})

(defn block-number-by-timestamp-url [chain-id timestamp]
  (when-let [host (-> chain-id util/bignum->int wallet/chain-ids host-by-name)]
    (str host "/api?module=block&action=getblocknobytime"
         "&timestamp=" timestamp
         "&closest=before")))
