(ns catacumba.handlers
  (:require [cuerdas.core :as str]
            [catacumba.core :as ct])
  (:import ratpack.http.Request
           ratpack.http.Response))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CORS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- allow-origin?
  [value {:keys [origin]}]
  (cond
    (nil? value) value
    (= origin "*") origin
    (set? origin) (origin value)
    (= origin value) origin))

(defn- handle-preflight
  [context request headers {:keys [allow-methods allow-headers max-age allow-credentials] :as opts}]
  (let [^Response response (:response context)
        ^String origin (get headers "origin")]
    (when-let [origin (allow-origin? origin opts)]
      (ct/set-headers! response {"Access-Control-Allow-Origin" origin
                                "Access-Control-Allow-Methods" (str/join "," allow-methods)})
      (when allow-credentials
        (ct/set-headers! response {"Access-Control-Allow-Credentials" true}))
      (when max-age
        (ct/set-headers! response {"Access-Control-Max-Age" max-age}))
      (when allow-headers
        (ct/set-headers! response {"Access-Control-Allow-Headers" (str/join "," allow-headers)})))
    (ct/send! context "")))

(defn- handle-response
  [context headers {:keys [allow-headers expose-headers origin] :as opts}]
  (let [^Response response (:response context)
        ^String origin (get headers "origin")]
    (when-let [origin (allow-origin? origin opts)]
      (ct/set-headers! response {"Access-Control-Allow-Origin" origin})
      (when allow-headers
        (ct/set-headers! response {"Access-Control-Allow-Headers" (str/join "," allow-headers)}))
      (when expose-headers
        (ct/set-headers! response {"Access-Control-Expose-Headers" (str/join "," expose-headers)})))
    (ct/delegate context)))

(defn- cors-preflight?
  [^Request request headers]
  (and (.. request getMethod isOptions)
       (contains? headers "origin")
       (contains? headers "access-control-request-method")))

(defn cors
  [{:keys [origin] :as opts}]
  (fn [context]
    (let [^Request request (:request context)
          headers (ct/get-headers request)]
      (if (cors-preflight? request headers)
        (handle-preflight context request headers opts)
        (handle-response context headers opts)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic request in Context
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn basic-request
  "A chain handler that populates the context
  with a basic request related properties."
  [context]
  (let [^Request request (:request context)]
    (->> {:path (str "/" (.. request getPath))
          :query-string (.. request getQuery)
          :method (keyword (.. request getMethod getName toLowerCase))
          :headers (ct/get-headers request)}
         (ct/delegate context))))
