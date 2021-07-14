(ns acme.web.view.page.create-stream
  (:require [acme.web.event.core :as event]
            [acme.web.event.stream :as stream]
            [acme.web.sub :as sub]
            [acme.web.util :refer [<sub]]
            [acme.web.view.common :as common]
            [acme.web.view.icon :as icon]
            [clojure.string :as string]
            [re-frame.core :refer [dispatch]]))

(defn footer []
  (if (<sub ::sub/wallet-main-network?)
    [:footer.bg-red-500.w-full.p-8.h-100.text-center.text-white
     [:div.max-w-lg.mx-auto.font-bold.uppercase
      "You are using the main network. Please switch to a test or local network."]]
    [:footer.bg-yellow-300.w-full.p-8.h-100.text-center
     [:div.max-w-lg.mx-auto
      [:strong.px-1 "Important:"]
      "Use this application exclusively for testing purposes. Do not use it to
      send money in the main Ethereum network."]]))

(defn overlay []
  [:div.co-content.flex.justify-center
   [:div.text-gray-700.self-center.min-w-full.max-w-full
    [:div.shadow-md.font-bold.border-2.border-purple-400.rounded-md.py-4.px-5.bg-purple-300
     [:img.mx-auto.mb-3
      {:src "/images/waiting-panda.svg"
       :style {:max-width "150px"}}]
     [:div.text-center
      (let [form-status (<sub ::sub/form-stream-status)]
        (condp = form-status
          :form/stream-transaction-created
          "Successfully created stream. Redirecting..."

          :form/creating-stream-transaction
          "Creating stream transaction..."

          :form/approving-sablier-contract
          "Approving stream deposit..."

          "Processing..."))]]]])

(defn recipient-address []
  (let [address (<sub ::sub/form-stream-recipient-address)
        valid? (not (<sub ::sub/form-stream-invalid-recipient-address))
        present? (not (string/blank? address))]
    [:div
     [:label.block.font-bold.text-gray-700
      {:for :stream-recipient-address}
      [:div.flex.items-center
       [:span.mr-2.w-5.h-5 [icon/outline-user]]
       "Recipient address"]]
     [:div.mt-1
      [:div.flex.space-x-2.items-center
       [:input#stream-recipient-address.co-input-text.font-mono.w-full.max-w-md
        {:placeholder "0x0"
         :required true
         :auto-complete :off
         :spell-check false
         :max-length 42
         :type :text
         :value address
         :on-change #(dispatch [::stream/form-update-recipient-address (-> % .-target .-value)])}]
       (when (and present? valid?)
         [common/identicon 28 address])]
      (when (and present? (not valid?))
        [:div.py-1.px-2.rounded-sm.text-red-500.text-sm.font-bold.up
         "Invalid recipient address"])]]))

(defn token-address []
  (let [address (<sub ::sub/form-stream-token-address)]
    [:div
     {:on-mouse-enter #(dispatch [::stream/on-mouse-enter:form-token-address])
      :on-mouse-leave #(dispatch [::stream/on-mouse-leave:form-token-address])}
     [:label.block.font-bold.text-gray-700
      {:for :token-address}
      [:div.flex.items-center
       [:span.mr-2.w-5.h-5 [icon/outline-document-text]]
       "Token contract address"]]
     [:div.flex.flex-col.mt-1
      [:div.flex.items-center
       [:input#token-address.co-input-text.font-mono.w-full.max-w-md
        {:placeholder "0x0"
         :required true
         :auto-complete :off
         :spell-check false
         :max-length 42
         :type :text
         :value address
         :on-blur #(dispatch [::stream/fetch-token])
         :on-change #(dispatch [::stream/form-update-token-address (-> % .-target .-value)])}]
       (when (<sub ::sub/form-stream-show-clipboard-action?)
         [:div.h-6.w-6.ml-1.transition.cursor-pointer.text-gray-600.hover:text-purple-600
          {:on-click #(dispatch [::event/clipboard-write
                                 {:id ::clipboard:create-stream:token-address
                                  :db-path [:form :stream :fields :token-address :value]}])}
          [icon/outline-clipboard-copy]])]
      (when (and (not (string/blank? address))
                 (<sub ::sub/form-stream-invalid-token-address))
        [:div.py-1.px-2.rounded-sm.text-red-500.text-sm.font-bold.up
         "Invalid token address"])]]))

(defn stream-amount []
  (let [amount (<sub ::sub/form-stream-amount)]
    [:div
     [:label.block.font-bold.text-gray-700
      {:for :stream-amount}
      "How much do you want to stream?"]
     [:div.flex.flex-col.mt-1
      [:div
       [:div.flex.items-center.space-x-2
        [:input#stream-amount.co-input-text.p-2
         {:placeholder "0.0000"
          :required true
          :type :text
          :value amount
          :on-change #(dispatch [::stream/form-update-amount (-> % .-target .-value)])}]
        (when-let [token-symbol (<sub ::sub/form-stream-token-symbol)]
          [:div.font-bold.space-x-1.p-2.bg-gray-200.rounded-md.items-center
           token-symbol])]]
      (when (and (not (string/blank? amount))
                 (<sub ::sub/form-stream-invalid-amount))
        [:div.py-1.px-2.rounded-sm.text-red-500.text-sm.font-bold.up
         "Invalid amount"])]]))

(defn stream-period []
  (let [time (<sub ::sub/form-stream-time)
        duration-unit (<sub ::sub/form-stream-duration-unit)]
    [:div
     [:label.block.font-bold.text-gray-700
      {:for :stream-time}
      "For how long do you want to stream?"]
     [:div.mt-1.flex.space-x-2
      [:input#stream-time.co-input-text.w-32.block
       {:placeholder 0
        :required true
        :step 1
        :min 0
        :type :number
        :value time
        :on-change #(dispatch [::stream/form-update-time (-> % .-target .-value)])}]
      [:select.co-select.w-32
       {:default-value duration-unit
        :on-change #(dispatch [::stream/form-update-duration-unit (-> % .-target .-value)])}
       [:option {:value "day"} "Day(s)"]
       [:option {:value "hour"} "Hour(s)"]
       [:option {:value "minute"} "Minute(s)"]]]
     (when (and (not (string/blank? time))
                (<sub ::sub/form-stream-invalid-time))
       [:div.py-1.px-2.rounded-sm.text-red-500.text-sm.font-bold.up
        "Invalid duration. Use a positive integer"])]))

(defn form-errors []
  (when-let [{:keys [id reason]} (first (<sub ::sub/form-stream-errors))]
    [:div.p-3.mb-4.bg-red-200.rounded-md
     (condp = id
       :error/metamask:user-denied-transaction
       "You need to approve the transaction."
       [:span
        "Could not create the stream."
        (when reason
          [:span.ml-1
           "Reason: "
           [:em reason]])])]))

(defn root []
  (let [valid? (<sub ::sub/form-stream-valid?)]
    [:div.p-8.flex.flex-col
     [:div.mb-6
      [:h1.co-page-title.flex-grow
       "Create Stream"]
      [:div.my-2.border-b-2.border-gray-100]]
     [form-errors]
     [:div.flex.flex-col
      [:div.space-y-4.flex-grow
       [recipient-address]
       [token-address]
       [stream-amount]
       [stream-period]]
      [:div.mt-8.space-x-4
       [:button.co-btn.co-btn-primary
        {:on-click #(dispatch [::stream/form-create])
         :disabled (not valid?)}
        "Create stream"]
       [:button.co-btn.co-btn-secondary
        {:on-click #(dispatch [::stream/form-cancel])}
        "Cancel"]]]]))
