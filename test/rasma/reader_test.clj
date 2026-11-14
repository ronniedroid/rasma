(ns rasma.reader-test
  (:require [clojure.test :refer [deftest is]]
            [rasma.reader :as reader]))

;; Reader creation

(deftest reader-creation
  (let [state (#'reader/make-reader "Hello")]
    (is (= "Hello" (:source state)))
    (is (= 0 (:position state)))
    (is (= 1 (:line state)))
    (is (= 1 (:column state)))))

;; End of file

(deftest end-of-file
  (let [empty-reader (#'reader/make-reader "")
        non-empty-reader (#'reader/make-reader "A")
        finished-reader (#'reader/advance non-empty-reader)]
    (is (#'reader/eof? empty-reader))
    (is (nil? (#'reader/current-char empty-reader)))
    (is (not (#'reader/eof? non-empty-reader)))
    (is (= \A (#'reader/current-char non-empty-reader)))
    (is (#'reader/eof? finished-reader))
    (is (nil? (#'reader/current-char finished-reader)))))

;; Advancing

(deftest advancing-reader
  (let [state (#'reader/make-reader "Hello")
        next-state (#'reader/advance state)]
    (is (= 1 (:position next-state)))
    (is (= 1 (:line next-state)))
    (is (= 2 (:column next-state)))
    (is (= 0 (:position state))))

  (let [state (#'reader/make-reader "A\nB")
        next-state (#'reader/advance
                    (#'reader/advance state))]
    (is (= 2 (:position next-state)))
    (is (= 2 (:line next-state)))
    (is (= 1 (:column next-state)))
    (is (= \B (#'reader/current-char next-state))))

  (let [state (#'reader/make-reader "")
        next-state (#'reader/advance state)]
    (is (= state next-state))))

;; Whitespace

(deftest whitespace-detection
  (doseq [character [\space \tab \newline \return \,]]
    (is (#'reader/whitespace? character)))

  (doseq [character [\A \1 \: \( \)]]
    (is (not (#'reader/whitespace? character)))))

(deftest skipping-whitespace
  (let [state (#'reader/make-reader "   Hello")
        result (#'reader/skip-whitespace state)]
    (is (= 3 (:position result)))
    (is (= \H (#'reader/current-char result))))

  (let [state (#'reader/make-reader "\t\n  Hello")
        result (#'reader/skip-whitespace state)]
    (is (= 4 (:position result)))
    (is (= 2 (:line result)))
    (is (= 3 (:column result)))
    (is (= \H (#'reader/current-char result))))

  (let [state (#'reader/make-reader ",,,Hello")
        result (#'reader/skip-whitespace state)]
    (is (= 3 (:position result)))
    (is (= \H (#'reader/current-char result))))

  (let [state (#'reader/make-reader "Hello")]
    (is (= state (#'reader/skip-whitespace state))))

  (let [state (#'reader/make-reader "   ")
        result (#'reader/skip-whitespace state)]
    (is (#'reader/eof? result))))

;; Comments

(deftest skipping-comments
  (let [state (#'reader/make-reader "; comment\nHello")
        result (#'reader/skip-comment state)]
    (is (= 9 (:position result)))
    (is (= 1 (:line result)))
    (is (= 10 (:column result)))
    (is (= \newline (#'reader/current-char result))))

  (let [state (#'reader/make-reader "; comment")
        result (#'reader/skip-comment state)]
    (is (#'reader/eof? result)))

  (let [state (#'reader/make-reader "; comment\n  Hello")
        result (#'reader/skip-whitespace state)]
    (is (= \H (#'reader/current-char result)))
    (is (= 2 (:line result)))
    (is (= 3 (:column result))))

  (let [state (#'reader/make-reader "; first\n; second\nHello")
        result (#'reader/skip-whitespace state)]
    (is (= \H (#'reader/current-char result)))
    (is (= 3 (:line result)))))

;; Delimiters

(deftest delimiter-detection
  (doseq [character [\space \tab \newline \,
                     \( \) \[ \] \{ \}
                     \" \;]]
    (is (#'reader/delimiter? character)))

  (doseq [character [\A \1 \: \. \- \_]]
    (is (not (#'reader/delimiter? character)))))

;; Token reading

(deftest reading-tokens
  (let [[state token] (#'reader/read-token
                       (#'reader/make-reader "Gtk.Label :orientation"))]
    (is (= "Gtk.Label" token))
    (is (= 9 (:position state)))
    (is (= 10 (:column state)))
    (is (= \space (#'reader/current-char state))))

  (let [[state token] (#'reader/read-token
                       (#'reader/make-reader "Label)"))]
    (is (= "Label" token))
    (is (= \) (#'reader/current-char state))))

  (let [[state token] (#'reader/read-token
                       (#'reader/make-reader "label\"Hello\""))]
    (is (= "label" token))
    (is (= \" (#'reader/current-char state))))

  (let [[state token] (#'reader/read-token
                       (#'reader/make-reader "value; comment"))]
    (is (= "value" token))
    (is (= \; (#'reader/current-char state))))

  (let [[state token] (#'reader/read-token
                       (#'reader/make-reader "vertical"))]
    (is (= "vertical" token))
    (is (#'reader/eof? state)))

  (let [state (#'reader/make-reader "Hello")
        [_ token] (#'reader/read-token state)]
    (is (= "Hello" token))
    (is (= 0 (:position state)))))

;; Token classification

(deftest classifying-booleans
  (is (= [:boolean true]
         (#'reader/classify-token "true")))
  (is (= [:boolean false]
         (#'reader/classify-token "false"))))

(deftest classifying-nil
  (is (= [:nil nil]
         (#'reader/classify-token "nil"))))

(deftest classifying-integers
  (is (= [:integer 42]
         (#'reader/classify-token "42")))
  (is (= [:integer -42]
         (#'reader/classify-token "-42")))
  (is (= [:integer 0]
         (#'reader/classify-token "0")))
  (is (= [:integer 42]
         (#'reader/classify-token "+42"))))

(deftest classifying-floats
  (is (= [:float 4.0]
         (#'reader/classify-token "4.0")))
  (is (= [:float -4.5]
         (#'reader/classify-token "-4.5")))
  (is (= [:float 0.5]
         (#'reader/classify-token ".5")))
  (is (= [:float 4.0]
         (#'reader/classify-token "4.")))
  (is (= [:float 100.0]
         (#'reader/classify-token "1.0e2"))))

(deftest classifying-keywords
  (is (= [:keyword :orientation]
         (#'reader/classify-token ":orientation")))
  (is (= [:keyword :default-width]
         (#'reader/classify-token ":default-width"))))

(deftest classifying-symbols
  (is (= [:symbol 'Gtk.Label]
         (#'reader/classify-token "Gtk.Label")))
  (is (= [:symbol 'vertical]
         (#'reader/classify-token "vertical")))
  (is (= [:symbol 'my-callback]
         (#'reader/classify-token "my-callback"))))

;; Keyword validation

(defn- reader-error-data
  "Runs `thunk` and returns the data of the error it throws, or nil."
  [thunk]
  (try
    (thunk)
    nil
    (catch clojure.lang.ExceptionInfo error
      (ex-data error))))

(deftest valid-keyword-names
  (doseq [keyword-name ["title" "default-width" "default_width"
                        "h2" "Title" "a-" "a--b" "a_"]]
    (is (= [:keyword (keyword keyword-name)]
           (#'reader/classify-token (str ":" keyword-name))))))

(deftest invalid-keyword-names
  (doseq [[token reason] [[":" :empty]
                          ["::" :double-colon]
                          ["::title" :double-colon]
                          [":2fast" :bad-start]
                          [":-foo" :bad-start]
                          [":_foo" :bad-start]
                          [":\u0639" :bad-start]
                          [":foo.bar" :bad-character]
                          [":foo?" :bad-character]
                          [":a:b" :bad-character]]]
    (let [data (reader-error-data
                #(#'reader/read-atom (#'reader/make-reader token)))]
      (is (= :invalid-keyword (:kind data)) token)
      (is (= reason (:reason data)) token)
      (is (= token (:token data)) token))))

(deftest invalid-keyword-reports-the-bad-character
  (is (= "." (:character (reader-error-data
                          #(#'reader/read-atom (#'reader/make-reader ":foo.bar"))))))
  (is (= "2" (:character (reader-error-data
                          #(#'reader/read-atom (#'reader/make-reader ":2fast")))))))

(deftest invalid-keyword-error-carries-a-span
  (let [state (#'reader/skip-whitespace
               (#'reader/make-reader "\n  ::title"))
        data (reader-error-data #(#'reader/read-atom state))
        span (:span data)]
    (is (= :double-colon (:reason data)))
    (is (= 2 (:start-line span)))
    (is (= 3 (:start-column span)))
    (is (= 2 (:end-line span)))
    (is (= 10 (:end-column span)))))

;; Columns count characters, not Java chars

(deftest advancing-over-an-emoji
  (let [state (#'reader/make-reader "a\uD83D\uDE00b")
        after-a (#'reader/advance state)
        after-emoji (#'reader/advance after-a)
        after-b (#'reader/advance after-emoji)]
    (is (= 1 (:position after-a)))
    (is (= 2 (:column after-a)))
    ;; the emoji is two Java chars but only one column
    (is (= 3 (:position after-emoji)))
    (is (= 3 (:column after-emoji)))
    (is (= \b (#'reader/current-char after-emoji)))
    (is (= 4 (:position after-b)))
    (is (= 4 (:column after-b)))))

(deftest advancing-over-a-lone-surrogate
  (let [state (#'reader/advance (#'reader/make-reader "\uD83D"))]
    (is (= 1 (:position state)))
    (is (= 2 (:column state)))))

(deftest a-tab-is-one-column
  (let [state (#'reader/advance (#'reader/make-reader "\tx"))]
    (is (= 1 (:position state)))
    (is (= 2 (:column state)))))

(deftest reading-a-token-with-an-emoji
  (let [[state token] (#'reader/read-token
                       (#'reader/make-reader "\uD83D\uDE00x next"))]
    (is (= "\uD83D\uDE00x" token))
    (is (= 3 (:position state)))
    (is (= 3 (:column state)))))

(deftest atom-span-with-an-emoji
  (let [[_ node] (#'reader/read-atom
                  (#'reader/make-reader "\uD83D\uDE00x"))
        span (:span node)]
    (is (= 0 (:start-offset span)))
    (is (= 3 (:end-offset span)))
    (is (= 3 (:end-column span)))))

(deftest invalid-keyword-reports-a-whole-emoji
  (let [data (reader-error-data
              #(#'reader/read-atom
                (#'reader/make-reader ":\uD83D\uDE00")))]
    (is (= :bad-start (:reason data)))
    (is (= "\uD83D\uDE00" (:character data)))))

;; Number validation

(deftest classifying-exponent-floats
  (is (= [:float 100000.0] (#'reader/classify-token "1e5")))
  (is (= [:float 100000.0] (#'reader/classify-token "1E5")))
  (is (= [:float 0.001] (#'reader/classify-token "1e-3")))
  (is (= [:float -2000.0] (#'reader/classify-token "-2e3")))
  (is (= [:float 150.0] (#'reader/classify-token "1.5e2")))
  (is (= [:symbol 'e5] (#'reader/classify-token "e5")))
  (is (= [:symbol (symbol "1e")] (#'reader/classify-token "1e")))
  (is (= [:symbol (symbol "1e+")] (#'reader/classify-token "1e+"))))

(deftest integers-at-the-edge-of-the-range
  (let [read-value (fn [source]
                     (:value (second (#'reader/read-atom
                                      (#'reader/make-reader source)))))]
    (is (= Long/MAX_VALUE (read-value "9223372036854775807")))
    (is (= Long/MIN_VALUE (read-value "-9223372036854775808")))))

(deftest integer-out-of-range
  (doseq [token ["9223372036854775808"
                 "-9223372036854775809"
                 "99999999999999999999"]]
    (let [data (reader-error-data
                #(#'reader/read-atom (#'reader/make-reader token)))]
      (is (= :integer-out-of-range (:kind data)) token)
      (is (= token (:token data)) token))))

(deftest integer-out-of-range-carries-a-span
  (let [state (#'reader/skip-whitespace
               (#'reader/make-reader "\n  99999999999999999999"))
        data (reader-error-data #(#'reader/read-atom state))
        span (:span data)]
    (is (= 2 (:start-line span)))
    (is (= 3 (:start-column span)))
    (is (= 23 (:end-column span)))))

(deftest float-out-of-range
  (doseq [[token direction] [["1e999" :too-large]
                             ["-1e999" :too-large]
                             ["1.5e400" :too-large]
                             ["1e-999" :too-small]
                             ["-1e-999" :too-small]
                             ["2e-324" :too-small]
                             [".5e-999" :too-small]]]
    (let [data (reader-error-data
                #(#'reader/read-atom (#'reader/make-reader token)))]
      (is (= :float-out-of-range (:kind data)) token)
      (is (= direction (:direction data)) token)
      (is (= token (:token data)) token))))

(deftest floats-that-are-fine
  (doseq [token ["0.0" "-0.0" "0e5" "0.0e-999" "1e308" "1e-320"]]
    (is (nil? (reader-error-data
               #(#'reader/read-atom (#'reader/make-reader token))))
        token)))

;; Reading atoms

(deftest reading-symbol-atoms
  (let [[state node] (#'reader/read-atom
                      (#'reader/make-reader "Gtk.Label"))]
    (is (= 9 (:position state)))
    (is (= :symbol (:kind node)))
    (is (= 'Gtk.Label (:value node)))))

(deftest reading-keyword-atoms
  (let [[_ node] (#'reader/read-atom
                  (#'reader/make-reader ":orientation"))]
    (is (= :keyword (:kind node)))
    (is (= :orientation (:value node)))))

(deftest reading-integer-atoms
  (let [[_ node] (#'reader/read-atom
                  (#'reader/make-reader "42"))]
    (is (= :integer (:kind node)))
    (is (= 42 (:value node)))))

(deftest reading-float-atoms
  (let [[_ node] (#'reader/read-atom
                  (#'reader/make-reader "4.0"))]
    (is (= :float (:kind node)))
    (is (= 4.0 (:value node)))))

(deftest reading-boolean-atoms
  (let [[_ node] (#'reader/read-atom
                  (#'reader/make-reader "true"))]
    (is (= :boolean (:kind node)))
    (is (true? (:value node)))))

(deftest reading-nil-atoms
  (let [[_ node] (#'reader/read-atom
                  (#'reader/make-reader "nil"))]
    (is (= :nil (:kind node)))
    (is (nil? (:value node)))))

;; Source spans

(deftest atom-source-span
  (let [[_ node] (#'reader/read-atom
                  (#'reader/make-reader "true"))
        span (:span node)]
    (is (= 0 (:start-offset span)))
    (is (= 4 (:end-offset span)))
    (is (= 1 (:start-line span)))
    (is (= 1 (:start-column span)))
    (is (= 1 (:end-line span)))
    (is (= 5 (:end-column span)))))

(deftest atom-source-span-after-whitespace
  (let [state (#'reader/skip-whitespace
               (#'reader/make-reader "\n  vertical"))
        [_ node] (#'reader/read-atom state)
        span (:span node)]
    (is (= 3 (:start-offset span)))
    (is (= 11 (:end-offset span)))
    (is (= 2 (:start-line span)))
    (is (= 3 (:start-column span)))
    (is (= 2 (:end-line span)))
    (is (= 11 (:end-column span)))))

;; Strings

(defn- read-string-node
  [source]
  (#'reader/read-string-literal (#'reader/make-reader source)))

(deftest reading-strings
  (let [[state node] (read-string-node "\"Hello World\"")
        span (:span node)]
    (is (= :string (:kind node)))
    (is (= "Hello World" (:value node)))
    (is (= 13 (:position state)))
    (is (#'reader/eof? state))
    (is (= 0 (:start-offset span)))
    (is (= 13 (:end-offset span)))
    (is (= 1 (:start-column span)))
    (is (= 14 (:end-column span))))

  (let [[_ node] (read-string-node "\"\"")]
    (is (= "" (:value node)))
    (is (= 3 (:end-column (:span node)))))

  (let [[state _] (read-string-node "\"hi\" next")]
    (is (= 4 (:position state)))
    (is (= \space (#'reader/current-char state)))))

(deftest strings-keep-delimiters-inside
  (let [[_ node] (read-string-node "\"a (b) ; c [d] , e\"")]
    (is (= "a (b) ; c [d] , e" (:value node)))))

(deftest string-escapes
  ;; Rasma text: "say \"hi\""
  (let [[_ node] (read-string-node "\"say \\\"hi\\\"\"")
        span (:span node)]
    (is (= "say \"hi\"" (:value node)))
    (is (= 12 (:end-offset span)))
    (is (= 13 (:end-column span))))
  ;; Rasma text: "a\\b"
  (is (= "a\\b" (:value (second (read-string-node "\"a\\\\b\"")))))
  ;; Rasma text: "one\ntwo"
  (is (= "one\ntwo" (:value (second (read-string-node "\"one\\ntwo\"")))))
  ;; Rasma text: "a\tb"
  (is (= "a\tb" (:value (second (read-string-node "\"a\\tb\""))))))

(deftest strings-with-unicode
  (let [[_ node] (read-string-node "\"\uD83D\uDE00 hi\"")
        span (:span node)]
    (is (= "\uD83D\uDE00 hi" (:value node)))
    (is (= 7 (:end-offset span)))
    (is (= 7 (:end-column span))))
  (let [kurdish "\u0633\u06B5\u0627\u0648"
        [_ node] (read-string-node (str "\"" kurdish "\""))]
    (is (= kurdish (:value node)))
    (is (= 7 (:end-column (:span node))))))

(deftest string-span-after-whitespace
  (let [state (#'reader/skip-whitespace
               (#'reader/make-reader "\n  \"Hi\""))
        [_ node] (#'reader/read-string-literal state)
        span (:span node)]
    (is (= 3 (:start-offset span)))
    (is (= 7 (:end-offset span)))
    (is (= 2 (:start-line span)))
    (is (= 3 (:start-column span)))
    (is (= 7 (:end-column span)))))

(deftest unclosed-strings
  (doseq [[source token end-column]
          [["\"Hello" "\"Hello" 7]
           ["\"Hello\nworld\"" "\"Hello" 7]
           ["\"Hello\r\nworld\"" "\"Hello" 7]
           ["\"" "\"" 2]
           ["\"abc\\" "\"abc\\" 6]
           ["\"abc\\\ndef\"" "\"abc\\" 6]]]
    (let [data (reader-error-data #(read-string-node source))
          span (:span data)]
      (is (= :unclosed-string (:kind data)) source)
      (is (= token (:token data)) source)
      (is (= 1 (:start-line span)) source)
      (is (= 1 (:start-column span)) source)
      (is (= 1 (:end-line span)) source)
      (is (= end-column (:end-column span)) source))))

(deftest invalid-string-escapes
  ;; Rasma text: "a\qb"
  (let [data (reader-error-data #(read-string-node "\"a\\qb\""))
        span (:span data)]
    (is (= :invalid-escape (:kind data)))
    (is (= "q" (:character data)))
    (is (= 1 (:start-line span)))
    (is (= 3 (:start-column span)))
    (is (= 5 (:end-column span))))
  ;; unicode escapes are not supported
  (is (= "u" (:character (reader-error-data
                          #(read-string-node "\"\\u0041\"")))))
  ;; an emoji after the backslash is reported whole
  (is (= "\uD83D\uDE00"
         (:character (reader-error-data
                      #(read-string-node "\"\\\uD83D\uDE00\""))))))

;; Collections and forms

(defn- simplify
  "Turns a node into plain data without spans, to make trees easy to compare."
  [node]
  (if (contains? #{:list :vector :map} (:kind node))
    [(:kind node) (mapv simplify (:value node))]
    [(:kind node) (:value node)]))

(defn- read-simple
  [source]
  (mapv simplify (reader/read-all source)))

(defn- read-error
  [source]
  (reader-error-data #(reader/read-all source)))

(deftest reading-nothing
  (is (= [] (reader/read-all "")))
  (is (= [] (reader/read-all "  \n , ; only a comment\n  "))))

(deftest reading-top-level-atoms
  (is (= [[:symbol 'a] [:string "b"] [:integer 3] [:keyword :k]]
         (read-simple "a \"b\" 3 :k"))))

(deftest reading-lists
  (is (= [[:list []]] (read-simple "()")))
  (is (= [[:list [[:symbol 'a] [:keyword :b] [:string "c"] [:integer 1]]]]
         (read-simple "(a :b \"c\" 1)")))
  (is (= [[:list [[:symbol 'a]
                  [:list [[:symbol 'b]]]
                  [:vector [[:symbol 'c]]]
                  [:map [[:keyword :d] [:integer 1]]]]]]
         (read-simple "(a (b) [c] {:d 1})"))))

(deftest reading-vectors-and-maps
  (is (= [[:vector []]] (read-simple "[]")))
  (is (= [[:vector [[:integer 1] [:integer 2]]]] (read-simple "[1, 2]")))
  (is (= [[:map []]] (read-simple "{}")))
  (is (= [[:map [[:keyword :a] [:integer 1] [:keyword :b] [:integer 2]]]]
         (read-simple "{:a 1 :b 2}"))))

(deftest comments-and-commas-inside-collections
  (is (= [[:list [[:symbol 'a] [:symbol 'b] [:symbol 'c]]]]
         (read-simple "(a, b ; a comment\n c)"))))

(deftest brackets-inside-strings-are-just-text
  (is (= [[:list [[:string "a)"] [:symbol 'b]]]]
         (read-simple "(\"a)\" b)"))))

(deftest closers-end-a-symbol
  (is (= [[:list [[:symbol 'a] [:string "b"]]]]
         (read-simple "(a\"b\")"))))

(deftest reading-several-top-level-forms
  (is (= [[:list [[:symbol 'using] [:symbol 'Gtk] [:float 4.0]]]
          [:list [[:symbol 'using] [:symbol 'Adw] [:integer 1]]]]
         (read-simple "(using Gtk 4.0)\n(using Adw 1)"))))

(deftest symbols-may-contain-reserved-characters-after-the-first
  (is (= [[:symbol (symbol "a#b")]
          [:symbol (symbol "C#")]
          [:symbol (symbol "notify::default-width")]]
         (read-simple "a#b C# notify::default-width"))))

(deftest collection-spans
  (let [[node] (reader/read-all "(a\n  [b])")
        span (:span node)
        inner-span (:span (second (:value node)))]
    (is (= 0 (:start-offset span)))
    (is (= 9 (:end-offset span)))
    (is (= 1 (:start-line span)))
    (is (= 1 (:start-column span)))
    (is (= 2 (:end-line span)))
    (is (= 7 (:end-column span)))
    (is (= 2 (:start-line inner-span)))
    (is (= 3 (:start-column inner-span)))
    (is (= 6 (:end-column inner-span)))))

(def ^:private hello-world
  (apply str
         (interpose
          "\n"
          ["(Adw.ApplicationWindow"
           "  :title \"Hello World\""
           "  :default-width 400"
           "  :default-height 200"
           ""
           "  :content"
           "    (Adw.ToolbarView"
           "      (:top"
           "        (Adw.HeaderBar))"
           ""
           "      :content"
           "        (Gtk.Box"
           "          :orientation vertical"
           "          :spacing 12"
           ""
           "          (Gtk.Label"
           "            :label \"Hello, World!\""
           "            :styles (title-1))"
           ""
           "          (Gtk.Button"
           "            :label \"Click Me\""
           "            :halign center))))"
           ""])))

(deftest reading-the-hello-world-example
  (let [[root :as forms] (reader/read-all hello-world)
        toolbar-view (nth (:value root) 8)
        top (nth (:value toolbar-view) 1)
        gtk-box (nth (:value toolbar-view) 3)
        label (nth (:value gtk-box) 5)
        button (nth (:value gtk-box) 6)]
    (is (= 1 (count forms)))
    (is (= :list (:kind root)))
    (is (= 9 (count (:value root))))
    (is (= [22 31] ((juxt :end-line :end-column) (:span root))))
    (is (= [8 7 9 25] ((juxt :start-line :start-column :end-line :end-column)
                       (:span top))))
    (is (= [16 11 18 31] ((juxt :start-line :start-column
                                :end-line :end-column)
                          (:span label))))
    (is (= [20 11 22 28] ((juxt :start-line :start-column
                                :end-line :end-column)
                          (:span button))))
    (is (= [:list [[:symbol 'Gtk.Label]
                   [:keyword :label] [:string "Hello, World!"]
                   [:keyword :styles] [:list [[:symbol 'title-1]]]]]
           (simplify label)))))

;; Collection errors

(deftest unclosed-collections
  (doseq [[source collection opener expected]
          [["(a (b c)" :list "(" ")"]
           ["[1 2" :vector "[" "]"]
           ["{:a 1" :map "{" "}"]]]
    (let [data (read-error source)
          span (:span data)]
      (is (= :unclosed-collection (:kind data)) source)
      (is (= collection (:collection data)) source)
      (is (= opener (:opener data)) source)
      (is (= expected (:expected data)) source)
      ;; the span points at the opening bracket only
      (is (= [1 1 1 2] ((juxt :start-line :start-column
                              :end-line :end-column)
                        span))
          source))))

(deftest unclosed-nested-collection-points-at-the-innermost
  (let [data (read-error "(a\n  (b c")
        span (:span data)]
    (is (= :unclosed-collection (:kind data)))
    (is (= [2 3 2 4] ((juxt :start-line :start-column
                            :end-line :end-column)
                      span)))))

(deftest unexpected-closers
  (doseq [[source column character] [[")" 1 ")"]
                                     ["a )" 3 ")"]
                                     ["(a) ]" 5 "]"]
                                     ["{}}" 3 "}"]]]
    (let [data (read-error source)
          span (:span data)]
      (is (= :unexpected-closer (:kind data)) source)
      (is (= character (:character data)) source)
      (is (= column (:start-column span)) source)
      (is (= (inc column) (:end-column span)) source))))

(deftest mismatched-closers
  (let [data (read-error "(a b]")
        span (:span data)
        opener-span (:opener-span data)]
    (is (= :mismatched-closer (:kind data)))
    (is (= :list (:collection data)))
    (is (= ")" (:expected data)))
    (is (= "]" (:found data)))
    (is (= [5 6] ((juxt :start-column :end-column) span)))
    (is (= [1 2] ((juxt :start-column :end-column) opener-span))))
  (let [data (read-error "[1 (2 3]")]
    (is (= :mismatched-closer (:kind data)))
    (is (= :list (:collection data)))
    (is (= "]" (:found data)))))

(deftest odd-maps
  (let [data (read-error "{:a 1 :b}")
        span (:span data)]
    (is (= :odd-map (:kind data)))
    (is (= 3 (:count data)))
    (is (= [1 10] ((juxt :start-column :end-column) span)))))

(deftest unsupported-syntax
  (doseq [[source character] [["#{1 2}" "#"]
                              ["(a #_b)" "#"]
                              ["\\a" "\\"]
                              ["^:private" "^"]]]
    (let [data (read-error source)]
      (is (= :unsupported-syntax (:kind data)) source)
      (is (= character (:character data)) source))))

(deftest errors-from-inside-collections-keep-their-kind
  (let [data (read-error "(a \"b)")]
    (is (= :unclosed-string (:kind data)))
    (is (= 4 (:start-column (:span data)))))
  (let [data (read-error "(a ::b)")]
    (is (= :invalid-keyword (:kind data)))
    (is (= 4 (:start-column (:span data)))))
  (let [data (read-error "[1 99999999999999999999]")]
    (is (= :integer-out-of-range (:kind data)))))
