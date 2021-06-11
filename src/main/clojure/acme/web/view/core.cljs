(ns acme.web.view.core
  (:require [acme.web.route :as route]
            [acme.web.sub :as sub]
            [acme.web.util :refer [<sub]]
            [acme.web.view.common :as common]
            [acme.web.view.page.about :as page-about]
            [acme.web.view.page.create-stream :as page-create-stream]
            [acme.web.view.page.landing :as page-landing]
            [acme.web.view.page.streams :as page-streams]
            [tailwind-hiccup.core :refer [tw]]))

(defmulti page-for identity)
(defmulti overlay-for identity)
(defmulti footer-for identity)

(defn nav-bar []
  (let [connected? (= :wallet/connected (<sub ::sub/wallet-status))]
    [:nav.bg-gray-600.py-2.text-lg.text-white.font-bold.p-8
     [:ul.flex.space-x-2.items-center.flex-wrap.max-w-4xl.mx-auto
      [:li
       [:a.co-logo.block.hover:bg-gray-600 {:href "/"}
        [:img.h-12.w-auto
         {:src "/images/logo.svg"}]]]
      (when connected?
        [:li
         [:a {:href (route/path-for ::route/streams)}
          "Streams"]])
      [:li.flex-grow
       [:a {:href (route/path-for ::route/about)}
        "About"]]
      (when connected?
        [:li.flex.items-center.space-x-2.py-2.rounded-md.whitespace-nowrap
         [:span.border-2.border-gray-400.rounded-full
          {:style {:padding "2px"}}
          [common/identicon 28 (<sub ::sub/wallet-account-address)]]
         [:div.hidden.sm:block.text-sm.font-mono
          (condp = (<sub ::sub/wallet-chain)
            :kovan "KOV"
            :goerli "OGOR"
            :ropsten "ROP"
            :rinkeby "RIN"
            :local "LOCAL"
            "")]
         [:span.hidden.sm:block.font-mono.tracking-wide.box-border.font-medium
          (<sub ::sub/wallet-account-short-address)]])]]))

(defn root []
  (let [active-page (<sub ::sub/active-page)
        status (<sub ::sub/wallet-status)]
    (when-not (= status :wallet/unknown)
      [:div.min-h-screen.bg-white.overflow-hidden.bg-purple-50
       (tw (when (or (= status :wallet/connecting)
                     (= status :wallet/connection-rejected)
                     (= status :wallet/missing-dependency)
                     (= status :wallet/disconnected))
             [:co-landing-page]))
       (when-let [overlay (<sub ::sub/overlay)]
         [:div.co-overlay
          [:div.co-bg.bg-gray-200]
          (overlay-for overlay)])
       [:div.flex.flex-col.min-h-screen
        [nav-bar]
        [:main.relative.pb-4.px-4.sm:pb-8.sm:px-8.flex-grow
         [:div.container.relative.mx-auto.max-w-4xl
          [:div.bg-white.shadow-md.text-gray-800.rounded-lg
           (page-for [active-page status])]]]
        (footer-for active-page)]])))

(defmethod overlay-for :overlay/create-stream [] [page-create-stream/overlay])
(defmethod overlay-for :default [])

(defmethod footer-for ::route/create-stream [] [page-create-stream/footer])
(defmethod footer-for :default [])

(defmethod page-for [::route/home :wallet/connecting]          [] [page-landing/root])
(defmethod page-for [::route/home :wallet/connection-rejected] [] [page-landing/root])
(defmethod page-for [::route/home :wallet/disconnected]        [] [page-landing/root])
(defmethod page-for [::route/home :wallet/missing-dependency]  [] [page-landing/root])

(defmethod page-for [::route/about :wallet/connected]           [] [page-about/root])
(defmethod page-for [::route/about :wallet/connection-rejected] [] [page-about/root])
(defmethod page-for [::route/about :wallet/disconnected]        [] [page-about/root])
(defmethod page-for [::route/about :wallet/missing-dependency]  [] [page-about/root])

(defmethod page-for [::route/home    :wallet/connected] [] [page-streams/root])
(defmethod page-for [::route/streams :wallet/connected] [] [page-streams/root])

(defmethod page-for [::route/create-stream :wallet/connected] [] [page-create-stream/root])

(defmethod page-for :default [] (page-for [::route/home :wallet/disconnected]))
