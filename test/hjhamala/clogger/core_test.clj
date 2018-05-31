(ns hjhamala.clogger.core-test
  (:require [clojure.test :refer :all]
            [hjhamala.clogger.core :as logger]
            [clojure.spec.alpha :as s]
            [clojure.string :as cstr]
            [jsonista.core :as json]))

(defn reset-init
  [f]
  (logger/init! {::logger/filter-fn  {:debug     (constantly true)
                                      :spy       (constantly true)
                                      :info      (constantly true)
                                      :warn      (constantly true)
                                      :error     (constantly true)
                                      :fatal     (constantly true)
                                      :essential (constantly true)}
                 ::logger/json? false
                 ::logger/select-keys []
                 ::log-level :all})
  (f))

(use-fixtures :each reset-init)

(deftest configuration
  (testing "filter-fns is a collection of maps where values are one arity functions"
    (let [configuration {:debug     (constantly true)
                         :spy       (fn [a] true)
                         :info      (constantly true)
                         :warn      (constantly true)
                         :error     (constantly true)
                         :fatal     (constantly true)
                         :essential (constantly true)}]
      (is (s/valid? ::logger/filter-fns-in-config configuration))))
  (testing "filter-fns doesnt accept function which takes more than one "
    (let [configuration {:debug     (fn [a b] true)
                         :spy       (fn [a] true)
                         :info      (constantly true)
                         :warn      (constantly true)
                         :error     (constantly true)
                         :fatal     (constantly true)
                         :essential (constantly true)}]
      (is (not (s/valid? ::logger/filter-fns-in-config configuration))))))

(deftest init!
  (testing "Default configuration should be valid"
    (let [configuration {::logger/filter-fns-in-config {
                                              :debug     (constantly true)
                                              :spy       (constantly true)
                                              :info      (constantly true)
                                              :warn      (constantly true)
                                              :error     (constantly true)
                                              :fatal     (constantly true)
                                              :essential (constantly true)}
                         ::logger/json? true
                         ::log-level :all}]
      (is (logger/init! configuration)))))

(deftest log-event?
  (testing "When all is enabled debug is loggable"
    (with-redefs [logger/*logging-level* :all]
      (let [private-logger? #'logger/log-event?]
        (is (private-logger? :info)))))
  (testing "When nothing is enabled even fatal is not loggable"
    (with-redefs [logger/*logging-level* :off]
      (let [private-logger? #'logger/log-event?]
        (is (not (private-logger? :fatal))))))
  (testing "When fatal is enabled fatal is loggable"
    (with-redefs [logger/*logging-level* :fatal]
      (let [private-logger? #'logger/log-event?]
        (is (private-logger? :fatal))))))

(deftest logging-event
  (testing "Single string is returned in map with key message"
    (logger/init! {::logger/log-level :all
                   ::logger/json? true})
    (let [result (with-out-str (logger/info "bar"))]
      (is (= "bar" (get (json/read-value result) "message")))))
  (testing "Namespace and line is in map"
    (logger/init! {::logger/log-level :all
                   ::logger/json? true})
    (let [result (with-out-str (logger/info "bar"))]
      (is (= "hjhamala.clogger.core-test" (get (json/read-value result) "ns")))
      (is (< 50 (get (json/read-value result) "line")))))
  (testing "When setting select-keys these are retained in map"
    (logger/init! {::logger/log-level :all
                   ::logger/json? true
                   ::logger/select-keys [:a]})
    (let [result (with-out-str (logger/info {:a 1 :b 2} {:c 1}))
          parsed (json/read-value result)]
      (is (= 1 (get parsed "a")))
      (is (= 1 (get parsed "c")))
      (is (nil? (get parsed "b")))))
  (testing "filter-fns can be used to prevent logging"
    (logger/init! {::logger/log-level :all
                   ::logger/json?     true
                   ::logger/filter-fn {:info #(= 1 (get % :a))}})
    (let [result (with-out-str (logger/info {:a 1}))
          parsed (json/read-value result)]
      (is (= 1 (get parsed "a"))))
    (let [result (with-out-str (logger/info {:a 2}))]
      (is (= "" result)))))

(deftest run-transformers-test
  (testing "Empty transformers collection should result message itself"
    (is (= {:a 1} (logger/run-transformers {:a 1} [])))
    (is (= "start bar end" (logger/run-transformers "bar" [#(str "start " %) #(str % " end")]))))
  
  
  (testing "Transformers are applied to non json message"
    (logger/init! {::logger/log-level :all
                   ::logger/json?     false
                   ::logger/transform-fn [#(assoc % :b 2) #(assoc % :c 3)]})
    (let [result (with-out-str (logger/info {:a 1}))]
      (is (cstr/includes? result ":a 1, :b 2, :c 3" )))))

