# Clogger

Clogger is an opinionated logging library with next goals:
* Logging is done to stdout
* Logs are printed either as Clojure maps or JSON objects
* Every log line contains namespace, line and timestamp
* If Clogger gets input which is not a map it is put on :message Clojure key.
* Every logging level can have filtering functions which can suppress logging.  

## Quickstart

```clj

(require '[hjhamala.clogger.core :as log])
;; Initialize logging
(log/init! {::log/log-level :all})
(log/info "bar")
;; => {:ts 2018-01-25T18:31:21, :level :info, :ns user, :line 1, :message bar}
```

## Configuration

### Logging level
Logging level can be set by using init! function with map key :hjhamala.clogger.core/log-level

Valid levels are :all :debug :spy :info :warn :error :fatal :essential :off

Debug + spy and fatal + essential share the same priority level. This means that if log level is :fatal also :essential
get logged.

```clj
(log/init! {::log/log-level :essential})
```

### JSON output

JSON output is set with key :hjhamala.clogger.core/json?

```clj
(log/init! {::log/log-level :essential
            ::log/json? true})
```

### Filter functions
Filter functions can be set to suppress logging based on logging map. This is very
useful for example when you want to suppress middleware based logging for certain Compojure routes.

```clj
(log/init! {::log/log-level :info
            ::log/filter-fn {:info (fn[m](when-not (= "/health-check" (:uri m)) true))}})
            
(log/info {:uri "/info"})
=> {"ts":"2018-01-25T18:43:03","level":"info","ns":"user","line":1,"uri":"/info"}  

(log/info {:uri "/health-check"})
=> nil
          
```
### Select-keys

Select-keys are convenience method for situation where you want to log certain keys from Ring request map.

If Clogger gets more than one map as parameter then logging adds keys from the first map only if select-keys configuration
has been set.

```clj

(log/init! {::log/log-level :info
            ::log/select-keys [:uri]})
            
(log/info request-map-with-uri {:message "bar"})
=> {"ts":"2018-01-25T18:51:02","level":"info","ns":"user","line":1,"uri":"/v1/api","message":"bar"}

(log/info {:uri "/health-check"})
=> nil
```

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
