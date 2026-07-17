#!/usr/bin/env bb
;; Energy Order Protocol — cell-runner `fire` contract tests for all five actors.
;; Run:  bb --classpath 20-actors 20-actors/energy_order/test_cells.cljc
(ns energy-order.test-cells
  (:require [mio.cell :as mio-cell]
            [tawami.cell :as tawami-cell]
            [okibi.cell :as okibi-cell]
            [toi.cell :as toi-cell]
            [yudane.cell :as yudane-cell]
            [mio.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(defn- tmp [n] (str "20-actors/energy_order/data/test-cell-" n ".kotoba.edn"))
(defn- clean! [p] (let [f (io/file p)] (when (.exists f) (.delete f))))

(defn- check-cell [fire n]
  (let [p (tmp n)]
    (clean! p)
    (let [r1 (fire p)
          r2 (fire p)]   ; second fire = idempotent no-op
      (is (map? r1) (str n " fire returns a summary map"))
      (is (:appended r1) (str n " first fire appends"))
      (is (string? (:head r1)) (str n " first fire has a head CID"))
      (is (not (:appended r2)) (str n " second fire is a no-op"))
      (is (= :no-change (:reason r2)) (str n " idempotent-by-content"))
      (is (:ok (k/verify-chain p)) (str n " ledger chain verifies"))
      (clean! p))))

(deftest mio-cell-fires    (check-cell mio-cell/fire "mio"))
(deftest tawami-cell-fires (check-cell tawami-cell/fire "tawami"))
(deftest okibi-cell-fires  (check-cell okibi-cell/fire "okibi"))
(deftest toi-cell-fires    (check-cell toi-cell/fire "toi"))
(deftest yudane-cell-fires (check-cell yudane-cell/fire "yudane"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'energy-order.test-cells)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
