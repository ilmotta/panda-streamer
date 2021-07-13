(ns acme.web.view.page.streams
  (:require [acme.web.event.core :as event]
            [acme.web.event.stream :as stream]
            [acme.web.sub :as sub]
            [acme.web.util :refer [<sub] :as util]
            [acme.web.view.common :as common]
            [acme.web.view.icon :as icon]
            [re-frame.core :refer [dispatch]]
            [tailwind-hiccup.core :refer [tw]]))

(defn header []
  (let [loading? (<sub ::sub/loading-streams?)]
    [:div.flex.flex-col.p-8
     [:div.flex.flex-wrap
      [:h1.co-page-title.flex-grow
       "Streams"]
      [:div.flex.space-x-3
       [:button.co-btn-secondary.flex
        {:on-click #(dispatch [::stream/filter-logs])}
        [:div.h-6.w-6
         (tw (when loading? [:animate-spin]))
         [icon/outline-refresh]]]
       [:div.flex.items-center
        [:select.co-select
         {:on-change #(dispatch [::stream/filter-logs {:hours (-> % .-target .-value)}])
          :default-value (str (<sub ::sub/max-history-hours))}
         [:option {:value 24} "Last 24h"]
         [:option {:value (* 7 24)} "Last 7 days"]
         [:option {:value (* 30 24)} "Last 30 days"]]]
       [:button.co-btn-primary.w-auto.flex.items-center
        {:on-click #(dispatch [::stream/form-show])}
        [:span.sm:hidden.h-6.w-6 [icon/plus]]
        [:span.hidden.sm:inline-block.ml-1.sm:m-0.uppercase.text-sm "New Stream"]]]]
     [:div.my-2.border-b-2.border-gray-100]]))

(defn root []
  (let [logs (<sub ::sub/stream-logs)
        _updated-at (<sub ::sub/streams-updated-at)
        loading? (<sub ::sub/loading-streams?)]
    [:div.pb-8
     [header]
     (cond
       loading?
       [common/loading-placeholder]

       (not (seq logs))
       [:div.mx-8.p-6.border-dashed.border-4.rounded-md.text-lg.text-center.h-72.flex.items-center.justify-center.bg-gray-50
        "No successful stream events could be found."]

       :else
       [:div.flex.flex-col
        (doall
         (for [log logs
               :let [{:keys [tx-hash sync-status]} log
                     {:keys [stream-id deposit start-time stop-time recipient]} (get log :args)]]
           [:div.flex.flex-col.px-8.py-2.bg-white.odd:bg-gray-50
            {:key tx-hash}
            [:div.flex.items-center.space-x-2.mb-2
             [:h2.text-lg.font-mono.font-bold
              (str "#" (.toString stream-id))]
             (condp = sync-status
               :incomplete
               [:div.flex.items-center.space-x-1
                [icon/spinner]]

               :complete
               (let [elapsed-percentage (util/elapsed-percentage-rounded start-time stop-time 5)]
                 (if (< elapsed-percentage 100)
                   [:div.w-full.sm:w-48 [common/progress-bar elapsed-percentage]]
                   [:div.flex.text-gray-500.items-center.space-x-1
                    [:span [icon/outline-check-circle]]]))

               [:span.text-red-500
                [icon/outline-exclamation-circle]])]
            [:div.flex.flex-col.sm:flex-row.sm:space-x-4
             [:div.flex.flex-col
              {:class "sm:w-1/3"}
              [:div.flex.items-center.space-x-1

               [:span "Deadline"]
               [:span (util/humanize-time
                       (util/current-time-delta-seconds stop-time))]]
              [:div.flex.items-center.space-x-1
               [:span "Transaction"]
               [:div.w-5.h-5.text-gray-500.cursor-pointer.hover:text-purple-500
                {:on-click #(dispatch [::event/clipboard-write {:id ::tx-hash
                                                                :text tx-hash}])}
                [icon/outline-clipboard-copy]]]]
             [:div.order-3.sm:order-2.flex.flex-col.flex-grow.space-y-1
              {:class "sm:w-1/3"}
              [:div.flex.flex-col
               [:div.flex.items-center
                [:span.mr-1.opacity-90 [common/identicon 24 recipient]]
                [:div.flex.flex-col
                 [:div.flex.items-center.space-x-1
                  [:span "To:"]
                  [:div.relative.whitespace-nowrap.flex
                   [:div.text-gray-500 "0x"]
                   [:div (subs (util/short-hash recipient) 2)]]
                  [:div.text-gray-500.-right-6.ml-1.w-5.h-5.cursor-pointer.hover:text-purple-500
                   {:on-click #(dispatch [::event/clipboard-write {:id ::recipient-address :text recipient}])}
                   [icon/outline-clipboard-copy]]]

                 [:div.flex.space-x-1.items-center.overflow-ellipsis
                  [:span (.toString deposit)]]]]]]
             [:div.order-2.sm:order-3.flex.space-y-1]]]))])]))
