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
