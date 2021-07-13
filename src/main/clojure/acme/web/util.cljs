(ns acme.web.util
  (:require ["@ethersproject/bignumber" :refer [BigNumber]]
            ["@metamask/jazzicon" :as jazzicon]
            [cljs.core.async :refer [go-loop go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as string]
            [re-frame.core :as re-frame]))

(defn <sub
  "This is a convenience function used throughout most view components. The name
  was taken from the official re-frame documentation, but this one goes one step
  further and eliminates the need to surround subscribe args in a vector. Given
  how common it is to subscribe this seems like a useful improvement. In this
  rather small app there are already ~30 calls to subscribe. Of course, we must
  be careful not to go all out and create short aliases for everything.

  Example:
  From: @(subscribe [:subscription-id arg-a arg-b])
  To:   (<sub :subscription-id arg-a arg-b)

  Inspiration:
  https://day8.github.io/re-frame/correcting-a-wrong/#lambdaisland-naming-lin"
  [& args]
  @(re-frame/subscribe (vec args)))

(def time-config
  {:year {:unit "y" :seconds (* 365 24 3600)}
   :month {:unit "mo" :seconds (* 30 24 3600)}
   :day {:unit "d" :seconds (* 24 3600)}
   :hour {:unit "h" :seconds 3600}
   :minute {:unit "min" :seconds 60}})

(defn bignum [value]
  (.from BigNumber value))

(defn bignum->int [value]
  (.toNumber (bignum value)))

(defn bignum-desc-comparator
  "A descending order comparator for Ethers BigNumber."
  [a b]
  (let [difference (.sub b a)]
    (cond
      (.gt difference 0) 1
      (.lt difference 0) -1
      :else 0)))

(defn humanize-time
  "Return an English humanized representation of `seconds`."
  [seconds]
  (let [past-word (when (pos? seconds) " ago")
        seconds (Math/abs seconds)
        {:keys [year month day hour minute]} time-config]
    (cond
      (< seconds (:seconds hour))
      (let [minutes (.floor js/Math (/ seconds (:seconds minute)))]
        (str minutes (:unit minute) past-word))

      (< seconds (:seconds day))
      (let [hours (.floor js/Math (/ seconds (:seconds hour)))]
        (str hours (:unit hour) past-word))

      (< seconds (:seconds month))
      (let [days (.floor js/Math (/ seconds (:seconds day)))]
        (str days (:unit day) past-word))

      (< seconds (:seconds year))
      (let [months (.floor js/Math (/ seconds (:seconds month)))]
        (str months (:unit month)))

      :else
      (let [years (.floor js/Math (/ seconds (:seconds year)))]
        (str years (:unit year) past-word)))))

(defn short-hash
  "Return the first 4 and last 4 characters of `hash` (excluding the initial 0x)."
  [hash]
  (string/join [(subs hash 0 6)
                "…"
                (subs hash (- (count hash) 4))]))

(defn unix-timestamp
  "Return the current Unix timestamp in seconds."
  ([]
   (unix-timestamp (.now js/Date)))
  ([date]
   (.floor js/Math (/ date 1000))))

(defn current-time-delta-seconds
  "Calculate the difference between the current Unix timestamp and
  `seconds-bignum`."
  [^BigNumber seconds]
  (- (unix-timestamp) (.toNumber seconds)))

(defn time-in-seconds
  "Normalize `t` to the number of seconds per `unit`."
  [unit t]
  (condp = unit
    "day" (* t 3600 24)
    "hour" (* t 3600)
    "minute" (* t 60)))

(defn elapsed-percentage-rounded
  "Calculate the percentage of time elapsed since `start-time-seconds` started and
  rounded to the `nearest-integer` (default 10). `start-time-seconds` should be
  less than `stop-time-seconds`.

  If the current timestamp is smaller than `start-time-seconds` then it simply
  returns zero."
  ([^BigNumber start-time-seconds
    ^BigNumber stop-time-seconds]
   (^BigNumber elapsed-percentage-rounded
    ^BigNumber start-time-seconds
    ^BigNumber stop-time-seconds
    10))
  ([start-time-seconds stop-time-seconds nearest-integer]
   (let [start-time-seconds (.toNumber start-time-seconds)
         stop-time-seconds (.toNumber stop-time-seconds)
         timestamp (unix-timestamp)]
     (cond
       (<= timestamp start-time-seconds) 0

       (<= timestamp stop-time-seconds)
       (let [percentage (* 100 (/ (- timestamp start-time-seconds)
                                  (- stop-time-seconds start-time-seconds)))]
         (* (.ceil js/Math
                   (/ percentage nearest-integer))
            nearest-integer))

       :else 100))))

(defn identicon-html
  "Generate an identicon node and return its inner HTML.
  The results from this function should be set in a Hiccup element using
  {:dangerouslySetInnerHTML :__html \"...\"}."
  [address size]
  (let [identicon (jazzicon size (js/parseInt (subs address 0 10) 16))]
    (set! (-> identicon .-style .-display) nil)
    (.-outerHTML identicon)))

(defn ^:deprecated find-block-by-timestamp
  "Run a binary search on all blocks until it finds one with a timestamp smaller
  than the requested `timestamp` by at most `max-error-seconds`. `timestamp` is
  a proper Unix timestamp in seconds. `provider` is used to fetch blocks by
  their number (index).

  If no block can be found (which is quite common in local/test blockchains)
  then it returns the earliest possible block that's still smaller than
  `timestamp`. If `timestamp` is smaller than the earliest block (0th) in the
  chain, then the first block is returned.

  DEPRECATED: Although this function works fine, it's usage has been replaced by
  the Etherscan API action `getblocknobytime`. One downside of using Etherscan
  is it's rate limited. The other downside is that the API key has to be
  hardcoded in the code in a completely static website. According to Etherscan
  blog, starting from Feb 15th 2020 developers are required to use a valid API
  key.

  This implementation obviously assumes blocks are ordered by timestamp, but not
  *strictly* ordered by timestamp, which is in accordance with many sources, as
  there's no concept of absolute clock in Ethereum blockchains. This means
  there's a margin for error whenever the algorithm finds a block timestamp is
  smaller than a subsequent one. From (1) it appears the difference in the
  Ethereum main blockchain can be as high as 11 blocks and some sources (2,
  though outdated) specifically say block timestamps can differ up to 900s. All
  of this means the current implementation has room for improvement and in some
  situations it could ignore a couple lower blocks. One solution could be to
  assume the final answer is always 'incorrect' and additionally verify the
  median timestamp delta from the previous 11 blocks, but this looked like
  over-engineering for the moment.

  It can correctly find the smallest block timestamp within `max-error-seconds`
  in a matter of seconds, but it hasn't been tested in congested blockchains
  where the network latency could make it considerably slower. In terms of
  asynchronous error handling it can be drastically improved to at least
  consider timing out `getBlock` calls.

  There's also the cost involved in making these calls, as a binary search will
  make O(log n) comparisons. The algorithm could perhaps be optimized with a
  simple memoization technique given that block timestamps are immutable.
  Another improvement is to store the earliest block number found in the Local
  Storage with a TTL, like 2h. Libraries like core.cache or core.memoize could
  be particularly handy. For the record, Infura gives 100k requests per day for
  free, which is plenty for certain apps.

  The approach presented in this function could be used to build more
  sofisticated searches by block timestamps, which is pretty interesting
  considering timestamps are not indexed and thus can't be efficiently searched
  without an external storage like PostgreSQL.

  (1) - https://www.sciencedirect.com/science/article/abs/pii/S0306457320309602
  (2) - https://github.com/ethereum/wiki/blob/c02254611f218f43cbb07517ca8e5d00fd6d6d75/Block-Protocol-2.0.md#block-validation-algorithm"
  [{:keys [timestamp provider max-error-seconds]}]
  (go
    (let [latest-block-number (<p! (.getBlockNumber provider))
          latest-block (<p! (.getBlock provider latest-block-number))]
      (<! (go-loop [left 0
                    right (dec (.-number latest-block))
                    earliest latest-block]
            (if (<= left right)
              (let [middle (int (Math/floor (/ (+ left right) 2)))
                    block (<p! (.getBlock provider middle))
                    block-t (.-timestamp block)]
                (cond
                  (< block-t timestamp)
                  (if (<= (- timestamp block-t) max-error-seconds)
                    block
                    (recur (inc middle) right block))

                  (> block-t timestamp)
                  (recur left (dec middle) earliest)

                  ;; In the rare ocasion where the timestamp being searched is equal
                  ;; to the block timestamp simply return it.
                  :else block))

              ;; The vast majority of the time the search will end here and it will
              ;; return the block with the smallest error from the actual timestamp
              ;; being searched.
              (if (= latest-block earliest)
                ;; When the earliest block found is equal to the latest block,
                ;; it means it couldn't find any block earlier than the latest
                ;; one. This can happen if the timestamp is earlier than the
                ;; oldest block. This is fairly easy to happen in new local/test
                ;; blockchains.
                (<p! (.getBlock provider 0))
                earliest)))))))

(defn ^:deprecated find-block-number-by-timestamp
  "A convenience wrapper to return the block number found or zero."
  [& args]
  (go
    (let [block (<! (apply find-block-by-timestamp args))]
      (or (and block (.-number block))
          0))))

(defn metamask-installed?
  "Return true when the ethereum object is present and it reports as MetaMask.

  As baffling as it sounds, the implementation below doesn't work after the
  Google Closure compilation runs (prod only).

  (some-> js/window .-ethereum .-isMetaMask)

  I couldn't find anything on the Internet, but on further inspection I believe
  it has something to do with lazy evaluation, because if the `some->`
  expression is printed to the console and then returned it returns `true` as
  expected."
  []
  (and (.-ethereum js/window)
       (-> js/window .-ethereum .-isMetaMask)))

(defn wallet-connected? [db]
  (and (= :wallet/connected (get-in db [:wallet :status]))
       (seq (js->clj (some-> js/window .-ethereum .-_state .-accounts)))))
