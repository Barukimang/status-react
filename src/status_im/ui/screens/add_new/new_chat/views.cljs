(ns status-im.ui.screens.add-new.new-chat.views
  (:require [re-frame.core :as re-frame]
            [status-im.i18n.i18n :as i18n]
            [status-im.multiaccounts.core :as multiaccounts]
            [status-im.ui.components.chat-icon.screen :as chat-icon]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.icons.icons :as icons]
            [quo.core :as quo]
            [status-im.utils.gfycat.core :as gfycat]
            [status-im.qr-scanner.core :as qr-scanner]
            [status-im.ui.components.list.views :as list]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.topbar :as topbar]
            [status-im.utils.debounce :as debounce]
            [status-im.utils.utils :as utils]
            [reagent.core :as reagent]
            [quo.react-native :as rn]
            [clojure.string :as string]
            [status-im.ui.components.invite.views :as invite]
            [status-im.ethereum.ens :as ens])
  (:require-macros [status-im.utils.views :as views]))

(defn- render-row [row]
  (let [first-name (first (multiaccounts/contact-two-names row false))]
    [quo/list-item
     {:title    first-name
      :icon     [chat-icon/contact-icon-contacts-tab
                 (multiaccounts/displayed-photo row)]
      :on-press #(re-frame/dispatch [:chat.ui/start-chat
                                     (:public-key row)])}]))

(defn- icon-wrapper [color icon]
  [react/view
   {:style {:width            32
            :height           32
            :border-radius    25
            :align-items      :center
            :justify-content  :center
            :background-color color}}
   icon])

(defn- input-icon
  [state new-contact? entered-nickname]
  (let [icon (if new-contact? :main-icons/add :main-icons/arrow-right)]
    (case state
      :searching
      [icon-wrapper colors/gray
       [react/activity-indicator {:color colors/white-persist}]]

      :valid
      [react/touchable-highlight
       {:on-press #(debounce/dispatch-and-chill [:contact.ui/contact-code-submitted new-contact? entered-nickname] 3000)}
       [icon-wrapper colors/blue
        [icons/icon icon {:color colors/white-persist}]]]

      [icon-wrapper colors/gray
       [icons/icon icon {:color colors/white-persist}]])))

(defn get-validation-label [value]
  (println "printing value...")
  (println value)
  (case value
    :invalid
    (i18n/label :t/user-not-found)
    :yourself
    (i18n/label :t/can-not-add-yourself)))

(defn filter-contacts [filter-text contacts]
  (let [lower-filter-text (string/lower-case (str filter-text))
        filter-fn         (fn [{:keys [name alias nickname]}]
                            (or
                             (string/includes? (string/lower-case (str name)) lower-filter-text)
                             (string/includes? (string/lower-case (str alias)) lower-filter-text)
                             (when nickname
                               (string/includes? (string/lower-case (str nickname)) lower-filter-text))))]
    (if filter-text
      (filter filter-fn contacts)
      contacts)))

(defn is-valid-username? [username]
  (let [is-chat-key? (and (string? username)
                          (string/starts-with? username "0x")
                          (= (count username) 132))
        is-ens? (ens/valid-eth-name-prefix? username)]
    (or is-chat-key? is-ens?)))

(views/defview new-chat []
  (views/letsubs [contacts      [:contacts/active]
                  {:keys [state ens-name public-key error]} [:contacts/new-identity]
                  search-value (reagent/atom "")]
                 [react/view {:style {:flex 1}}
                  [topbar/topbar
                   {:title  (i18n/label :t/new-chat)
                    :modal? true
                    :right-accessories
                    [{:icon                :qr
                      :accessibility-label :scan-contact-code-button
                      :on-press            #(re-frame/dispatch [::qr-scanner/scan-code
                                                                {:title   (i18n/label :t/new-chat)
                                                                 :handler :contact/qr-code-scanned}])}]}]
                  [react/view {:flex-direction :row
                               :padding        16}
                   [react/view {:flex          1}
                    [quo/text-input
                     {:on-change-text
                      #(do
                         (reset! search-value %)
                         (println %)
                         (println (is-valid-username? %))
                         (re-frame/dispatch [:set-in [:contacts/new-identity :state] :searching])
                         (debounce/debounce-and-dispatch [:new-chat/set-new-identity %] 600))
                      :on-submit-editing
                      #(when (= state :valid)
                         (debounce/dispatch-and-chill [:contact.ui/contact-code-submitted false nil] 3000))
                      :placeholder         (i18n/label :t/enter-contact-code)
                      :show-cancel         false
                      :accessibility-label :enter-contact-code-input
                      :auto-capitalize     :none
                      :return-key-type     :go}]]]
                  [react/view (if (and
                                   (= (count contacts) 0)
                                   (= @search-value ""))
                                {:flex 1}
                                {:justify-content :flex-end})
                   (if (and
                        (= (count contacts) 0)
                        (= @search-value ""))
                     [react/view {:flex 1
                                  :align-items :center
                                  :padding-horizontal 58
                                  :padding-top 160}
                      [quo/text {:size  :base
                                 :align :center
                                 :color :secondary}
                       "You don’t have any contacts yet.\nInvite your friends to start chatting."]
                      [invite/button]]
                     [list/flat-list {:data                      (filter-contacts @search-value contacts)
                                      :key-fn                    :address
                                      :render-fn                 render-row
                                      :enableEmptySections       true
                                      :keyboardShouldPersistTaps :always}])]
                  (when-not (= @search-value "")
                    [react/view
                     [quo/text {:style {:margin-horizontal 16
                                        :margin-vertical 14}
                                :size  :base
                                :align :left
                                :color :secondary}
                      "Non contacts"]
                     (when (and (= state :searching)
                                (is-valid-username? @search-value))
                       [rn/activity-indicator])
                     [quo/text {:style {:margin-horizontal 16}
                                :size  :base
                                :align :center
                                :color :secondary}
                      (if (is-valid-username? @search-value)
                        (cond (= state :error)
                              (get-validation-label error)

                              (= state :valid)
                              (str (if ens-name
                                     ens-name
                                     (gfycat/generate-gfy public-key))
                                   " • ")

                              :else "")
                        "Invalid username or chat key")
                      (when (= state :valid)
                        [quo/list-item
                         {:key-fn   :address
                          :title    ens-name
                          :subtitle (str (str (string/trim (subs (gfycat/generate-gfy public-key) 0 30)) "...")
                                         " • "
                                         (utils/get-shortened-address public-key))
                          :icon     [chat-icon/contact-icon-contacts-tab
                                     (multiaccounts/displayed-photo public-key)]
                          :icon-container-style {:padding-horizontal 0}
                          :container-style {:padding-horizontal 0}
                          :chevron  false
                          :on-press #(re-frame/dispatch [:chat.ui/start-chat
                                                         (:public-key public-key)])}])]])]))

(defn- nickname-input [entered-nickname]
  [quo/text-input
   {:on-change-text      #(reset! entered-nickname %)
    :auto-capitalize     :none
    :max-length          32
    :auto-focus          false
    :accessibility-label :nickname-input
    :placeholder         (i18n/label :t/add-nickname)
    :return-key-type     :done
    :auto-correct        false}])

(views/defview new-contact []
  (views/letsubs [{:keys [state ens-name public-key error]} [:contacts/new-identity]
                  entered-nickname (reagent/atom "")]
    [react/view {:style {:flex 1}}
     [topbar/topbar
      {:title  (i18n/label :t/new-contact)
       :modal? true
       :right-accessories
       [{:icon                :qr
         :accessibility-label :scan-contact-code-button
         :on-press            #(re-frame/dispatch [::qr-scanner/scan-code
                                                   {:title        (i18n/label :t/new-contact)
                                                    :handler      :contact/qr-code-scanned
                                                    :new-contact? true}])}]}]
     [react/view {:flex-direction :row
                  :padding        16}
      [react/view {:flex          1
                   :padding-right 16}
       [quo/text-input
        {:on-change-text
         #(do
            (re-frame/dispatch [:set-in [:contacts/new-identity :state] :searching])
            (debounce/debounce-and-dispatch [:new-chat/set-new-identity %] 600))
         :on-submit-editing
         #(when (= state :valid)
            (debounce/dispatch-and-chill [:contact.ui/contact-code-submitted true @entered-nickname] 3000))
         :placeholder         (i18n/label :t/enter-contact-code)
         :show-cancel         false
         :accessibility-label :enter-contact-code-input
         :auto-capitalize     :none
         :return-key-type     :go}]]
      [react/view {:justify-content :center
                   :align-items     :center}
       [input-icon state true @entered-nickname]]]
     [react/view {:min-height 30 :justify-content :flex-end :margin-bottom 16}
      [quo/text {:style {:margin-horizontal 16}
                 :size  :small
                 :align :center
                 :color :secondary}
       (cond (= state :error)
             (get-validation-label error)
             (= state :valid)
             (str (when ens-name (str ens-name " • "))
                  (utils/get-shortened-address public-key))
             :else "")]]
     [react/text {:style {:margin-horizontal 16 :color colors/gray}}
      (i18n/label :t/nickname-description)]
     
     [react/view {:padding 16}

      [nickname-input entered-nickname]
      [react/text {:style {:align-self :flex-end :margin-top 16
                           :color      colors/gray}}
       (str (count @entered-nickname) " / 32")]]]))