(ns voice-fn.processors.openai
  (:require
   [clojure.core.async :as a]
   [voice-fn.frames :as frames]
   [voice-fn.pipeline :as pipeline]
   [wkok.openai-clojure.api :as api]))

(def token-content (comp :content :delta first :choices))

(defmethod pipeline/process-frame :llm/openai
  [_type pipeline processor frame]
  (let [{:llm/keys [model messages] :openai/keys [api-key]} (:processor/config processor)
        main-ch (:pipeline/main-ch @pipeline)]
    (case (:frame/type frame)
      :text/input (a/pipeline
                    1
                    main-ch
                    (comp (map token-content) (filter some?) (map frames/llm-output-text-chunk-frame))
                    (api/create-chat-completion {:model model
                                                 :messages (conj messages {:role "user" :content (:frame/data frame)})
                                                 :stream true}
                                                {:api-key api-key
                                                 :version :http-2 :as :stream})))))
