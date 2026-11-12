(ns rasma.test-runner
  (:require [clojure.test :as test]
            [rasma.reader-test]))

(defn -main
  [& _args]
  (let [result (test/run-tests 'rasma.reader-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
