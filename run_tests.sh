#!/usr/bin/env bash
# Energy Order Protocol — suite-level digest test runner (babashka).
set -uo pipefail
cd "$(dirname "$0")"

for suite in test_digest test_cells test_validate test_conformance; do
  echo "== test/energy_order/${suite}.cljc =="
  bb "test/energy_order/${suite}.cljc"
done
