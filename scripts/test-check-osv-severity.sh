#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
checker="${repository_root}/scripts/check-osv-severity.sh"
fixtures="${repository_root}/scripts/testdata"

if "${checker}" "${fixtures}/below-threshold.json"; then
  :
else
  echo "Expected below-threshold report to pass." >&2
  exit 1
fi

if output="$("${checker}" "${fixtures}/scanner-output-critical.json" 2>&1)"; then
  echo "Expected critical report to fail." >&2
  exit 1
fi

grep -Fq 'org.example:unsafe-library@1.2.3' <<<"${output}"
grep -Fq 'GHSA-test-critical' <<<"${output}"
