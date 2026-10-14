#!/usr/bin/env bash
# Ejecuta k6 contra una ventana real y contrasta el resultado con PostgreSQL.
set -euo pipefail

if docker compose version >/dev/null 2>&1; then
  compose=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose=(docker-compose)
else
  echo 'No se encontró Docker Compose (docker compose ni docker-compose).' >&2
  exit 1
fi

VUS="${VUS:-300}"
RUN_ID="${RUN_ID:-$(date +%s)}"
if [[ ! "$VUS" =~ ^[1-9][0-9]*$ ]]; then
  echo 'VUS debe ser un entero positivo.' >&2
  exit 1
fi
if [[ ! "$RUN_ID" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo 'RUN_ID solo puede contener letras, números, guiones y guiones bajos.' >&2
  exit 1
fi

api_container="$("${compose[@]}" ps -q api)"
if [[ -z "$api_container" ]] || [[ "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' "$api_container" 2>/dev/null || true)" != 'healthy' ]]; then
  echo 'Levanta primero el stack: docker compose up --build -d --wait' >&2
  exit 1
fi

sql() {
  "${compose[@]}" exec -T postgres psql -U slot_reservation -d slot_reservation -At -c "$1"
}

selection="$(sql "SELECT s.id, s.capacity - s.reserved - s.held FROM catalog_delivery_slot s JOIN geo_delivery_zone z ON z.id = s.zone_id WHERE z.status = 'ACTIVE' AND s.cutoff_at > now() + interval '5 minutes' AND s.reserved + s.held < s.capacity ORDER BY 2 DESC, s.start_at LIMIT 1;")"
if [[ -z "$selection" ]]; then
  echo 'No quedan ventanas con cupo: ejecuta docker compose down -v && docker compose up --build -d --wait' >&2
  exit 1
fi
IFS='|' read -r SLOT_ID INITIAL_FREE <<< "$selection"
if [[ -z "$SLOT_ID" || ! "$INITIAL_FREE" =~ ^[0-9]+$ ]]; then
  echo "La consulta de selección devolvió un formato inesperado: $selection" >&2
  exit 1
fi
EXPECTED=$(( INITIAL_FREE < VUS ? INITIAL_FREE : VUS ))
export SLOT_ID INITIAL_FREE VUS RUN_ID

mkdir -p load/results
chmod 0777 load/results
set +e
"${compose[@]}" --profile load run --rm -e SLOT_ID -e INITIAL_FREE -e VUS -e RUN_ID k6 run /scripts/flash-sale.js
k6_status=$?
set -e

over_capacity="$(sql 'SELECT count(*) FROM catalog_delivery_slot WHERE reserved + held > capacity;')"
final_free="$(sql "SELECT capacity - reserved - held FROM catalog_delivery_slot WHERE id = '$SLOT_ID';")"
confirmed="$(sql "SELECT count(*) FROM reservation_booking WHERE slot_id = '$SLOT_ID' AND status = 'CONFIRMED' AND order_id LIKE 'k6-$RUN_ID-%';")"
summary_file='load/results/flash-sale-summary.json'
summary='{}'
[[ -f "$summary_file" ]] && summary="$(<"$summary_file")"
json_number() {
  local key="$1"
  local value
  value="$(printf '%s' "$summary" | sed -nE "s/.*\\\"$key\\\"[[:space:]]*:[[:space:]]*([0-9.]+).*/\\1/p" | head -n 1)"
  printf '%s' "${value:-n/a}"
}

printf '\n%-24s %s\n' 'Métrica' 'Resultado'
printf '%-24s %s\n' 'Usuarios virtuales' "$VUS"
printf '%-24s %s\n' 'Cupo inicial' "$INITIAL_FREE"
printf '%-24s %s\n' 'Retenciones' "$(json_number holdsCreated)"
printf '%-24s %s\n' 'Confirmaciones' "$(json_number bookingsConfirmed)"
printf '%-24s %s\n' 'SLOT_FULL' "$(json_number slotFull)"
printf '%-24s %s ms\n' 'p95' "$(json_number p95Ms)"
printf '%-24s %s\n' 'Ventanas sobre capacidad' "$over_capacity"

expected_free=$(( INITIAL_FREE - VUS ))
(( expected_free < 0 )) && expected_free=0
if (( k6_status != 0 || over_capacity != 0 || final_free != expected_free || confirmed != EXPECTED )); then
  echo "FALLA invariante: k6=$k6_status, sobrecapacidad=$over_capacity, cupo final=$final_free (esperado $expected_free), confirmadas=$confirmed (esperado $EXPECTED)" >&2
  exit 1
fi
echo 'OK venta relámpago: invariante de capacidad verificada.'
