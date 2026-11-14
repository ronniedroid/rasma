(ns rasma.errors
  "Errors are data. Every stage of the compiler reports a problem by throwing
  an exception built with `error`. The exception carries a map with:

    :kind  what went wrong, as a keyword
    :span  where it happened (a rasma.ast Span), or nil
    ...    extra keys that depend on the kind

  Nothing here knows how an error should be worded. Turning the data into
  text for people is the job of rasma.render.

  Reader errors and the extra keys they carry:

    :invalid-keyword       :reason (:empty :double-colon :bad-start
                           :bad-character), :token, :character
    :integer-out-of-range  :token
    :float-out-of-range    :token, :direction (:too-large :too-small)
    :unclosed-string       :token
    :invalid-escape        :character
    :unclosed-collection   :collection, :opener, :expected
    :unexpected-closer     :character
    :mismatched-closer     :collection, :opener-span, :expected, :found
    :odd-map               :count
    :unsupported-syntax    :character")

(defn error
  "Builds an exception that carries the error as data, not just a message."
  [kind span data]
  (ex-info (str "Rasma error: " (name kind))
           (merge {:kind kind :span span} data)))

(defn error-data
  "Returns the error data of `throwable` if it was built by `error`,
  otherwise nil."
  [throwable]
  (let [data (ex-data throwable)]
    (when (and (contains? data :kind)
               (contains? data :span))
      data)))
