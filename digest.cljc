#!/usr/bin/env bb
;; Energy Order Protocol — cross-actor DIGEST (the suite SSoT / orchestrator).
(ns energy-order.digest
  "digest.cljc — the Energy Order Protocol cross-actor composition (ADR-2606211200).

  This is the suite ORCHESTRATOR, above all five actors (so it depends on all of
  them; no actor depends on it). It runs the full pipeline once —
    撓/燠/樋/委  →  claim emitters  →  澪 mio verify (§9)  →  reward proposals
  — and renders ONE unified picture: the org Flowrate, per-leg contribution, per
  flow-class breakdown, and the advisory moyai reward total. It content-addresses
  the digest (via the shared mio.kotoba commit-DAG CID) so the whole org-wide
  Energy Order state is verifiable as one CID.

  OBSERVATION ONLY — a composition of the legs' observations; it actuates nothing
  and holds no key. The reward total is advisory (1 SBT=1 vote disposes)."
  (:require [mio.methods.analyze :as mio]
            [mio.methods.reward :as reward]
            [mio.methods.kotoba :as k]
            [tawami.methods.tawami-edn :as tawami-edn]
            [tawami.methods.claim :as tawami-claim]
            [okibi.methods.okibi-edn :as okibi-edn]
            [okibi.methods.claim :as okibi-claim]
            [toi.methods.toi-edn :as toi-edn]
            [toi.methods.claim :as toi-claim]
            [yudane.methods.yudane-edn :as yudane-edn]
            [yudane.methods.claim :as yudane-claim]
            [clojure.string :as str]))

(def ^:private default-seeds
  {:tawami "20-actors/tawami/kotoba/seed.edn"
   :okibi  "20-actors/okibi/kotoba/seed.edn"
   :toi    "20-actors/toi/kotoba/seed.edn"
   :yudane "20-actors/yudane/kotoba/seed.edn"})

(defn all-claims
  "Compose the four legs' flow-improvement claims from their seeds."
  ([] (all-claims default-seeds))
  ([seeds]
   (vec (concat
         (tawami-claim/from-assets (tawami-edn/assets (:tawami seeds)))
         (okibi-claim/from-nodes (okibi-edn/sources (:okibi seeds))
                                 (okibi-edn/sinks (:okibi seeds)))
         (toi-claim/from-nodes (toi-edn/jobs (:toi seeds))
                               (toi-edn/sites (:toi seeds)))
         (yudane-claim/from-offers (yudane-edn/offers (:yudane seeds)))))))

(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn summary
  "The org-wide Energy Order picture from a set of claims."
  [claims]
  (let [a (mio/analyze claims)
        rows (get a "claims")
        ps (reward/proposals a)
        verified (filter #(= :verified (get % "verdict")) rows)
        by-leg (->> rows
                    (group-by #(get % "source_actor"))
                    (map (fn [[actor crows]]
                           (let [v (filter #(= :verified (get % "verdict")) crows)]
                             {:actor actor
                              :claims (count crows)
                              :verified (count v)
                              :flowrate (reduce + 0.0 (map #(get % "useful_flow_score") v))})))
                    (sort-by :actor)
                    vec)
        by-class (->> verified
                      (group-by #(get % "flow_class"))
                      (map (fn [[cls crows]]
                             {:flow-class cls
                              :verified (count crows)
                              :flowrate (reduce + 0.0 (map #(get % "useful_flow_score") crows))}))
                      (sort-by #(- (:flowrate %)))
                      vec)]
    {:total-claims (count rows)
     :verified-claims (count verified)
     :flowrate (get-in a ["totals" "verified_flowrate_score"])
     :total-moyai-credit (:total-moyai-credit (reward/totals ps))
     :by-leg by-leg
     :by-flow-class by-class}))

;; ── cross-actor composition datoms + content-address ─────────────────────────

(defn- add [e a v] [":db/add" e a v])

(defn datoms
  "Cross-actor :eo.composition/* + :eo.leg/* datoms (the suite SSoT, tatara pattern)."
  [s]
  (let [e "eo-digest:flowrate"
        head [(add e ":eo.composition/total-claims" (:total-claims s))
              (add e ":eo.composition/verified-claims" (:verified-claims s))
              (add e ":eo.composition/flowrate" (round3 (:flowrate s)))
              (add e ":eo.composition/total-moyai-credit" (round3 (:total-moyai-credit s)))
              (add e ":eo/derived" true)]
        legs (mapcat (fn [l]
                       (let [le (str "eo-leg:" (:actor l))]
                         [(add le ":eo.leg/claims" (:claims l))
                          (add le ":eo.leg/verified" (:verified l))
                          (add le ":eo.leg/flowrate" (round3 (:flowrate l)))
                          (add le ":eo/derived" true)]))
                     (:by-leg s))]
    (vec (concat head legs))))

(defn digest-cid
  "Content-address the digest (via the shared mio.kotoba commit-DAG CID) — the whole
  org-wide Energy Order state as one verifiable CID."
  [s]
  (k/tx-cid (datoms s)))

;; ── unified report ───────────────────────────────────────────────────────────

(defn render-report
  [s]
  (str
   "# Energy Order Protocol — cross-actor DIGEST\n\n"
   "OBSERVATION ONLY. The whole suite in one picture: 撓/燠/樋/委 emit flow-improvement "
   "claims → 澪 mio verifies (§9) + accounts the org **Flowrate** → advisory moyai reward "
   "(1 SBT=1 vote disposes). `Proof of Work → Proof of Useful Flow`: value is ORDERED "
   "flow, never CONSUMED energy. Digest CID `" (digest-cid s) "`.\n\n"
   "## Org Flowrate\n\n"
   "- **verified Flowrate = " (round3 (:flowrate s)) " kWh-equiv** ("
   (:verified-claims s) "/" (:total-claims s) " claims verified)\n"
   "- advisory moyai reciprocity credit: " (round3 (:total-moyai-credit s))
   " (drafted-unsent; cash≡0)\n\n"
   "## Contribution by leg\n\n"
   "| leg | claims | verified | Flowrate kWh-equiv |\n|---|---|---|---|\n"
   (str/join "\n"
             (for [l (:by-leg s)]
               (str "| " (:actor l) " | " (:claims l) " | " (:verified l)
                    " | " (round3 (:flowrate l)) " |")))
   "\n\n## Flowrate by flow class\n\n"
   "| flow class | verified | Flowrate kWh-equiv |\n|---|---|---|\n"
   (str/join "\n"
             (for [c (:by-flow-class s)]
               (str "| " (name (:flow-class c)) " | " (:verified c)
                    " | " (round3 (:flowrate c)) " |")))
   "\n\n_撓 tawami flexibility · 燠 okibi waste-heat · 樋 toi compute-routing · 委 yudane "
   "intention → 澪 mio verification backbone. hikari actuates under Council gate._\n"))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& _]
     (let [s (summary (all-claims))]
       (println (render-report s))
       (println (str "-- digest cid " (digest-cid s) " · Flowrate "
                     (round3 (:flowrate s)) " kWh-equiv · "
                     (:verified-claims s) "/" (:total-claims s) " verified --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
