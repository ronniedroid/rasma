(ns rasma.render-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [rasma.errors :as errors]
            [rasma.reader :as reader]
            [rasma.render :as render]))

(defn- render-source
  "Reads `source`, and renders the error it throws. Returns nil if there is
  no error."
  [source]
  (try
    (reader/read-all source)
    nil
    (catch clojure.lang.ExceptionInfo e
      (render/render-error (errors/error-data e) source {:file "window.rsm"}))))

;; Exact output

(deftest rendering-an-unclosed-string
  (is (= (string/join
          "\n"
          ["-- UNCLOSED STRING -------------------------------------------------- window.rsm"
          ""
          "I found the start of a string, but I never found the end:"
          ""
          "2|   :label \"Hello"
          "            ^^^^^^"
          ""
          "A string must end with a `\"` on the same line. To put a line break inside a string, write `\\n`."])
         (render-source "(Gtk.Label\n  :label \"Hello\n  :xalign 0)"))))

(deftest rendering-a-mismatched-bracket-shows-both-places
  (is (= (string/join
          "\n"
          ["-- MISMATCHED BRACKET ----------------------------------------------- window.rsm"
          ""
          "I was reading a list that starts here:"
          ""
          "1| (Gtk.Box"
          "   ^"
          ""
          "I expected it to end with `)`, but I found `]`:"
          ""
          "2|   :spacing 12]"
          "                ^"
          ""
          "A `(` must be closed by `)`."])
         (render-source "(Gtk.Box\n  :spacing 12]"))))

(deftest rendering-a-multi-line-span-underlines-the-first-line
  (is (= (string/join
          "\n"
          ["-- INCOMPLETE MAP --------------------------------------------------- window.rsm"
          ""
          "A map needs a value for every key, but this map has 3 forms:"
          ""
          "1| {:a 1"
          "   ^^^^^"
          ""
          "Keys and values come in pairs, like `{:a 1 :b 2}`. Is a value missing, or is there an extra key?"])
         (render-source "{:a 1\n :b}"))))

(deftest rendering-keeps-tabs-in-the-caret-padding
  (is (= (string/join
          "\n"
          ["-- INVALID KEYWORD -------------------------------------------------- window.rsm"
          ""
          "I found a keyword that starts with two colons:"
          ""
          "2| \t::b)"
          "   \t^^^"
          ""
          "A keyword has a single colon, like `:title`."])
         (render-source "(a\n\t::b)"))))

(deftest rendering-counts-an-emoji-as-one-column
  (is (= (string/join
          "\n"
          ["-- INVALID KEYWORD -------------------------------------------------- window.rsm"
          ""
          "I found a keyword that starts with two colons:"
          ""
          "1| (a \ud83d\ude00 ::b)"
          "        ^^^"
          ""
          "A keyword has a single colon, like `:title`."])
         (render-source "(a \ud83d\ude00 ::b)"))))

;; Structure

(deftest header-is-as-wide-as-the-terminal
  (let [header (first (string/split-lines (render-source "(a ::b)")))]
    (is (= 80 (count header)))
    (is (string/starts-with? header "-- INVALID KEYWORD -"))
    (is (string/ends-with? header " window.rsm"))))

(deftest header-without-a-file-name
  (let [data (try (reader/read-all "(a ::b)")
                  (catch clojure.lang.ExceptionInfo e (errors/error-data e)))
        header (first (string/split-lines
                       (render/render-error data "(a ::b)")))]
    (is (= 80 (count header)))
    (is (not (string/includes? header "window.rsm")))))

;; Every kind of error the reader can produce has its own message

(deftest every-reader-error-is-rendered
  (doseq [source [":"
                  "::a"
                  ":2a"
                  ":a.b"
                  "99999999999999999999"
                  "1e999"
                  "1e-999"
                  "\"abc"
                  "\"a\\qb\""
                  "(a"
                  "[a"
                  "{:a"
                  ")"
                  "(a]"
                  "{:a}"
                  "#_a"
                  "\\a"
                  "^a"]]
    (let [text (render-source source)]
      (is (string? text) source)
      (is (string/starts-with? text "-- ") source)
      (is (not (string/includes? text "Something went wrong")) source)
      (is (string/includes? text "1| ") source))))

(deftest unknown-kinds-still-render
  (let [text (render/render-error {:kind :something-new :span nil} "")]
    (is (string/includes? text "-- ERROR "))
    (is (string/includes? text "something-new"))))

(deftest error-data-only-recognizes-rasma-errors
  (is (nil? (errors/error-data (ex-info "other" {:foo 1}))))
  (is (nil? (errors/error-data (Exception. "plain"))))
  (is (= :a (:kind (errors/error-data (errors/error :a nil {}))))))
