#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: check-osv-severity.sh REPORT.json" >&2
  exit 2
fi

jq -e '.results | type == "array"' "$1" >/dev/null
exec python3 "$(dirname "${BASH_SOURCE[0]}")/check-osv-severity.py" "$1"
