(ns acme.web.view.common
  (:require [acme.web.util :as util]))

(defn identicon [size address]
  (when address
    [:span {:dangerouslySetInnerHTML {:__html (util/identicon-html address size)}}]))

(defn progress-bar [percentage]
  [:div.meter.h-4.bg-gradient-to-b.from-gray-200.to-gray-300.border.border-purple-300
   [:span.bg-purple-300 {:style {:width (str percentage "%")}}]])

(defn loading-placeholder []
  [:div.animate-pulse.mx-8.bg-gradient-to-b.from-gray-100.to-white
   [:svg.text-white
    {:height "168" :fill "currentColor" :width "100%"}
    [:path
     {:d "M0 0h872v168H0V0zm0 3.99A3.988 3.988 0 013.993 0h133.014A3.99 3.99 0 01141 3.99v16.02a3.988 3.988 0 01-3.993 3.99H3.993A3.99 3.99 0 010 20.01V3.99zm0 96A3.998 3.998 0 014 96h262c2.21 0 4 1.784 4 3.99v16.02a3.998 3.998 0 01-4 3.99H4c-2.21 0-4-1.784-4-3.99V99.99zm291 0a3.998 3.998 0 014.002-3.99h287.996A3.995 3.995 0 01587 99.99v16.02a3.998 3.998 0 01-4.002 3.99H295.002a3.995 3.995 0 01-4.002-3.99V99.99zm-291-48C0 49.786 1.79 48 3.992 48h179.016A3.99 3.99 0 01187 51.99v16.02c0 2.204-1.79 3.99-3.992 3.99H3.992A3.99 3.99 0 010 68.01V51.99zm0 96c0-2.204 1.79-3.99 3.992-3.99h179.016a3.99 3.99 0 013.992 3.99v16.02c0 2.204-1.79 3.99-3.992 3.99H3.992A3.99 3.99 0 010 164.01v-16.02zm291 0a3.995 3.995 0 013.998-3.99h314.004a3.993 3.993 0 013.998 3.99v16.02a3.995 3.995 0 01-3.998 3.99H294.998a3.993 3.993 0 01-3.998-3.99v-16.02zm0-144A3.998 3.998 0 01295.002 0h287.996A3.995 3.995 0 01587 3.99v16.02a3.998 3.998 0 01-4.002 3.99H295.002A3.995 3.995 0 01291 20.01V3.99zm0 48a3.997 3.997 0 013.998-3.99h254.004A3.992 3.992 0 01553 51.99v16.02a3.997 3.997 0 01-3.998 3.99H294.998A3.992 3.992 0 01291 68.01V51.99z"
      :fill-rule "evenodd"}]]])
