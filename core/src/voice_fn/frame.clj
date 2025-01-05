(ns voice-fn.frame
  "Defines the core frame concept and frame creation functions for the voice-fn pipeline.
   A frame represents a discrete unit of data or control flow in the pipeline.")

(defrecord BaseFrame [type data ts])

(defn frame? [frame]
  (instance? BaseFrame frame))

(defn create-frame
  [type data]
  (let [ts (System/currentTimeMillis)]
    (map->BaseFrame {:type type
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
  "Define a frame creator function and its predicate.
   Usage: (defframe audio-input :frame.audio/input-raw \"Doc string\")"
  [name docstring type]
  `(do
     (defn ~name
       ~docstring
       [data#]
       (create-frame ~type data#))

     (defn ~(symbol (str name "?"))
       [frame#]
       (and (frame? frame#) (= ~type (:frame/type frame#))))))

;;
;; System Frames
;; These frames control core pipeline functionality
;;

(defframe system-start
  "Frame sent when the pipeline begins"
  :frame.system/start)

(defframe system-stop
  "Frame sent when the pipeline stops"
  :frame.system/stop)

(defframe system-error
  "General error frame"
  :frame.system/error)

;;
;; Audio Frames
;; Frames for handling raw audio data
;;

(defframe audio-input-raw
  "Raw audio input frame from input transport"
  :frame.audio/input-raw)

(defframe audio-output-raw
  "Raw audio output frame for playback through output transport"
  :frame.audio/output-raw)

(defframe audio-tts-raw
  "Raw audio frame generated by TTS service"
  :frame.audio.tts/output-raw)

;;
;; Transcription Frames
;; Frames for speech-to-text processing
;;

(defframe transcription-complete
  "Final transcription result"
  :frame.transcription/complete)

(defframe transcription-interim
  "Interim transcription result"
  :frame.transcription/interim)

;;
;; Context Frames
;; Frames for managing conversation context
;;

(defframe context-messages
  "Frame containing LLM messages for context"
  :frame.context/messages)

;;
;; LLM Output Frames
;; Frames for language model outputs
;;

(defframe llm-text-chunk
  "Chunk of text from streaming LLM output"
  :frame.llm/text-chunk)

(defframe llm-text-sentence
  "Complete sentence from LLM output"
  :frame.llm/text-sentence)

(defframe llm-full-response-start
  "Indicates the start of an LLM response"
  :frame.llm/response-start)

(defframe llm-full-response-end
  "Indicates the end of an LLM response"
  :frame.llm/response-end)

;;
;; User Interaction Frames
;; Frames for handling user speech events
;;

(defframe user-speech-start
  "User started speaking"
  :frame.user/speech-start)

(defframe user-speech-stop
  "User stopped speaking"
  :frame.user/speech-stop)

;;
;; Control Frames
;; Frames for pipeline flow control
;;

(defframe control-bot-interrupt
  "Bot should be interrupted"
  :frame.control/bot-interrupt)

(defframe control-interrupt-start
  "Start pipeline interruption"
  :frame.control/interrupt-start)

(defframe control-interrupt-stop
  "Stop pipeline interruption"
  :frame.control/interrupt-stop)

;;
;; Input/Output Text Frames
;; Frames for text processing
;;

(defframe text-input
  "Text input frame for LLM processing"
  :frame.text/input)