(ns status-im.ui.screens.communities.community
  (:require [status-im.ui.components.topbar :as topbar]
            [quo.react-native :as rn]
            [status-im.ui.components.toolbar :as toolbar]
            [quo.core :as quo]
            [status-im.constants :as constants]
            [status-im.utils.handlers :refer [>evt <sub]]
            [status-im.i18n.i18n :as i18n]
            [status-im.utils.datetime :as datetime]
            [status-im.communities.core :as communities]
            [status-im.ui.components.list.views :as list]
            [status-im.ui.screens.home.views.inner-item :as inner-item]
            [status-im.ui.screens.chat.photos :as photos]
            [status-im.react-native.resources :as resources]
            [status-im.ui.components.chat-icon.screen :as chat-icon.screen]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.icons.icons :as icons]
            [status-im.utils.core :as utils]
            [status-im.ui.components.plus-button :as components.plus-button]
            [status-im.utils.config :as config]))

(def request-cooldown-ms (* 60 1000))

(defn can-request-access-again? [requested-at]
  (> (datetime/timestamp) (+ (* requested-at 1000) request-cooldown-ms)))

(defn toolbar-content [id display-name color images show-members-count? members]
  (let [thumbnail-image (get-in images [:thumbnail :uri])]
    [rn/view {:style {:flex           1
                      :align-items    :center
                      :flex-direction :row}}
     [rn/view {:padding-right 10}
      (cond
        (= id constants/status-community-id)
        [rn/image {:source (resources/get-image :status-logo)
                   :style  {:width  40
                            :height 40}}]
        (seq thumbnail-image)
        [photos/photo thumbnail-image {:size 40}]

        :else
        [chat-icon.screen/chat-icon-view-toolbar
         id
         true
         display-name
         (or color (rand-nth colors/chat-colors))])]
     [rn/view {:style {:flex 1 :justify-content :center}}
      [quo/text {:number-of-lines     1
                 :accessibility-label :community-name-text}
       display-name]
      [quo/text {:number-of-lines 1
                 :size            :small
                 :color           :secondary}
       (if show-members-count?
         (i18n/label-pluralize members :t/community-members {:count members})
         (i18n/label :t/open-membership))]]]))

(defn hide-sheet-and-dispatch [event]
  (>evt [:bottom-sheet/hide])
  (>evt event))

(defn community-plus-actions [{:keys [id permissions can-manage-users?]}]
  (let [can-invite?     (and can-manage-users? (not= (:access permissions) constants/community-no-membership-access))
        can-share?      (not= (:access permissions) constants/community-invitation-only-access)]
    [:<>
     [quo/list-item
      {:theme               :accent
       :title               (i18n/label :t/create-channel)
       :accessibility-label :community-create-channel
       :icon                :main-icons/channel
       :on-press            #(hide-sheet-and-dispatch [::communities/create-channel-pressed id])}]
     [quo/separator]
     (when can-invite?
       [quo/list-item
        {:theme               :accent
         :title               (i18n/label :t/invite-people)
         :icon                :main-icons/share
         :accessibility-label :community-invite-people
         :on-press            #(>evt [::communities/invite-people-pressed id])}])
     (when (and can-share? (not can-invite?))
       [quo/list-item
        {:theme               :accent
         :title               (i18n/label :t/invite-people)
         :icon                :main-icons/share
         :accessibility-label :community-share
         :on-press            #(>evt [::communities/share-community-pressed id])}])]))

(defn community-actions [{:keys [id name images color can-manage-users?]}]
  (let [thumbnail-image (get-in images [:thumbnail :uri])]
    [:<>
     [quo/list-item
      {:title    name
       :subtitle (i18n/label :t/view-profile)
       :on-press #(hide-sheet-and-dispatch [:navigate-to :community-management {:community-id id}])
       :chevron  true
       :icon     (cond
                   (= id constants/status-community-id)
                   [rn/image {:source (resources/get-image :status-logo)
                              :style  {:width  40
                                       :height 40}}]
                   (seq thumbnail-image)
                   [photos/photo thumbnail-image {:size 40}]

                   :else
                   [chat-icon.screen/chat-icon-view-chat-sheet
                    name
                    true
                    name
                    (or color (rand-nth colors/chat-colors))])}]
     (when (and config/communities-management-enabled? can-manage-users?)
       [quo/list-item
        {:theme               :accent
         :title               (i18n/label :t/export-key)
         :accessibility-label :community-export-key
         :icon                :main-icons/objects
         :on-press            #(hide-sheet-and-dispatch [::communities/export-pressed id])}])]))

(defn welcome-blank-page []
  [rn/view {:style {:padding 16 :flex 1 :flex-direction :row :align-items :center :justify-content :center}}
   [quo/text {:align :center
              :color :secondary}
    (i18n/label :t/welcome-community-blank-message)]])

(defn community-chat-item [home-item]
  [inner-item/home-list-item home-item])

(defn community-chat-list [chats]
  (if (empty? chats)
    [welcome-blank-page]
    [list/flat-list
     {:key-fn                       :chat-id
      :content-container-style      {:padding-vertical 8}
      :keyboard-should-persist-taps :always
      :data                         chats
      :render-fn                    community-chat-item
      :footer                       [rn/view {:height 68}]}]))

(defn community-channel-list [id]
  (let [chats (<sub [:chats/by-community-id id])
        chats (cond->> chats
                (= id constants/status-community-id)
                (map #(assoc % :color colors/blue)))]
    [community-chat-list chats]))

(defn channel-preview-item [{:keys [id color name]}]
  (let [color (or color colors/default-community-color)]
    [quo/list-item
     {:icon                      [chat-icon.screen/chat-icon-view-chat-list
                                  id true name color false false]
      :title                     [rn/view {:flex-direction :row
                                           :flex           1
                                           :padding-right  16
                                           :align-items    :center}
                                  [icons/icon :main-icons/tiny-group
                                   {:color           colors/black
                                    :width           15
                                    :height          15
                                    :container-style {:width        15
                                                      :height       15
                                                      :margin-right 2}}]
                                  [quo/text {:weight              :medium
                                             :accessibility-label :chat-name-text
                                             :ellipsize-mode      :tail
                                             :number-of-lines     1}
                                   (utils/truncate-str name 30)]]
      :title-accessibility-label :chat-name-text}]))

(defn community-channel-preview-list [_ chats-without-id]
  (let [chats (reduce-kv
               (fn [acc k v]
                 (conj acc (assoc v :id (name k))))
               []
               chats-without-id)]
    [list/flat-list
     {:key-fn                       :id
      :content-container-style      {:padding-vertical 8}
      :keyboard-should-persist-taps :always
      :data                         chats
      :render-fn                    channel-preview-item}]))

(defn community [route]
  (let [{:keys [community-id]} (get-in route [:route :params])
        {:keys [id chats name images members permissions color joined can-request-access?
                can-join? requested-to-join-at admin]
         :as   community}      (<sub [:communities/community community-id])]
    [rn/view {:style {:flex 1}}
     [topbar/topbar
      {:content
       [toolbar-content
        id
        name
        color
        images
        (not= (:access permissions) constants/community-no-membership-access)
        (count members)]
       :right-accessories
       (when (or admin joined)
         [{:icon                :main-icons/more
           :accessibility-label :community-menu-button
           :on-press #(>evt [:bottom-sheet/show-sheet
                             {:content (fn []
                                         [community-actions community])}])}])}]
     (if joined
       [community-channel-list id]
       [community-channel-preview-list id chats])
     (when admin
       [components.plus-button/plus-button
        {:on-press #(>evt [:bottom-sheet/show-sheet
                           {:content (fn []
                                       [community-plus-actions community])}])
         :accessibility-label :new-chat-button}])
     (when-not joined
       (cond
         can-join?
         [toolbar/toolbar
          {:show-border? true
           :center       [quo/button {:on-press #(>evt [::communities/join id])
                                      :type     :secondary}
                          (i18n/label :t/join)]}]
         can-request-access?
         (if (and (pos? requested-to-join-at)
                  (not (can-request-access-again? requested-to-join-at)))
           [toolbar/toolbar
            {:show-border? true
             :left       [quo/text {:color :secondary} (i18n/label :t/membership-request-pending)]}]
           [toolbar/toolbar
            {:show-border? true
             :center       [quo/button {:on-press #(>evt [::communities/request-to-join id])
                                        :type     :secondary}
                            (i18n/label :t/request-access)]}])
         :else
         [toolbar/toolbar
          {:show-border? true
           :center       [quo/button {:on-press #(>evt [::communities/join id])
                                      :type     :secondary}
                          (i18n/label :t/follow)]}]))]))
