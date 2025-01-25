(ns voice-fn.frame
  "Defines the core frame concept and frame creation functions for the voice-fn pipeline.
   A frame represents a discrete unit of data or control flow in the pipeline."
  (:require
   [malli.clj-kondo :as mc]
   [malli.core :as m]
   [voice-fn.schema :as schema]))

(defrecord Frame [type data ts])

(defn frame? [frame]
  (instance? Frame frame))

(defn create-frame
  [type data]
  (let [ts (System/currentTimeMillis)]
    (map->Frame {:type type
                 :frame/type type
                 :data data
                 :frame/data data
                 :ts ts
                 :frame/ts ts})))

(defn system-frame?
  "Returns true if the frame is a system frame that should be processed immediately"
  [frame]
  (let [frame-type (:frame/type frame)]
    (or (= frame-type :frame.system/start)
        (= frame-type :frame.system/stop)
        (= frame-type :frame.control/bot-interrupt)
        (= frame-type :frame.user/speech-start)
        (= frame-type :frame.user/speech-stop)
        (= frame-type :frame.control/interrupt-start)
        (= frame-type :frame.control/interrupt-stop))))

(defmacro defframe
  "Define a frame creator function and its predicate with schema validation.
   Usage: (defframe audio-input
                    \"Doc string\"
                    {:type :frame.audio/input-raw
                     :schema [:map [:data AudioData]])}"
  [name docstring {:keys [type schema] :or {schema :any}}]
  (let [frame-schema [:map
                      [:frame/type [:= type]]
                      [:frame/data schema]
                      [:frame/ts :any]]
        frame-schema-name (symbol (str name "-schema"))
        pred-name (symbol (str name "?"))]
    `(do
       ;; Define the frame schema
       (def ~frame-schema-name ~frame-schema)

       ;; Define the frame creator function with schema validation
       (def ~name
         ~docstring
         (fn
           [data#]
           (let [frame# (create-frame ~type data#)]
             (when-let [err# (m/explain ~frame-schema frame#)]
               (throw (ex-info "Invalid frame data"
                               {:error err#
                                :frame frame#})))
             frame#)))

       ;; Define the predicate function
       (def ~pred-name
         (fn [frame#]
           (and (frame? frame#)
                (nil? (m/explain ~frame-schema-name frame#)))))

       ;; Add clj-kondo type hints
       (m/=> ~name [:=> [:cat ~schema] ~frame-schema-name])
       (m/=> ~pred-name [:=> [:cat any?] :boolean]))))

;;
;; System Frames
;; These frames control core pipeline functionality
;;

(defframe system-start
  "Frame sent when the pipeline begins"
  {:type :frame.system/start
   :schema :boolean})

(def FramePredicate
  [:fn {:error/message "Must be a function that takes a frame and returns boolean"
        :gen/fmap (fn [_] system-start?)} ; Example generator
   (fn [f]
     (and (fn? f)
          (try
            (boolean? (f (create-frame :test/frame {})))
            (catch Exception _
              false))))])

(def FrameCreator
  [:fn
   {:error/message "Must be a function that takes type and data and returns a valid frame"
    :gen/fmap (fn [_] system-start)} ; Example generator
   (fn [f]
     (and (fn? f)
          (try
            (let [result (f {:test "data"})]
              (frame? result))
            (catch Exception _
              false))))])

(defframe system-stop
  "Frame sent when the pipeline stops"
  {:type :frame.system/stop
   :schema :boolean})

(defframe system-error
  "General error frame"
  {:type :frame.system/error})

(defframe system-config-change
  "Frame with configuration changes for the running pipeline"
  {:type :frame.system/config-change
   :schema schema/PartialConfigSchema})

;;
;; Audio Frames
;; Frames for handling raw audio data
;;

(defframe audio-input-raw
  "Raw audio input frame from input transport"
  {:type :frame.audio/input-raw
   :schema schema/ByteArray})

(defframe audio-output-raw
  "Raw audio output frame for playback through output transport"
  {:type :frame.audio/output-raw})

(defframe audio-tts-raw
  "Raw audio frame generated by TTS service"
  {:type :frame.audio.tts/output-raw})

;;
;; Transcription Frames
;; Frames for speech-to-text processing
;;

(defframe transcription
  "Transcription result. NOTE: This doesn't mean it is a full transcription, but
  a transcription chunk that the transcriptor has full confidence in."
  {:type :frame.transcription/result})

(defframe transcription-interim
  "Interim transcription result"
  {:type :frame.transcription/interim})

;;
;; Context Frames
;; Frames for managing conversation context
;;

(defframe llm-context
  "Frame containing LLM context"
  {:type :frame.llm/context
   :schema schema/LLMContext})

;;
;; LLM Output Frames
;; Frames for language model outputs
;;

(defframe llm-text-chunk
  "Chunk of text from streaming LLM output"
  {:type :frame.llm/text-chunk})

(defframe llm-text-sentence
  "Complete sentence from LLM output"
  {:type :frame.llm/text-sentence})

(defframe llm-full-response-start
  "Indicates the start of an LLM response"
  {:type :frame.llm/response-start})

(defframe llm-full-response-end
  "Indicates the end of an LLM response"
  {:type :frame.llm/response-end})

(defframe llm-tools-call-request
  "Frame containing a tool call request. Used when the LLM assistant wants to
  invoke a function to answer a user question. A tool-call-request should contain:
  - :function-name - name of the function to call (this needs to be registered at pipeline creation)

  - :arguments - map of arguments (as described in the function registration)

  - :tool-call-id - the call-id. When the result is pushed back to the LLM
    assistant, it will recognize the results based on this ID
"
  {:type :frame.llm/tool-request})

;;
;; User Interaction Frames
;; Frames for handling user speech events
;;

(defframe user-speech-start
  "User started speaking"
  {:type :frame.user/speech-start
   :schema :boolean})

(defframe user-speech-stop
  "User stopped speaking"
  {:type :frame.user/speech-stop
   :scheam :boolean})

;;
;; Control Frames
;; Frames for pipeline flow control
;;

(defframe control-bot-interrupt
  "Bot should be interrupted"
  {:type :frame.control/bot-interrupt
   :schema :boolean})

(defframe control-interrupt-start
  "Start pipeline interruption"
  {:type :frame.control/interrupt-start
   :schema :boolean})

(defframe control-interrupt-stop
  "Stop pipeline interruption"
  {:type :frame.control/interrupt-stop
   :schema :boolean})

;;
;; Input/Output Text Frames
;; Frames for text processing
;;

(defframe speak-frame
  "Text frame meant for TTS processors to generate speech from the input"
  {:type :frame.tts/speak
   :schema :string})

(defframe text-input
  "Text input frame for LLM processing"
  {:type :frame.text/input})

(mc/emit!)
