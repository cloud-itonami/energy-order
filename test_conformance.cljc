#!/usr/bin/env bb
;; Energy Order Protocol — write-surface CONFORMANCE: every leg's emitted claims
;; validate against the com.etzhayyim.mio.flowClaim lexicon.
;; Run:  bb --classpath 20-actors 20-actors/energy_order/test_conformance.cljc
(ns energy-order.test-conformance
  (:require [mio.methods.lexicon :as lex]
            [tawami.methods.tawami-edn :as tawami-edn]
            [tawami.methods.claim :as tawami-claim]
            [okibi.methods.okibi-edn :as okibi-edn]
            [okibi.methods.claim :as okibi-claim]
            [toi.methods.toi-edn :as toi-edn]
            [toi.methods.claim :as toi-claim]
            [yudane.methods.yudane-edn :as yudane-edn]
            [yudane.methods.claim :as yudane-claim]
            [clojure.test :refer [deftest is run-tests]]))

(def schema (lex/load-schema "20-actors/mio/kotoba/lexicon.flowClaim.edn"))

(defn- all-claims []
  (concat
   (tawami-claim/from-assets (tawami-edn/assets "20-actors/tawami/kotoba/seed.edn"))
   (okibi-claim/from-nodes (okibi-edn/sources "20-actors/okibi/kotoba/seed.edn")
                           (okibi-edn/sinks "20-actors/okibi/kotoba/seed.edn"))
   (toi-claim/from-nodes (toi-edn/jobs "20-actors/toi/kotoba/seed.edn")
                         (toi-edn/sites "20-actors/toi/kotoba/seed.edn"))
   (yudane-claim/from-offers (yudane-edn/offers "20-actors/yudane/kotoba/seed.edn"))))

(deftest every-emitted-claim-conforms-to-the-lexicon
  (let [cs (all-claims)]
    (is (= 25 (count cs)) "the full suite claim set")
    (doseq [c cs]
      (is (lex/valid? schema c)
          (str (:source-actor c) "/" (:id c) " is a valid flowClaim: "
               (lex/validate-claim schema c))))))

(deftest the-write-surface-rejects-a-malformed-leg-claim
  ;; a leg that tried to submit an out-of-range or forbidden claim would be rejected.
  (let [c (first (tawami-claim/from-assets (tawami-edn/assets "20-actors/tawami/kotoba/seed.edn")))]
    (is (lex/valid? schema c) "the real emitted claim is valid")
    (is (not (lex/valid? schema (assoc c :additionality 2.0))) "tampered additionality rejected")
    (is (not (lex/valid? schema (assoc c :consumed-reward-kwh 9))) "consumption-reward rejected")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'energy-order.test-conformance)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
