(ns acme.web.coeffect
  (:require [acme.web.util :as util]
            [re-frame.core :refer [reg-cofx]]))

(reg-cofx
 ::timestamp
 (fn [cofx _]
   (assoc cofx :timestamp (util/unix-timestamp))))
