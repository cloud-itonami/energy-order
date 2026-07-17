#!/usr/bin/env bb
;; Energy Order Protocol — suite integrity validator tests.
;; Run:  bb --classpath 20-actors 20-actors/energy_order/test_validate.cljc
(ns energy-order.test-validate
  (:require [energy-order.validate :as v]
            [clojure.test :refer [deftest is run-tests]]))

(deftest every-actor-passes-integrity
  (let [results (v/validate)]
    (is (= 5 (count results)) "all five actors checked")
    (doseq [r results]
      (is (:ok r) (str (:actor r) " integrity: leaks=" (:leaks r) " dup-ids=" (:dup-ids r)))
      (is (empty? (:leaks r)) (str (:actor r) " emits no unrepresentable (charter-gate) attribute"))
      (is (:ids-unique r) (str (:actor r) " has unique seed ids")))))

(deftest gates-are-actually-checked
  ;; the suite verifies a non-trivial number of gate attributes absent.
  (let [total (reduce + (map :unrepresentable-checked (v/validate)))]
    (is (>= total 20) "at least 20 charter-gate attributes verified absent across the suite")))

(deftest validator-is-not-vacuous
  ;; prove the leak detector actually fires on a synthetic leak.
  (is (= [":mio.obs/consumed-reward"]
         (v/leaks-in [":mio.obs/consumed-reward" ":mio/trade"]
                     "[[:db/add e :mio.obs/consumed-reward 5][:db/add e :mio.obs/order-delta-kwh 9]]"))
      "a forbidden attr present in the datoms IS detected")
  (is (empty? (v/leaks-in [":mio/trade" ":mio/signal"]
                          "[[:db/add e :mio.obs/order-delta-kwh 9]]"))
      "clean datoms leak nothing"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'energy-order.test-validate)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
