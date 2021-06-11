(ns acme.web.view.icon)

(defn spinner []
  [:svg
   {:class "animate-spin h-4 w-4"
    :fill "none"
    :view-box "0 0 24 24"}
   [:circle.opacity-25
    {:cx 12
     :cy 12
     :r 10
     :stroke "currentColor"
     :stroke-width 4}]
   [:path
    {:fill "currentColor"
     :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}]])

(defn outline-refresh []
  [:svg
   {:stroke "currentColor" :view-box "0 0 24 24" :fill "none"}
   [:path
    {:d "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15",
     :stroke-width "2"
     :stroke-linejoin "round"
     :stroke-linecap "round"}]])

(defn outline-clipboard-copy []
  [:svg
   {:stroke "currentColor" :view-box "0 0 24 24" :fill "none"}
   [:path
    {:d "M8 7v8a2 2 0 002 2h6M8 7V5a2 2 0 012-2h4.586a1 1 0 01.707.293l4.414 4.414a1 1 0 01.293.707V15a2 2 0 01-2 2h-2M8 7H6a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2v-2"
     :stroke-width "2"
     :stroke-linejoin "round"
     :stroke-linecap "round"}]])

(defn outline-document-text []
  [:svg
   {:stroke "currentColor" :view-box "0 0 24 24" :fill "none"}
   [:path
    {:d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
     :stroke-width "2"
     :stroke-linejoin "round"
     :stroke-linecap "round"}]])

(defn outline-user []
  [:svg
   {:stroke "currentColor" :view-box "0 0 24 24" :fill "none"}
   [:path
    {:d "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
     :stroke-width "2"
     :stroke-linejoin "round"
     :stroke-linecap "round"}]])

(defn plus []
  [:svg {:stroke "currentColor" :view-box "0 0 24 24" :fill "none"}
   [:path
    {:d "M12 6v6m0 0v6m0-6h6m-6 0H6"
     :stroke-width "2"
     :stroke-linejoin "round"
     :stroke-linecap "round"}]])

(defn outline-calendar []
  [:svg.h-5.w-5
   {:stroke "currentColor" :view-box "0 0 24 24" :fill "none"}
   [:path
    {:d "M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
     :stroke-width "2"
     :stroke-linejoin "round"
     :stroke-linecap "round"}]])

(defn outline-currency-dollar []
  [:svg.h-5.w-5
   {:stroke "currentColor" :view-box "0 0 24 24" :fill "none"}
   [:path
    {:d "M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
     :stroke-width "2"
     :stroke-linejoin "round"
     :stroke-linecap "round"}]])

(defn outline-check-circle []
  [:svg.h-5.w-5
   {:stroke "currentColor" :view-box "0 0 24 24" :fill "none"}
   [:path
    {:d "M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
     :stroke-width "2"
     :stroke-linejoin "round"
     :stroke-linecap "round"}]])

(defn outline-exclamation-circle []
  [:svg.h-5.w-5
   {:stroke "currentColor" :view-box "0 0 24 24" :fill "none"}
   [:path
    {:d "M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
     :stroke-width "2"
     :stroke-linejoin "round"
     :stroke-linecap "round"}]])
