(ns rasma.render
  "Turns error data (see rasma.errors) and the source text into a message
  for people to read."
  (:require [clojure.string :as string]))

(def ^:private width 80)

(defn- code-points
  "The characters of `text` as one-character strings, so that an emoji
  counts once. This matches how the reader counts columns."
  [text]
  (re-seq #"(?s)." text))

(defn- source-lines
  [source]
  (mapv #(string/replace % #"\r$" "")
        (string/split source #"\n" -1)))

(defn- plural
  [number noun]
  (str number " " noun (when (not= number 1) "s")))

(defn- header
  [title file]
  (let [left (str "-- " title " ")
        right (when file (str " " file))
        dashes (apply str (repeat (max 3 (- width
                                            (count left)
                                            (count right)))
                                  "-"))]
    (str left dashes right)))

(defn- excerpt
  "Shows the source line a span starts on, with carets under the span.
  A span that continues on later lines is underlined to the end of its
  first line. Tabs in the source line are copied into the padding, so the
  carets stay under the right characters."
  [lines span]
  (let [line-number (:start-line span)
        line (get lines (dec line-number) "")
        characters (code-points line)
        start-column (:start-column span)
        end-column (if (= (:end-line span) line-number)
                     (:end-column span)
                     (inc (count characters)))
        before (take (dec start-column) characters)
        padding (str (apply str (map #(if (= % "\t") "\t" " ") before))
                     (apply str (repeat (- (dec start-column) (count before))
                                        " ")))
        carets (apply str (repeat (max 1 (- end-column start-column)) "^"))
        gutter (str line-number "| ")
        blank-gutter (apply str (repeat (count gutter) " "))]
    (str gutter line "\n" blank-gutter padding carets)))

(def ^:private openers
  {:list "(" :vector "[" :map "{"})

;; Each method returns {:title "..." :parts [...]}. A part is either a
;; paragraph (a string) or [:excerpt span], which shows the source.
(defmulti ^:private describe :kind)

(defmethod describe :invalid-keyword
  [{:keys [span reason character]}]
  {:title "INVALID KEYWORD"
   :parts
   (case reason
     :empty
     ["I found a colon with no name after it:"
      [:excerpt span]
      "A keyword is a colon followed by a name, like `:title`."]

     :double-colon
     ["I found a keyword that starts with two colons:"
      [:excerpt span]
      "A keyword has a single colon, like `:title`."]

     :bad-start
     [(str "A keyword name must start with a letter, but this one starts with `"
           character "`:")
      [:excerpt span]
      "Keyword names start with a letter and continue with letters, digits, `-` and `_`."]

     :bad-character
     (cond-> [(str "I found a character that is not allowed in a keyword: `"
                   character "`")
              [:excerpt span]
              "Keyword names can only contain letters, digits, `-` and `_`."]
       (= character ".")
       (conj "Dots are for type names like `Gtk.Label`, not for keyword names."))

     ["I found a keyword I do not understand:"
      [:excerpt span]])})

(defmethod describe :integer-out-of-range
  [{:keys [span]}]
  {:title "INTEGER OUT OF RANGE"
   :parts ["This number is too large for me to represent:"
           [:excerpt span]
           "Integers must fit in 64 bits, so they go from -9223372036854775808 to 9223372036854775807."]})

(defmethod describe :float-out-of-range
  [{:keys [span direction]}]
  {:title "FLOAT OUT OF RANGE"
   :parts (if (= direction :too-small)
            ["This number is so close to zero that it would silently become 0.0:"
             [:excerpt span]
             "If you meant zero, write `0.0`."]
            ["This number is too large for me to represent as a float:"
             [:excerpt span]
             "Floats go up to about 1.8e308."])})

(defmethod describe :unclosed-string
  [{:keys [span]}]
  {:title "UNCLOSED STRING"
   :parts ["I found the start of a string, but I never found the end:"
           [:excerpt span]
           "A string must end with a `\"` on the same line. To put a line break inside a string, write `\\n`."]})

(defmethod describe :invalid-escape
  [{:keys [span character]}]
  {:title "INVALID ESCAPE"
   :parts [(str "I do not know the escape `\\" character "`:")
           [:excerpt span]
           "These are the escapes I understand: `\\\"`, `\\\\`, `\\n` and `\\t`. To write a backslash, use `\\\\`."]})

(defmethod describe :unclosed-collection
  [{:keys [span collection opener expected]}]
  (let [word (name collection)]
    {:title (str "UNCLOSED " (string/upper-case word))
     :parts [(str "I found the start of a " word
                  ", but I never found the closing `" expected "`:")
             [:excerpt span]
             (str "Every `" opener "` needs a matching `" expected
                  "` after it. Check where this " word " should end.")]}))

(defmethod describe :unexpected-closer
  [{:keys [span character]}]
  {:title "UNEXPECTED CLOSING BRACKET"
   :parts [(str "I found a `" character "` that does not close anything:")
           [:excerpt span]
           (str "Every closing bracket needs a matching opening bracket before it. "
                "Maybe there is one `" character "` too many?")]})

(defmethod describe :mismatched-closer
  [{:keys [span collection opener-span expected found]}]
  {:title "MISMATCHED BRACKET"
   :parts [(str "I was reading a " (name collection) " that starts here:")
           [:excerpt opener-span]
           (str "I expected it to end with `" expected "`, but I found `"
                found "`:")
           [:excerpt span]
           (str "A `" (openers collection) "` must be closed by `" expected
                "`.")]})

(defmethod describe :odd-map
  [{:keys [span count]}]
  {:title "INCOMPLETE MAP"
   :parts [(str "A map needs a value for every key, but this map has "
                (plural count "form") ":")
           [:excerpt span]
           "Keys and values come in pairs, like `{:a 1 :b 2}`. Is a value missing, or is there an extra key?"]})

(defmethod describe :unsupported-syntax
  [{:keys [span character]}]
  {:title "UNSUPPORTED SYNTAX"
   :parts [(str "I found a `" character "`, which Rasma does not support:")
           [:excerpt span]
           (case character
             "#" "Rasma has no sets, tagged literals or `#_` discards."
             "\\" "Rasma has no character literals. Use a string instead."
             "^" "Rasma has no metadata."
             "Rasma does not use this syntax.")]})

(defmethod describe :default
  [{:keys [kind span]}]
  {:title "ERROR"
   :parts (cond-> [(str "Something went wrong: " (name kind) ".")]
            span (conj [:excerpt span]))})

(defn render-error
  "Renders error data as text. `source` is the text that was being read.
  Options: :file, a name to show in the header."
  ([data source]
   (render-error data source nil))
  ([data source {:keys [file]}]
   (let [lines (source-lines source)
         {:keys [title parts]} (describe data)
         body (map (fn [part]
                     (if (string? part)
                       part
                       (excerpt lines (second part))))
                   parts)]
     (string/join "\n\n" (cons (header title file) body)))))
