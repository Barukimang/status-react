(ns status-im.anon-metrics.interceptors
  (:require [status-im.ethereum.json-rpc :as json-rpc]
            [taoensso.timbre :as log]
            [re-frame.core :as re-frame]
            [status-im.utils.fx :as fx]
            [re-frame.interceptor :refer [->interceptor get-coeffect]]))

(defn catch-events-fn [context]
    (let [[event-type view-id params] (get-in context [:coeffects :event])]
      (let [filtered-params (filter (fn [[k v]] (= :screen k)) params)]
      [
       (log/info :catch-event-fn event-type view-id params filtered-params)
       (comment
        (json-rpc/call {
                        :method     "appmetrics_validateAppMetrics"
                        :params     params
                        :on-success log/debug "appmetrics_validateAppMetrics successful"
                        :on-failure log/debug "appmetrics_validateAppMetrics failure"}))
       ])))

(def catch-events
  (->interceptor
    :id     :catch-events
    :before (fn catch-events-before [context]
              [
                (log/info "catch-events/interceptor fired")
                (catch-events-fn context)
               ]
              context)))
