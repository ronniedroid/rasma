(ns rasma.reader
  (:require [rasma.ast :as ast]
            [rasma.errors :as errors]
            [clojure.string :as string]))

(defn- make-reader
  [source]
  {:source source
   :position 0
   :line 1
   :column 1})

(defn- eof?
  [reader]
  (>= (:position reader)
      (count (:source reader))))

(defn- current-char
  [reader]
  (when-not (eof? reader)
    (.charAt ^String (:source reader)
             (:position reader))))

(defn- advance
  "Moves the finger one character forward.

  `:position` counts Java chars (so it can be used with `subs`), while
  `:column` counts characters as a person sees them: an emoji is two
  Java chars but one column. A tab is one column."
  [reader]
  (if-let [character (current-char reader)]
    (let [code-point (.codePointAt ^String (:source reader)
                                   (:position reader))
          width (Character/charCount code-point)
          newline? (= character \newline)]
      (-> reader
          (update :position + width)
          (update :line #(if newline? (inc %) %))
          (update :column #(if newline? 1 (inc %)))))
    reader))

(defn- whitespace?
  [character]
  (or (Character/isWhitespace character)
      (= character \,)))

(defn- skip-comment
  [reader]
  (loop [reader (advance reader)]
    (let [character (current-char reader)]
      (if (or (nil? character)
              (= character \newline))
        reader
        (recur (advance reader))))))

(defn- skip-whitespace
  [reader]
  (loop [reader reader]
    (let [character (current-char reader)]
      (cond
        (nil? character)
        reader

        (whitespace? character)
        (recur (advance reader))

        (= character \;)
        (recur (skip-comment reader))

        :else
        reader))))

(defn- delimiter?
  [character]
  (or (whitespace? character)
      (contains? #{\( \) \[ \] \{ \} \" \;} character)))

(defn- read-token
  [reader]
  (let [start (:position reader)]
    (loop [reader reader]
      (let [character (current-char reader)]
        (if (or (nil? character)
                (delimiter? character))
          [reader (subs (:source reader) start (:position reader))]
          (recur (advance reader)))))))

(defn- keyword-problem
  "Returns nil if `keyword-name` (the text after the colon) is valid,
  otherwise a map saying what is wrong. Characters are reported as
  one-character strings, because an emoji is two Java chars."
  [keyword-name]
  (let [characters (re-seq #"(?s)." keyword-name)
        first-character (first characters)]
    (cond
      (empty? characters)
      {:reason :empty}

      (= first-character ":")
      {:reason :double-colon}

      (not (re-matches #"[a-zA-Z]" first-character))
      {:reason :bad-start :character first-character}

      :else
      (when-let [bad-character (first (remove #(re-matches #"[a-zA-Z0-9_-]" %)
                                              characters))]
        {:reason :bad-character :character bad-character}))))

(def ^:private integer-pattern
  #"[+-]?\d+")

(def ^:private float-pattern
  #"[+-]?(?:(?:\d+\.\d*|\d*\.\d+)(?:[eE][+-]?\d+)?|\d+[eE][+-]?\d+)")

(defn- integer-token?
  [token]
  (boolean (re-matches integer-pattern token)))

(defn- float-token?
  [token]
  (boolean (re-matches float-pattern token)))

(defn- keyword-token?
  [token]
  (string/starts-with? token ":"))

(defn- classify-token
  [token]
  (cond
    (= token "true")
    [:boolean true]

    (= token "false")
    [:boolean false]

    (= token "nil")
    [:nil nil]

    (integer-token? token)
    [:integer (Long/parseLong token)]

    (float-token? token)
    [:float (Double/parseDouble token)]

    (keyword-token? token)
    [:keyword (keyword (subs token 1))]

    :else
    [:symbol (symbol token)]))

(defn- fits-in-long?
  [token]
  (try
    (Long/parseLong token)
    true
    (catch NumberFormatException _
      false)))

(defn- float-range-problem
  "Returns :too-large if the token overflowed to infinity, :too-small if
  a non-zero number rounded down to zero, otherwise nil."
  [token]
  (let [value (Double/parseDouble token)
        mantissa (first (string/split token #"[eE]"))]
    (cond
      (Double/isInfinite value)
      :too-large

      (and (zero? value)
           (re-find #"[1-9]" mantissa))
      :too-small)))

(defn- validate-token
  "Throws a reader error if `token` is malformed. Returns nil otherwise."
  [token span]
  (cond
    (keyword-token? token)
    (when-let [problem (keyword-problem (subs token 1))]
      (throw (errors/error :invalid-keyword
                           span
                           (assoc problem :token token))))

    (integer-token? token)
    (when-not (fits-in-long? token)
      (throw (errors/error :integer-out-of-range
                           span
                           {:token token})))

    (float-token? token)
    (when-let [direction (float-range-problem token)]
      (throw (errors/error :float-out-of-range
                           span
                           {:token token :direction direction})))))

(defn- span-between
  "The span from where reader `start` is to where reader `end` is."
  [start end]
  (ast/make-span
   (:position start) (:position end)
   (:line start) (:column start)
   (:line end) (:column end)))

(defn- read-atom
  [reader]
  (let [start reader
        [reader token] (read-token reader)
        span (span-between start reader)]
    (validate-token token span)
    (let [[kind value] (classify-token token)]
      [reader (ast/make-node kind value span)])))

;; Strings

(def ^:private string-escapes
  {\" "\""
   \\ "\\"
   \n "\n"
   \t "\t"})

(defn- line-end?
  [character]
  (contains? #{\newline \return} character))

(defn- unclosed-string-error
  [start reader]
  (errors/error :unclosed-string
                (span-between start reader)
                {:token (subs (:source start)
                              (:position start)
                              (:position reader))}))

(defn- read-string-literal
  "Reads a string such as \"Hello\". The reader must be on the opening quote."
  [reader]
  (let [start reader
        source (:source reader)
        after-quote (advance reader)]
    (loop [reader after-quote
           segment-start (:position after-quote)
           pieces []]
      (let [character (current-char reader)]
        (cond
          (or (nil? character)
              (line-end? character))
          (throw (unclosed-string-error start reader))

          (= character \")
          (let [end (advance reader)
                text (apply str
                            (conj pieces
                                  (subs source
                                        segment-start
                                        (:position reader))))]
            [end (ast/make-node :string text (span-between start end))])

          (= character \\)
          (let [after-backslash (advance reader)
                escaped (current-char after-backslash)]
            (cond
              (or (nil? escaped)
                  (line-end? escaped))
              (throw (unclosed-string-error start after-backslash))

              (contains? string-escapes escaped)
              (let [after-escape (advance after-backslash)]
                (recur after-escape
                       (:position after-escape)
                       (conj pieces
                             (subs source segment-start (:position reader))
                             (string-escapes escaped))))

              :else
              (let [after-escape (advance after-backslash)]
                (throw (errors/error
                        :invalid-escape
                        (span-between reader after-escape)
                        {:character (subs source
                                          (:position after-backslash)
                                          (:position after-escape))})))))

          :else
          (recur (advance reader) segment-start pieces))))))

;; Collections and forms

(declare read-form)

(def ^:private collection-delimiters
  {\( {:kind :list   :closer \)}
   \[ {:kind :vector :closer \]}
   \{ {:kind :map    :closer \}}})

(def ^:private closing-characters
  #{\) \] \}})

;; Sets, tagged literals, discard forms, character literals and metadata
;; are outside the language for now.
(def ^:private unsupported-start-characters
  #{\# \\ \^})

(defn- read-collection
  "Reads a list, vector or map. The reader must be on the opening bracket."
  [reader]
  (let [start reader
        opener-span (span-between start (advance start))
        {:keys [kind closer]} (collection-delimiters (current-char reader))]
    (loop [reader (advance reader)
           children []]
      (let [reader (skip-whitespace reader)
            character (current-char reader)]
        (cond
          (nil? character)
          (throw (errors/error :unclosed-collection
                               opener-span
                               {:collection kind
                                :opener (str (current-char start))
                                :expected (str closer)}))

          (= character closer)
          (let [end (advance reader)
                span (span-between start end)]
            (when (and (= kind :map)
                       (odd? (count children)))
              (throw (errors/error :odd-map
                                   span
                                   {:count (count children)})))
            [end (ast/make-node kind children span)])

          (contains? closing-characters character)
          (throw (errors/error :mismatched-closer
                               (span-between reader (advance reader))
                               {:collection kind
                                :opener-span opener-span
                                :expected (str closer)
                                :found (str character)}))

          :else
          (let [[reader child] (read-form reader)]
            (recur reader (conj children child))))))))

(defn- read-form
  "Reads one form. The reader must be on the first character of a form:
  not on whitespace, a comment or the end of the text."
  [reader]
  (let [character (current-char reader)]
    (cond
      (= character \")
      (read-string-literal reader)

      (contains? collection-delimiters character)
      (read-collection reader)

      (contains? closing-characters character)
      (throw (errors/error :unexpected-closer
                           (span-between reader (advance reader))
                           {:character (str character)}))

      (contains? unsupported-start-characters character)
      (throw (errors/error :unsupported-syntax
                           (span-between reader (advance reader))
                           {:character (str character)}))

      :else
      (read-atom reader))))

(defn read-all
  "Reads every top-level form in `source` and returns them as a vector of
  nodes. Throws an ex-info carrying error data if the source is malformed."
  [source]
  (loop [reader (skip-whitespace (make-reader source))
         forms []]
    (if (eof? reader)
      forms
      (let [[reader form] (read-form reader)]
        (recur (skip-whitespace reader)
               (conj forms form))))))
