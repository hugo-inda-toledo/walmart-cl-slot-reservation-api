#!/usr/bin/env bash
# Contratos estáticos de la prueba de carga. La ejecución real se valida con Docker Compose.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scenario="$root/load/flash-sale.js"
wrapper="$root/load/run-flash-sale.sh"
compose="$root/docker-compose.yml"

require() {
  local file="$1"
  local pattern="$2"
  grep -Fq -- "$pattern" "$file" || {
    printf 'FALTA %s en %s\n' "$pattern" "$file" >&2
    exit 1
  }
}

require "$compose" 'grafana/k6:1.8.2'
require "$compose" 'profiles: [load]'
require "$compose" './load:/scripts:ro'
require "$compose" './load/results:/results'
require "$scenario" "executor: 'per-vu-iterations'"
require "$scenario" 'iterations: 1'
require "$scenario" 'X-Customer-Id'
require "$scenario" 'Idempotency-Key'
require "$scenario" 'holds_created: [`count==${expected}`]'
require "$scenario" 'bookings_confirmed: [`count==${expected}`]'
require "$scenario" "unexpected_responses: ['count==0']"
require "$scenario" "summaryTrendStats"
require "$scenario" "'p(99)'"
require "$scenario" '/results/flash-sale-summary.json'
require "$wrapper" 'docker compose'
require "$wrapper" 'docker-compose'
require "$wrapper" 'catalog_delivery_slot'
require "$wrapper" 'reservation_booking'
require "$wrapper" 'reserved + held > capacity'
require "$wrapper" 'SLOT_ID'
require "$wrapper" 'INITIAL_FREE'
require "$wrapper" 'RUN_ID'
require "$wrapper" 'RUN_ID solo puede contener letras, números, guiones y guiones bajos.'
require "$wrapper" '^[A-Za-z0-9_-]+$'
