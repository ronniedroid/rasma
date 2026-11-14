(ns rasma.test-runner
  (:require [clojure.test :as test]
            [rasma.reader-test]
            [rasma.render-test]))

(defn -main
  [& _args]
  (let [result (test/run-tests 'rasma.reader-test 'rasma.render-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
