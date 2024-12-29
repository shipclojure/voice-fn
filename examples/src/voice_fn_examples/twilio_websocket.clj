(ns voice-fn-examples.twilio-websocket
  (:require
   [clojure.core.async :as a]
   [clojure.data.xml :as xml]
   [muuntaja.core :as m]
   [reitit.core]
   [reitit.dev.pretty :as pretty]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as r]
   [ring.websocket :as ws]
   [taoensso.telemere :as t]
   [voice-fn.core]
   [voice-fn.pipeline :as vpipe]
   [voice-fn.secrets :refer [secret]]
   [voice-fn.transport.serializers :as vs]))

(defn emit-xml-str
  [xml-data]
  (-> xml-data
      (xml/sexp-as-element)
      (xml/emit-str)))

(defn header
  [req header-name]
  (get (:headers req) header-name))

(defn host
  [req]
  (header req "host"))

(defn xml-response
  [body]
  (-> (r/response body)
      (r/content-type "text/xml")))

(defn twilio-inbound-handler
  "Handler to direct the incoming call to stream audio to our websocket handler"
  [req]
  (prn "Got request for inbound")
  (let [h (host req)
        ws-url (str "wss://" h "/ws")]
    ;; https://www.twilio.com/docs/voice/twiml/connect
    (xml-response
      (emit-xml-str [:Response
                     [:Connect
                      [:Stream {:url ws-url}]]]))))

(defn create-twilio-ai-pipeline
  [in out]
  {:pipeline/config {:audio-in/sample-rate 8000
                     :audio-in/encoding :ulaw
                     :audio-in/channels 1
                     :audio-in/sample-size-bits 8
                     :audio-out/sample-rate 8000
                     :audio-out/bitrate 64000
                     :audio-out/sample-size-bits 8
                     :audio-out/channels 1
                     :pipeline/language :ro
                     :transport/in-ch in
                     :transport/serializer (vs/make-twilio-serializer "hello")
                     :transport/out-ch out}
   :pipeline/processors [{:processor/type :transport/async-input
                          :processor/accepted-frames #{:system/start :system/stop}
                          :processor/generates-frames #{:audio/raw-input}}
                         {:processor/type :transcription/deepgram
                          :processor/accepted-frames #{:system/start :system/stop :audio/raw-input}
                          :processor/generates-frames #{:text/input}
                          :processor/config {:transcription/api-key (secret [:deepgram :api-key])
                                             :transcription/interim-results? false
                                             :transcription/punctuate? false
                                             :transcription/model :nova-2}}
                         {:processor/type :log/text-input
                          :processor/accepted-frames #{:text/input}
                          :processor/generates-frames #{}
                          :processor/config {}}]})

;; Using ring websocket protocols to setup a websocket server
(defn twilio-ws-handler
  [req]
  (assert (ws/upgrade-request? req))
  (let [in (a/chan 1024)
        out (a/chan 1024)
        pipeline (vpipe/create-pipeline (create-twilio-ai-pipeline in out))
        start-pipeline (fn [socket]
                         ;; listen on the output channel we provided to send
                         ;; that audio back to twilio
                         (a/go-loop []
                           (when-let [output (a/<! out)]
                             (prn "Output" output)
                             (ws/send socket output)
                             (recur)))
                         (vpipe/start-pipeline! pipeline))]
    {::ws/listener
     {:on-open (fn on-open [socket]
                 (start-pipeline socket)
                 (prn "Opening socket" socket)

                 nil)
      :on-message (fn on-text [_ws payload]
                    (a/put! in payload))
      :on-close (fn on-close [_ws _status-code _reason]
                  (vpipe/stop-pipeline! pipeline))
      :on-error (fn on-error [ws error]
                  (prn error)
                  (t/log! :debug error))
      :on-ping (fn on-ping [ws payload]
                 (ws/send ws payload))}}))

(def routes
  [["/inbound-call" {:summary "Webhook where a call is made"
                     :post {:handler twilio-inbound-handler}}]
   ["/ws" {:summary "Websocket endpoint to receive a twilio call"
           :get {:handler twilio-ws-handler}}]])

(def app
  (ring/ring-handler
    (ring/router
      routes
      {:exception pretty/exception
       :data {:muuntaja m/instance
              :middleware [;; query-params & form-params
                           parameters/parameters-middleware
                           ;; content-negotiation
                           muuntaja/format-negotiate-middleware
                           ;; encoding response body
                           muuntaja/format-response-middleware
                           ;; exception handling
                           exception/exception-middleware
                           ;; decoding request body
                           muuntaja/format-request-middleware
                           ;; coercing response bodys
                           coercion/coerce-response-middleware
                           ;; coercing request parameters
                           coercion/coerce-request-middleware]}})
    (ring/create-default-handler)))

(defn start [& {:keys [port] :or {port 3000}}]
  (println (str "server running in port " port))
  (jetty/run-jetty #'app {:port port, :join? false}))

(comment

  (def server (start :port 3000))
  (.stop server)

  ,)