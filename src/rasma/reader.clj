(ns rasma.reader
  (:require [rasma.ast :as ast]
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
  [reader]
  (if-let [character (current-char reader)]
    (-> reader
        (update :position inc)
        (update :line
                #(if (= character \newline)
                   (inc %)
                   %))
        (update :column
                #(if (= character \newline)
                   1
                   (inc %))))
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
  (loop [reader reader
         characters []]
    (let [character (current-char reader)]
      (if (or (nil? character)
              (delimiter? character))
        [reader (apply str characters)]
        (recur (advance reader)
               (conj characters character))))))

(defn- classify-token
  [token]
  (cond
    (= token "true")
    [:boolean true]

    (= token "false")
    [:boolean false]

    (= token "nil")
    [:nil nil]

    (re-matches #"[+-]?\d+" token)
    [:integer (Long/parseLong token)]

    (re-matches #"[+-]?(?:\d+\.\d*|\d*\.\d+)(?:[eE][+-]?\d+)?" token)
    [:float (Double/parseDouble token)]

    (string/starts-with? token ":")
    [:keyword (keyword (subs token 1))]

    :else
    [:symbol (symbol token)]))

(defn- read-atom
  [reader]
  (let [start reader
        [reader token] (read-token reader)
        [kind value] (classify-token token)
        span (ast/make-span
              (:position start)
              (:position reader)
              (:line start)
              (:column start)
              (:line reader)
              (:column reader))]
    [reader (ast/make-node kind value span)]))
