(ns hjhamala.clogger.core
  (:require [clj-time.format :as format]
            [jsonista.core :as json]
            [clj-time.core :as time]
            [clojure.spec.alpha :as s]))

(defonce ^:dynamic *logging-level*
         :off)

(defonce ^:dynamic *json?*
         false)

(defonce ^:dynamic *filter-fns*
         {})

(defonce ^:dynamic *transform-fns*
         [])

(defonce ^:dynamic *select-from-map* [])

(s/def ::json? boolean?)

(s/def ::accepts-map #(try (% {}) true (catch Exception e nil)))

(s/def ::log-level #{:all :debug :spy :info :warn :error :fatal :essential :off})

(s/def ::filter-fn (s/every-kv ::log-level ::accepts-map))

(s/def ::filter-fns-in-config (s/and
                                (s/keys :req-un [::debug ::spy ::info ::warn ::error ::fatal ::essential])
                                ::filter-fn))


(s/def ::select-keys (s/coll-of keyword?))

(s/def ::transform-fn (s/coll-of ifn?))

(s/def ::init-input (s/keys ::opt [::filter-fn ::json? ::log-level ::select-keys ::transform-fn]))

(def logging-levels->int
  {:all       1
   :debug     2
   :spy       2
   :info      3
   :warn      4
   :error     5
   :fatal     6
   :essential 6
   :off       7})

(defn init!
  [configuration]
  (if (s/valid? ::init-input configuration)
    (do
      (and (::log-level configuration) (alter-var-root #'*logging-level* (constantly (::log-level configuration))))
      (and (some? (::json? configuration))
           (alter-var-root #'*json?* (constantly (::json? configuration))))
      (and (::filter-fn configuration) (alter-var-root #'*filter-fns* #(merge % (::filter-fn configuration))))
      (and (::transform-fn configuration) (alter-var-root #'*transform-fns* (constantly (::transform-fn configuration))))
      (and (::select-keys configuration) (alter-var-root #'*select-from-map* (constantly (::select-keys configuration))))
      true)
    (println "Invalid configuration!")))

(defn- log-event?
  [level]
  (>= (level logging-levels->int) (*logging-level* logging-levels->int)))

(defn- iso-8859-time
  []
  (format/unparse (format/formatters :date-hour-minute-second) (time/now)))

(defn- safe-print
  [message]
  (locking *out*
    (println message)))

(defn run-transformers
  [m transform-fns]
  (if (empty? transform-fns)
    m
    (loop [fns transform-fns message m]
      (if (empty? fns) 
        message
        (recur (rest fns) ((first fns) message))))))

(defn logging-event
  [level line ns selectable-map m]
  (try (when (log-event? level)
         (let [result (merge
                        {:ts (iso-8859-time) :level level :ns ns :line line}
                        (select-keys selectable-map *select-from-map*)
                        m)]
           (when (or (nil? (get *filter-fns* level)) ((get *filter-fns* level) result))
             (let [transformed-result (run-transformers result *transform-fns*)]
               (if *json?*
                 (safe-print (json/write-value-as-string transformed-result))
                 (safe-print transformed-result)))
             (when (= :spy level)
               (or (:message m) m)))))
       (catch Exception e (safe-print (str "Exception in logging: " level ":" line ":" ns ":" selectable-map ":" m)))))

(defmulti logg (fn [level line ns & xs] (cond
                                          (and (= 1 (count xs)) (-> (first xs) map?)) :map
                                          (= 1 (count xs)) :str
                                          (every? map? xs) :mapcoll
                                          :else :coll)))

(defmethod logg :map [level line ns & m] (logging-event level line ns {} (first m)))

(defmethod logg :mapcoll [level line ns request & ms] (logging-event level line ns request (apply merge ms)))

(defmethod logg :str [level line ns & xs] (logging-event level line ns {} {:message (str (first xs))}))

(defmethod logg :coll [level line ns & xs] (logging-event level line ns {} {:message (map str xs)}))

(defmacro debug
  [& x]
  `(logg :debug ~(:line (meta &form)) ~(str *ns*) ~@x))

(defmacro spy
  [& x]
  `(logg :spy ~(:line (meta &form)) ~(str *ns*) ~@x))

(defmacro info
  [& x]
  `(logg :info ~(:line (meta &form)) ~(str *ns*) ~@x))

(defmacro warn
  [& x]
  `(logg :warn ~(:line (meta &form)) ~(str *ns*) ~@x))

(defmacro error
  [& x]
  `(logg :error ~(:line (meta &form)) ~(str *ns*) ~@x))

(defmacro fatal
  [& x]
  `(logg :fatal ~(:line (meta &form)) ~(str *ns*) ~@x))

(defmacro essential
  [& x]
  `(logg :essential ~(:line (meta &form)) ~(str *ns*) ~@x))

