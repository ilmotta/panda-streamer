(ns acme.web.view.page.landing
  (:require [acme.web.event.wallet :as wallet]
            [acme.web.sub :as sub]
            [acme.web.util :refer [<sub]]
            [re-frame.core :refer [dispatch]]
            [tailwind-hiccup.core :refer [tw]]))

(defn root []
  (let [status (<sub ::sub/wallet-status)
        connecting? (= status :wallet/connecting)
        missing-metamask? (= status :wallet/missing-dependency)]
    [:div.sm:mt-4
     [:h1.text-4xl.tracking-tight.text-center.font-extrabold.text-gray-900.sm:text-5xl.md:text-6xl
      [:span.block "Simplify"]
      [:span.block.text-purple-600.xl:inline "real-time finance"]]
     [:img.mt-3
      {:src-set (str "images/pandas-landing-page-small-screen.png 1087w,"
                     "images/pandas-landing-page.png 1715w")
       :sizes (str "(max-width: 640px) 1087px,"
                   "1715px")
       :src "images/pandas-landing-page.png"}]
     (if missing-metamask?
       [:div.font-bold.bg-indigo-400.p-3.py-5.mt-3.text-center.text-white.rounded-sm
        "Please, install"
        [:a.text-gray-800.underline.hover:text-indigo-900.mx-1
         {:href "https://metamask.io/"
          :target "_blank"}
         "MetaMask"] "and then reload this page."]
       [:div.mt-5.sm:mt-0.sm:flex.sm:justify-center
        [:div.rounded-md.shadow
         [:button.transition.duration-500.w-full.flex.items-center.justify-center.px-8.py-3.border.border-transparent.text-base.font-medium.rounded-md.text-white.bg-purple-600.md:py-4.md:text-lg.md:px-10.disabled:cursor-auto.disabled:opacity-70
          (tw (when-not connecting?
                [:hover:bg-purple-800])
              {:on-click #(dispatch [::wallet/request-connection])
               :disabled connecting?})
          (if connecting?
            [:span {:dangerouslySetInnerHTML {:__html "Waiting wallet approval&hellip;"}}]
            "Get started")]]])
     [:div.sm:mx-16.mt-3.md:mt-5.text-md
      [:p.text-center.text-gray-600.sm:text-lg.md:text-xl.lg:mx-0
       "Connect your wallet and send money in real-time. After a one-time deposit, these pandas will"
       [:em.px-1 "stream money"] "without you lifting a finger again."]]]))
