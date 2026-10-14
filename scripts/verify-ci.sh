#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repository_root}"

docker run --rm -v "$PWD:/repo" -w /repo \
  rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 \
  -color .github/workflows/ci.yml
npx --yes @redocly/cli@2.62.1 lint src/main/resources/openapi/openapi.yml --format=github-actions
./mvnw -B -DskipTests org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom

mkdir -p target
set +e
docker run --rm -v "$PWD:/workspace" -w /workspace \
  ghcr.io/google/osv-scanner@sha256:f7ba4be68bac8086b1f88fd598fdca1ca67239c79ad2c2b5c78e03a82e5187c4 \
  scan source --config /workspace/osv-scanner.toml --format json --sbom /workspace/target/bom.json --output /workspace/target/osv-results.json 2> target/osv-scanner.stderr
status=$?
set -e

if grep -Eqi 'failed to (get response|resolve transitive|fetch Maven)|API query failed' target/osv-scanner.stderr; then
  echo "OSV-Scanner could not resolve dependency metadata." >&2
  cat target/osv-scanner.stderr >&2
  exit 127
fi
if [[ ! -s target/osv-results.json ]] || ! jq -e '.results | type == "array"' target/osv-results.json >/dev/null; then
  echo "OSV-Scanner could not resolve dependencies or produce a valid report." >&2
  exit "${status:-127}"
fi
if [[ "$status" -ne 0 && "$status" -ne 1 ]]; then
  echo "OSV-Scanner failed with exit code $status." >&2
  exit "$status"
fi
./scripts/check-osv-severity.sh target/osv-results.json
