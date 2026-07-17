#!/usr/bin/env bb
;; Energy Order Protocol — suite-wide INTEGRITY validator (ontology ⊨ code).
(ns energy-order.validate
  "validate.cljc — the Energy Order Protocol self-validating integrity checker.

  For every actor it proves two invariants machine-checked from the ontology, so
  the charter gates declared in the ontology and the behaviour of the code can
  never drift apart:

    1. NEGATIVE-SPACE: every attribute the actor's ontology declares
       `:unrepresentable` (e.g. :mio.obs/consumed-reward, :tawami/dispatch,
       :okibi.sink/cooling-load, :toi.job/kill-order, :yudane/denunciation) is
       ACTUALLY ABSENT from that actor's full emitted datom set. The gate is
       declared once (ontology) and enforced in code; this asserts they agree.
    2. ID UNIQUENESS: the actor's seed entities have unique ids.

  Pure / offline. A green run means no charter-gate has silently regressed across
  the suite. Reused as a regression guard (test_validate.cljc)."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [mio.methods.mio-edn :as mio-edn]
            [mio.methods.analyze :as mio-a]
            [tawami.methods.tawami-edn :as tawami-edn]
            [tawami.methods.analyze :as tawami-a]
            [okibi.methods.okibi-edn :as okibi-edn]
            [okibi.methods.analyze :as okibi-a]
            [toi.methods.toi-edn :as toi-edn]
            [toi.methods.analyze :as toi-a]
            [yudane.methods.yudane-edn :as yudane-edn]
            [yudane.methods.analyze :as yudane-a]))

(defn- resource-path [path]
  (or (io/resource path)
      (throw (ex-info "Energy Order dependency resource missing" {:resource path}))))

(defn- tx-data?
  "The mio and yudane ontology resources
  are now Datomic/Datascript tx-data on disk (ADR-2606230001 fan-out, 2026-07); the other
  suite ontologies (tawami/okibi/toi) are not yet migrated and stay plain top-level maps.
  Tolerate both so this shared reader keeps working for every actor regardless of
  migration state."
  [content]
  (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

(defn- reconstitute-entity
  "Strip :db/id + the promoted :mio.ontology namespace back to the original
  bare key (:mio.ontology/id was already namespaced pre-transform under
  :ontology/* and is left untouched — irrelevant here since only
  :unrepresentable is read)."
  [entity]
  (into {}
        (map (fn [[k v]] [(keyword (name k)) v]))
        (dissoc entity :db/id)))

(defn- unrepresentable [ontology-path]
  (let [content (edn/read-string (slurp ontology-path))
        m (if (tx-data? content) (reconstitute-entity (first content)) content)]
    (:unrepresentable m)))

;; per-actor spec: how to read its ontology, render its full datoms, and list its seed ids
(def specs
  [{:actor "mio"
    :ontology (resource-path "mio/kotoba/ontology.mio.edn")
    :datoms (fn [] (mio-a/render-datoms
                    (mio-a/analyze (mio-edn/claims (resource-path "mio/kotoba/seed.edn")))))
    :ids (fn [] (map :id (mio-edn/claims (resource-path "mio/kotoba/seed.edn"))))}
   {:actor "tawami"
    :ontology (resource-path "tawami/kotoba/ontology.tawami.edn")
    :datoms (fn [] (tawami-a/render-datoms
                    (tawami-a/analyze (tawami-edn/assets (resource-path "tawami/kotoba/seed.edn")))))
    :ids (fn [] (map :id (tawami-edn/assets (resource-path "tawami/kotoba/seed.edn"))))}
   {:actor "okibi"
    :ontology (resource-path "okibi/kotoba/ontology.okibi.edn")
    :datoms (fn [] (let [p (resource-path "okibi/kotoba/seed.edn")
                         s (okibi-edn/sources p)
                         k (okibi-edn/sinks p)]
                     (okibi-a/render-datoms (okibi-a/analyze s k))))
    :ids (fn [] (let [p (resource-path "okibi/kotoba/seed.edn")]
                  (concat (map :id (okibi-edn/sources p)) (map :id (okibi-edn/sinks p)))))}
   {:actor "toi"
    :ontology (resource-path "toi/kotoba/ontology.toi.edn")
    :datoms (fn [] (let [p (resource-path "toi/kotoba/seed.edn")
                         j (toi-edn/jobs p)
                         s (toi-edn/sites p)]
                     (toi-a/render-datoms (toi-a/analyze j s))))
    :ids (fn [] (let [p (resource-path "toi/kotoba/seed.edn")]
                  (concat (map :id (toi-edn/jobs p)) (map :id (toi-edn/sites p)))))}
   {:actor "yudane"
    :ontology (resource-path "yudane/kotoba/ontology.yudane.edn")
    :datoms (fn [] (yudane-a/render-datoms
                    (yudane-a/analyze (yudane-edn/offers (resource-path "yudane/kotoba/seed.edn")))))
    :ids (fn [] (map :id (yudane-edn/offers (resource-path "yudane/kotoba/seed.edn"))))}])

(defn leaks-in
  "The unrepresentable attrs that ACTUALLY appear in the datom EDN string (a leak).
  Pure — testable with synthetic input to prove the check is non-vacuous."
  [unrep edn-str]
  (vec (filter #(str/includes? edn-str %) unrep)))

(defn check-actor
  [spec]
  (let [unrep (unrepresentable (:ontology spec))
        edn ((:datoms spec))
        leaks (leaks-in unrep edn)
        ids ((:ids spec))
        dups (->> ids frequencies (filter (fn [[_ n]] (> n 1))) (map first) vec)]
    {:actor (:actor spec)
     :unrepresentable-checked (count unrep)
     :leaks leaks
     :ids-unique (empty? dups)
     :dup-ids dups
     :ok (and (empty? leaks) (empty? dups))}))

(defn validate [] (mapv check-actor specs))

(defn render-report
  [results]
  (str
   "# Energy Order Protocol — INTEGRITY report (ontology ⊨ code)\n\n"
   "For each actor: every `:unrepresentable` attribute its ontology declares is "
   "ACTUALLY absent from its emitted datoms (the charter gate is enforced, not just "
   "documented), and seed ids are unique.\n\n"
   "| actor | unrepresentable checked | leaks | ids unique | ok |\n|---|---|---|---|---|\n"
   (str/join "\n"
             (for [r results]
               (str "| " (:actor r) " | " (:unrepresentable-checked r)
                    " | " (if (empty? (:leaks r)) "none" (str/join "," (:leaks r)))
                    " | " (:ids-unique r) " | " (if (:ok r) "✓" "✗") " |")))
   "\n\n_a green run = no charter gate has silently regressed across the suite._\n"))

#?(:clj
   (defn -main [& _]
     (let [results (validate)]
       (println (render-report results))
       (let [bad (remove :ok results)]
         (println (str "-- " (count results) " actors checked, "
                       (reduce + (map :unrepresentable-checked results))
                       " gate-attrs verified absent, "
                       (count bad) " failing --"))
         (System/exit (if (empty? bad) 0 1))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
