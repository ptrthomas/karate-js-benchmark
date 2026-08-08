#!/usr/bin/env bash
#
# Build karate-js from a local checkout of karatelabs/karate (main) and run the benchmark.
#
# The benchmark deliberately does NOT use the karate-js published to Maven Central - that lags
# main by months. karate-js is installed into ~/.m2 from source here (the same "rebuild core
# first" step karate-ext does), and pom.xml's <karate.version> must match the <version> in the
# karate root pom.
#
# Usage:
#   ./build.sh                  # build karate-js from source, then run the benchmark
#   ./build.sh --no-run         # build only
#   ./build.sh results.csv      # run and write the CSV to a specific path
#   KARATE_SRC=/path/to/karate ./build.sh
#
set -euo pipefail

cd "$(dirname "$0")"

# locate the karate checkout. An explicit KARATE_SRC is taken at its word - if it is wrong
# we fail rather than silently falling back to some other checkout and benchmarking that.
if [[ -n "${KARATE_SRC:-}" ]]; then
  if [[ ! -f "$KARATE_SRC/karate-js/pom.xml" ]]; then
    echo "ERROR: KARATE_SRC=$KARATE_SRC is not a karatelabs/karate checkout" >&2
    echo "       (no karate-js/pom.xml under it)" >&2
    exit 1
  fi
else
  for candidate in ../karate ../../karate; do
    if [[ -f "$candidate/karate-js/pom.xml" ]]; then
      KARATE_SRC=$candidate
      break
    fi
  done
fi

if [[ -z "${KARATE_SRC:-}" ]]; then
  echo "ERROR: could not find a karatelabs/karate checkout at ../karate or ../../karate." >&2
  echo "       set KARATE_SRC=/path/to/karate and re-run." >&2
  exit 1
fi
KARATE_SRC=$(cd "$KARATE_SRC" && pwd)

echo "==> karate source: $KARATE_SRC ($(git -C "$KARATE_SRC" rev-parse --abbrev-ref HEAD) @ $(git -C "$KARATE_SRC" rev-parse --short HEAD))"

# the version karate main currently builds - must match <karate.version> in our pom
SRC_VERSION=$(mvn -q -f "$KARATE_SRC/pom.xml" help:evaluate -Dexpression=project.version -DforceStdout)
OUR_VERSION=$(mvn -q help:evaluate -Dexpression=karate.version -DforceStdout)

if [[ "$SRC_VERSION" != "$OUR_VERSION" ]]; then
  echo "ERROR: version mismatch - karate main is $SRC_VERSION, our pom pins $OUR_VERSION." >&2
  echo "       update <karate.version> in pom.xml to $SRC_VERSION." >&2
  exit 1
fi

# -am is REQUIRED: it also installs the karate-parent pom that karate-js declares as its
# parent. RC versions of that parent are not on Maven Central, so without -am the install
# looks fine but this project cannot resolve karate-js on any machine with a cold ~/.m2.
echo "==> building karate-js $SRC_VERSION from source"
mvn -q -f "$KARATE_SRC/pom.xml" -pl karate-js -am install -DskipTests

if [[ "${1:-}" == "--no-run" ]]; then
  echo "==> build only, done"
  exit 0
fi

# archive the previous run before clean removes it - a benchmark result is expensive to
# reproduce and target/ is about to be wiped
if [[ -f target/benchmark.csv ]]; then
  mkdir -p results
  STAMP=$(date +%Y%m%d-%H%M%S)
  cp target/benchmark.csv "results/prev-$STAMP.csv"
  echo "==> archived previous run to results/prev-$STAMP.csv"
fi

echo "==> running benchmark"
mvn -q clean compile
mvn -q compile exec:java ${1:+-Dexec.args="$1"}
