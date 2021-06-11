(ns acme.web.view.page.about)

(defn root []
  [:div.text-lg.p-8
   [:h1.leading-7.font-extrabold.text-4xl.mb-8
    "Panda Streamer"]
   [:img.sm:float-right.sm:ml-4.mb-2
    {:class "sm:w-3/5"
     :src "/images/panda-about.png"}]
   [:p
    "This is a fictional Decentralized Finance (DeFi) application built out of
    love and curiosity for blockchain technologies, in particular Ethereum
    DApps."
    [:span.ml-1 "It allows you to"] [:em.mx-1 "stream"]
    "any ERC-20 token to someone else over a specified period of time."
    [:span.ml-1 "It achieves this in a safe way using the"]
    [:a.co-base.ml-1
     {:href "https://faq.sablier.finance/"
      :target "_blank"}
     "Sablier Protocol"] ", which in turn is inspired by"
    [:a.co-base.ml-1
     {:href "https://eips.ethereum.org/EIPS/eip-1620"
      :target "_blank"}
     "EIP-1620 (ERC-1620 Money Streaming)"] "."]
   [:p.mt-4
    "The app is meant to be used"
    [:strong.ml-1 "exclusively for testing purposes"]
    ", i.e. do not use it to stream tokens in the main Ethereum network. Panda
    Streamer has been tested in local environments as well as in the Rinkeby
    network."
    [:span.ml-1 "Currently the application is only integrated with the"]
    [:a.co-base.ml-1
     {:href "https://metamask.io/"
      :target "_blank"}
     "MetaMask crypto wallet."]]

   [:h2.leading-7.font-extrabold.text-2xg.mt-5
    "Who developed this application?"]
   [:p
    [:a.co-base
     {:href "https://github.com/ilmotta"
      :target "_blank"}
     "Icaro Motta"]]

   [:h2.leading-7.font-extrabold.text-2xg.mt-5
    "Is this app truly decentralized?"]
   [:p
    "Panda Streamer does not persist data outside of your local browsing
    environment. It doesn't have any sort of analytics and it doesn't mess with
    your browser cookies. All the data is remotely stored in the blockchain."]])
