(ns acme.web.domain.sablier
  (:require ["@ethersproject/abi" :refer [Interface]]
            ["@ethersproject/contracts" :refer [Contract]]
            [acme.web.util :as util]
            [cljs.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]))

;; Prefer changing the local Sablier contract address in shadow-cljs.edn.
(goog-define sablier-local-contract-address "")

(def contract-addresses
  {:goerli "0x590b3974533141a44a89033deEcf932F52fcFDea"
   :kovan "0xc04Ad234E01327b24a831e3718DBFcbE245904CC"
   :rinkeby "0xc04Ad234E01327b24a831e3718DBFcbE245904CC"
   :ropsten "0xc04Ad234E01327b24a831e3718DBFcbE245904CC"
   :local sablier-local-contract-address})

(def chain-ids
  {3 :ropsten
   4 :rinkeby
   5 :goerli
   42 :kovan
   1337 :local})

;; These ABIs were cleaned-up to only have the minimum necessary to approve
;; spenders of an ERC-20 token, to create basic streams and to retrieve
;; CreateStream events. The Sablier ABI was taken directly from Etherscan in the
;; Rinkeby network.
(def abi
  {::erc20-token [{:constant false,
                   :inputs
                   [{:name "_spender", :type "address"}
                    {:name "_value", :type "uint256"}],
                   :name "approve",
                   :outputs [{:name "", :type "bool"}],
                   :payable false,
                   :stateMutability "nonpayable",
                   :type "function"}
                  {:constant true,
                   :inputs [],
                   :name "symbol",
                   :outputs [{:name "", :type "string"}],
                   :payable false,
                   :stateMutability "view",
                   :type "function"}]
   ::sablier [{:inputs [],
               :payable false,
               :stateMutability "nonpayable",
               :type "constructor"}
              {:anonymous false,
               :inputs
               [{:indexed true,
                 :name "streamId",
                 :type "uint256"}
                {:indexed true,
                 :name "sender",
                 :type "address"}
                {:indexed true,
                 :name "recipient",
                 :type "address"}
                {:indexed false,
                 :name "deposit",
                 :type "uint256"}
                {:indexed false,
                 :name "tokenAddress",
                 :type "address"}
                {:indexed false,
                 :name "startTime",
                 :type "uint256"}
                {:indexed false,
                 :name "stopTime",
                 :type "uint256"}],
               :name "CreateStream",
               :type "event"}
              {:constant false,
               :inputs
               [{:name "recipient", :type "address"}
                {:name "deposit", :type "uint256"}
                {:name "tokenAddress", :type "address"}
                {:name "startTime", :type "uint256"}
                {:name "stopTime", :type "uint256"}],
               :name "createStream",
               :outputs [{:name "", :type "uint256"}],
               :payable false,
               :stateMutability "nonpayable",
               :type "function"}]})

(defn round-stream-deposit [preferred-deposit delta-seconds]
  (.sub preferred-deposit
        (.mod preferred-deposit delta-seconds)))

(defn make-token-contract [signer token-address]
  (new Contract
       token-address
       (clj->js (abi ::erc20-token))
       signer))

(defn make-sablier-contract [signer sablier-address]
  (new Contract
       sablier-address
       (clj->js (abi ::sablier))
       signer))

(defn calculate-stream-deposit
  [{:keys [duration-seconds
           initial-delay-seconds
           preferred-deposit]}]
  (let [start-time (+ (util/unix-timestamp) initial-delay-seconds)
        stop-time (+ start-time duration-seconds)
        delta (- stop-time start-time)]
    {:start-time start-time
     :stop-time stop-time
     :deposit (.toString (round-stream-deposit preferred-deposit delta))}))

(defn address-for [chain-id]
  (let [chain-name (chain-ids (util/bignum->int chain-id))]
    (get contract-addresses chain-name)))

(defn get-address [^js signer]
  (go
    (<p! (.getAddress signer))))

(defn make-create-stream-filter [^js contract address]
  (js->clj ((-> contract .-filters .-CreateStream) nil address)))

(def log-parser
  (new Interface (clj->js (abi ::sablier))))

(defn js->clj-log [{:keys [log parsed-log]}]
  (let [args ^js (.-args parsed-log)]
    {:block-number (.-blockNumber log)
     :block-hash (.-blockHash log)
     :tx-hash (.-transactionHash log)
     :args {:deposit (.-deposit args)
            :recipient (.-recipient args)
            :sender (.-sender args)
            :start-time (.-startTime args)
            :stop-time (.-stopTime args)
            :stream-id (.-streamId args)
            :token-address (.-tokenAddress args)}}))

(defn js->clj-receipt [receipt]
  {:tx-hash (.-transactionHash receipt)
   :status (if (= 1 (.-status receipt)) :ok :error)
   :gas-used (.-gasUsed receipt)
   :cumulative-gas-used (.-cumulativeGasUsed receipt)
   :block-hash (.-blockHash receipt)
   :block-number (.-blockNumber receipt)
   :logs (js->clj (.-logs receipt))})

(def logs-create-stream-transducer
  (comp
   (map (fn [log]
          {:log log
           :parsed-log (.parseLog log-parser log)}))
   (map js->clj-log)
   (map #(assoc % :sync-status :incomplete))))

(defn fetch-stream-receipt [log provider]
  (go
    (let [receipt (<p! (.waitForTransaction provider (:tx-hash log)))]
      (js->clj-receipt receipt))))

(defn fetch-stream-logs [{:keys [from-block provider chain-id]}]
  (go
    (try
      (let [payer (.getSigner provider)
            payer-address (<! (get-address payer))
            sablier-address (address-for chain-id)
            sablier-contract (make-sablier-contract payer sablier-address)
            filter (merge (make-create-stream-filter sablier-contract payer-address)
                          {:fromBlock (or from-block 0)
                           :toBlock "latest"})
            logs (<p! (.getLogs provider (clj->js filter)))]
        logs)
      (catch js/Error error
        error))))
