#!/usr/bin/env bash
# Regenera los límites comunales simplificados de las regiones de Coquimbo (04) y Metropolitana (13)
# a partir del shapefile "División comunal" de la Mapoteca SIIT de la Biblioteca del Congreso Nacional.
#
# Uso: scripts/geo/build-comuna-boundaries.sh SALIDA.sql
#
# Requiere curl, unzip, shasum (o sha256sum) y Node con npx. No forma parte de ./mvnw verify.
# Misma entrada => mismo archivo SQL byte a byte. Si la BCN publica un archivo distinto, la verificación
# SHA-256 falla de forma explícita; regenerar con datos nuevos implica fijar el nuevo hash y escribir una
# migración V2_n nueva (nunca editar una migración ya aplicada).
set -euo pipefail

readonly SOURCE_URL="https://www.bcn.cl/obtienearchivo?id=repositorio/10221/10396/5/comunas_final.zip"
readonly SOURCE_SHA256="${BCN_COMUNAS_SHA256:-cca97e9c0d8ec28dbceb17242489a80a9ffc817f7bb926f9f5cde5b61eb130a6}"
readonly MAPSHAPER="mapshaper@0.7.68"
readonly SIMPLIFY_PERCENT="8%"
# El servidor de la BCN responde 401 a clientes sin User-Agent de navegador.
readonly USER_AGENT="Mozilla/5.0 (compatible; slot-reservation-geo-build)"

if [[ $# -ne 1 ]]; then
  echo "Uso: build-comuna-boundaries.sh SALIDA.sql" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_dir}/../.." && pwd)"
output="$1"
cache_dir="${repository_root}/target/geo-cache"
archive="${cache_dir}/comunas_final.zip"

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

mkdir -p "${cache_dir}"
if [[ ! -s "${archive}" ]]; then
  echo "Descargando ${SOURCE_URL}" >&2
  curl --fail --location --silent --show-error --max-time 600 --user-agent "${USER_AGENT}" \
    --output "${archive}.part" "${SOURCE_URL}"
  mv "${archive}.part" "${archive}"
fi

actual_sha256="$(sha256_of "${archive}")"
if [[ "${actual_sha256}" != "${SOURCE_SHA256}" ]]; then
  echo "SHA-256 inesperado para ${archive}" >&2
  echo "  esperado: ${SOURCE_SHA256}" >&2
  echo "  obtenido: ${actual_sha256}" >&2
  echo "La fuente cambió: revise el archivo de la BCN antes de fijar un hash nuevo." >&2
  exit 1
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT
unzip -q "${archive}" -d "${work_dir}/shp"

# Coquimbo y Metropolitana; simplificación sobre arcos compartidos (topología preservada) conservando
# todas las partes; reproyección de Web Mercator a WGS 84; limpieza y coordenadas con 6 decimales.
npx --yes "${MAPSHAPER}" "${work_dir}/shp/comunas.shp" \
  -filter 'codregion == 4 || codregion == 13' \
  -filter-fields cod_comuna \
  -simplify "${SIMPLIFY_PERCENT}" keep-shapes \
  -proj wgs84 \
  -clean \
  -o "${work_dir}/comunas.geojson" format=geojson precision=0.000001

node "${script_dir}/geojson-to-sql.mjs" \
  "${work_dir}/comunas.geojson" \
  "${script_dir}/expected-comunas-04-13.txt" \
  "${output}"
echo "Escrito ${output}" >&2
