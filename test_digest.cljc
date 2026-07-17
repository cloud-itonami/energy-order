#!/usr/bin/env bb
;; Energy Order Protocol — cross-actor digest tests.
;; Run:  bb --classpath 20-actors 20-actors/energy-order/test_digest.cljc
(ns energy-order.test-digest
  (:require [energy-order.digest :as d]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(defn- s [] (d/summary (d/all-claims)))

(deftest composes-all-four-legs
  (let [cs (d/all-claims)]
    (is (= 25 (count cs)) "25 claims across the suite")
    (is (= #{"tawami" "okibi" "toi" "yudane"} (set (map :source-actor cs))))))

(deftest summary-accounts-the-pipeline
  (let [su (s)]
    (is (= 25 (:total-claims su)))
    (is (= 23 (:verified-claims su)) "23 verify (2 tawami slow-flex filtered)")
    (is (pos? (:flowrate su)))
    ;; moyai credit is 1:1 with verified flowrate
    (is (< (Math/abs (- (:flowrate su) (:total-moyai-credit su))) 1e-6))))

(deftest by-leg-sums-to-flowrate
  (let [su (s)
        legsum (reduce + 0.0 (map :flowrate (:by-leg su)))]
    (is (= 4 (count (:by-leg su))) "four legs")
    (is (< (Math/abs (- legsum (:flowrate su))) 1e-6) "leg contributions sum to the org Flowrate")))

(deftest by-flow-class-spans-the-domains
  (let [classes (set (map :flow-class (:by-flow-class (s))))]
    (is (contains? classes :waste-heat))
    (is (contains? classes :compute-routing))
    (is (contains? classes :intention))))

(deftest digest-cid-is-deterministic
  (let [su (s)]
    (is (= (d/digest-cid su) (d/digest-cid su)))
    (is (str/starts-with? (d/digest-cid su) "b"))))

(deftest datoms-are-derived-and-currency-free
  (let [ds (d/datoms (s))
        edn (pr-str ds)]
    (is (str/includes? edn ":eo.composition/flowrate"))
    (is (str/includes? edn ":eo.composition/total-moyai-credit"))
    (is (str/includes? edn ":eo/derived"))
    (is (not (str/includes? edn ":cash")))
    (is (not (str/includes? edn ":usd")))))

(deftest report-tells-the-pouf-story
  (let [md (d/render-report (s))]
    (is (str/includes? md "Proof of Work → Proof of Useful Flow"))
    (is (str/includes? md "ORDERED"))
    (is (str/includes? md "Digest CID"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'energy-order.test-digest)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
