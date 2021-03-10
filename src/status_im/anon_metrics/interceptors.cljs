(ns status-im.anon-metrics.interceptors
  (:require [re-frame.core :as re-frame]
            [status-im.utils.fx :as fx]
            [re-frame.interceptor :refer [->interceptor get-coeffect]]))

(def catch-events
  (->interceptor
    :id     :catch-events
    :before (fn catch-events-before
              [context]
              ((prn "interceptor prn : ")(prn context))
              context)))

(fx/defn sample-event-fn
         {:events [:sample-event]
          :interceptors [catch-events]}
         [{:keys [db event]}]
         {:db (assoc db :hello :world)})

(comment
  do(
     (re-frame/dispatch [:sample-event {:hello :world}])))