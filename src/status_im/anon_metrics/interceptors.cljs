(ns status-im.anon-metrics.interceptors
  (:require [status-im.ethereum.json-rpc :as json-rpc]
            [taoensso.timbre :as log]
            [re-frame.interceptor :refer [->interceptor get-coeffect]]
            [status-im.utils.platform :as platform]
            [status-im.utils.build :as build]
            [status-im.anon-metrics.transformers :as txf]))

(defn catch-events-fn [context]
  (log/info :catch-event-fn (get-in context [:coeffects :event]))
  (when-let [transformed-payload (txf/transform context)]
       (json-rpc/call {:method "appmetrics_validateAppMetrics"
                       :params [[{:event (get-in context [:coeffects :event :event-type])
                                  :val transformed-payload
                                  :app_version build/version
                                  :os platform/os}]]
                       :on-failure log/error})))

(defn catch-events-before [context]
  (log/info "catch-events/interceptor fired")
  (catch-events-fn context)
  context)

(def catch-events
  (->interceptor
    :id     :catch-events
    :before catch-events))
