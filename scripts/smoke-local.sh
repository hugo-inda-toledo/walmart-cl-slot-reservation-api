#!/usr/bin/env bash
# Verifica las URLs públicas del stack local de la API. No requiere jq ni Python.
set -euo pipefail

base_url="${1:-${BASE_URL:-http://localhost:${API_PORT:-8080}}}"
base_url="${base_url%/}"
curl_options=(--silent --connect-timeout 5 --max-time 15)
failed=0

request() {
  local path="$1"
  if ! RESPONSE_BODY="$(curl "${curl_options[@]}" "$base_url$path")"; then
    RESPONSE_HEADERS=''
    RESPONSE_STATUS="000"
    return 1
  fi

  if ! RESPONSE_HEADERS="$(curl "${curl_options[@]}" -D - -o /dev/null -w '\n%{http_code}' "$base_url$path")"; then
    RESPONSE_STATUS="000"
    return 1
  fi
  RESPONSE_STATUS="${RESPONSE_HEADERS##*$'\n'}"
}

ok() {
  printf 'OK %s\n' "$1"
}

fail() {
  printf 'FALLA %s\n' "$1"
  failed=1
}

is_problem_details() {
  local result
  shopt -s nocasematch
  [[ "$RESPONSE_HEADERS" =~ content-type:[[:space:]]*application/problem\+json([\;[:space:]]|$) ]]
  result=$?
  shopt -u nocasematch
  return "$result"
}

if ! request '/actuator/health/readiness'; then
  fail 'readiness: no se pudo conectar a la API'
elif [[ "$RESPONSE_STATUS" != '200' ]]; then
  fail "readiness: se esperaba HTTP 200 y se recibió $RESPONSE_STATUS"
elif [[ ! "$RESPONSE_BODY" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]; then
  fail 'readiness: la respuesta no informa status UP'
else
  ok 'readiness'
fi

if ! request '/swagger-ui.html'; then
  fail 'Swagger UI: no se pudo conectar a la API'
elif [[ "$RESPONSE_STATUS" != '200' && "$RESPONSE_STATUS" != '302' ]]; then
  fail "Swagger UI: se esperaba HTTP 200 o 302 y se recibió $RESPONSE_STATUS"
else
  ok 'Swagger UI'
fi

if ! request '/openapi/openapi.yml'; then
  fail 'contrato OpenAPI: no se pudo conectar a la API'
elif [[ "$RESPONSE_STATUS" != '200' ]]; then
  fail "contrato OpenAPI: se esperaba HTTP 200 y se recibió $RESPONSE_STATUS"
elif [[ ! "$RESPONSE_BODY" =~ openapi:[[:space:]]*3\.1(\.|[[:space:]]|$) ]]; then
  fail 'contrato OpenAPI: no contiene openapi: 3.1'
else
  ok 'contrato OpenAPI'
fi

if ! request '/api/v1/demo-addresses'; then
  fail 'direcciones demo: no se pudo conectar a la API'
elif [[ "$RESPONSE_STATUS" != '200' ]]; then
  fail "direcciones demo: se esperaba HTTP 200 y se recibió $RESPONSE_STATUS"
elif [[ "$RESPONSE_BODY" != *la-serena-monjitas* ]]; then
  fail 'direcciones demo: no contiene la-serena-monjitas'
else
  ok 'direcciones demo'
fi

if ! request '/api/v1/delivery-dates?comunaCode=04101&latitude=-29.9045&longitude=-71.2489'; then
  fail 'fecha agotada La Serena: no se pudo conectar a la API'
elif [[ "$RESPONSE_STATUS" != '200' ]]; then
  fail "fecha agotada La Serena: se esperaba HTTP 200 y se recibió $RESPONSE_STATUS"
elif [[ "$RESPONSE_BODY" != *'"SOLD_OUT"'* ]]; then
  fail 'fecha agotada La Serena: no contiene SOLD_OUT; verifica el modo demo'
else
  ok 'fecha agotada La Serena'
fi

if ! request '/api/v1/no-existe'; then
  fail '404 Problem Details: no se pudo conectar a la API'
elif [[ "$RESPONSE_STATUS" != '404' ]]; then
  fail "404 Problem Details: se esperaba HTTP 404 y se recibió $RESPONSE_STATUS"
elif ! is_problem_details; then
  fail '404 Problem Details: Content-Type no es application/problem+json'
else
  ok '404 Problem Details'
fi

exit "$failed"
